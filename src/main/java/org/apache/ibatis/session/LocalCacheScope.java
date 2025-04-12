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
package org.apache.ibatis.session;

/**
 * LocalCacheScope 枚举定义了 MyBatis 本地缓存的作用范围。
 *
 * - SESSION: 缓存作用范围为整个会话（SqlSession）。在同一个会话中，执行多次相同的查询时，
 *            如果参数相同，MyBatis 会直接从缓存中获取结果，而不会再次访问数据库。
 *            适用于需要减少数据库访问次数、提高性能的场景。
 *
 * - STATEMENT: 缓存作用范围为单个语句。在每次执行查询时，MyBatis 都会清空缓存并重新加载数据。
 *              适用于需要确保数据实时性、避免脏读的场景。
 *
 * @author Eduardo Macarron
 */
public enum LocalCacheScope {
  /**
   * 缓存作用范围为整个会话（SqlSession）。
   * 在同一个会话中，重复执行相同的查询会使用缓存。
   */
  SESSION,

  /**
   * 缓存作用范围为单个语句。
   * 每次执行查询都会清空缓存，确保数据的实时性。
   */
  STATEMENT
}
