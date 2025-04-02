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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * StatementHandler 接口封装了 JDBC Statement 操作，负责对 JDBC statement 的操作，包括创建、准备、参数化等
 * 它是 MyBatis 四大核心接口之一（Executor、StatementHandler、ParameterHandler、ResultSetHandler）
 *
 * @author Clinton Begin
 */
public interface StatementHandler {

  /**
   * 创建 JDBC Statement 对象，并进行一些预处理工作
   *
   * @param connection 数据库连接对象
   * @param transactionTimeout 事务超时时间
   * @return Statement对象
   * @throws SQLException SQL异常
   */
  Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException;

  /**
   * 设置 Statement 的参数，使用 ParameterHandler 对预编译语句进行参数设置
   *
   * @param statement Statement对象
   * @throws SQLException SQL异常
   */
  void parameterize(Statement statement) throws SQLException;

  /**
   * 将 SQL 语句加入批处理
   *
   * @param statement Statement对象
   * @throws SQLException SQL异常
   */
  void batch(Statement statement) throws SQLException;

  /**
   * 执行更新操作（包括 insert、update、delete）
   *
   * @param statement Statement对象
   * @return 影响的行数
   * @throws SQLException SQL异常
   */
  int update(Statement statement) throws SQLException;

  /**
   * 执行查询操作，返回结果列表
   *
   * @param statement Statement对象
   * @param resultHandler 结果集处理器
   * @return 查询结果列表
   * @throws SQLException SQL异常
   */
  <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException;

  /**
   * 执行查询操作，返回游标对象，用于流式查询大量数据
   *
   * @param statement Statement对象
   * @return Cursor游标对象
   * @throws SQLException SQL异常
   */
  <E> Cursor<E> queryCursor(Statement statement) throws SQLException;

  /**
   * 获取 BoundSql 对象，该对象包含解析后的 SQL 语句和参数映射信息
   *
   * @return BoundSql对象
   */
  BoundSql getBoundSql();

  /**
   * 获取参数处理器
   *
   * @return ParameterHandler对象
   */
  ParameterHandler getParameterHandler();

}
