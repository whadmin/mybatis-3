/*
 *    Copyright 2009-2022 the original author or authors.
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
package org.apache.ibatis.executor.parameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * ParameterHandler 负责设置 PreparedStatement 的参数
 *
 * <p>在 MyBatis 执行流程中，ParameterHandler 处于以下位置：</p>
 * <pre>
 * SqlSession -> Executor -> StatementHandler -> ParameterHandler
 * </pre>
 *
 * <p>ParameterHandler 的主要职责：</p>
 * <ul>
 *   <li>获取用户传入的参数对象</li>
 *   <li>解析参数映射配置</li>
 *   <li>将 Java 对象参数转换为 JDBC 类型</li>
 *   <li>按顺序设置 PreparedStatement 中的参数值</li>
 * </ul>
 *
 * <p>工作原理：</p>
 * <ol>
 *   <li>解析 SQL 语句中的参数占位符 #{}</li>
 *   <li>找到参数占位符对应的 Java 对象属性</li>
 *   <li>使用 TypeHandler 将 Java 类型转换为 JDBC 类型</li>
 *   <li>通过 PreparedStatement.setXXX() 方法设置参数值</li>
 * </ol>
 *
 * <p>默认实现：</p>
 * <ul>
 *   <li>DefaultParameterHandler - 标准实现，处理大多数场景</li>
 * </ul>

 *
 * @author Clinton Begin
 */
public interface ParameterHandler {

  /**
   * 获取参数对象
   *
   * <p>返回用户传入的原始参数对象，可能是：</p>
   * <ul>
   *   <li>单个基本类型值：Integer, String 等</li>
   *   <li>POJO 对象：如 User, Order 等自定义类型</li>
   *   <li>Map 对象：存储多个命名参数</li>
   *   <li>List/Array：集合类型参数</li>
   * </ul>
   *
   * @return 用户传入的参数对象
   */
  Object getParameterObject();

  /**
   * 设置 PreparedStatement 的参数值
   *
   * <p>该方法将完成以下工作：</p>
   * <ol>
   *   <li>获取 ParameterMapping 列表，其中包含每个参数的名称、位置、类型等信息</li>
   *   <li>遍历所有参数，为每个 ? 占位符设置对应的值</li>
   *   <li>根据参数类型调用对应的 TypeHandler 进行类型转换</li>
   *   <li>调用 PreparedStatement 的 setXXX 方法设置参数</li>
   * </ol>
   *
   * <p>具体流程：</p>
   * <pre>
   * 1. 遍历 ParameterMapping 列表
   * 2. 从 parameterObject 中获取参数值
   * 3. 获取适当的 TypeHandler
   * 4. 调用 TypeHandler.setParameter() 方法设置参数
   * </pre>
   *
   * @param ps 预编译的 SQL 语句对象
   * @throws SQLException 如果设置参数失败
   */
  void setParameters(PreparedStatement ps) throws SQLException;

}
