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
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * 缓存提供者的服务提供接口(SPI)。
 * <p>
 * 每个命名空间将创建一个缓存实例。
 * <p>
 * 缓存实现必须具有一个接收缓存id作为String参数的构造函数。
 * <p>
 * MyBatis将命名空间作为id传递给构造函数。
 *
 * <pre>
 * public MyCache(final String id) {
 *   if (id == null) {
 *     throw new IllegalArgumentException("缓存实例需要一个ID");
 *   }
 *   this.id = id;
 *   initialize();
 * }
 * </pre>
 *
 * @author Clinton Begin
 */

public interface Cache {

  /**
   * 获取缓存的标识符
   *
   * @return 此缓存的标识符，通常是mapper的命名空间
   */
  String getId();

  /**
   * 将对象存入缓存
   *
   * @param key
   *          可以是任何对象，但通常是{@link CacheKey}
   * @param value
   *          查询的结果对象
   */
  void putObject(Object key, Object value);

  /**
   * 从缓存获取对象
   *
   * @param key
   *          缓存键
   *
   * @return 存储在缓存中的对象，如果不存在则返回null
   */
  Object getObject(Object key);

  /**
   * 从缓存移除对象
   * <p>
   * 从3.3.0版本开始，此方法仅在回滚期间为先前在缓存中缺失的值调用。
   * 这允许任何阻塞缓存释放可能之前放在键上的锁。阻塞缓存在值为null时放置锁，
   * 当值再次可用时释放锁。这样，其他线程将等待值可用，而不是直接查询数据库。
   *
   * @param key
   *          缓存键
   *
   * @return 不使用返回值
   */
  Object removeObject(Object key);

  /**
   * 清空此缓存实例中的所有对象
   */
  void clear();

  /**
   * 获取缓存中元素数量
   * <p>
   * 可选实现。此方法不会被核心代码调用。
   *
   * @return 存储在缓存中的元素数量（不是其容量）
   */
  int getSize();

  /**
   * 获取读写锁
   * <p>
   * 可选实现。从3.2.6版本开始，此方法不再被核心代码调用。
   * <p>
   * 缓存所需的任何锁定必须由缓存提供者内部提供。
   *
   * @return 读写锁对象，默认返回null
   */
  default ReadWriteLock getReadWriteLock() {
    return null;
  }

}
