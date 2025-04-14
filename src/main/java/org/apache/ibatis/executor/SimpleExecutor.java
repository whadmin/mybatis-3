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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 简单执行器实现类，继承自BaseExecutor
 * 主要特点：
 * 1. 每次执行都会创建新的Statement对象
 * 2. 执行完成后关闭Statement对象
 * 3. 不会复用Statement对象
 * 4. 执行流程简单直接
 *
 * 整体执行流程：
 * 1. 通过Configuration创建合适的StatementHandler
 * 2. 获取数据库连接
 * 3. 准备Statement对象并设置参数
 * 4. 执行SQL操作(查询/更新)
 * 5. 处理结果集(查询)或获取影响行数(更新)
 * 6. 关闭Statement资源
 *
 * 使用场景：
 * - 适用于单条SQL执行
 * - 对资源要求不严格的场景
 * - 无需批处理的场景
 */
public class SimpleExecutor extends BaseExecutor {

  /**
   * 构造函数
   *
   * @param configuration MyBatis配置对象
   * @param transaction 事务对象
   */
  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  /**
   * 执行更新操作（包括insert、update、delete）
   * 执行流程：
   * 1. 创建StatementHandler处理器
   * 2. 获取数据库连接并准备Statement对象
   * 3. 设置SQL参数
   * 4. 执行更新操作
   * 5. 处理返回结果
   * 6. 关闭Statement对象释放资源
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @return 受影响的行数
   * @throws SQLException SQL异常
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      // 获取全局配置对象
      Configuration configuration = ms.getConfiguration();
      // 创建StatementHandler对象，根据SQL类型创建不同的实现类
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      // 准备Statement对象并设置参数
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 通过handler执行更新操作，返回受影响的行数
      return handler.update(stmt);
    } finally {
      // 无论执行成功与否，都确保关闭Statement对象，释放资源
      closeStatement(stmt);
    }
  }

  /**
   * 执行查询操作
   * 执行流程：
   * 1. 创建StatementHandler处理器
   * 2. 获取数据库连接并准备Statement对象
   * 3. 设置SQL参数
   * 4. 执行查询操作
   * 5. 使用ResultSetHandler处理结果集映射
   * 6. 关闭Statement对象释放资源
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @param rowBounds 分页参数
   * @param resultHandler 结果处理器
   * @param boundSql 绑定的SQL对象
   * @return 查询结果列表
   * @throws SQLException SQL异常
   */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
      BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      // 获取全局配置对象
      Configuration configuration = ms.getConfiguration();
      // 创建StatementHandler对象，包含了ResultSetHandler的创建
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler,
          boundSql);
      // 获取连接，准备Statement，并设置参数
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 执行查询并通过ResultSetHandler处理结果集
      return handler.query(stmt, resultHandler);
    } finally {
      // 无论执行成功与否，都确保关闭Statement对象，释放资源
      closeStatement(stmt);
    }
  }

  /**
   * 执行游标查询
   * 执行流程：
   * 1. 创建StatementHandler处理器
   * 2. 获取数据库连接并准备Statement对象
   * 3. 设置SQL参数
   * 4. 执行游标查询操作
   * 5. 设置Statement在游标关闭时自动关闭
   *
   * 用于大数据量查询，返回Cursor对象实现流式查询
   * 注意：statement会在cursor关闭时自动关闭
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @param rowBounds 分页参数
   * @param boundSql 绑定的SQL对象
   * @return Cursor对象
   * @throws SQLException SQL异常
   */
  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
      throws SQLException {
    // 获取全局配置对象
    Configuration configuration = ms.getConfiguration();
    // 创建StatementHandler对象，resultHandler传null因为使用Cursor处理
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    // 获取连接，准备Statement并设置参数
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    // 执行游标查询，返回Cursor对象
    Cursor<E> cursor = handler.queryCursor(stmt);
    // 设置Statement在游标关闭时自动关闭，避免资源泄露
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * 刷新批处理语句
   * 简单执行器不支持批处理，始终返回空列表
   *
   * 该方法在SimpleExecutor中是空实现，因为SimpleExecutor不支持批处理操作
   * 真正的批处理实现在BatchExecutor中
   *
   * @param isRollback 是否回滚
   * @return 空的批处理结果列表
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    // SimpleExecutor不支持批处理，直接返回空列表
    return Collections.emptyList();
  }

  /**
   * 准备Statement对象
   * 执行流程：
   * 1. 获取数据库连接
   * 2. 根据SQL类型创建对应的Statement对象
   * 3. 设置超时时间等属性
   * 4. 设置SQL参数
   *
   * @param handler StatementHandler对象
   * @param statementLog 语句日志对象
   * @return 准备好的Statement对象
   * @throws SQLException SQL异常
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    // 获取数据库连接，内部会处理日志记录
    Connection connection = getConnection(statementLog);
    // 通过StatementHandler创建Statement对象并设置超时时间
    stmt = handler.prepare(connection, transaction.getTimeout());
    // 设置SQL参数(PreparedStatement)或处理动态SQL(Statement)
    handler.parameterize(stmt);
    return stmt;
  }

}
