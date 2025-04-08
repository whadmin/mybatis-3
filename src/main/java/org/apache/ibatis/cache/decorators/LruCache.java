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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * LRU (最近最少使用) 缓存装饰器
 * <p>
 * 实现最近最少使用的缓存淘汰策略，当缓存达到上限时，
 * 自动淘汰最长时间未被访问的缓存项。
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  /**
   * 被装饰的底层缓存，实际存储数据
   */
  private final Cache delegate;

  /**
   * 用于跟踪缓存项访问顺序的映射
   * 利用LinkedHashMap的访问顺序特性实现LRU
   */
  private Map<Object, Object> keyMap;

  /**
   * 记录最近被淘汰的键
   * 当缓存满时，此变量会被设置为最老的键
   */
  private Object eldestKey;

  /**
   * 构造函数
   *
   * @param delegate 被装饰的底层缓存
   */
  public LruCache(Cache delegate) {
    this.delegate = delegate;
    // 默认缓存大小为1024
    setSize(1024);
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
   * 设置LRU缓存的最大容量
   * <p>
   * 创建一个自定义的LinkedHashMap，通过重写removeEldestEntry方法
   * 实现LRU淘汰机制
   *
   * @param size 缓存的最大容量
   */
  public void setSize(final int size) {
    // 创建一个LinkedHashMap，第三个参数true表示按访问顺序排序（而非插入顺序）
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      /**
       * 重写removeEldestEntry方法实现LRU淘汰策略
       * 当Map大小超过设定的阈值时，自动标记最老的元素准备移除
       *
       * @param eldest 最老的Map.Entry
       * @return true表示移除最老的元素，false表示保留
       */
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        // 判断当前大小是否超过设定的阈值
        boolean tooBig = size() > size;
        if (tooBig) {
          // 如果超过，记录最老的键，用于后续从实际缓存中删除
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  /**
   * 向缓存添加对象
   * <p>
   * 先添加到实际缓存，然后更新访问顺序记录，
   * 如果缓存已满，会触发淘汰机制
   *
   * @param key 缓存键
   * @param value 要缓存的对象
   */
  @Override
  public void putObject(Object key, Object value) {
    // 先存储到底层缓存
    delegate.putObject(key, value);
    // 更新访问顺序并处理淘汰
    cycleKeyList(key);
  }

  /**
   * 从缓存获取对象
   * <p>
   * 获取对象的同时更新访问顺序，将该键标记为最近使用过的
   *
   * @param key 缓存键
   * @return 缓存的对象，如果不存在则返回null
   */
  @Override
  public Object getObject(Object key) {
    // 更新键的访问记录，标记为最近使用
    keyMap.get(key); // touch
    // 从底层缓存获取实际值
    return delegate.getObject(key);
  }

  /**
   * 从缓存移除对象
   * <p>
   * 同时从访问顺序记录中移除
   *
   * @param key 缓存键
   * @return 被移除的对象，如果不存在则返回null
   */
  @Override
  public Object removeObject(Object key) {
    // 从访问顺序记录中移除
    keyMap.remove(key);
    // 从底层缓存移除
    return delegate.removeObject(key);
  }

  /**
   * 清空缓存
   * <p>
   * 清空实际缓存和访问顺序记录
   */
  @Override
  public void clear() {
    // 清空实际缓存
    delegate.clear();
    // 清空访问顺序记录
    keyMap.clear();
  }

  /**
   * 更新访问顺序记录并处理淘汰
   * <p>
   * 将新访问的键添加到访问顺序记录中，
   * 如果有被淘汰的键，从实际缓存中删除
   *
   * @param key 新访问的键
   */
  private void cycleKeyList(Object key) {
    // 将新访问的键添加到访问顺序记录中
    keyMap.put(key, key);
    // 如果有被淘汰的键，从实际缓存中删除
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
