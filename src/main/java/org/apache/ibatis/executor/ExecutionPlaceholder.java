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
package org.apache.ibatis.executor;

/**
 * 执行占位符枚举
 *
 * 作用：
 * 1. 在嵌套查询执行期间作为缓存中的临时占位符
 * 2. 防止循环依赖和重复查询
 * 3. 标识查询正在进行中，尚未完成
 *
 * 使用场景：
 * 在 BaseExecutor.queryFromDatabase() 方法中，执行查询前先在缓存中
 * 放入此占位符，查询完成后再替换为实际结果。这样可以防止在处理嵌套查询时，
 * 同一查询被重复执行或形成循环引用。
 *
 * 工作原理：
 * 1. 执行查询前，在缓存中放入占位符: localCache.putObject(key, EXECUTION_PLACEHOLDER)
 * 2. 执行实际查询操作
 * 3. 移除占位符: localCache.removeObject(key)
 * 4. 将查询结果存入缓存: localCache.putObject(key, list)
 *
 * 在延迟加载的实现中，通过检查缓存中的值是否为此占位符，可以确定:
 * - 如果为占位符: 说明查询正在进行中，需要等待
 * - 如果不为占位符: 可以安全地使用缓存的结果
 *
 * @author Clinton Begin
 */
public enum ExecutionPlaceholder {
  EXECUTION_PLACEHOLDER
}
