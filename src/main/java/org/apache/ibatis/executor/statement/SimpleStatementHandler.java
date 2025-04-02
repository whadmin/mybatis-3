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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * SimpleStatementHandler 是最基本的 StatementHandler 实现类
 * 负责处理不需要预编译的 Statement 对象
 * 直接将 SQL 语句发送到数据库执行，不支持参数化
 *
 * @author Clinton Begin
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  /**
   * 构造函数
   *
   * @param executor Executor对象，用于执行SQL
   * @param mappedStatement MappedStatement对象，包含SQL相关配置信息
   * @param parameter SQL参数对象
   * @param rowBounds 分页参数
   * @param resultHandler 结果集处理器
   * @param boundSql 解析后的SQL语句对象
   */
  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter,
      RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  /**
   * 执行更新操作（包括 insert、update、delete）
   * 支持自动生成主键
   *
   * @param statement Statement对象
   * @return 受影响的行数
   * @throws SQLException SQL异常
   */
  @Override
  public int update(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      // 使用JDBC3的方式获取自动生成的主键
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      rows = statement.getUpdateCount();
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      // 使用selectKey方式获取主键
      statement.execute(sql);
      rows = statement.getUpdateCount();
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {
      // 普通更新操作
      statement.execute(sql);
      rows = statement.getUpdateCount();
    }
    return rows;
  }

  /**
   * 将SQL语句添加到批处理中
   *
   * @param statement Statement对象
   * @throws SQLException SQL异常
   */
  @Override
  public void batch(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.addBatch(sql);
  }

  /**
   * 执行查询操作，返回结果列表
   *
   * @param statement Statement对象
   * @param resultHandler 结果集处理器
   * @return 查询结果列表
   * @throws SQLException SQL异常
   */
  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.handleResultSets(statement);
  }

  /**
   * 执行查询操作，返回游标对象
   *
   * @param statement Statement对象
   * @return Cursor游标对象
   * @throws SQLException SQL异常
   */
  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.handleCursorResultSets(statement);
  }

  /**
   * 创建Statement实例
   * 根据ResultSetType类型创建对应的Statement对象
   *
   * @param connection 数据库连接对象
   * @return Statement对象
   * @throws SQLException SQL异常
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      return connection.createStatement();
    }
    return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
  }

  /**
   * 设置参数（SimpleStatementHandler不需要设置参数，因为SQL中已经包含了参数值）
   *
   * @param statement Statement对象
   */
  @Override
  public void parameterize(Statement statement) {
    // N/A
  }

}
