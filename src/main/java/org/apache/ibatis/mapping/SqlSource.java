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
package org.apache.ibatis.mapping;

/**
 * SqlSource 表示从 XML 文件或注解中读取的映射语句的内容。
 * 它负责根据用户传入的参数创建将要传递给数据库的 SQL 语句。
 *
 * <h3>一、核心职责（功能）</h3>
 * <p>SqlSource 在 MyBatis 中承担以下核心职责：</p>
 * <ol>
 *   <li><b>解析 SQL 语句中的动态元素</b> - 处理 if、where、foreach 等动态标签</li>
 *   <li><b>解析 SQL 中的参数占位符</b> - 处理 #{property} 和 ${property} 两种语法</li>
 *   <li><b>将参数与 SQL 语句绑定</b> - 建立 Java 对象属性与 SQL 参数的映射关系</li>
 *   <li><b>生成最终的可执行 SQL</b> - 创建可被 JDBC 直接执行的 SQL 语句</li>
 * </ol>
 *
 * <h3>二、核心职责示例</h3>
 *
 * <h4>1. 解析 SQL 语句中的动态元素和参数</h4>
 * <pre>
 * <select id="findUser">
 *   SELECT * FROM user
 *   <where>
 *     <if test="id != null">id = #{id}</if>
 *     <if test="name != null">AND name LIKE #{name}%</if>
 *   </where>
 * </select>
 * </pre>
 *
 * <h4>2. 将参数与 SQL 语句绑定</h4>
 * <p>MyBatis 支持两种参数绑定方式：</p>
 *
 * <h5>A. #{} 参数占位符（预编译方式，推荐使用）</h5>
 * <pre>
 * // 原始SQL
 * SELECT * FROM order WHERE user_id = #{userId} AND status = #{status}
 *
 * // 参数对象
 * {userId=1001, status="PENDING"}
 *
 * // 绑定过程：
 * 1. 识别 #{userId} 和 #{status} 占位符
 * 2. 从参数对象中提取 userId=1001 和 status="PENDING"
 * 3. 将 #{userId} 和 #{status} 替换为 ? 并记录参数位置信息
 * 4. 在执行时通过 PreparedStatement 设置参数值
 *
 * // 处理后SQL（预编译形式）
 * SELECT * FROM order WHERE user_id = ? AND status = ?
 *
 * // 绑定信息
 * [(位置1, 值:1001, 类型:INTEGER), (位置2, 值:"PENDING", 类型:VARCHAR)]
 * </pre>
 *
 * <h5>B. ${} 参数占位符（文本替换方式，存在SQL注入风险）</h5>
 * <pre>
 * // 原始SQL
 * SELECT * FROM ${tableName} WHERE id = ${id}
 *
 * // 参数对象
 * {tableName="user_order", id=1001}
 *
 * // 绑定过程：
 * 1. 识别 ${tableName} 和 ${id} 占位符
 * 2. 从参数对象中提取 tableName="user_order" 和 id=1001
 * 3. 将占位符直接替换为实际值，不使用参数化查询
 * 4. 不创建参数映射（直接文本替换）
 *
 * // 处理后SQL（文本替换形式）
 * SELECT * FROM user_order WHERE id = 1001
 *
 * // 绑定信息（无参数映射，因为直接替换了文本）
 * []
 * </pre>
 *
 * <h4>3. 生成最终可执行的 SQL 语句</h4>
 * <pre>
 * PreparedStatement ps = connection.prepareStatement(
 *     "SELECT * FROM orders WHERE status = ? LIMIT ? OFFSET ?");
 * ps.setString(1, "ACTIVE");
 * ps.setInt(2, 10);
 * ps.setInt(3, 20);
 * ResultSet rs = ps.executeQuery();
 * </pre>
 *
 * <h3>四、主要实现类</h3>
 * <p>SqlSource 接口有三种主要实现，各自适用不同场景：</p>
 * <ul>
 *   <li><b>DynamicSqlSource</b>：处理含有动态 SQL 元素的语句
 *   <li><b>RawSqlSource</b>：处理静态 SQL 语句（不含动态元素，只有参数占位符）
 *   <li><b>StaticSqlSource</b>：存储已解析完成的 SQL 和参数映射
 * </ul>
 *
 * <h3>五、工作流程</h3>
 * <p>SqlSource 在 MyBatis 执行过程中的工作流程：</p>
 * <ol>
 *   <li><b>解析阶段</b>：MyBatis 启动时，XML 或注解中的 SQL 被解析成对应的 SqlSource 实现</li>
 *   <li><b>预处理阶段</b>：执行 SQL 前，先判断是否包含动态元素，选择合适的 SqlSource 实现</li>
 *   <li><b>参数处理阶段</b>：根据传入的参数对象，处理动态 SQL 并创建参数映射</li>
 *   <li><b>SQL 生成阶段</b>：SqlSource 根据处理结果生成 BoundSql 对象</li>
 *   <li><b>执行阶段</b>：BoundSql 被传递给 StatementHandler，最终由 JDBC 执行</li>
 * </ol>
 *
 * @author Clinton Begin
 */
public interface SqlSource {

  /**
   * 根据参数对象生成绑定的 SQL 语句
   *
   * <p>此方法是 SqlSource 接口的核心，它将：</p>
   * <ul>
   *   <li>计算动态 SQL 条件</li>
   *   <li>替换参数占位符</li>
   *   <li>创建参数映射</li>
   *   <li>生成最终 SQL 语句</li>
   * </ul>
   *
   * @param parameterObject 用户传入的参数对象，可能是 Map、自定义对象或原始类型
   * @return BoundSql 对象，包含最终要执行的 SQL 语句和参数信息
   */
  BoundSql getBoundSql(Object parameterObject);

}
