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
package org.apache.ibatis.executor.keygen;

import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;

/**
 * 通过执行 SQL 语句生成主键的实现
 * <p>
 * 用于处理 <selectKey> 标签配置的主键生成方式。根据配置，可以在主 SQL 执行前或执行后
 * 运行指定的 SQL 语句（通常是 SELECT 语句）获取主键值并设置到参数对象中。
 * </p>
 *
 * <p>主要应用场景：</p>
 * <ul>
 *   <li>获取数据库序列值作为主键（如 Oracle 的 SEQUENCE）</li>
 *   <li>获取数据库函数生成的值（如 UUID 函数）</li>
 *   <li>获取自定义逻辑生成的主键值</li>
 * </ul>
 *
 * <p>配置示例：</p>
 * <pre>
 * <insert id="insertUser">
 *     <selectKey keyProperty="id" order="BEFORE" resultType="long">
 *         SELECT SEQ_USER.NEXTVAL FROM DUAL
 *     </selectKey>
 *     INSERT INTO user (id, name) VALUES (#{id}, #{name})
 * </insert>
 * </pre>
 *
 * @author Clinton Begin
 * @author Jeff Butler
 */
public class SelectKeyGenerator implements KeyGenerator {

  /**
   * selectKey 语句的后缀标识
   * <p>在 MyBatis 内部用于标识 selectKey 对应的 MappedStatement</p>
   */
  public static final String SELECT_KEY_SUFFIX = "!selectKey";

  /**
   * 是否在主 SQL 执行前执行 selectKey
   * <p>由 <selectKey> 的 order 属性决定：BEFORE=true, AFTER=false</p>
   */
  private final boolean executeBefore;

  /**
   * selectKey 对应的映射语句对象
   * <p>包含了 selectKey 的 SQL 语句及配置信息</p>
   */
  private final MappedStatement keyStatement;

  /**
   * 构造函数
   *
   * @param keyStatement selectKey 对应的映射语句
   * @param executeBefore 是否在主 SQL 前执行，对应 order="BEFORE|AFTER"
   */
  public SelectKeyGenerator(MappedStatement keyStatement, boolean executeBefore) {
    this.executeBefore = executeBefore;
    this.keyStatement = keyStatement;
  }

  /**
   * SQL 执行前处理
   * <p>
   * 当 executeBefore=true（即 order="BEFORE"）时，
   * 执行 selectKey 语句获取主键值并设置到参数对象中
   * </p>
   */
  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    if (executeBefore) {
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  /**
   * SQL 执行后处理
   * <p>
   * 当 executeBefore=false（即 order="AFTER"）时，
   * 执行 selectKey 语句获取主键值并设置到参数对象中
   * </p>
   */
  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    if (!executeBefore) {
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  /**
   * 处理生成的主键
   * <p>
   * 执行 selectKey 语句，并将结果设置到参数对象的指定属性中
   * </p>
   *
   * @param executor MyBatis 执行器
   * @param ms 映射语句对象
   * @param parameter 参数对象
   */
  private void processGeneratedKeys(Executor executor, MappedStatement ms, Object parameter) {
    try {
      if (parameter != null && keyStatement != null && keyStatement.getKeyProperties() != null) {
        // 获取主键属性名数组，如 ["id"]
        String[] keyProperties = keyStatement.getKeyProperties();
        final Configuration configuration = ms.getConfiguration();
        final MetaObject metaParam = configuration.newMetaObject(parameter);

        // 创建一个简单执行器用于执行 selectKey 语句
        // 注意：不要关闭 keyExecutor，事务将由父执行器关闭
        Executor keyExecutor = configuration.newExecutor(executor.getTransaction(), ExecutorType.SIMPLE);

        // 执行 selectKey 语句，获取生成的主键值
        List<Object> values = keyExecutor.query(keyStatement, parameter, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);

        // 检验查询结果
        if (values.size() == 0) {
          throw new ExecutorException("SelectKey 没有返回数据。");
        }
        if (values.size() > 1) {
          throw new ExecutorException("SelectKey 返回了多个值。");
        } else {
          // 将查询结果作为主键值设置到参数对象
          MetaObject metaResult = configuration.newMetaObject(values.get(0));
          if (keyProperties.length == 1) {
            // 单个主键属性的情况
            if (metaResult.hasGetter(keyProperties[0])) {
              // 结果对象有对应属性的 getter 方法，取该属性值
              setValue(metaParam, keyProperties[0], metaResult.getValue(keyProperties[0]));
            } else {
              // 结果对象没有对应属性的 getter 方法，可能是单值对象
              // 直接使用结果对象本身作为属性值
              setValue(metaParam, keyProperties[0], values.get(0));
            }
          } else {
            // 多个主键属性的情况
            handleMultipleProperties(keyProperties, metaParam, metaResult);
          }
        }
      }
    } catch (ExecutorException e) {
      throw e;
    } catch (Exception e) {
      throw new ExecutorException("执行 SelectKey 或设置结果到参数对象时出错。原因: " + e, e);
    }
  }

  /**
   * 处理多个主键属性的情况
   * <p>
   * 当 keyProperty 配置了多个属性（如 keyProperty="id,code"）时，
   * 需要将多个列值分别设置到对应的属性中
   * </p>
   *
   * @param keyProperties 主键属性名数组
   * @param metaParam 参数对象的元对象
   * @param metaResult 结果对象的元对象
   */
  private void handleMultipleProperties(String[] keyProperties, MetaObject metaParam, MetaObject metaResult) {
    // 获取主键列名数组，如 ["id_column", "code_column"]
    String[] keyColumns = keyStatement.getKeyColumns();

    if (keyColumns == null || keyColumns.length == 0) {
      // 未指定 keyColumn，直接使用属性名作为列名
      for (String keyProperty : keyProperties) {
        setValue(metaParam, keyProperty, metaResult.getValue(keyProperty));
      }
    } else {
      // 指定了 keyColumn，需要将列值映射到属性
      if (keyColumns.length != keyProperties.length) {
        throw new ExecutorException(
            "如果 SelectKey 指定了 keyColumns，其数量必须与 keyProperties 的数量匹配。");
      }
      for (int i = 0; i < keyProperties.length; i++) {
        setValue(metaParam, keyProperties[i], metaResult.getValue(keyColumns[i]));
      }
    }
  }

  /**
   * 设置参数对象的属性值
   * <p>将生成的主键值设置到参数对象的指定属性</p>
   *
   * @param metaParam 参数对象的元对象
   * @param property 要设置的属性名
   * @param value 属性值
   */
  private void setValue(MetaObject metaParam, String property, Object value) {
    if (!metaParam.hasSetter(property)) {
      throw new ExecutorException("在 " + metaParam.getOriginalObject().getClass().getName()
          + " 中没有找到属性 '" + property + "' 的 setter 方法。");
    }
    metaParam.setValue(property, value);
  }
}
