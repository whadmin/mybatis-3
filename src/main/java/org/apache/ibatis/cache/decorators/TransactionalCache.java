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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 二级缓存的事务性缓冲区
 * <p>
 * 主要功能：为二级缓存提供事务隔离，确保在事务提交前，对缓存的修改不会影响其他事务
 *
 * 工作原理：
 * 1. 事务期间缓存操作:
 *    - 读取: 直接从底层缓存读取，若有更新操作则强制返回null避免脏读
 *    - 写入: 不直接写入底层缓存，而是存入暂存区(entriesToAddOnCommit)
 *    - 清除: 不直接清除底层缓存，只设置清除标记(clearOnCommit)
 *
 * 2. 事务提交时:
 *    - 如果设置了clearOnCommit标记，先清空底层缓存
 *    - 将暂存区中的所有条目写入底层缓存
 *    - 处理缓存未命中记录，支持BlockingCache的解锁操作
 *    - 重置所有临时状态
 *
 * 3. 事务回滚时:
 *    - 释放所有缓存未命中键的锁(针对BlockingCache)
 *    - 丢弃暂存区中的所有缓存条目
 *    - 重置所有临时状态
 *
 * 核心设计：类似Git的暂存区机制，所有修改先放入暂存区，只有在提交时才真正应用
 *
 * 使用场景:
 * - 在多会话并发环境中，确保缓存一致性和事务隔离
 * - 防止某个会话中的更新在事务提交前对其他会话可见
 * - 作为CachingExecutor的核心组件，实现MyBatis的二级缓存事务控制
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  /**
   * 日志记录器，用于记录缓存操作异常等情况
   */
  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  /**
   * 被装饰的底层缓存，实际存储数据的地方
   * 相当于Git中的"仓库"，存储最终提交的数据
   */
  private final Cache delegate;

  /**
   * 提交时是否清空缓存的标志
   * 当执行更新操作时，此标志会被设置为true
   * 防止更新操作导致的脏读问题
   */
  private boolean clearOnCommit;

  /**
   * 缓存暂存区，存储待提交的缓存条目
   * 相当于Git中的"暂存区"(stage/index)
   * 所有缓存修改都先写入此暂存区，只有在事务提交时才会真正写入底层缓存
   * 这种设计实现了写入操作的事务隔离性，避免未提交的修改被其他事务看到
   */
  private final Map<Object, Object> entriesToAddOnCommit;

  /**
   * 记录缓存未命中的键集合
   * 主要用于支持BlockingCache的解锁操作
   * 防止死锁情况发生
   */
  private final Set<Object> entriesMissedInCache;

  /**
   * 构造函数
   *
   * @param delegate 被装饰的底层缓存，实际存储数据的地方
   */
  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  /**
   * 获取缓存ID
   *
   * @return 缓存ID，委托给底层缓存
   */
  @Override
  public String getId() {
    return delegate.getId();
  }

  /**
   * 获取缓存大小
   *
   * @return 缓存条目数量，委托给底层缓存
   */
  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 从缓存获取对象
   * 如果clearOnCommit为true，表示事务中有更新操作，强制返回null避免脏读
   *
   * @param key 缓存键
   * @return 缓存的对象，如果未找到或标记清空则返回null
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key);
    if (object == null) {
      // 缓存未命中，记录到未命中集合中，用于后续解锁操作
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      // 如果事务中有更新操作，强制返回null避免脏读
      // 确保事务隔离性
      return null;
    }
    return object;
  }

  /**
   * 将对象存入缓存
   * 注意：此方法不会立即将对象写入底层缓存，而是放入暂存区
   * 类似Git的"git add"操作，只是标记要提交的内容
   *
   * @param key 缓存键
   * @param object 要缓存的对象
   */
  @Override
  public void putObject(Object key, Object object) {
    // 放入暂存区，等待事务提交时批量写入底层缓存
    // 避免其他事务看到未提交的修改
    entriesToAddOnCommit.put(key, object);
  }

  /**
   * 从缓存移除对象
   * 在这个实现中实际上什么也不做，返回null
   * 注意：此方法主要被BlockingCache用于解锁操作
   *
   * @param key 缓存键
   * @return 始终返回null
   */
  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 清空缓存
   * 注意：此方法不会立即清空底层缓存，而是设置标记，在事务提交时清空
   * 实现清除操作的事务隔离
   */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * 提交事务，应用所有缓存更改
   * 类似Git的"git commit"操作，将暂存区的内容提交到仓库
   * 执行流程：
   * 1. 如果标记了清空，则清空底层缓存
   * 2. 将暂存区中的所有条目写入底层缓存
   * 3. 处理未命中的entries，支持BlockingCache解锁
   * 4. 重置所有状态
   */
  public void commit() {
    if (clearOnCommit) {
      // 如果需要，清空底层缓存
      delegate.clear();
    }
    // 将暂存区的内容刷新到底层缓存
    flushPendingEntries();
    // 重置所有状态
    reset();
  }

  /**
   * 回滚事务，放弃所有缓存更改
   * 类似Git的"git reset"操作，丢弃暂存区的所有更改
   * 执行流程：
   * 1. 解锁所有未命中的entries（主要用于BlockingCache）
   * 2. 重置所有状态，丢弃暂存区的内容
   */
  public void rollback() {
    // 解锁所有未命中的entries
    unlockMissedEntries();
    // 重置所有状态，丢弃暂存区
    reset();
  }

  /**
   * 重置所有状态，清空暂存区
   * 在事务提交或回滚后调用
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 将暂存区的内容刷新到底层缓存
   * 包括两部分：
   * 1. 要添加的entries - 将暂存区中的键值对写入底层缓存
   * 2. 未命中的entries - 写入null值解锁BlockingCache
   */
  private void flushPendingEntries() {
    // 将暂存区中的所有条目写入底层缓存
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }

    // 对于未命中但又不在暂存区中的entries，写入null值
    // 这主要是为了支持BlockingCache的解锁机制
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 解锁所有未命中的entries
   * 主要用于BlockingCache的解锁机制，防止死锁
   * 在事务回滚时调用
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        // 在BlockingCache中，removeObject实际上是用来释放锁的
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("尝试通知缓存适配器回滚时发生意外异常。"
            + "考虑将缓存适配器升级到最新版本。原因: " + e);
      }
    }
  }
}
