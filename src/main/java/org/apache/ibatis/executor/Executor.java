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

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * Executor 接口定义了 MyBatis 中的核心数据库操作方法。
 * 它是 MyBatis 执行 SQL 语句的基础接口，支持增删改查、事务管理和缓存操作。
 *
 * 不同的 Executor 实现类（如 SimpleExecutor、ReuseExecutor、BatchExecutor）提供了不同的执行策略。
 *
 * @author Clinton Begin
 */
public interface Executor {

  /**
   * 空的结果处理器，用于表示没有结果处理器。
   */
  ResultHandler NO_RESULT_HANDLER = null;

  /**
   * 执行更新操作（包括 INSERT、UPDATE、DELETE）。
   *
   * @param ms 映射的 SQL 语句
   * @param parameter 参数对象
   * @return 受影响的行数
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  int update(MappedStatement ms, Object parameter) throws SQLException;

  /**
   * 执行查询操作，支持缓存。
   *
   * @param ms 映射的 SQL 语句
   * @param parameter 参数对象
   * @param rowBounds 分页信息
   * @param resultHandler 结果处理器
   * @param cacheKey 缓存键
   * @param boundSql 绑定的 SQL 对象
   * @param <E> 返回结果的类型
   * @return 查询结果列表
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
      CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  /**
   * 执行查询操作，不使用缓存。
   *
   * @param ms 映射的 SQL 语句
   * @param parameter 参数对象
   * @param rowBounds 分页信息
   * @param resultHandler 结果处理器
   * @param <E> 返回结果的类型
   * @return 查询结果列表
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler)
      throws SQLException;

  /**
   * 执行查询操作，返回游标对象。
   *
   * @param ms 映射的 SQL 语句
   * @param parameter 参数对象
   * @param rowBounds 分页信息
   * @param <E> 返回结果的类型
   * @return 查询结果的游标
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  /**
   * 刷新批处理语句。
   *
   * @return 批处理结果列表
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  List<BatchResult> flushStatements() throws SQLException;

  /**
   * 提交事务。
   *
   * @param required 是否需要提交
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  void commit(boolean required) throws SQLException;

  /**
   * 回滚事务。
   *
   * @param required 是否需要回滚
   * @throws SQLException 如果执行过程中发生 SQL 异常
   */
  void rollback(boolean required) throws SQLException;

  /**
   * 创建缓存键。
   *
   * @param ms 映射的 SQL 语句
   * @param parameterObject 参数对象
   * @param rowBounds 分页信息
   * @param boundSql 绑定的 SQL 对象
   * @return 缓存键
   */
  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  /**
   * 检查指定的缓存键是否已缓存。
   *
   * @param ms 映射的 SQL 语句
   * @param key 缓存键
   * @return 如果已缓存则返回 true，否则返回 false
   */
  boolean isCached(MappedStatement ms, CacheKey key);

  /**
   * 清空本地缓存。
   */
  void clearLocalCache();

  /**
   * 延迟加载属性。
   *
   * @param ms 映射的 SQL 语句
   * @param resultObject 结果对象
   * @param property 属性名
   * @param key 缓存键
   * @param targetType 属性的目标类型
   */
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  /**
   * 获取当前事务对象。
   *
   * @return 事务对象
   */
  Transaction getTransaction();

  /**
   * 关闭执行器。
   *
   * @param forceRollback 是否强制回滚
   */
  void close(boolean forceRollback);

  /**
   * 检查执行器是否已关闭。
   *
   * @return 如果已关闭则返回 true，否则返回 false
   */
  boolean isClosed();

  /**
   * 设置执行器的包装器。
   *
   * @param executor 包装的执行器
   */
  void setExecutorWrapper(Executor executor);

}
