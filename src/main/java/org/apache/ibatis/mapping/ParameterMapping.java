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

import java.sql.ResultSet;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 参数映射类，用于描述 SQL 语句中的参数与 Java 对象属性之间的映射关系。
 *
 * <p>主要用途：</p>
 * <ul>
 *   <li>定义 SQL 语句中参数的属性名、数据类型和参数模式</li>
 *   <li>确定如何将 Java 对象的属性值转换为 JDBC 参数</li>
 *   <li>处理存储过程的输入/输出参数</li>
 * </ul>
 *
 * <p>参数映射通常通过 #{} 语法在 SQL 语句中定义，例如：</p>
 * <pre>
 * &lt;select id="findUser"&gt;
 *   SELECT * FROM user WHERE id = #{id,javaType=int,jdbcType=INTEGER}
 * &lt;/select&gt;
 * </pre>
 *
 * @author Clinton Begin
 */
public class ParameterMapping {

  /**
   * MyBatis 配置对象，包含类型处理器注册表等全局信息
   */
  private Configuration configuration;

  /**
   * 参数属性名
   * 对应 Java 对象的属性名或 Map 的键名
   */
  private String property;

  /**
   * 参数模式（IN, OUT, INOUT）
   * 主要用于存储过程参数
   * IN: 输入参数（默认）
   * OUT: 输出参数
   * INOUT: 既是输入也是输出的参数
   */
  private ParameterMode mode;

  /**
   * 参数的 Java 类型
   * 默认为 Object.class
   */
  private Class<?> javaType = Object.class;

  /**
   * 参数的 JDBC 类型
   * 例如：VARCHAR, INTEGER, TIMESTAMP 等
   */
  private JdbcType jdbcType;

  /**
   * 数值类型的精度
   * 用于 DECIMAL 或 NUMERIC 类型
   */
  private Integer numericScale;

  /**
   * 类型处理器
   * 负责 Java 类型与 JDBC 类型之间的转换
   */
  private TypeHandler<?> typeHandler;

  /**
   * 结果映射 ID
   * 当参数是 ResultSet 类型时需要指定
   */
  private String resultMapId;

  /**
   * JDBC 类型名称
   * 用于存储过程参数的类型名称
   */
  private String jdbcTypeName;

  /**
   * 表达式
   * 目前未使用
   */
  private String expression;

  /**
   * 私有构造函数
   * 防止直接实例化，必须通过 Builder 创建
   */
  private ParameterMapping() {
  }

  /**
   * 参数映射构建器类
   * 使用建造者模式构建 ParameterMapping 对象
   */
  public static class Builder {
    private final ParameterMapping parameterMapping = new ParameterMapping();

    /**
     * 构造函数 - 使用类型处理器
     *
     * @param configuration MyBatis 配置对象
     * @param property 参数属性名
     * @param typeHandler 类型处理器
     */
    public Builder(Configuration configuration, String property, TypeHandler<?> typeHandler) {
      parameterMapping.configuration = configuration;
      parameterMapping.property = property;
      parameterMapping.typeHandler = typeHandler;
      parameterMapping.mode = ParameterMode.IN; // 默认为输入参数
    }

    /**
     * 构造函数 - 使用 Java 类型
     *
     * @param configuration MyBatis 配置对象
     * @param property 参数属性名
     * @param javaType 参数的 Java 类型
     */
    public Builder(Configuration configuration, String property, Class<?> javaType) {
      parameterMapping.configuration = configuration;
      parameterMapping.property = property;
      parameterMapping.javaType = javaType;
      parameterMapping.mode = ParameterMode.IN; // 默认为输入参数
    }

    /**
     * 设置参数模式
     *
     * @param mode 参数模式（IN, OUT, INOUT）
     * @return 当前构建器实例
     */
    public Builder mode(ParameterMode mode) {
      parameterMapping.mode = mode;
      return this;
    }

    /**
     * 设置 Java 类型
     *
     * @param javaType 参数的 Java 类型
     * @return 当前构建器实例
     */
    public Builder javaType(Class<?> javaType) {
      parameterMapping.javaType = javaType;
      return this;
    }

    /**
     * 设置 JDBC 类型
     *
     * @param jdbcType 参数的 JDBC 类型
     * @return 当前构建器实例
     */
    public Builder jdbcType(JdbcType jdbcType) {
      parameterMapping.jdbcType = jdbcType;
      return this;
    }

    /**
     * 设置数值精度
     *
     * @param numericScale 数值类型的精度
     * @return 当前构建器实例
     */
    public Builder numericScale(Integer numericScale) {
      parameterMapping.numericScale = numericScale;
      return this;
    }

    /**
     * 设置结果映射 ID
     *
     * @param resultMapId 结果映射的 ID
     * @return 当前构建器实例
     */
    public Builder resultMapId(String resultMapId) {
      parameterMapping.resultMapId = resultMapId;
      return this;
    }

