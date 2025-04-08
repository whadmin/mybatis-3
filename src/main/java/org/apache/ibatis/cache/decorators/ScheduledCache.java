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

import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.Cache;

/**
 * 定时缓存装饰器
 * <p>
 * 提供基于时间间隔的缓 存自动清理机制。
 * 在每次缓存操作前检查时间间隔，如果超过设定的清理间隔，则自动清空缓存。
 * 默认清理间隔为1小时。
 *
 * @author Clinton Begin
 */
public class ScheduledCache implements Cache {

  /**
   * 被装饰的底层缓存对象
   */
  private final Cache delegate;

  /**
   * 清理时间间隔，单位为毫秒
   * 默认为1小时
   */
  protected long clearInterval;

  /**
   * 上次清理缓存的时间戳
   */
  protected long lastClear;

  /**
   * 构造函数
   *
   * @param delegate 被装饰的底层缓存对象
   */
  public ScheduledCache(Cache delegate) {
    this.delegate = delegate;
    // 默认清理间隔为1小时
    this.clearInterval = TimeUnit.HOURS.toMillis(1);
    // 初始化上次清理时间为当前时间
    this.lastClear = System.currentTimeMillis();
  }

  /**
   * 设置清理间隔时间
   *
   * @param clearInterval 清理间隔，单位为毫秒
   */
  public void setClearInterval(long clearInterval) {
    this.clearInterval = clearInterval;
  }

  /**
   * 获取缓存ID
   *
   * @return 缓存的唯一标识符
   */
  @Override
  public String getId() {
    return delegate.getId();
  }

  /**
   * 获取缓存大小
   * 在返回大小前先检查是否需要清理缓存
   *
   * @return 缓存中的元素数量
   */
  @Override
  public int getSize() {
    clearWhenStale();
    return delegate.getSize();
  }

  /**
   * 向缓存添加对象
   * 在添加前先检查是否需要清理缓存
   *
   * @param key 缓存键
   * @param object 要缓存的对象
   */
  @Override
  public void putObject(Object key, Object object) {
    clearWhenStale();
    delegate.putObject(key, object);
  }

  /**
   * 从缓存获取对象
   * 在获取前先检查是否需要清理缓存
   * 如果执行了清理操作，则返回null（强制缓存未命中）
   *
   * @param key 缓存键
   * @return 缓存对象，如果执行了清理或对象不存在则返回null
   */
  @Override
  public Object getObject(Object key) {
    // 如果刚执行了清理操作，直接返回null强制缓存未命中
    return clearWhenStale() ? null : delegate.getObject(key);
  }

  /**
   * 从缓存移除对象
   * 在移除前先检查是否需要清理缓存
   *
   * @param key 缓存键
   * @return 被移除的对象，如果不存在则返回null
   */
  @Override
  public Object removeObject(Object key) {
    clearWhenStale();
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存
   * 更新最后清理时间并清空底层缓存
   */
  @Override
  public void clear() {
    lastClear = System.currentTimeMillis();
    delegate.clear();
  }

  /**
   * 获取缓存对象的哈希码
   *
   * @return 哈希码
   */
  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  /**
   * 判断对象是否相等
   *
   * @param obj 要比较的对象
   * @return 是否相等
   */
  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  /**
   * 检查并在必要时清理缓存
   * 如果当前时间距离上次清理的时间超过了设定的清理间隔，则清空缓存
   *
   * @return 如果执行了清理操作则返回true，否则返回false
   */
  private boolean clearWhenStale() {
    if (System.currentTimeMillis() - lastClear > clearInterval) {
      clear();
      return true;
    }
    return false;
  }
}
