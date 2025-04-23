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
package org.apache.ibatis.executor.keygen;

import java.sql.Statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * 主键生成器接口
 * <p>
 * 负责在 SQL 执行前后处理主键生成和设置。
 * MyBatis 在插入操作中支持两种主键生成方式：
 * 1. 预先生成主键（如 Oracle 序列）：在 SQL 执行前生成并设置到参数对象中
 * 2. 自动生成主键（如 MySQL 自增 ID）：在 SQL 执行后从数据库获取生成的主键并设置到参数对象中
 * </p>
 *
 * <p>主要实现类：</p>
 * <ul>
 *   <li>{@link Jdbc3KeyGenerator}：基于 JDBC3 规范获取数据库自动生成的主键</li>
 *   <li>{@link SelectKeyGenerator}：通过执行额外 SQL 语句获取或生成主键</li>
 *   <li>{@link NoKeyGenerator}：不生成任何主键，用作默认实现</li>
 * </ul>
 *
 * <p>配置示例：</p>
 * <pre>
 * <!-- 使用数据库自增主键 -->
 * <insert id="insertUser" useGeneratedKeys="true" keyProperty="id">
 *     INSERT INTO user (name, email) VALUES (#{name}, #{email})
 * </insert>
 *
 * <!-- 使用自定义 SQL 生成主键 -->
 * <insert id="insertUser">
 *     <selectKey keyProperty="id" order="BEFORE" resultType="long">
 *         SELECT SEQ_USER.NEXTVAL FROM DUAL
 *     </selectKey>
 *     INSERT INTO user (id, name, email) VALUES (#{id}, #{name}, #{email})
 * </insert>
 * </pre>
 *
 * @author Clinton Begin
 */
public interface KeyGenerator {

  /**
   * SQL 执行前的处理
   * <p>
   * 在执行 SQL 语句前调用，主要用于预先生成主键并设置到参数对象中。
   * 如 SelectKeyGenerator 会在此方法中执行 SELECT 语句获取主键值。
   * </p>
   *
   * @param executor MyBatis 执行器
   * @param ms 映射语句对象，包含了 SQL 相关配置
   * @param stmt JDBC 语句对象
   * @param parameter 用户传入的参数对象，如实体类
   */
  void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

  /**
   * SQL 执行后的处理
   * <p>
   * 在执行 SQL 语句后调用，主要用于获取数据库生成的主键并设置到参数对象中。
   * 如 Jdbc3KeyGenerator 会在此方法中通过 Statement.getGeneratedKeys() 获取自增主键值。
   * </p>
   *
   * @param executor MyBatis 执行器
   * @param ms 映射语句对象，包含了 SQL 相关配置
   * @param stmt JDBC 语句对象，包含执行结果
   * @param parameter 用户传入的参数对象，执行后可能会被设置主键值
   */
  void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

}
