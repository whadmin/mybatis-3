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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;

/**
 * 同步缓存装饰器
 * <p>
 * 为缓存操作添加线程安全支持的装饰器。
 * 通过在所有缓存操作方法上添加synchronized关键字，
 * 确保多线程环境下缓存的线程安全性。
 *
 * @author Clinton Begin
 */
public class SynchronizedCache implements Cache {

  /**
   * 被装饰的底层缓存
   * 实际存储缓存数据的对象
   */
  private final Cache delegate;

  /**
   * 构造函数
   *
   * @param delegate 被装饰的底层缓存对象
   */
  public SynchronizedCache(Cache delegate) {
    this.delegate = delegate;
  }

  /**
   * 获取缓存ID
   * 注意：此方法未加同步，因为ID通常是不可变的
   *
   * @return 缓存的唯一标识符
   */
  @Override
  public String getId() {
    return delegate.getId();
  }

  /**
   * 获取缓存大小
   * 使用synchronized关键字确保线程安全
   *
   * @return 缓存中的元素数量
   */
  @Override
  public synchronized int getSize() {
    return delegate.getSize();
  }

  /**
   * 添加对象到缓存
   * 使用synchronized关键字确保线程安全
   *
   * @param key 缓存键
   * @param object 要缓存的对象
   */
  @Override
  public synchronized void putObject(Object key, Object object) {
    delegate.putObject(key, object);
  }

  /**
   * 从缓存获取对象
   * 使用synchronized关键字确保线程安全
   *
   * @param key 缓存键
   * @return 缓存中的对象，如果不存在则返回null
   */
  @Override
  public synchronized Object getObject(Object key) {
    return delegate.getObject(key);
  }

  /**
   * 从缓存移除对象
   * 使用synchronized关键字确保线程安全
   *
   * @param key 缓存键
   * @return 被移除的对象，如果不存在则返回null
   */
  @Override
  public synchronized Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存
   * 使用synchronized关键字确保线程安全
   */
  @Override
  public synchronized void clear() {
    delegate.clear();
  }

  /**
   * 获取哈希码
   * 注意：此方法未加同步，因为底层哈希码计算通常是线程安全的
   *
   * @return 哈希码
   */
  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  /**
   * 判断对象是否相等
   * 注意：此方法未加同步，因为对象比较通常是线程安全的
   *
   * @param obj 要比较的对象
   * @return 是否相等
   */
  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

}
