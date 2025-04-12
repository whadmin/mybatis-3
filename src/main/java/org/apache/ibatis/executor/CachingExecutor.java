/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * CachingExecutor 是 MyBatis 中的二级缓存执行器，用于为底层执行器添加缓存功能。
 *
 * 功能：
 * 1. 为 MyBatis 提供二级缓存支持，可以跨 SqlSession 共享缓存数据
 * 2. 在查询操作时优先从缓存获取数据，减少数据库访问
 * 3. 在更新操作时根据配置决定是否清空相关缓存
 *
 * 设计原理：
 * 1. 装饰器模式：CachingExecutor 装饰了实际的执行器（如 SimpleExecutor），在不改变原有执行器功能的基础上添加缓存功能
 * 2. 委托模式：大部分操作通过委托给底层执行器来完成，只在必要时添加缓存处理逻辑
 * 3. 事务一致性：使用 TransactionalCacheManager 管理缓存，确保缓存操作与事务保持一致
 *
 * 缓存工作流程：
 * 1. 查询时优先检查缓存，命中则直接返回
 * 2. 缓存未命中时执行实际查询并将结果存入缓存
 * 3. 执行更新操作时根据配置清空相关缓存
 * 4. 事务提交时提交缓存，事务回滚时回滚缓存
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor {

  /**
   * 实际的执行器，CachingExecutor 将大部分操作委托给它执行。
   * 可能是 SimpleExecutor、ReuseExecutor 或 BatchExecutor。
   */
  private final Executor delegate;

  /**
   * 事务缓存管理器，用于管理事务中的缓存操作。
   * 确保缓存的一致性和事务性，只有当事务提交时才会真正更新缓存。
   */
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  /**
   * 构造函数，接收一个执行器作为被装饰对象。
   *
   * @param delegate 被装饰的执行器
   */
  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    // 设置执行器的包装器为当前对象，形成双向关联
    delegate.setExecutorWrapper(this);
  }

  /**
   * 获取当前事务对象。
   *
   * @return 当前事务对象
   */
  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  /**
   * 关闭执行器，同时处理缓存的提交或回滚。
   *
   * @param forceRollback 是否强制回滚
   */
  @Override
  public void close(boolean forceRollback) {
    try {
      // 如果需要强制回滚，则回滚缓存；否则提交缓存
      // 解决 issues #499, #524 and #573
      if (forceRollback) {
        tcm.rollback();
      } else {
        tcm.commit();
      }
    } finally {
      // 无论缓存操作成功与否，都要关闭底层执行器
      delegate.close(forceRollback);
    }
  }

  /**
   * 检查执行器是否已关闭。
   *
   * @return 如果执行器已关闭则返回 true，否则返回 false
   */
  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  /**
   * 执行更新操作（包括 INSERT、UPDATE、DELETE）。
   * 更新操作会根据映射语句的配置决定是否清空相关缓存。
   *
   * @param ms 映射语句
   * @param parameterObject 参数对象
   * @return 受影响的行数
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    // 如果需要刷新缓存，则清空对应的缓存
    flushCacheIfRequired(ms);
    // 委托给实际执行器执行更新操作
    return delegate.update(ms, parameterObject);
  }

  /**
   * 执行游标查询，返回一个可遍历的结果集游标。
   * 游标查询也会根据映射语句的配置决定是否清空缓存。
   *
   * @param ms 映射语句
   * @param parameter 参数对象
   * @param rowBounds 分页信息
   * @return 查询结果游标
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    // 如果需要刷新缓存，则清空对应的缓存
    flushCacheIfRequired(ms);
    // 委托给实际执行器执行游标查询
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  /**
   * 执行查询操作并返回结果列表。
   * 这个方法是一个简化版，内部会计算缓存键并调用带缓存键的查询方法。
   *
   * @param ms 映射语句
   * @param parameterObject 参数对象
   * @param rowBounds 分页信息
   * @param resultHandler 结果处理器
   * @return 查询结果列表
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler)
      throws SQLException {
    // 获取绑定的 SQL 对象，包含最终要执行的 SQL 和参数信息
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    // 创建缓存键，用于在缓存中标识这个查询
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
    // 调用重载的 query 方法执行查询
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 执行查询操作并返回结果列表，支持缓存。
   * 这是查询操作的核心方法，实现了缓存的逻辑：
   * 1. 检查是否有可用的缓存
   * 2. 如果启用了缓存，尝试从缓存中获取结果
   * 3. 如果缓存未命中，执行实际查询并将结果存入缓存
   *
   * @param ms 映射语句
   * @param parameterObject 参数对象
   * @param rowBounds 分页信息
   * @param resultHandler 结果处理器
   * @param key 缓存键
   * @param boundSql 绑定的 SQL 对象
   * @return 查询结果列表
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler,
      CacheKey key, BoundSql boundSql) throws SQLException {
    // 获取映射语句中配置的缓存
    Cache cache = ms.getCache();
    // 如果有缓存配置
    if (cache != null) {
      // 根据配置决定是否刷新缓存
      flushCacheIfRequired(ms);
      // 如果当前查询允许使用缓存且没有结果处理器，则尝试使用缓存
      // 注意：有结果处理器时不使用缓存，因为结果处理器通常用于自定义结果处理逻辑
      if (ms.isUseCache() && resultHandler == null) {
        // 确保存储过程不包含 OUT 参数，因为缓存不支持 OUT 参数
        ensureNoOutParams(ms, boundSql);
        // 尝试从缓存中获取结果
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          // 缓存未命中，执行实际查询
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          // 将查询结果存入缓存 (解决 issue #578 和 #116)
          tcm.putObject(cache, key, list);
        }
        return list;
      }
    }
    // 如果没有缓存配置或不使用缓存，直接执行查询
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 刷新并返回批处理语句的执行结果。
   *
   * @return 批处理结果列表
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  /**
   * 提交事务，同时提交缓存。
   *
   * @param required 是否需要提交
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  @Override
  public void commit(boolean required) throws SQLException {
    // 首先提交底层执行器的事务
    delegate.commit(required);
    // 然后提交缓存，将暂存的缓存修改应用到实际缓存中
    tcm.commit();
  }

  /**
   * 回滚事务，同时回滚缓存。
   *
   * @param required 是否需要回滚
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      // 首先回滚底层执行器的事务
      delegate.rollback(required);
    } finally {
      // 如果需要回滚，则回滚缓存，丢弃暂存的缓存修改
      if (required) {
        tcm.rollback();
      }
    }
  }

  /**
   * 确保存储过程不包含 OUT 参数，因为缓存不支持 OUT 参数。
   * 如果存在 OUT 参数，则抛出异常。
   *
   * @param ms 映射语句
   * @param boundSql 绑定的 SQL 对象
   */
  private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
    // 如果是存储过程调用
    if (ms.getStatementType() == StatementType.CALLABLE) {
      // 检查所有参数的模式
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        // 如果存在非 IN 模式的参数（OUT 或 INOUT），则抛出异常
        if (parameterMapping.getMode() != ParameterMode.IN) {
          throw new ExecutorException(
              "Caching stored procedures with OUT params is not supported.  Please configure useCache=false in "
                  + ms.getId() + " statement.");
        }
      }
    }
  }

  /**
   * 创建缓存键。
   * 缓存键用于在缓存中唯一标识一个查询。
   *
   * @param ms 映射语句
   * @param parameterObject 参数对象
   * @param rowBounds 分页信息
   * @param boundSql 绑定的 SQL 对象
   * @return 缓存键
   */
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  /**
   * 检查指定的查询是否已缓存。
   *
   * @param ms 映射语句
   * @param key 缓存键
   * @return 如果已缓存则返回 true，否则返回 false
   */
  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  /**
   * 延迟加载一个属性。
   *
   * @param ms 映射语句
   * @param resultObject 结果对象
   * @param property 属性名
   * @param key 缓存键
   * @param targetType 目标类型
   */
  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key,
      Class<?> targetType) {
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  /**
   * 清空本地缓存（一级缓存）。
   */
  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  /**
   * 如果映射语句配置需要刷新缓存，则清空相关缓存。
   * 通常在更新操作前调用。
   *
   * @param ms 映射语句
   */
  private void flushCacheIfRequired(MappedStatement ms) {
    Cache cache = ms.getCache();
    // 如果有缓存配置且需要刷新缓存，则清空缓存
    if (cache != null && ms.isFlushCacheRequired()) {
      tcm.clear(cache);
    }
  }

  /**
   * 设置执行器的包装器。
   * 在 CachingExecutor 中不支持此操作，因为它本身就是一个包装器。
   *
   * @param executor 执行器
   */
  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
