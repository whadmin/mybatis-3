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

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * MyBatis执行器的基础实现类，提供了模板方法模式的骨架实现
 * 主要功能：
 * 1. 处理一级缓存（会话级别的缓存）
 * 2. 处理事务管理
 * 3. 处理连接获取
 * 4. 处理语句执行
 * 5. 处理延迟加载
 *
 * 主要子类：
 * - SimpleExecutor: 简单执行器，每次执行创建新的Statement
 * - ReuseExecutor: 可重用执行器，重用Statement对象
 * - BatchExecutor: 批处理执行器，用于批量执行SQL
 *
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

  /**
   * 日志对象
   */
  private static final Log log = LogFactory.getLog(BaseExecutor.class);

  /**
   * 事务对象，用于管理数据库连接和事务操作
   */
  protected Transaction transaction;

  /**
   * 包装的执行器对象，用于实现装饰器模式
   */
  protected Executor wrapper;

  /**
   * 延迟加载队列，存储需要延迟加载的对象，
   * 使用示例：
   * <!-- 用户映射配置 -->
   * <resultMap id="userMap" type="User">
   *   <id property="id" column="user_id"/>
   *   <result property="name" column="user_name"/>
   *   <!-- 配置延迟加载的订单集合 -->
   *   <collection property="orders"
   *               select="getOrdersByUserId"
   *               column="user_id"
   *               fetchType="lazy"/>
   * </resultMap>
   *
   * <!-- 查询用户 -->
   * <select id="getUser" resultMap="userMap">
   *   SELECT user_id, user_name FROM users WHERE user_id = #{id}
   * </select>
   *
   * <!-- 查询订单，将被延迟调用 -->
   * <select id="getOrdersByUserId" resultType="Order">
   *   SELECT order_id, price FROM orders WHERE user_id = #{userId}
   * </select>
   *
   * // Java代码：
   * // 1. 查询用户
   * User user = sqlSession.selectOne("getUser", 1);
   * System.out.println("用户名: " + user.getName());
   *
   * // 2. 此时orders尚未加载，创建了DeferredLoad对象并加入队列
   *
   * // 3. 首次访问orders属性时触发延迟加载
   * System.out.println("订单数量: " + user.getOrders().size());
   *
   * 延迟加载流程：
   * 1. 执行主查询(getUser)获取用户基本信息
   * 2. 遇到延迟加载的orders属性，创建DeferredLoad对象
   * 3. 将DeferredLoad对象加入deferredLoads队列
   * 4. 当首次访问user.getOrders()时，触发加载
   * 5. 执行关联查询(getOrdersByUserId)获取订单信息
   * 6. 通过resultObject.setValue()将订单列表设置到用户对象
   */
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;

  /**
   * 一级缓存，用于存储普通查询结果
   * 作用域: Session级别或Statement级别（可配置）
   *
   * 工作原理：
   * 1. 执行查询时，先检查缓存中是否存在相同查询的结果
   * 2. 如果缓存命中，直接返回缓存的结果集，不再访问数据库
   * 3. 如果缓存未命中，执行数据库查询，并将结果存入缓存
   * 4. 在执行更新、提交或回滚时自动清空缓存
   *
   * 使用示例：
   * // XML配置文件中设置缓存范围
   * <settings>
   *   <!-- SESSION: 一个会话内共享缓存，默认值 -->
   *   <!-- STATEMENT: 仅在语句执行范围内有效 -->
   *   <setting name="localCacheScope" value="SESSION"/>
   * </settings>
   *
   * // 使用MyBatis API
   * // 1. 首次查询，访问数据库并缓存结果
   * List<User> users1 = sqlSession.selectList("getUsers", parameter);
   *
   * // 2. 相同参数再次查询，直接从缓存获取，不访问数据库
   * List<User> users2 = sqlSession.selectList("getUsers", parameter);
   *
   * // 3. 执行更新操作将清空缓存
   * sqlSession.update("updateUser", someParameter);
   *
   * // 4. 更新后再查询，缓存已清空，将再次访问数据库
   * List<User> users3 = sqlSession.selectList("getUsers", parameter);
   *
   * // 5. 手动清空缓存
   * sqlSession.clearCache();
   *
   * 缓存键组成：
   * 1. SQL语句ID (Mapper方法的完全限定名)
   * 2. 查询参数值
   * 3. 分页参数(RowBounds)
   * 4. SQL语句文本(包括动态SQL生成的实际SQL)
   * 5. 环境ID
   *
   * 注意事项：
   * 1. 一级缓存默认启用，且作用域为SESSION
   * 2. 可通过localCacheScope配置项修改作用域
   * 3. 使用resultHandler时不会启用缓存
   * 4. 存储过程查询也会缓存结果(可能是空列表)
   * 5. 在缓存生命周期内，返回的是同一对象引用
   */
  protected PerpetualCache localCache;

  /**
   * 一级缓存，用于存储存储过程的输出参数
   * 作用：缓存存储过程调用后的输出参数值，以便在相同参数再次调用时直接获取，避免重复执行
   *
   * 工作原理：
   * 1. 执行存储过程时，将包含输出参数的整个参数对象缓存在localOutputParameterCache中
   * 2. 同时，将存储过程执行的结果集(可能为空列表)存储在localCache中
   * 3. 当使用相同的参数再次调用相同的存储过程时：
   *    - 先检查localCache是否命中，若命中则不再执行查询
   *    - 然后从localOutputParameterCache中获取之前的输出参数值并填充到新的参数对象中
   * 4. 避免了重复执行数据库调用，提高性能
   *
   * 完整使用示例：
   *
   * CREATE PROCEDURE get_user_info(
   *     IN user_id INT,               -- 输入参数
   *     OUT total_orders INT,         -- 输出参数
   *     OUT total_spent DECIMAL(10,2) -- 输出参数
   * )
   * BEGIN
   *     -- 获取订单总数
   *     SELECT COUNT(*) INTO total_orders
   *     FROM orders WHERE user_id = user_id;
   *
   *     -- 获取消费总额
   *     SELECT SUM(amount) INTO total_spent
   *     FROM orders WHERE user_id = user_id;
   * END
   *
   * // 1. 准备参数Map，包含输入参数和输出参数占位符
   * Map<String, Object> paramMap = new HashMap<>();
   * paramMap.put("userId", 123);
   * paramMap.put("totalOrders", null); // OUT参数初始为null
   * paramMap.put("totalSpent", null);  // OUT参数初始为null
   *
   * // 2. 执行存储过程 - 首次调用会访问数据库
   * sqlSession.selectOne("getUserInfo", paramMap);
   *
   * // 3. 此时paramMap中已包含存储过程的输出值
   * Integer totalOrders = (Integer) paramMap.get("totalOrders");
   * BigDecimal totalSpent = (BigDecimal) paramMap.get("totalSpent");
   * System.out.println("用户订单数: " + totalOrders);
   * System.out.println("消费总额: " + totalSpent);
   *
   * // 4. 如果使用相同参数再次调用相同存储过程
   * // 会直接从localOutputParameterCache获取输出参数值
   * // 不会再次执行实际的存储过程
   * Map<String, Object> paramMap2 = new HashMap<>();
   * paramMap2.put("userId", 123);
   * paramMap2.put("totalOrders", null);
   * paramMap2.put("totalSpent", null);
   *
   * // 5. 第二次调用 - 直接使用缓存，不访问数据库
   * sqlSession.selectOne("getUserInfo", paramMap2);
   *
   * // 6. paramMap2中的值会从缓存中获取，而不是重新执行存储过程
   * Integer cachedTotalOrders = (Integer) paramMap2.get("totalOrders");
   * BigDecimal cachedTotalSpent = (BigDecimal) paramMap2.get("totalSpent");
   */
  protected PerpetualCache localOutputParameterCache;

  /**
   * MyBatis配置对象
   */
  protected Configuration configuration;

  /**
   * 查询堆栈深度，用于处理嵌套查询
   */
  protected int queryStack;

  /**
   * 执行器是否已关闭的标志
   */
  private boolean closed;

  /**
   * 构造函数
   *
   * @param configuration MyBatis配置对象
   * @param transaction 事务对象
   */
  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }

  /**
   * 获取当前事务对象
   * 如果执行器已关闭，则抛出异常
   *
   * @return 当前事务对象
   * @throws ExecutorException 如果执行器已关闭
   */
  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  /**
   * 关闭执行器
   * 执行流程：
   * 1. 回滚未完成的事务
   * 2. 关闭事务连接
   * 3. 清空所有缓存和队列
   * 4. 标记执行器为已关闭状态
   *
   * @param forceRollback 是否强制回滚
   */
  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore. There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  /**
   * 执行更新操作（包括insert、update、delete）
   * 执行流程：
   * 1. 检查执行器状态
   * 2. 清空本地缓存
   * 3. 调用doUpdate执行实际的更新操作
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @return 受影响的行数
   * @throws SQLException SQL异常
   */
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    // 检查执行器状态
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 清空本地缓存
    clearLocalCache();
    // 调用doUpdate执行实际的更新操作
    return doUpdate(ms, parameter);
  }

  /**
   * 刷新语句，用于批处理操作
   * 默认不进行回滚操作
   *
   * @return 批处理的结果列表
   * @throws SQLException SQL异常
   */
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  /**
   * 刷新语句，可指定是否回滚
   * 执行流程：
   * 1. 检查执行器状态
   * 2. 调用doFlushStatements执行实际的刷新操作
   *
   * @param isRollBack 是否回滚操作
   * @return 批处理的结果列表
   * @throws SQLException SQL异常
   */
  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return doFlushStatements(isRollBack);
  }

  /**
   * 执行查询操作，创建缓存键并调用重载的query方法
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @param rowBounds 分页参数
   * @param resultHandler 结果处理器
   * @return 查询结果列表
   * @throws SQLException SQL异常
   */
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler)
      throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 执行查询操作，支持一级缓存
   * 执行流程：
   * 1. 检查执行器状态
   * 2. 检查是否需要清空缓存
   * 3. 尝试从一级缓存获取结果
   * 4. 缓存未命中则查询数据库
   * 5. 处理延迟加载队列
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @param rowBounds 分页参数
   * @param resultHandler 结果处理器
   * @param key 缓存键
   * @param boundSql 绑定SQL
   * @return 查询结果列表
   * @throws SQLException SQL异常
   */
  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
      CacheKey key, BoundSql boundSql) throws SQLException {
    // 设置错误上下文，用于异常处理时打印详细信息
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());

    // 检查执行器是否已关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }

    // 如果是嵌套查询的最外层，并且需要清空缓存，则清空本地缓存
    // queryStack == 0 表示是最外层查询
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }

    List<E> list;
    try {
      // 查询堆栈深度加1，用于处理嵌套查询
      queryStack++;

      // 如果没有指定结果处理器，则尝试从本地缓存获取结果
      // 如果指定了结果处理器，则不使用缓存
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;

      // 如果缓存命中
      if (list != null) {
        // 处理存储过程的输出参数缓存
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        // 缓存未命中，从数据库查询
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      // 查询堆栈深度减1
      queryStack--;
    }

    // 如果是最外层查询，需要处理延迟加载队列
    if (queryStack == 0) {
      // 处理所有待处理的延迟加载对象
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // 清空延迟加载队列
      // issue #601
      deferredLoads.clear();

      // 如果缓存作用域是 STATEMENT，则清空本地缓存
      // issue #482
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        clearLocalCache();
      }
    }

    return list;
  }

  /**
   * 执行游标查询
   * 用于大数据量查询，返回可遍历的Cursor对象
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @param rowBounds 分页参数
   * @return Cursor对象
   * @throws SQLException SQL异常
   */
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  /**
   * 延迟加载处理
   * 执行流程：
   * 1. 检查执行器状态
   * 2. 创建延迟加载对象
   * 3. 如果可以立即加载则直接加载
   * 4. 否则加入延迟加载队列
   *
   * @param ms 映射语句对象
   * @param resultObject 结果对象
   * @param property 要加载的属性
   * @param key 缓存键
   * @param targetType 目标类型
   */
  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key,
      Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    if (deferredLoad.canLoad()) {
      deferredLoad.load();
    } else {
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  /**
   * 创建缓存键
   * 缓存键由多个部分组成，确保唯一性：
   * 1. SQL语句ID
   * 2. 分页偏移量
   * 3. 分页大小
   * 4. SQL语句
   * 5. 参数值
   * 6. 环境ID
   */
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    CacheKey cacheKey = new CacheKey();
    // 更新缓存键的组成部分
    cacheKey.update(ms.getId());
    cacheKey.update(rowBounds.getOffset());
    cacheKey.update(rowBounds.getLimit());
    cacheKey.update(boundSql.getSql());

    // 处理参数映射
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    MetaObject metaObject = null;

    // 处理每个参数
    for (ParameterMapping parameterMapping : parameterMappings) {
        // 只处理非OUT参数
        if (parameterMapping.getMode() != ParameterMode.OUT) {
            Object value;
            String propertyName = parameterMapping.getProperty();
            // 获取参数值的不同情况处理
            if (boundSql.hasAdditionalParameter(propertyName)) {
                // 从附加参数中获取
                value = boundSql.getAdditionalParameter(propertyName);
            } else if (parameterObject == null) {
                // 参数对象为空
                value = null;
            } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                // 参数对象有对应的类型处理器
                value = parameterObject;
            } else {
                // 从参数对象中获取属性值
                if (metaObject == null) {
                    metaObject = configuration.newMetaObject(parameterObject);
                }
                value = metaObject.getValue(propertyName);
            }
            // 将参数值更新到缓存键
            cacheKey.update(value);
        }
    }

    // 添加环境ID作为缓存键的一部分
    if (configuration.getEnvironment() != null) {
        cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  /**
   * 提交事务
   * 执行流程：
   * 1. 检查执行器状态
   * 2. 清空本地缓存
   * 3. 刷新未执行的语句
   * 4. 提交事务（如果required为true）
   *
   * @param required 是否要求提交事务
   * @throws SQLException SQL异常
   */
  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    clearLocalCache();
    flushStatements();
    if (required) {
      transaction.commit();
    }
  }

  /**
   * 回滚事务
   * 执行流程：
   * 1. 清空本地缓存
   * 2. 刷新未执行的语句
   * 3. 回滚事务（如果required为true）
   *
   * @param required 是否要求回滚事务
   * @throws SQLException SQL异常
   */
  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        clearLocalCache();
        flushStatements(true);
      } finally {
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  /**
   * 清空本地缓存
   * 同时会清空：
   * 1. 查询结果缓存
   * 2. 存储过程输出参数缓存
   */
  @Override
  public void clearLocalCache() {
    if (!closed) {
      localCache.clear();
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds,
      ResultHandler resultHandler, BoundSql boundSql) throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds,
      BoundSql boundSql) throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   *
   * @param statement
   *          a current statement
   *
   * @throws SQLException
   *           if a database access error occurs, this method is called on a closed <code>Statement</code>
   *
   * @since 3.4.0
   *
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  /**
   * 处理本地缓存中的输出参数
   * 适用于存储过程调用的场景，处理OUT参数
   * 执行流程：
   * 1. 检查是否是存储过程调用
   * 2. 获取缓存的参数值
   * 3. 将缓存的输出参数值设置到当前参数对象
   */
  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter,
      BoundSql boundSql) {
    // 只处理存储过程调用
    if (ms.getStatementType() == StatementType.CALLABLE) {
        // 获取缓存的参数值
        final Object cachedParameter = localOutputParameterCache.getObject(key);
        if (cachedParameter != null && parameter != null) {
            final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
            final MetaObject metaParameter = configuration.newMetaObject(parameter);
            // 处理所有非IN类型的参数（即OUT或INOUT参数）
            for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                if (parameterMapping.getMode() != ParameterMode.IN) {
                    final String parameterName = parameterMapping.getProperty();
                    // 从缓存参数中获取值并设置到当前参数对象
                    final Object cachedValue = metaCachedParameter.getValue(parameterName);
                    metaParameter.setValue(parameterName, cachedValue);
                }
            }
        }
    }
  }

  /**
   * 从数据库中执行查询
   * 执行流程：
   * 1. 先在缓存中放入占位符，防止循环引用
   * 2. 执行实际的查询操作
   * 3. 将查询结果存入缓存
   * 4. 对于存储过程，缓存输出参数
   */
  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds,
      ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    // 在缓存中添加占位符，防止重复查询和循环引用
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
        // 调用子类的具体查询实现
        list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
        // 移除占位符
        localCache.removeObject(key);
    }
    // 将查询结果存入缓存
    localCache.putObject(key, list);
    // 如果是存储过程，缓存输出参数
    if (ms.getStatementType() == StatementType.CALLABLE) {
        localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  /**
   * 获取数据库连接
   * 如果开启了调试日志，则返回带日志功能的连接包装器
   *
   * @param statementLog 语句日志对象
   * @return 数据库连接
   * @throws SQLException SQL异常
   */
  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    }
    return connection;
  }

  /**
   * 设置执行器包装器
   * 用于实现装饰器模式，增强执行器功能
   *
   * @param wrapper 执行器包装器
   */
  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  /**
   * 延迟加载内部类
   * 用于处理关联查询的延迟加载功能
   * 核心作用：存储需要延迟加载的对象信息，并在合适时机执行实际加载
   *
   * 使用示例：
   * <!-- 用户映射配置 -->
   * <resultMap id="userMap" type="User">
   *   <id property="id" column="user_id"/>
   *   <result property="name" column="user_name"/>
   *   <!-- 配置延迟加载的订单集合 -->
   *   <collection property="orders"
   *               select="getOrdersByUserId"
   *               column="user_id"
   *               fetchType="lazy"/>
   * </resultMap>
   *
   * <!-- 查询用户 -->
   * <select id="getUser" resultMap="userMap">
   *   SELECT user_id, user_name FROM users WHERE user_id = #{id}
   * </select>
   *
   * <!-- 查询订单，将被延迟调用 -->
   * <select id="getOrdersByUserId" resultType="Order">
   *   SELECT order_id, price FROM orders WHERE user_id = #{userId}
   * </select>
   *
   * // Java代码：
   * // 1. 查询用户
   * User user = sqlSession.selectOne("getUser", 1);
   * System.out.println("用户名: " + user.getName());
   *
   * // 2. 此时orders尚未加载，创建了DeferredLoad对象并加入队列
   *
   * // 3. 首次访问orders属性时触发延迟加载
   * System.out.println("订单数量: " + user.getOrders().size());
   *
   * 延迟加载流程：
   * 1. 执行主查询(getUser)获取用户基本信息
   * 2. 遇到延迟加载的orders属性，创建DeferredLoad对象
   * 3. 将DeferredLoad对象加入deferredLoads队列
   * 4. 当首次访问user.getOrders()时，触发加载
   * 5. 执行关联查询(getOrdersByUserId)获取订单信息
   * 6. 通过resultObject.setValue()将订单列表设置到用户对象
   */
  private static class DeferredLoad {

    /**
     * 结果对象的元对象，用于设置延迟加载的属性值
     */
    private final MetaObject resultObject;

    /**
     * 需要延迟加载的属性名
     */
    private final String property;

    /**
     * 目标类型，用于类型转换
     */
    private final Class<?> targetType;

    /**
     * 缓存键，用于从localCache获取实际数据
     */
    private final CacheKey key;

    /**
     * 本地缓存，存储查询结果
     */
    private final PerpetualCache localCache;

    /**
     * 对象工厂，用于创建对象实例
     */
    private final ObjectFactory objectFactory;

    /**
     * 结果提取器，用于从结果列表中提取目标对象
     */
    private final ResultExtractor resultExtractor;

    /**
     * 构造函数
     * @param resultObject 结果对象元对象
     * @param property 要加载的属性名
     * @param key 缓存键
     * @param localCache 本地缓存
     * @param configuration MyBatis配置
     * @param targetType 目标类型
     */
    public DeferredLoad(MetaObject resultObject, String property, CacheKey key, PerpetualCache localCache,
        Configuration configuration, Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    /**
     * 检查是否可以立即加载
     * 条件：缓存中存在对应的数据且不是占位符
     * @return 是否可以加载
     */
    public boolean canLoad() {
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
    }

    /**
     * 执行加载操作
     * 流程：
     * 1. 从缓存获取数据列表
     * 2. 从列表中提取目标类型的对象
     * 3. 将对象设置到结果对象的指定属性
     */
    public void load() {
      @SuppressWarnings("unchecked")
      // 假设从缓存中获取的是List类型
      List<Object> list = (List<Object>) localCache.getObject(key);
      // 从列表中提取单个对象或集合
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      // 设置到结果对象的属性中
      resultObject.setValue(property, value);
    }
  }

}
