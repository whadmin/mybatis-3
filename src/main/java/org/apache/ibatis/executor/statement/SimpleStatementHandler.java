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
 * SimpleStatementHandler 是 StatementHandler 接口的基础实现
 * 它使用 java.sql.Statement 执行 SQL 语句，主要特点：
 * 1. 不支持参数占位符，SQL 中直接包含具体参数值
 * 2. SQL 语句在每次执行时解析，不进行预编译
 * 3. 适合执行简单静态 SQL 或一次性执行的语句
 * 4. 性能相对 PreparedStatement 较低，存在 SQL 注入风险
 *
 * 使用示例：
 * <select id="selectById" statementType="SIMPLE" resultType="User">
 *   SELECT * FROM user WHERE id = ${id}
 * </select>
 *
 * @author Clinton Begin
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  /**
   * 构造函数
   *
   * @param executor Executor对象，负责整体SQL执行流程
   * @param mappedStatement MappedStatement对象，封装了XML中配置的SQL信息
   * @param parameter 用户传入的参数对象，用于SQL拼接
   * @param rowBounds 用于分页的参数对象
   * @param resultHandler 结果集处理器
   * @param boundSql 已完成参数绑定的SQL对象，包含最终要执行的SQL语句
   */
  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter,
      RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  /**
   * 执行更新操作（包括 insert、update、delete）
   * 使用 java.sql.Statement.execute() 方法执行SQL
   * 支持多种主键生成策略：
   * 1. Jdbc3KeyGenerator：利用JDBC的getGeneratedKeys获取自增主键
   * 2. SelectKeyGenerator：通过额外SQL查询获取主键
   *
   * @param statement Statement对象
   * @return 受影响的行数
   * @throws SQLException SQL执行异常
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
   * 用于批量执行多条SQL语句
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
   * 执行查询操作并返回结果列表
   * 直接使用SQL字符串执行查询，SQL中已包含具体值
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
   * 执行查询并返回游标对象
   * 游标允许流式处理结果集，适用于处理大量数据
   *
   * @param statement Statement对象
   * @return Cursor游标对象，支持流式访问结果集
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
   * 根据配置的ResultSetType属性创建适当的Statement对象
   * ResultSetType控制结果集的特性，如是否可滚动、是否敏感等
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
   * 设置SQL参数
   * 对于SimpleStatementHandler，此方法为空实现
   * 因为SimpleStatementHandler使用的SQL已经包含了具体参数值，
   * 不像PreparedStatementHandler需要设置参数占位符的值
   *
   * @param statement Statement对象
   */
  @Override
  public void parameterize(Statement statement) {
    // 不需要实现，因为SimpleStatementHandler使用的SQL已包含具体参数值
  }
}