    /**
     * 设置类型处理器
     *
     * @param typeHandler 类型处理器实例
     * @return 当前构建器实例
     */
    public Builder typeHandler(TypeHandler<?> typeHandler) {
      parameterMapping.typeHandler = typeHandler;
      return this;
    }

    /**
     * 设置 JDBC 类型名称
     *
     * @param jdbcTypeName JDBC 类型名称
     * @return 当前构建器实例
     */
    public Builder jdbcTypeName(String jdbcTypeName) {
      parameterMapping.jdbcTypeName = jdbcTypeName;
      return this;
    }

    /**
     * 设置表达式
     *
     * @param expression 表达式字符串
     * @return 当前构建器实例
     */
    public Builder expression(String expression) {
      parameterMapping.expression = expression;
      return this;
    }

    /**
     * 构建参数映射对象
     * 解析类型处理器并验证参数有效性
     *
     * @return 构建好的参数映射对象
     */
    public ParameterMapping build() {
      resolveTypeHandler();
      validate();
      return parameterMapping;
    }

    /**
     * 验证参数映射的有效性
     * 检查 ResultSet 类型是否指定了 resultMapId
     * 检查是否存在有效的类型处理器
     *
     * @throws IllegalStateException 当验证失败时
     */
    private void validate() {
      if (ResultSet.class.equals(parameterMapping.javaType)) {
        if (parameterMapping.resultMapId == null) {
          throw new IllegalStateException("缺少属性 '" + parameterMapping.property + "' 的结果映射。"
              + "java.sql.ResultSet 类型的参数需要指定 resultMap。");
        }
      } else if (parameterMapping.typeHandler == null) {
        throw new IllegalStateException("属性 '" + parameterMapping.property + "' 的类型处理器为 null。"
            + "可能未指定类型处理器和/或无法找到 javaType (" + parameterMapping.javaType.getName()
            + ") : jdbcType (" + parameterMapping.jdbcType + ") 组合的类型处理器。");
      }
    }

    /**
     * 解析类型处理器
     * 当未显式指定类型处理器时，根据 javaType 和 jdbcType 自动查找合适的类型处理器
     */
    private void resolveTypeHandler() {
      if (parameterMapping.typeHandler == null && parameterMapping.javaType != null) {
        Configuration configuration = parameterMapping.configuration;
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        parameterMapping.typeHandler = typeHandlerRegistry.getTypeHandler(parameterMapping.javaType,
            parameterMapping.jdbcType);
      }
    }
  }

  /**
   * 获取参数属性名
   *
   * @return 参数属性名
   */
  public String getProperty() {
    return property;
  }

  /**
   * 获取参数模式
   * 用于处理存储过程的输出参数
   *
   * @return 参数模式（IN, OUT, INOUT）
   */
  public ParameterMode getMode() {
    return mode;
  }

  /**
   * 获取参数的 Java 类型
   * 用于处理存储过程的输出参数
   *
   * @return 参数的 Java 类型
   */
  public Class<?> getJavaType() {
    return javaType;
  }

  /**
   * 获取参数的 JDBC 类型
   * 当属性类型没有对应的类型处理器时，UnknownTypeHandler 会使用
   *
   * @return 参数的 JDBC 类型
   */
  public JdbcType getJdbcType() {
    return jdbcType;
  }

  /**
   * 获取数值精度
   * 用于处理存储过程的输出参数
   *
   * @return 数值类型的精度
   */
  public Integer getNumericScale() {
    return numericScale;
  }

  /**
   * 获取类型处理器
   * 用于设置 PreparedStatement 的参数
   *
   * @return 类型处理器
   */
  public TypeHandler<?> getTypeHandler() {
    return typeHandler;
  }

  /**
   * 获取结果映射 ID
   * 用于处理存储过程的输出参数
   *
   * @return 结果映射 ID
   */
  public String getResultMapId() {
    return resultMapId;
  }

  /**
   * 获取 JDBC 类型名称
   * 用于处理存储过程的输出参数
   *
   * @return JDBC 类型名称
   */
  public String getJdbcTypeName() {
    return jdbcTypeName;
  }

  /**
   * 获取表达式
   * 目前未使用
   *
   * @return 表达式字符串
   */
  public String getExpression() {
    return expression;
  }

  /**
   * 重写 toString 方法
   * 用于调试和日志输出
   *
   * @return 参数映射的字符串表示
   */
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ParameterMapping{");
    // sb.append("configuration=").append(configuration); // configuration 没有有用的 .toString() 实现
    sb.append("property='").append(property).append('\'');
    sb.append(", mode=").append(mode);
    sb.append(", javaType=").append(javaType);
    sb.append(", jdbcType=").append(jdbcType);
    sb.append(", numericScale=").append(numericScale);
    // sb.append(", typeHandler=").append(typeHandler); // typeHandler 也没有有用的 .toString() 实现
    sb.append(", resultMapId='").append(resultMapId).append('\'');
    sb.append(", jdbcTypeName='").append(jdbcTypeName).append('\'');
    sb.append(", expression='").append(expression).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
