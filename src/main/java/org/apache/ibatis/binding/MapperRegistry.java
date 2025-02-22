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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper接口注册表，管理Mapper接口和对应代理工厂的注册关系
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>注册Mapper接口</li>
 *   <li>创建Mapper接口的代理实例</li>
 *   <li>管理Mapper接口的代理工厂</li>
 *   <li>检查Mapper接口的有效性</li>
 * </ul>
 *
 * <p>工作流程：</p>
 * <ul>
 *   <li>1. 扫描并注册Mapper接口</li>
 *   <li>2. 为每个Mapper接口创建代理工厂</li>
 *   <li>3. 根据需要生成Mapper接口的代理实例</li>
 *   <li>4. 缓存和复用代理工厂</li>
 * </ul>
 *
 * <p>注意事项：</p>
 * <ul>
 *   <li>Mapper接口必须是接口而不能是类</li>
 *   <li>同一个Mapper接口只能注册一次</li>
 *   <li>注册后的Mapper对所有SqlSession可见</li>
 *   <li>代理工厂会被缓存以提升性能</li>
 * </ul>
 */
public class MapperRegistry {

  /**
   * MyBatis配置对象，包含完整的映射器配置信息
   */
  private final Configuration config;

  /**
   * 已知Mapper接口的代理工厂映射表
   * Key: Mapper接口类
   * Value: 对应的代理工厂
   */
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new ConcurrentHashMap<>();

  /**
   * 构造函数
   *
   * @param config MyBatis配置对象
   */
  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  /**
   * 获取Mapper接口的代理实例
   *
   * @param <T> Mapper接口类型
   * @param type Mapper接口类
   * @param sqlSession 当前SqlSession
   * @return Mapper接口的代理实例
   * @throws BindingException 当Mapper未注册或创建代理实例失败时
   */
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  /**
   * 检查指定的Mapper接口是否已注册
   *
   * @param <T> Mapper接口类型
   * @param type Mapper接口类
   * @return 如果已注册返回true，否则返回false
   */
  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * 添加Mapper接口到注册表
   *
   * <p>处理流程：</p>
   * <ol>
   *   <li>检查是否为接口</li>
   *   <li>检查是否已经注册</li>
   *   <li>创建代理工厂</li>
   *   <li>解析接口注解</li>
   * </ol>
   *
   * @param <T> Mapper接口类型
   * @param type Mapper接口类
   * @throws BindingException 当类型不是接口或已经注册时
   */
  public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) {
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        knownMappers.put(type, new MapperProxyFactory<>(type));
        // 在解析注解之前必须先添加到knownMappers
        // 否则可能会导致注解解析时的自动绑定失败
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * 获取所有已注册的Mapper接口类
   *
   * @return Mapper接口类集合（只读）
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * 扫描指定包下的所有Mapper接口并注册
   *
   * @param packageName 包名
   * @param superType 父类型，用于过滤
   */
  public void addMappers(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {
      addMapper(mapperClass);
    }
  }

  /**
   * 扫描指定包下的所有类并注册为Mapper
   *
   * @param packageName 包名
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

}
