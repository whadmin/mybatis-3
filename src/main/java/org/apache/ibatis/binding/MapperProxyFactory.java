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
package org.apache.ibatis.binding;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperProxy.MapperMethodInvoker;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper代理工厂类，用于创建Mapper接口的代理实例。
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>为Mapper接口创建动态代理实例</li>
 *   <li>维护方法的调用器缓存，提高方法调用性能</li>
 *   <li>管理Mapper接口的代理对象生命周期</li>
 *   <li>与SqlSession协同工作，处理数据库操作</li>
 * </ul>
 *
 * <p>处理逻辑：</p>
 * <ol>
 *   <li>接收Mapper接口类型，初始化方法缓存</li>
 *   <li>当需要创建代理实例时，构造MapperProxy对象</li>
 *   <li>使用JDK动态代理创建接口代理实例</li>
 *   <li>代理实例将方法调用委托给MapperProxy处理</li>
 *   <li>MapperProxy使用缓存的调用器执行实际的数据库操作</li>
 * </ol>
 *
 * @author Lasse Voss
 *
 * @param <T> Mapper接口类型
 */
public class MapperProxyFactory<T> {

  /**
   * Mapper接口的Class对象
   */
  private final Class<T> mapperInterface;

  /**
   * 用于缓存Mapper接口中方法的调用器
   * 键为方法对象，值为对应的调用器
   * 使用ConcurrentHashMap保证线程安全
   */
  private final Map<Method, MapperMethodInvoker> methodCache = new ConcurrentHashMap<>();

  /**
   * 构造函数，初始化Mapper接口类型
   *
   * @param mapperInterface Mapper接口的Class对象
   */
  public MapperProxyFactory(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * 获取Mapper接口的Class对象
   *
   * @return Mapper接口的Class对象
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * 获取方法调用器缓存Map
   *
   * @return 方法调用器缓存Map
   */
  public Map<Method, MapperMethodInvoker> getMethodCache() {
    return methodCache;
  }

  /**
   * 使用JDK动态代理创建Mapper接口的代理实例
   *
   * @param mapperProxy MapperProxy代理对象
   * @return Mapper接口的代理实例
   */
  @SuppressWarnings("unchecked")
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(),
        new Class[] { mapperInterface }, mapperProxy);
  }

  /**
   * 创建Mapper接口的代理实例
   *
   * @param sqlSession 当前的SqlSession对象
   * @return Mapper接口的代理实例
   */
  public T newInstance(SqlSession sqlSession) {
    final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }
}
