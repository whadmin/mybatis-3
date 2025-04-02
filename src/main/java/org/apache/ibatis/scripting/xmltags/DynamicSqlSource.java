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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 动态 SQL 源类
 * 该类实现了 SqlSource 接口，用于生成动态 SQL 语句。
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration; // MyBatis 配置
  private final SqlNode rootSqlNode; // 根 SQL 节点

  /**
   * 构造函数
   * @param configuration MyBatis 配置
   * @param rootSqlNode 根 SQL 节点
   */
  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 创建动态上下文
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // 应用根 SQL 节点
    rootSqlNode.apply(context);
    // 创建 SQL 源解析器
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 获取参数类型
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // 解析 SQL 语句
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // 获取绑定的 SQL
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 设置额外参数
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql; // 返回绑定的 SQL
  }

}
