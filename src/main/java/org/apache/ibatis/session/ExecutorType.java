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
package org.apache.ibatis.session;

/**
 * ExecutorType 枚举定义了 MyBatis 中执行器的类型。
 *
 * - SIMPLE: 每次执行都会创建新的 `Statement` 对象，适用于简单的场景。
 * - REUSE: 复用 `Statement` 对象，减少创建和销毁 `Statement` 的开销，适用于执行相同 SQL 的场景。
 * - BATCH: 批量执行多条语句，适用于批量更新或插入操作，能够显著提高性能。
 *
 * 根据业务需求选择合适的执行器类型。
 *
 * @author Clinton Begin
 */
public enum ExecutorType {

  /**
   * 简单执行器。
   * 每次执行都会创建新的 `Statement` 对象。
   * 优点：实现简单，适用于不需要复用的场景。
   * 缺点：频繁创建和销毁 `Statement`，性能较低。
   */
  SIMPLE,

  /**
   * 可重用执行器。
   * 复用 `Statement` 对象，减少创建和销毁的开销。
   * 优点：适用于多次执行相同 SQL 的场景，性能较好。
   * 缺点：需要管理 `Statement` 的生命周期，可能会增加复杂性。
   */
  REUSE,

  /**
   * 批量执行器。
   * 将多条语句累积到批处理中，一次性发送到数据库执行。
   * 优点：适用于批量更新或插入操作，能够显著提高性能。
   * 缺点：需要注意批量操作的事务管理和内存消耗。
   */
  BATCH

}
