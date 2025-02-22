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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.util.MapUtil;

/**
 * Mapper代理类，用于创建Mapper接口的动态代理实现。
 * 实现了InvocationHandler接口以提供代理逻辑，同时实现Serializable接口支持序列化。
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>作为Mapper接口的动态代理实现，处理所有Mapper接口方法的调用</li>
 *   <li>支持接口默认方法的调用（Java 8特性）</li>
 *   <li>通过缓存机制优化方法调用性能</li>
 *   <li>适配不同Java版本（8和9+）的反射API调用方式</li>
 * </ul>
 *
 * <p>处理逻辑：</p>
 * <ol>
 *   <li>当调用Mapper接口的方法时，会先进入invoke方法</li>
 *   <li>判断是否是Object类的方法，如果是则直接调用</li>
 *   <li>否则获取缓存的方法调用器（MapperMethodInvoker）</li>
 *   <li>根据方法是否为默认方法，选择不同的调用器实现</li>
 *   <li>最终执行对应的SQL操作或默认方法实现</li>
 * </ol>
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -4724728412955527868L;

  /**
   * 定义允许的方法查找模式，包括私有、保护、包级别和公共方法的访问权限
   */
  private static final int ALLOWED_MODES = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
      | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;

  /**
   * 用于Java 8中获取方法句柄的构造器
   */
  private static final Constructor<Lookup> lookupConstructor;

  /**
   * 用于Java 9及以上版本获取私有方法查找器的方法
   */
  private static final Method privateLookupInMethod;

  /**
   * 维护的SqlSession实例，用于执行SQL操作
   */
  private final SqlSession sqlSession;

  /**
   * Mapper接口的Class对象
   */
  private final Class<T> mapperInterface;

  /**
   * 缓存方法的调用器，避免重复创建MapperMethod对象
   */
  private final Map<Method, MapperMethodInvoker> methodCache;

  /**
   * 静态初始化块，初始化Java版本相关的反射工具
   */
  static {
    Method privateLookupIn;
    try {
      // 尝试获取Java 9+的privateLookupIn方法
      privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    } catch (NoSuchMethodException e) {
      privateLookupIn = null;
    }
    privateLookupInMethod = privateLookupIn;

    Constructor<Lookup> lookup = null;
    if (privateLookupInMethod == null) {
      // 如果是Java 8，则使用Lookup构造器
      try {
        lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        lookup.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
            e);
      } catch (Exception e) {
        lookup = null;
      }
    }
    lookupConstructor = lookup;
  }

  /**
   * 构造函数，初始化代理所需的核心成员
   */
  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethodInvoker> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  /**
   * 代理方法的主要实现，处理所有的方法调用
   *
   * @param proxy 代理对象
   * @param method 被调用的方法
   * @param args 方法参数
   * @return 方法执行结果
   * @throws Throwable 执行过程中可能抛出的异常
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 如果是Object类的方法，直接调用
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      }
      // 对于Mapper接口方法，使用缓存的调用器执行
      return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * 获取方法的调用器，如果缓存中没有则创建新的调用器
   *
   * @param method 需要执行的方法
   * @return 方法调用器
   * @throws Throwable 创建调用器过程中可能抛出的异常
   */
  private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
    try {
      return MapUtil.computeIfAbsent(methodCache, method, m -> {
        if (!m.isDefault()) {
          // 非默认方法，创建普通方法调用器
          return new PlainMethodInvoker(new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
        }
        try {
          // 默认方法，根据Java版本选择不同的方法句柄获取方式
          if (privateLookupInMethod == null) {
            return new DefaultMethodInvoker(getMethodHandleJava8(method));
          }
          return new DefaultMethodInvoker(getMethodHandleJava9(method));
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException
            | NoSuchMethodException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (RuntimeException re) {
      Throwable cause = re.getCause();
      throw cause == null ? re : cause;
    }
  }

  /**
   * 获取Java 9及以上版本的方法句柄
   */
  private MethodHandle getMethodHandleJava9(Method method)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup())).findSpecial(
        declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
        declaringClass);
  }

  /**
   * 获取Java 8版本的方法句柄
   */
  private MethodHandle getMethodHandleJava8(Method method)
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass);
  }

  /**
   * Mapper方法调用器接口，定义了执行Mapper方法的标准
   * 用于统一处理普通方法和默认方法的调用逻辑
   */
  interface MapperMethodInvoker {
    /**
     * 执行Mapper方法
     *
     * @param proxy 代理对象
     * @param method 要执行的方法
     * @param args 方法参数
     * @param sqlSession SQL会话对象
     * @return 方法执行结果
     * @throws Throwable 执行过程中可能抛出的异常
     */
    Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable;
  }

  /**
   * 普通方法调用器实现类
   * 用于处理Mapper接口中的普通方法（非默认方法）
   * 将方法调用委托给MapperMethod执行具体的数据库操作
   */
  private static class PlainMethodInvoker implements MapperMethodInvoker {
    /**
     * MapperMethod实例，封装了SQL操作的具体执行逻辑
     */
    private final MapperMethod mapperMethod;

    public PlainMethodInvoker(MapperMethod mapperMethod) {
      this.mapperMethod = mapperMethod;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      // 委托给MapperMethod执行实际的数据库操作
      return mapperMethod.execute(sqlSession, args);
    }
  }

  /**
   * 默认方法调用器实现类
   * 用于处理Mapper接口中的默认方法（Java 8特性）
   * 通过方法句柄（MethodHandle）直接调用接口的默认实现
   */
  private static class DefaultMethodInvoker implements MapperMethodInvoker {
    /**
     * 方法句柄，用于调用接口的默认方法实现
     */
    private final MethodHandle methodHandle;

    public DefaultMethodInvoker(MethodHandle methodHandle) {
      this.methodHandle = methodHandle;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      // 将方法句柄绑定到代理对象并执行默认方法
      return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
  }
}
