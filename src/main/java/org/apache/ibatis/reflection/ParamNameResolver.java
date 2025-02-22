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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 参数名称解析器，用于Mapper方法参数与转换成执行SqlSession参数的映射关系
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>解析方法参数名称（支持@Param注解和参数名发现机制）</li>
 *   <li>处理参数到SQL参数的转换</li>
 *   <li>支持多种参数传递方式（注解、顺序、Map等）</li>
 * </ul>
 *

 */
public class ParamNameResolver {

  /**
   * 默认参数名前缀
   * 当参数没有@Param注解时，使用 "param1", "param2" 等作为参数名
   */
  public static final String GENERIC_NAME_PREFIX = "param";

  /**
   * 是否使用实际参数名
   *
   * <p>该属性控制是否使用Java 8引入的参数名反射机制获取方法的实际参数名：</p>
   */
  private final boolean useActualParamName;

  /**
   * 参数名映射
   * key: 参数索引位置
   * value: 参数名
   */
  private final SortedMap<Integer, String> names;

  /**
   * 是否已经使用了@Param注解
   */
  private boolean hasParamAnnotation;

  /**
   * 构造函数，解析方法参数信息并建立参数索引到参数名的映射关系
   *
   * <p>参数名解析规则（按优先级）：</p>
   * <ol>
   *   <li>@Param注解指定的名称</li>
   *   <li>方法的实际参数名（需要开启-parameters编译选项）</li>
   *   <li>默认名称（param1, param2, ...）</li>
   * </ol>
   *
   * <p>特殊处理：</p>
   * <ul>
   *   <li>跳过RowBounds和ResultHandler类型的参数</li>
   *   <li>使用TreeMap保证参数顺序</li>
   *   <li>最终生成不可修改的参数名映射</li>
   * </ul>
   *
   *
   * @param config MyBatis配置对象，用于判断是否启用实际参数名
   * @param method 要解析的方法对象
   */
  public ParamNameResolver(Configuration config, Method method) {
    this.useActualParamName = config.isUseActualParamName();
    final Class<?>[] paramTypes = method.getParameterTypes();
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;

    // 遍历所有参数
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 跳过特殊参数类型
      if (isSpecialParameter(paramTypes[paramIndex])) {
        continue;
      }
      String name = null;

      // 1. 首先查找@Param注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          name = ((Param) annotation).value();
          break;
        }
      }

      if (name == null) {
        // 2. 尝试获取实际参数名
        if (useActualParamName) {
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // 3. 使用默认命名：参数位置索引（从1开始）
          name = String.valueOf(map.size() + 1);
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  /**
   * 将Mapper接口方法的参数转换为SqlSession执行时需要的参数
   *
   * @param args Mapper方法的参数数组
   * @return 转换后的参数对象，可能是原始参数值或ParamMap
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      // 单参数且无@Param注解的情况
      Object value = args[names.firstKey()];
      return wrapToMapIfCollection(value, useActualParamName ? names.get(names.firstKey()) : null);
    } else {
      // 多参数或有@Param注解的情况
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        // 添加参数名到参数值的映射
        param.put(entry.getValue(), args[entry.getKey()]);
        // 添加通用参数名（param1, param2, ...）到参数值的映射
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * 如果参数是集合类型，将其包装为Map
   *
   * <p>优化说明：</p>
   * <ul>
   *   <li>使用ParamMap提供类型安全</li>
   *   <li>为List类型提供双重访问方式（collection和list）</li>
   *   <li>数组类型统一使用array键访问</li>
   * </ul>
   *
   * <p>SQL中的访问方式：</p>
   * <pre>
   * // 对于List参数
   * #{collection[0]}, #{list[0]}
   *
   * // 对于数组参数
   * #{array[0]}
   *
   * // 在foreach中使用
   * <foreach collection="list" item="item">
   *     #{item}
   * </foreach>
   * </pre>
   *
   * @param object 要处理的参数对象
   * @return 处理后的参数对象
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }

  /**
   * 判断是否为特殊参数类型
   * 特殊参数（RowBounds和ResultHandler）会被框架特殊处理，
   * 不会作为SQL参数传递
   */
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * 获取参数的实际名称
   * 通过反射获取方法参数的真实名称，
   * 需要编译时开启-parameters选项才能获取到
   */
  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  /**
   * 获取参数名称映射
   *
   * @return 不可修改的参数名称映射
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

}
