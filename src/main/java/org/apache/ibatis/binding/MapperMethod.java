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
package org.apache.ibatis.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper方法封装类，负责处理Mapper接口中定义的方法的具体执行逻辑
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>解析Mapper方法的签名信息</li>
 *   <li>执行对应的SQL命令（SELECT、INSERT、UPDATE、DELETE等）</li>
 *   <li>处理方法参数和返回值的转换</li>
 *   <li>支持多种返回类型（集合、游标、Map等）</li>
 * </ul>
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  /**
   * SQL命令对象，封装了Mapper方法对应 SQL语句的类型和标识符
   */
  private final SqlCommand command;

  /**
   * 方法签名对象，封装了方法的参数和返回值信息
   */
  private final MethodSignature method;

  /**
   * 构造函数，初始化SQL命令和方法签名
   *
   * @param mapperInterface Mapper接口类
   * @param method 方法对象
   * @param config MyBatis配置对象
   */
  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  /**
   * 执行Mapper方法，这是Mapper接口方法调用的核心处理方法
   *
   * <p>执行流程：</p>
   * <ol>
   *   <li>获取Mapper方法对应的SQL命令类型（INSERT、UPDATE、DELETE、SELECT等）</li>
   *   <li>将方法参数转换为参数 mybatis 参数对象 </li>
   *   <li>根据SQL类型调用SqlSession对应的方法执行SQL语句</li>
   *   <li>将执行结果处理后转换为方法的返回类型</li>
   * </ol>
   *
   * <p>SQL命令类型与SqlSession方法的对应关系：</p>
   * <ul>
   *   <li>INSERT -> sqlSession.insert()</li>
   *   <li>UPDATE -> sqlSession.update()</li>
   *   <li>DELETE -> sqlSession.delete()</li>
   *   <li>SELECT -> 根据返回类型选择：
   *     <ul>
   *       <li>selectOne(): 返回单个对象</li>
   *       <li>selectList(): 返回集合</li>
   *       <li>selectMap(): 返回Map</li>
   *       <li>selectCursor(): 返回Cursor</li>
   *       <li>select(): 使用ResultHandler处理结果</li>
   *     </ul>
   *   </li>
   *   <li>FLUSH -> sqlSession.flushStatements()</li>
   * </ul>
   *
   * @param sqlSession 当前SQL会话对象，用于执行SQL操作
   * @param args Mapper方法的参数数组
   * @return 方法执行结果，会根据方法签名进行类型转换
   */
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
        // 转换参数并执行插入操作
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        // 转换参数并执行更新操作
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        // 转换参数并执行删除操作
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        if (method.returnsVoid() && method.hasResultHandler()) {
          // 情况1：无返回值但使用ResultHandler处理结果集
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          // 情况2：返回集合类型（List、Array等）
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          // 情况3：返回Map类型
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          // 情况4：返回Cursor类型（用于流式查询）
          result = executeForCursor(sqlSession, args);
        } else {
          // 情况5：返回单个对象
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          // 处理Optional返回类型
          if (method.returnsOptional() &&
              (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        // 执行批量语句刷新
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }

    // 处理原始类型返回值为null的情况
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + "' attempted to return null from a method with a primitive return type ("
          + method.getReturnType() + ").");
    }
    return result;
  }

  /**
   * 处理行数统计结果
   * 根据方法返回类型将受影响的行数转换为相应的返回值
   *
   * @param rowCount 受影响的行数
   * @return 转换后的返回值
   */
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) ||
               Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) ||
               Long.TYPE.equals(method.getReturnType())) {
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) ||
               Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName()
          + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  /**
   * 使用ResultHandler执行查询
   * 处理使用自定义ResultHandler的查询操作
   */
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  /**
   * 执行返回多个结果的查询
   * 处理返回List、Array等集合类型的查询操作
   */
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // 处理集合类型转换
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      }
      return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
    }
    return result;
  }

  /**
   * 执行返回Cursor的查询
   * 处理返回Cursor类型的查询操作，用于流式查询
   */
  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (!arrayComponentType.isPrimitive()) {
      return list.toArray((E[]) array);
    }
    for (int i = 0; i < list.size(); i++) {
      Array.set(array, i, list.get(i));
    }
    return array;
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  /**
   * SQL命令类，封装了SQL语句的元数据信息
   * 主要用于存储SQL语句的类型和标识符
   */
  public static class SqlCommand {
    /**
     * SQL语句的名称，通常是 "命名空间.方法名"
     */
    private final String name;

    /**
     * SQL语句的类型（INSERT、UPDATE、DELETE、SELECT、FLUSH等）
     */
    private final SqlCommandType type;

    /**
     * 构造函数，解析Mapper方法对应的SQL命令信息
     *
     * @param configuration MyBatis配置对象
     * @param mapperInterface Mapper接口类
     * @param method 方法对象
     */
    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass, configuration);

      // 处理@Flush注解的特殊情况
      if (ms == null) {
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName, Class<?> declaringClass,
        Configuration configuration) {
      String statementId = mapperInterface.getName() + "." + methodName;
      if (configuration.hasStatement(statementId)) {
        return configuration.getMappedStatement(statementId);
      }
      if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName, declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  /**
   * 方法签名类，封装了Mapper方法的签名信息
   * 负责处理方法的参数和返回值相关的所有操作
   */
  public static class MethodSignature {
    /**
     * 是否返回多个结果
     */
    private final boolean returnsMany;
    /**
     * 是否返回Map
     */
    private final boolean returnsMap;
    /**
     * 是否返回void
     */
    private final boolean returnsVoid;
    /**
     * 是否返回Cursor
     */
    private final boolean returnsCursor;
    /**
     * 是否返回Optional
     */
    private final boolean returnsOptional;
    /**
     * 返回类型
     */
    private final Class<?> returnType;
    /**
     * 如果返回Map，指定Map的key
     */
    private final String mapKey;
    /**
     * 结果处理器索引
     */
    private final Integer resultHandlerIndex;
    /**
     * 行绑定索引
     */
    private final Integer rowBoundsIndex;
    /**
     * 参数名解析器
     */
    private final ParamNameResolver paramNameResolver;

    /**
     * 构造函数，解析方法签名信息
     *
     * @param configuration MyBatis配置对象
     * @param mapperInterface Mapper接口类
     * @param method 方法对象
     */
    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 解析返回类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }

      // 解析返回值特性
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      this.returnsCursor = Cursor.class.equals(this.returnType);
      this.returnsOptional = Optional.class.equals(this.returnType);
      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;

      // 解析参数特性
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    /**
     * 将方法参数转换为SQL参数
     *
     * @param args 方法参数数组
     * @return 转换后的SQL参数
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    /**
     * 提取结果处理器参数
     */
    public ResultHandler<?> extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler<?>) args[resultHandlerIndex] : null;
    }

    /**
     * 提取分页参数
     */
    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : RowBounds.DEFAULT;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     *
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index != null) {
            throw new BindingException(
                method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
          index = i;
        }
      }
      return index;
    }

    public String getMapKey() {
      return mapKey;
    }

    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }
  }

}
