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
package org.apache.ibatis.session.defaults;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;

/**
 * DefaultSqlSessionFactory 是 SqlSessionFactory 的默认实现。
 * 它负责创建 SqlSession 实例，管理数据库连接和事务。
 *
 * @作者 Clinton Begin
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {

  // 配置对象，包含了MyBatis的所有配置信息
  private final Configuration configuration;

  /**
   * 构造函数，接受一个 Configuration 对象。
   *
   * @param configuration MyBatis 配置对象
   */
  public DefaultSqlSessionFactory(Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * 打开一个新的 SqlSession，使用默认的执行器类型和不自动提交。
   *
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession() {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
  }

  /**
   * 打开一个新的 SqlSession，使用默认的执行器类型和指定的自动提交设置。
   *
   * @param autoCommit 是否自动提交
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession(boolean autoCommit) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, autoCommit);
  }

  /**
   * 打开一个新的 SqlSession，使用指定的执行器类型和不自动提交。
   *
   * @param execType 执行器类型
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession(ExecutorType execType) {
    return openSessionFromDataSource(execType, null, false);
  }

  /**
   * 打开一个新的 SqlSession，使用默认的执行器类型和指定的事务隔离级别。
   *
   * @param level 事务隔离级别
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession(TransactionIsolationLevel level) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), level, false);
  }

  /**
   * 打开一个新的 SqlSession，使用指定的执行器类型和事务隔离级别。
   *
   * @param execType 执行器类型
   * @param level 事务隔离级别
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
    return openSessionFromDataSource(execType, level, false);
  }

  /**
   * 打开一个新的 SqlSession，使用指定的执行器类型和自动提交设置。
   *
   * @param execType 执行器类型
   * @param autoCommit 是否自动提交
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
    return openSessionFromDataSource(execType, null, autoCommit);
  }

  /**
   * 打开一个新的 SqlSession，使用默认的执行器类型和指定的数据库连接。
   *
   * @param connection 数据库连接
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession(Connection connection) {
    return openSessionFromConnection(configuration.getDefaultExecutorType(), connection);
  }

  /**
   * 打开一个新的 SqlSession，使用指定的执行器类型和数据库连接。
   *
   * @param execType 执行器类型
   * @param connection 数据库连接
   * @return 新的 SqlSession 实例
   */
  @Override
  public SqlSession openSession(ExecutorType execType, Connection connection) {
    return openSessionFromConnection(execType, connection);
  }

  /**
   * 获取当前的配置对象。
   *
   * @return 配置对象
   */
  @Override
  public Configuration getConfiguration() {
    return configuration;
  }
  /**
   * 从数据源打开一个新的 SqlSession。
   *
   * @param execType 执行器类型
   * @param level 事务隔离级别
   * @param autoCommit 是否自动提交
   * @return 新的 SqlSession 实例
   */
  private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level,
                                               boolean autoCommit) {
    Transaction tx = null;
    try {
      final Environment environment = configuration.getEnvironment();
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
      final Executor executor = configuration.newExecutor(tx, execType);
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      closeTransaction(tx); // 可能已经获取了连接，所以需要关闭
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * 从现有的数据库连接打开一个新的 SqlSession。
   *
   * @param execType 执行器类型
   * @param connection 数据库连接
   * @return 新的 SqlSession 实例
   */
  private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
    try {
      boolean autoCommit;
      try {
        autoCommit = connection.getAutoCommit();
      } catch (SQLException e) {
        // 如果驱动或数据库不支持事务，则自动提交
        autoCommit = true;
      }
      final Environment environment = configuration.getEnvironment();
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      final Transaction tx = transactionFactory.newTransaction(connection);
      final Executor executor = configuration.newExecutor(tx, execType);
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * 从环境中获取事务工厂。
   *
   * @param environment 环境对象
   * @return 事务工厂
   */
  private TransactionFactory getTransactionFactoryFromEnvironment(Environment environment) {
    if (environment == null || environment.getTransactionFactory() == null) {
      return new ManagedTransactionFactory();
    }
    return environment.getTransactionFactory();
  }

  /**
   * 关闭事务。
   *
   * @param tx 事务对象
   */
  private void closeTransaction(Transaction tx) {
    if (tx != null) {
      try {
        tx.close();
      } catch (SQLException ignore) {
        // 有意忽略。优先处理之前的错误。
      }
    }
  }

}
