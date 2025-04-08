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
   * 1. 创建StatementHandler
   * 2. 准备Statement对象
   * 3. 执行更新操作
   * 4. 关闭Statement对象
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
      Configuration configuration = ms.getConfiguration();
      // 创建StatementHandler对象
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      // 准备Statement对象并设置参数
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 执行更新操作
      return handler.update(stmt);
    } finally {
      // 关闭Statement对象
      closeStatement(stmt);
    }
  }

  /**
   * 执行查询操作
   * 执行流程：
   * 1. 创建StatementHandler
   * 2. 准备Statement对象
   * 3. 执行查询操作
   * 4. 处理返回结果
   * 5. 关闭Statement对象
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
      Configuration configuration = ms.getConfiguration();
      // 创建StatementHandler对象
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler,
          boundSql);
      // 准备Statement对象并设置参数
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 执行查询并返回结果
      return handler.query(stmt, resultHandler);
    } finally {
      // 关闭Statement对象
      closeStatement(stmt);
    }
  }

  /**
   * 执行游标查询
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
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * 刷新批处理语句
   * 简单执行器不支持批处理，始终返回空列表
   *
   * @param isRollback 是否回滚
   * @return 空的批处理结果列表
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    return Collections.emptyList();
  }

  /**
   * 准备Statement对象
   * 执行步骤：
   * 1. 获取数据库连接
   * 2. 创建Statement对象
   * 3. 设置参数
   *
   * @param handler StatementHandler对象
   * @param statementLog 语句日志对象
   * @return 准备好的Statement对象
   * @throws SQLException SQL异常
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    Connection connection = getConnection(statementLog);
    stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    return stmt;
  }

}
