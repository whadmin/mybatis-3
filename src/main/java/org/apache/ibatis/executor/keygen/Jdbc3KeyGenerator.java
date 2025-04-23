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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession.StrictMap;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.util.MapUtil;

/**
 * JDBC3 规范中的自动生成主键实现
 * 用于处理数据库自动生成的主键（如自增ID）并将其设置到参数对象中
 *
 * <p>使用示例：</p>
 * <pre>
 * 在 Mapper XML 中：
 * <insert id="insertUser" useGeneratedKeys="true" keyProperty="id">;
 *     INSERT INTO user (name, email) VALUES (#{name}, #{email})
 * </insert>;
 *
 * 在代码中：
 * User user = new User();
 * user.setName("张三");
 * user.setEmail("zhangsan@example.com");
 * // user.id 此时为 null
 *
 * userMapper.insertUser(user);
 * // 执行后，user.id 会被自动填充为数据库生成的主键值
 * </pre>
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  /**
   * 第二个泛型参数名称常量
   */
  private static final String SECOND_GENERIC_PARAM_NAME = ParamNameResolver.GENERIC_NAME_PREFIX + "2";

  /**
   * 共享的单例实例
   *
   * @since 3.4.3
   */
  public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

  /**
   * 生成过多主键时的错误消息模板
   */
  private static final String MSG_TOO_MANY_KEYS = "Too many keys are generated. There are only %d target objects. "
      + "You either specified a wrong 'keyProperty' or encountered a driver bug like #1523.";

  /**
   * 执行SQL前的处理，对于JDBC3的自动生成主键不需要在执行前做任何事情
   * <p>注：区别于 SelectKeyGenerator，该生成器仅在SQL执行后获取数据库生成的键</p>
   */
  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // 不需要做任何事情
  }

  /**
   * 执行SQL后的处理，用于获取自动生成的主键并设置到参数对象中
   */
  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    processBatch(ms, stmt, parameter);
  }

  /**
   * 处理批量生成的主键
   * <p>从 Statement.getGeneratedKeys() 获取生成的主键，并将值设置到参数对象的指定属性中</p>
   *
   * @param ms SQL映射语句
   * @param stmt SQL声明
   * @param parameter 参数对象
   */
  public void processBatch(MappedStatement ms, Statement stmt, Object parameter) {
    // 获取主键属性名数组，例如 ["id"]
    final String[] keyProperties = ms.getKeyProperties();
    if (keyProperties == null || keyProperties.length == 0) {
      return;
    }
    try (ResultSet rs = stmt.getGeneratedKeys()) { // 获取自动生成的主键结果集
      final ResultSetMetaData rsmd = rs.getMetaData();
      final Configuration configuration = ms.getConfiguration();
      if (rsmd.getColumnCount() < keyProperties.length) {
        // 生成的主键列数小于指定的主键属性数，可能有错误
      } else {
        // 将生成的主键分配给参数对象
        assignKeys(configuration, rs, rsmd, keyProperties, parameter);
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    }
  }

  /**
   * 根据参数类型分配主键到对应的参数对象
   * <p>处理三种情况：单参数、多参数、批量插入的多参数</p>
   *
   * @param configuration MyBatis配置
   * @param rs 包含生成主键的结果集
   * @param rsmd 结果集元数据
   * @param keyProperties 主键属性名数组
   * @param parameter 参数对象
   */
  @SuppressWarnings("unchecked")
  private void assignKeys(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd, String[] keyProperties,
      Object parameter) throws SQLException {
    if (parameter instanceof ParamMap || parameter instanceof StrictMap) {
      // 多参数或带@Param注解的单参数，如 @Param("user") User user
      assignKeysToParamMap(configuration, rs, rsmd, keyProperties, (Map<String, ?>) parameter);
    } else if (parameter instanceof ArrayList && !((ArrayList<?>) parameter).isEmpty()
        && ((ArrayList<?>) parameter).get(0) instanceof ParamMap) {
      // 批量操作中的多参数或带@Param注解的单参数
      assignKeysToParamMapList(configuration, rs, rsmd, keyProperties, (ArrayList<ParamMap<?>>) parameter);
    } else {
      // 不带@Param注解的单参数，如 User user
      assignKeysToParam(configuration, rs, rsmd, keyProperties, parameter);
    }
  }

  /**
   * 将生成的主键分配给单个参数对象
   * <p>适用于 mapper.insert(user) 或 mapper.insertBatch(userList) 的情况</p>
   */
  private void assignKeysToParam(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, Object parameter) throws SQLException {
    // 将参数转换为集合
    Collection<?> params = collectionize(parameter);
    if (params.isEmpty()) {
      return;
    }
    // 为每个主键属性创建主键分配器
    List<KeyAssigner> assignerList = new ArrayList<>();
    for (int i = 0; i < keyProperties.length; i++) {
      assignerList.add(new KeyAssigner(configuration, rsmd, i + 1, null, keyProperties[i]));
    }
    // 遍历结果集并分配主键
    Iterator<?> iterator = params.iterator();
    while (rs.next()) {
      if (!iterator.hasNext()) {
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, params.size()));
      }
      Object param = iterator.next();
      assignerList.forEach(x -> x.assign(rs, param));
    }
  }

  /**
   * 将生成的主键分配给参数映射列表（批量操作）
   * <p>适用于带@Param注解的批量插入，如 mapper.insertBatch(@Param("users") List<User> users)</p>
   */
  private void assignKeysToParamMapList(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, ArrayList<ParamMap<?>> paramMapList) throws SQLException {
    Iterator<ParamMap<?>> iterator = paramMapList.iterator();
    List<KeyAssigner> assignerList = new ArrayList<>();
    long counter = 0;
    while (rs.next()) {
      if (!iterator.hasNext()) {
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
      }
      ParamMap<?> paramMap = iterator.next();
      if (assignerList.isEmpty()) {
        // 首次遍历时创建主键分配器
        for (int i = 0; i < keyProperties.length; i++) {
          assignerList
              .add(getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i], keyProperties, false)
                  .getValue());
        }
      }
      // 分配主键
      assignerList.forEach(x -> x.assign(rs, paramMap));
      counter++;
    }
  }

  /**
   * 将生成的主键分配给参数映射（多参数情况）
   * <p>适用于多参数情况，如 mapper.insert(@Param("user") User user, @Param("dept") Department dept)</p>
   */
  private void assignKeysToParamMap(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, Map<String, ?> paramMap) throws SQLException {
    if (paramMap.isEmpty()) {
      return;
    }
    // 创建参数名到<迭代器,分配器列表>的映射
    Map<String, Entry<Iterator<?>, List<KeyAssigner>>> assignerMap = new HashMap<>();
    for (int i = 0; i < keyProperties.length; i++) {
      Entry<String, KeyAssigner> entry = getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i],
          keyProperties, true);
      Entry<Iterator<?>, List<KeyAssigner>> iteratorPair = MapUtil.computeIfAbsent(assignerMap, entry.getKey(),
          k -> MapUtil.entry(collectionize(paramMap.get(k)).iterator(), new ArrayList<>()));
      iteratorPair.getValue().add(entry.getValue());
    }
    // 遍历结果集并分配主键
    long counter = 0;
    while (rs.next()) {
      for (Entry<Iterator<?>, List<KeyAssigner>> pair : assignerMap.values()) {
        if (!pair.getKey().hasNext()) {
          throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
        }
        Object param = pair.getKey().next();
        pair.getValue().forEach(x -> x.assign(rs, param));
      }
      counter++;
    }
  }

  /**
   * 为参数映射获取对应的主键分配器
   * <p>解析 keyProperty 如 "user.id" 确定参数名和属性名</p>
   */
  private Entry<String, KeyAssigner> getAssignerForParamMap(Configuration config, ResultSetMetaData rsmd,
      int columnPosition, Map<String, ?> paramMap, String keyProperty, String[] keyProperties, boolean omitParamName) {
    Set<String> keySet = paramMap.keySet();
    // 注意：如果唯一参数有 {@code @Param("param2")} 注解，
    // 必须使用参数名引用，例如 'param2.id'
    boolean singleParam = !keySet.contains(SECOND_GENERIC_PARAM_NAME);
    int firstDot = keyProperty.indexOf('.');
    if (firstDot == -1) {
      if (singleParam) {
        return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
      }
      throw new ExecutorException("Could not determine which parameter to assign generated keys to. "
          + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
          + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
          + keySet);
    }
    String paramName = keyProperty.substring(0, firstDot);
    if (keySet.contains(paramName)) {
      String argParamName = omitParamName ? null : paramName;
      String argKeyProperty = keyProperty.substring(firstDot + 1);
      return MapUtil.entry(paramName, new KeyAssigner(config, rsmd, columnPosition, argParamName, argKeyProperty));
    }
    if (singleParam) {
      return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
    } else {
      throw new ExecutorException("Could not find parameter '" + paramName + "'. "
          + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
          + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
          + keySet);
    }
  }

  /**
   * 为单个参数获取主键分配器
   */
  private Entry<String, KeyAssigner> getAssignerForSingleParam(Configuration config, ResultSetMetaData rsmd,
      int columnPosition, Map<String, ?> paramMap, String keyProperty, boolean omitParamName) {
    // 假设'keyProperty'是单个参数的属性
    String singleParamName = nameOfSingleParam(paramMap);
    String argParamName = omitParamName ? null : singleParamName;
    return MapUtil.entry(singleParamName, new KeyAssigner(config, rsmd, columnPosition, argParamName, keyProperty));
  }

  /**
   * 获取单个参数的名称
   */
  private static String nameOfSingleParam(Map<String, ?> paramMap) {
    // 实际上只有一个参数，所以任何键都可以
    return paramMap.keySet().iterator().next();
  }

  /**
   * 将参数对象转换为集合
   * <p>处理单个对象、集合和数组参数</p>
   */
  private static Collection<?> collectionize(Object param) {
    if (param instanceof Collection) {
      return (Collection<?>) param;
    }
    if (param instanceof Object[]) {
      return Arrays.asList((Object[]) param);
    } else {
      return Arrays.asList(param);
    }
  }

  /**
   * 主键分配器，负责将生成的主键值设置到对象的属性中
   */
  private static class KeyAssigner {
    private final Configuration configuration;
    private final ResultSetMetaData rsmd;
    private final TypeHandlerRegistry typeHandlerRegistry;
    private final int columnPosition;
    private final String paramName;
    private final String propertyName;
    private TypeHandler<?> typeHandler;

    /**
     * 创建主键分配器
     *
     * @param configuration MyBatis配置
     * @param rsmd 结果集元数据
     * @param columnPosition 列位置
     * @param paramName 参数名，如 "user"
     * @param propertyName 属性名，如 "id"
     */
    protected KeyAssigner(Configuration configuration, ResultSetMetaData rsmd, int columnPosition, String paramName,
        String propertyName) {
      this.configuration = configuration;
      this.rsmd = rsmd;
      this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      this.columnPosition = columnPosition;
      this.paramName = paramName;
      this.propertyName = propertyName;
    }

    /**
     * 将结果集中的主键值分配给参数对象的属性
     * <p>例如：将自增ID值设置到 user.id 属性</p>
     *
     * @param rs 结果集
     * @param param 参数对象
     */
    protected void assign(ResultSet rs, Object param) {
      if (paramName != null) {
        // 如果设置了paramName，param是ParamMap，需要先获取真正的参数对象
        param = ((ParamMap<?>) param).get(paramName);
      }
      MetaObject metaParam = configuration.newMetaObject(param);
      try {
        if (typeHandler == null) {
          if (!metaParam.hasSetter(propertyName)) {
            throw new ExecutorException("No setter found for the keyProperty '" + propertyName + "' in '"
                + metaParam.getOriginalObject().getClass().getName() + "'.");
          }
          Class<?> propertyType = metaParam.getSetterType(propertyName);
          typeHandler = typeHandlerRegistry.getTypeHandler(propertyType,
              JdbcType.forCode(rsmd.getColumnType(columnPosition)));
        }
        if (typeHandler == null) {
          // 类型处理器为空，可能有错误
        } else {
          // 获取结果并设置到对象属性中
          Object value = typeHandler.getResult(rs, columnPosition);
          metaParam.setValue(propertyName, value);
        }
      } catch (SQLException e) {
        throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e,
            e);
      }
    }
  }
}
