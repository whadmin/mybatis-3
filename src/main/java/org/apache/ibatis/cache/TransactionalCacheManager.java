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
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;
import org.apache.ibatis.util.MapUtil;

/**
 * 事务缓存管理器
 * <p>
 * 管理多个二级缓存的事务特性，协调缓存的提交和回滚操作。
 * 主要用于CachingExecutor中，确保缓存操作与数据库事务保持一致性。
 *
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  /**
   * 存储所有二级缓存的事务缓存包装器映射
   * <p>
   * 键：原始的二级缓存实例（每个命名空间一个）
   * 值：对应的TransactionalCache包装器，提供事务功能
   */
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  /**
   * 清空指定缓存,这个方法只是标记缓存为清空状态，实际清空操作在事务提交时执行
   *
   * @param cache 要清空的缓存
   */
  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  /**
   * 从指定缓存获取对象
   * <p>
   * 在事务未提交前，新添加的数据不会直接写入底层缓存，
   * 因此这里的查询结果可能与事务提交后不同
   *
   * @param cache 要查询的缓存
   * @param key 缓存键
   * @return 缓存中的对象，如果不存在则返回null
   */
  public Object getObject(Cache cache, CacheKey key) {
    return getTransactionalCache(cache).getObject(key);
  }

  /**
   * 向指定缓存存入对象
   * <p>
   * 在事务未提交前，数据只存入临时区域，不会直接写入底层缓存
   *
   * @param cache 目标缓存
   * @param key 缓存键
   * @param value 要存储的对象
   */
  public void putObject(Cache cache, CacheKey key, Object value) {
    getTransactionalCache(cache).putObject(key, value);
  }

  /**
   * 提交所有事务缓存的更改
   * <p>
   * 将所有临时修改应用到底层缓存中：
   * 1. 如果缓存被标记为清空，则清空底层缓存
   * 2. 将所有暂存的对象写入底层缓存
   */
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  /**
   * 回滚所有事务缓存的更改
   * <p>
   * 放弃所有临时修改，不写入底层缓存：
   * 1. 丢弃所有暂存的对象
   * 2. 撤销清空标记
   */
  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  /**
   * 获取或创建指定缓存的事务包装器
   * <p>
   * 如果该缓存已有事务包装器，则返回现有的；
   * 否则创建新的事务包装器并返回
   *
   * @param cache 需要事务功能的缓存
   * @return 对应的事务缓存包装器
   */
  private TransactionalCache getTransactionalCache(Cache cache) {
    return MapUtil.computeIfAbsent(transactionalCaches, cache, TransactionalCache::new);
  }
}
