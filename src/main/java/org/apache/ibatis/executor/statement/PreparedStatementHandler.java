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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * PreparedStatement处理器，继承自BaseStatementHandler
 * 负责处理预编译语句(PreparedStatement)的操作，是MyBatis最常用的Statement处理器
 * 相比Statement，PreparedStatement可以防止SQL注入，并且具有更好的性能
 *
 * @author Clinton Begin
 */
public class PreparedStatementHandler extends BaseStatementHandler {

  /**
   * 构造函数
   *
   * @param executor Executor对象，负责执行SQL
   * @param mappedStatement MappedStatement对象，包含SQL相关配置信息
   * @param parameter 参数对象
   * @param rowBounds 分页参数
   * @param resultHandler 结果集处理器
   * @param boundSql 解析后的SQL语句对象
   */
  public PreparedStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter,
      RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  /**
   * 执行更新操作（包括insert、update、delete）
   * 支持自动生成主键的功能
   *
   * @param statement Statement对象
   * @return 受影响的行数
   * @throws SQLException SQL异常
   */
  @Override
  public int update(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    int rows = ps.getUpdateCount();
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
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
    PreparedStatement ps = (PreparedStatement) statement;
    ps.addBatch();
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
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    return resultSetHandler.handleResultSets(ps);
  }

  /**
   * 执行查询操作，返回Cursor对象
   * 用于流式查询，适合处理大量数据
   *
   * @param statement Statement对象
   * @return Cursor对象
   * @throws SQLException SQL异常
   */
  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    return resultSetHandler.handleCursorResultSets(ps);
  }

  /**
   * 实例化PreparedStatement对象，根据不同的SQL类型和配置创建相应的PreparedStatement
   *
   * 一、Insert语句的PreparedStatement创建
   * -------------------------------
   * 1. 自增主键配置 - 单列
   * <insert id="insertUser" useGeneratedKeys="true" keyProperty="id">
   *     INSERT INTO users(name, age) VALUES(#{name}, #{age})
   * </insert>
   *
   * JDBC示例:
   * PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
   * ps.executeUpdate();
   * ResultSet rs = ps.getGeneratedKeys(); // 获取自增主键值
   *
   * 2. 自增主键配置 - 多列
   * <insert id="insertUser" useGeneratedKeys="true"
   *         keyProperty="id,uuid" keyColumn="id,uuid_col">
   *     INSERT INTO users(name, age) VALUES(#{name}, #{age})
   * </insert>
   *
   * JDBC示例:
   * String[] columns = {"id", "uuid_col"};
   * PreparedStatement ps = conn.prepareStatement(sql, columns);
   *
   * 二、Select/Update语句的PreparedStatement创建
   * ---------------------------------------
   * 1. 默认结果集类型
   * <select id="selectUsers" resultType="User">
   *     SELECT * FROM users WHERE age > #{minAge}
   * </select>
   *
   * JDBC示例:
   * PreparedStatement ps = conn.prepareStatement(sql);
   *
   * 2. 自定义结果集类型
   * <select id="selectUsers" resultType="User" resultSetType="SCROLL_SENSITIVE">
   *     SELECT * FROM users WHERE age > #{minAge}
   * </select>
   *
   * ResultSetType说明:
   * - DEFAULT: 使用数据库驱动默认设置
   * - FORWARD_ONLY: 结果集只能向前遍历，性能最好
   * - SCROLL_SENSITIVE: 支持结果集滚动，能够反映数据库的实时更改
   * - SCROLL_INSENSITIVE: 支持结果集滚动，但不反映数据库的实时更改
   *
   * JDBC示例:
   * PreparedStatement ps = conn.prepareStatement(sql,
   *     ResultSet.TYPE_SCROLL_SENSITIVE,
   *     ResultSet.CONCUR_READ_ONLY);
   *
   * @param connection 数据库连接对象
   * @return PreparedStatement对象
   * @throws SQLException 当创建PreparedStatement失败时抛出
   * @see java.sql.Connection#prepareStatement(String, int)
   * @see java.sql.Connection#prepareStatement(String, String[])
   * @see java.sql.Connection#prepareStatement(String, int, int)
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    String sql = boundSql.getSql();
    if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
      String[] keyColumnNames = mappedStatement.getKeyColumns();
      if (keyColumnNames == null) {
        // 使用JDBC3的方式获取自动生成的主键
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      } else {
        // 指定主键列名
        return connection.prepareStatement(sql, keyColumnNames);
      }
    }
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      // 创建默认的PreparedStatement
      return connection.prepareStatement(sql);
    } else {
      // 创建指定结果集类型的PreparedStatement
      return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(),
          ResultSet.CONCUR_READ_ONLY);
    }
  }

  /**
   * 设置PreparedStatement的参数
   * 使用ParameterHandler处理参数设置
   *
   * @param statement Statement对象
   * @throws SQLException SQL异常
   */
  @Override
  public void parameterize(Statement statement) throws SQLException {
    parameterHandler.setParameters((PreparedStatement) statement);
  }

}
