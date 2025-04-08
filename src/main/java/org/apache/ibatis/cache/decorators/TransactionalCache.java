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
 * 此类持有在一个会话期间要添加到二级缓存中的所有缓存条目。
 * 这些条目在调用commit时发送到缓存，或在会话回滚时丢弃。
 * 已添加对阻塞缓存的支持。因此，任何返回缓存未命中的get()调用
 * 都会跟随一个put()调用，以便释放与该键关联的任何锁。
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  /**
   * 日志记录器
   */
  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  /**
   * 被装饰的底层缓存，实际存储数据的地方
   */
  private final Cache delegate;

  /**
   * 提交时是否清空缓存的标志
   * 当执行更新操作时，此标志会被设置为true
   */
  private boolean clearOnCommit;

  /**
   * 在事务提交时要添加到缓存的entries
   * 临时存储区，避免在事务完成前修改底层缓存
   */
  private final Map<Object, Object> entriesToAddOnCommit;

  /**
   * 记录缓存未命中的键集合
   * 主要用于支持BlockingCache的解锁操作
   */
  private final Set<Object> entriesMissedInCache;

  /**
   * 构造函数
   *
   * @param delegate 被装饰的底层缓存
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
      // 缓存未命中，记录到未命中集合中
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      // 如果事务中有更新操作，强制返回null避免脏读
      return null;
    }
    return object;
  }

  /**
   * 将对象存入缓存
   * 注意：此方法不会立即将对象写入底层缓存，而是暂存在临时映射中
   *
   * @param key 缓存键
   * @param object 要缓存的对象
   */
  @Override
  public void putObject(Object key, Object object) {
    // 暂存在临时映射中，等待事务提交时批量写入
    entriesToAddOnCommit.put(key, object);
  }

  /**
   * 从缓存移除对象
   * 在这个实现中实际上什么也不做，返回null
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
   */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * 提交事务，应用所有缓存更改
   * 1. 如果标记了清空，则清空底层缓存
   * 2. 将所有暂存的entries写入底层缓存
   * 3. 重置所有状态
   */
  public void commit() {
    if (clearOnCommit) {
      // 如果需要，清空底层缓存
      delegate.clear();
    }
    // 刷新待处理的entries到底层缓存
    flushPendingEntries();
    // 重置所有状态
    reset();
  }

  /**
   * 回滚事务，放弃所有缓存更改
   * 1. 解锁所有未命中的entries（主要用于BlockingCache）
   * 2. 重置所有状态
   */
  public void rollback() {
    // 解锁所有未命中的entries
    unlockMissedEntries();
    // 重置所有状态
    reset();
  }

  /**
   * 重置所有状态，清空临时存储区
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 刷新所有待处理的entries到底层缓存
   * 包括两部分：
   * 1. 要添加的entries
   * 2. 未命中的entries（用于BlockingCache的解锁）
   */
  private void flushPendingEntries() {
    // 写入所有待添加的entries
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }

    // 对于未命中但又不在待添加列表中的entries，写入null值
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
