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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * <p>
 * 简单的阻塞缓存装饰器
 * <p>
 * 这是EhCache的BlockingCache装饰器的简化版实现。当缓存中找不到元素时，它会在缓存键上设置锁。
 * 这样，其他线程将等待，直到该元素被填充，而不是直接访问数据库。
 * <p>
 * 由于其特性，如果使用不当，该实现可能导致死锁。
 *
 * @author Eduardo Macarron
 */
public class BlockingCache implements Cache {

  /**
   * 获取锁的超时时间（毫秒）
   * 0表示永不超时
   */
  private long timeout;

  /**
   * 被装饰的实际缓存对象
   * 采用装饰器模式，此类只负责阻塞逻辑，实际缓存操作委托给delegate
   */
  private final Cache delegate;

  /**
   * 用于存储每个缓存键对应的锁
   * 使用ConcurrentHashMap保证线程安全
   * CountDownLatch作为锁机制，初始计数为1
   */
  private final ConcurrentHashMap<Object, CountDownLatch> locks;

  /**
   * 构造函数，初始化阻塞缓存
   *
   * @param delegate 被装饰的实际缓存对象
   */
  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    // 委托给实际缓存对象
    return delegate.getId();
  }

  @Override
  public int getSize() {
    // 委托给实际缓存对象
    return delegate.getSize();
  }

  /**
   * 向缓存中添加对象
   * 添加完成后释放对应的锁，通知等待的线程
   *
   * @param key 缓存键
   * @param value 缓存值
   */
  @Override
  public void putObject(Object key, Object value) {
    try {
      // 委托实际缓存存储数据
      delegate.putObject(key, value);
    } finally {
      // 无论存储是否成功，都释放锁
      // 这样等待该键的其他线程可以继续执行
      releaseLock(key);
    }
  }

  /**
   * 从缓存获取对象
   * 1. 先获取该键的锁
   * 2. 尝试从缓存获取数据
   * 3. 如果数据存在，释放锁让其他线程也能获取
   * 4. 如果数据不存在，保持锁定状态，等待其他线程填充缓存
   *
   * @param key 缓存键
   * @return 缓存值，不存在则返回null
   */
  @Override
  public Object getObject(Object key) {
    // 获取该键的锁
    acquireLock(key);
    // 尝试从缓存获取值
    Object value = delegate.getObject(key);
    if (value != null) {
      // 值存在，释放锁让其他线程能获取
      releaseLock(key);
    }
    // 值不存在时不释放锁，调用者需调用putObject存入数据并释放锁
    return value;
  }

  /**
   * 从缓存移除对象
   * 实际上这个方法在MyBatis中仅用于释放锁，并不实际删除缓存项
   *
   * @param key 缓存键
   * @return 始终返回null
   */
  @Override
  public Object removeObject(Object key) {
    // 尽管方法名为移除对象，但实际上只是释放锁
    // 这是MyBatis内部使用约定
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    // 清空实际缓存
    delegate.clear();
    // 注意：没有清除locks，可能是个潜在问题
  }

  /**
   * 获取指定键的锁
   * 如果锁已存在，则等待锁释放
   *
   * @param key 缓存键
   * @throws CacheException 获取锁超时或线程被中断时抛出
   */
  private void acquireLock(Object key) {
    // 创建一个新的闭锁，计数为1
    CountDownLatch newLatch = new CountDownLatch(1);
    while (true) {
      // 尝试将新闭锁放入locks映射
      CountDownLatch latch = locks.putIfAbsent(key, newLatch);
      if (latch == null) {
        // putIfAbsent返回null表示成功放入，获取到锁
        break;
      }
      try {
        if (timeout > 0) {
          // 有超时设置，等待指定时间
          boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);
          if (!acquired) {
            // 超时未获取到锁，抛出异常
            throw new CacheException(
                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
          }
        } else {
          // 无超时设置，无限等待
          latch.await();
        }
      } catch (InterruptedException e) {
        // 线程被中断，抛出异常
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    }
  }

  /**
   * 释放指定键的锁
   * 通过countDown()方法减少闭锁计数，使等待的线程能继续执行
   *
   * @param key 缓存键
   * @throws IllegalStateException 尝试释放未获取的锁时抛出
   */
  private void releaseLock(Object key) {
    // 从locks映射中移除该键对应的闭锁
    CountDownLatch latch = locks.remove(key);
    if (latch == null) {
      // 如果没有找到闭锁，说明尝试释放一个未获取的锁
      throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
    }
    // 减少闭锁计数，释放所有等待线程
    latch.countDown();
  }

  /**
   * 获取超时时间
   *
   * @return 超时时间（毫秒）
   */
  public long getTimeout() {
    return timeout;
  }

  /**
   * 设置超时时间
   *
   * @param timeout 超时时间（毫秒）
   */
  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
