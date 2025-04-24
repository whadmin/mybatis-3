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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * DefaultParameterHandler 是 ParameterHandler 接口的默认实现
 *
 * <p>主要职责：</p>
 * <ul>
 *   <li>为 PreparedStatement 中的参数占位符(?)设置实际参数值</li>
 *   <li>处理各种类型的参数对象(单值、POJO、Map等)</li>
 *   <li>使用 TypeHandler 进行 Java 类型到 JDBC 类型的转换</li>
 * </ul>
 *
 * <p>工作原理：</p>
 * <ol>
 *   <li>获取 BoundSql 中的 ParameterMapping 列表</li>
 *   <li>遍历每个参数映射，从参数对象中提取对应的值</li>
 *   <li>根据参数类型选择合适的 TypeHandler</li>
 *   <li>调用 TypeHandler.setParameter() 方法设置 PreparedStatement 的参数</li>
 * </ol>
 *
 * <p>参数处理流程示例：</p>
 * <pre>
 * // SQL: SELECT * FROM user WHERE id = #{id} AND name = #{name}
 * // 参数对象: {id: 1, name: "张三"}
 *
 * 1. 解析得到 ParameterMapping 列表: [id, name]
 * 2. 对于 id 参数:
 *    - 从参数对象中获取值: 1
 *    - 使用 IntegerTypeHandler 将值设置到 PreparedStatement: ps.setInt(1, 1)
 * 3. 对于 name 参数:
 *    - 从参数对象中获取值: "张三"
 *    - 使用 StringTypeHandler 将值设置到 PreparedStatement: ps.setString(2, "张三")
 * </pre>
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

  /**
   * TypeHandler 注册表，用于查找合适的类型处理器
   * 包含所有 Java 类型到 JDBC 类型的映射及其对应的处理器
   */
  private final TypeHandlerRegistry typeHandlerRegistry;

  /**
   * 当前正在执行的 SQL 语句的映射信息
   * 包含 SQL ID、参数类型、缓存配置等
   */
  private final MappedStatement mappedStatement;

  /**
   * 用户传入的参数对象
   * 可能是单个值、POJO 对象、Map 或其他复杂类型
   */
  private final Object parameterObject;

  /**
   * 包含解析后的 SQL 语句及参数映射信息
   * 存储已完成占位符替换的 SQL 和参数映射列表
   */
  private final BoundSql boundSql;

  /**
   * MyBatis 全局配置对象
   * 包含类型别名、缓存配置、插件等信息
   */
  private final Configuration configuration;

  /**
   * 构造函数
   *
   * @param mappedStatement SQL语句映射信息
   * @param parameterObject 用户传入的参数对象
   * @param boundSql 绑定SQL对象，包含SQL语句和参数映射
   */
  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  /**
   * 获取参数对象
   * 返回传入构造函数的原始参数对象
   *
   * @return 参数对象
   */
  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  /**
   * 为 PreparedStatement 设置参数值
   * 这是 DefaultParameterHandler 的核心方法，完成参数绑定过程
   *
   * <p>参数处理流程：</p>
   * <ol>
   *   <li>获取所有参数映射</li>
   *   <li>遍历每个参数映射，跳过 OUT 类型参数</li>
   *   <li>根据参数名从参数对象中提取值</li>
   *   <li>使用 TypeHandler 设置 PreparedStatement 中的参数</li>
   * </ol>
   *
   * @param ps 要设置参数的 PreparedStatement 对象
   * @throws SQLException 如果设置参数过程中发生异常
   */
  @Override
  public void setParameters(PreparedStatement ps) {
    // 设置错误上下文，方便定位问题
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());

    // 获取参数映射列表
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings != null) {
      // 创建参数对象的元对象，用于反射获取属性值
      MetaObject metaObject = null;

      // 遍历所有参数映射
      for (int i = 0; i < parameterMappings.size(); i++) {
        ParameterMapping parameterMapping = parameterMappings.get(i);

        // 跳过输出参数（存储过程OUT参数）
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          Object value;
          String propertyName = parameterMapping.getProperty();

          // 获取参数值，按以下顺序查找：
          // 1. 附加参数
          if (boundSql.hasAdditionalParameter(propertyName)) {
            // issue #448 首先检查附加参数
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (parameterObject == null) {
            // 2. 参数对象为空，值为null
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            // 3. 参数对象本身有TypeHandler（基本类型或简单对象）
            value = parameterObject;
          } else {
            // 4. 复杂对象，通过反射获取属性值
            if (metaObject == null) {
              metaObject = configuration.newMetaObject(parameterObject);
            }
            value = metaObject.getValue(propertyName);
          }

          // 获取参数的类型处理器和JDBC类型
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          JdbcType jdbcType = parameterMapping.getJdbcType();

          // 处理null值的JDBC类型
          if (value == null && jdbcType == null) {
            jdbcType = configuration.getJdbcTypeForNull();
          }

          try {
            // 使用类型处理器设置参数值
            // 索引i+1是因为JDBC参数索引从1开始
            typeHandler.setParameter(ps, i + 1, value, jdbcType);
          } catch (TypeException | SQLException e) {
            // 包装异常，提供更详细的错误信息
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          }
        }
      }
    }
  }
}
