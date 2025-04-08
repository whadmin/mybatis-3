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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 日志缓存装饰器
 * <p>
 * 用于记录缓存命中率统计信息的装饰器。
 * 每次从缓存获取数据时，都会计数并记录命中率。
 * 这对于缓存性能监控和调优非常有用。
 *
 * @author Clinton Begin
 */
public class LoggingCache implements Cache {

  /**
   * 日志记录器
   * 使用缓存ID作为日志名称
   */
  private final Log log;

  /**
   * 被装饰的底层缓存对象
   */
  private final Cache delegate;

  /**
   * 缓存请求总次数
   * 记录调用getObject的总次数
   */
  protected int requests;

  /**
   * 缓存命中次数
   * 记录成功从缓存获取数据的次数
   */
  protected int hits;

  /**
   * 构造函数
   *
   * @param delegate 被装饰的底层缓存对象
   */
  public LoggingCache(Cache delegate) {
    this.delegate = delegate;
    // 使用缓存ID作为日志名称初始化日志记录器
    this.log = LogFactory.getLog(getId());
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
   *
   * @return 缓存中的元素数量
   */
  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 向缓存添加对象
   * 直接委托给底层缓存，不进行统计
   *
   * @param key 缓存键
   * @param object 要缓存的对象
   */
  @Override
  public void putObject(Object key, Object object) {
    delegate.putObject(key, object);
  }

  /**
   * 从缓存获取对象
   * 记录请求次数和命中次数，并在调试级别日志中输出命中率
   *
   * @param key 缓存键
   * @return 缓存的对象，如果不存在则返回null
   */
  @Override
  public Object getObject(Object key) {
    // 增加请求计数
    requests++;
    // 从底层缓存获取值
    final Object value = delegate.getObject(key);
    // 如果值不为null，增加命中计数
    if (value != null) {
      hits++;
    }
    // 在调试级别输出命中率统计
    if (log.isDebugEnabled()) {
      log.debug("Cache Hit Ratio [" + getId() + "]: " + getHitRatio());
    }
    return value;
  }

  /**
   * 从缓存移除对象
   * 直接委托给底层缓存，不进行统计
   *
   * @param key 缓存键
   * @return 被移除的对象，如果不存在则返回null
   */
  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存
   * 直接委托给底层缓存，不重置统计数据
   */
  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 获取哈希码
   *
   * @return 底层缓存的哈希码
   */
  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  /**
   * 判断对象是否相等
   *
   * @param obj 要比较的对象
   * @return 是否与底层缓存相等
   */
  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  /**
   * 计算缓存命中率
   * 命中率 = 命中次数 / 请求总次数
   *
   * @return 缓存命中率，0.0到1.0之间的小数
   */
  private double getHitRatio() {
    return (double) hits / (double) requests;
  }

}
