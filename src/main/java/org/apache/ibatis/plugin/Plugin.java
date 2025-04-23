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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

/**
 * MyBatis 插件实现类，基于 JDK 动态代理实现拦截器功能
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  /**
   * 被代理的目标对象，如 Executor、StatementHandler 等
   */
  private final Object target;

  /**
   * 用户自定义的拦截器实现
   */
  private final Interceptor interceptor;

  /**
   * 拦截器要拦截的方法签名映射
   * key: 接口类，如 Executor.class, StatementHandler.class 等
   * value: 要拦截的方法集合
   */
  private final Map<Class<?>, Set<Method>> signatureMap;

  /**
   * 私有构造函数，只能通过 wrap 方法创建实例
   *
   * @param target 被代理的目标对象
   * @param interceptor 拦截器实例
   * @param signatureMap 方法签名映射
   */
  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  /**
   * 将目标对象包装成代理对象
   * 该方法是插件机制的入口，通过该方法为目标对象创建代理
   *
   * 示例:
   * StatementHandler handler = ...; // 原始对象
   * LogInterceptor logInterceptor = new LogInterceptor(); // 自定义拦截器
   * StatementHandler proxy = (StatementHandler)Plugin.wrap(handler, logInterceptor); // 创建代理
   *
   * @param target 要被代理的目标对象
   * @param interceptor 拦截器实例
   * @return 代理对象或原始对象（如果无需代理）
   */
  public static Object wrap(Object target, Interceptor interceptor) {
    // 获取拦截器中定义的签名映射（通过@Intercepts注解解析）
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    // 获取目标对象的类型
    Class<?> type = target.getClass();
    // 获取目标类型中需要被拦截的接口（接口必须在signatureMap中定义）
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    if (interfaces.length > 0) {
      // 使用JDK动态代理创建代理对象
      // 例如：如果target是PreparedStatementHandler，且interceptor拦截了StatementHandler接口，
      // 则会创建一个实现了StatementHandler接口的代理对象
      return Proxy.newProxyInstance(type.getClassLoader(), interfaces, new Plugin(target, interceptor, signatureMap));
    }
    // 如果没有需要被代理的接口，返回原始对象
    // 例如：如果interceptor只拦截了Executor接口，但target是StatementHandler类型，则返回原始target
    return target;
  }

  /**
   * 代理对象的方法调用处理
   * 实现InvocationHandler接口的方法，当调用代理对象的方法时会被调用
   *
   * 示例流程:
   * 1. 调用 statementHandler.prepare(conn, timeout)
   * 2. 触发该invoke方法
   * 3. 判断prepare方法是否需要被拦截
   * 4. 如需拦截，调用interceptor.intercept处理
   * 5. 如不需拦截，直接调用原始对象的prepare方法
   *
   * @param proxy 代理对象
   * @param method 被调用的方法
   * @param args 方法参数
   * @return 方法调用结果
   * @throws Throwable 如果调用过程中发生异常
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 获取当前方法所属接口中需要被拦截的方法集合
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      // 如果当前方法需要被拦截
      if (methods != null && methods.contains(method)) {
        // 调用拦截器的intercept方法处理
        // 例如：如果是StatementHandler.prepare方法，且有对应拦截器，
        // 则会创建Invocation对象并调用interceptor.intercept方法
        return interceptor.intercept(new Invocation(target, method, args));
      }
      // 如果不需要拦截，则直接调用原始方法
      // 例如：对于StatementHandler.getBoundSql方法，如果没有配置拦截，
      // 则直接调用原始StatementHandler对象的getBoundSql方法
      return method.invoke(target, args);
    } catch (Exception e) {
      // 异常处理，解包异常
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  /**
   * 从拦截器的@Intercepts注解中解析出需要拦截的方法签名映射
   *
   * 示例:
   * 对于如下拦截器定义:
   * @Intercepts({
   *   @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
   *   @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
   * })
   * public class MyInterceptor implements Interceptor { ... }
   *
   * 将生成包含两个接口的映射:
   * 1. StatementHandler.class -> prepare方法
   * 2. Executor.class -> update方法
   *
   * @param interceptor 拦截器实例
   * @return 方法签名映射
   */
  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    // 获取拦截器类上的@Intercepts注解
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // 如果没有@Intercepts注解，抛出异常
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException(
          "No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    // 获取所有的@Signature
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    // 遍历每个@Signature，构建签名映射
    for (Signature sig : sigs) {
      // 为每个接口类型创建方法集合
      // 例如: signatureMap.put(StatementHandler.class, new HashSet<>())
      Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type(), k -> new HashSet<>());
      try {
        // 根据方法名和参数类型获取方法对象
        // 例如: StatementHandler.class.getMethod("prepare", Connection.class, Integer.class)
        Method method = sig.type().getMethod(sig.method(), sig.args());
        // 将方法添加到集合中
        methods.add(method);
      } catch (NoSuchMethodException e) {
        // 如果方法不存在，抛出异常
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e,
            e);
      }
    }
    return signatureMap;
  }

  /**
   * 获取类型中所有需要被代理的接口
   * 需要被代理的接口必须在signatureMap中定义
   *
   * 示例:
   * 如果target是PreparedStatementHandler类型，它实现了StatementHandler接口
   * 且signatureMap包含StatementHandler.class作为key
   * 则返回的结果中会包含StatementHandler.class
   *
   * 为什么需要递归查找?
   * 因为可能存在多级继承的情况，比如:
   * class A implements X, Y {}
   * class B extends A implements Z {}
   * 如果target是B类型，我们需要找出X、Y、Z三个接口中哪些在signatureMap中
   *
   * @param type 目标对象类型
   * @param signatureMap 方法签名映射
   * @return 需要被代理的接口数组
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<>();
    // 遍历类的继承层次结构
    while (type != null) {
      // 遍历当前类实现的所有接口
      for (Class<?> c : type.getInterfaces()) {
        // 如果接口在签名映射中，则添加到集合
        // 例如: 如果c是StatementHandler.class且signatureMap包含这个key，则添加
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      // 向上查找父类
      // 例如: 从PreparedStatementHandler查找BaseStatementHandler，再查找Object
      type = type.getSuperclass();
    }
    // 转换为数组返回
    return interfaces.toArray(new Class<?>[0]);
  }
}
