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

import java.io.Closeable;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;

/**
 * MyBatis的核心接口，提供数据库操作和事务管理的主要方法
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>执行SQL语句（增删改查）</li>
 *   <li>批量更新操作</li>
 *   <li>事务控制</li>
 *   <li>获取映射器</li>
 * </ul>
 *
 * <p>事务管理：</p>
 * <ul>
 *   <li>commit()：提交事务</li>
 *   <li>rollback()：回滚事务</li>
 *   <li>close()：关闭会话</li>
 * </ul>
 *
 * <p>查询方法：</p>
 * <ul>
 *   <li>selectOne：查询单条记录</li>
 *   <li>selectList：查询多条记录</li>
 *   <li>selectMap：将查询结果转为Map</li>
 *   <li>selectCursor：流式查询</li>
 *   <li>select：使用ResultHandler处理结果</li>
 * </ul>
 *
 * <p>更新方法：</p>
 * <ul>
 *   <li>insert：插入记录</li>
 *   <li>update：更新记录</li>
 *   <li>delete：删除记录</li>
 * </ul>
 *
 * <p>注意事项：</p>
 * <ul>
 *   <li>非线程安全，每个线程应该使用独立的SqlSession实例</li>
 *   <li>使用完后必须关闭，建议使用try-with-resources语法</li>
 *   <li>默认不自动提交事务，需要手动调用commit</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * try (SqlSession session = sqlSessionFactory.openSession()) {
 *   UserMapper mapper = session.getMapper(UserMapper.class);
 *   User user = mapper.getById(1);
 *   session.commit();
 * }
 * </pre>
 *
 * @author Clinton Begin
 */
public interface SqlSession extends Closeable {

  /**
   * 根据指定的SQL ID查询单条记录
   *
   * @param <T> 返回对象类型
   * @param statement SQL语句的唯一标识
   * @return 查询结果对象，如果未找到返回null
   */
  <T> T selectOne(String statement);

  /**
   * 根据指定的SQL ID和参数查询单条记录
   *
   * @param <T> 返回对象类型
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @return 查询结果对象，如果未找到返回null
   */
  <T> T selectOne(String statement, Object parameter);

  /**
   * 根据指定的SQL ID查询多条记录
   *
   * @param <E> 返回列表元素类型
   * @param statement SQL语句的唯一标识
   * @return 查询结果列表
   */
  <E> List<E> selectList(String statement);

  /**
   * 根据指定的SQL ID和参数查询多条记录
   *
   * @param <E> 返回列表元素类型
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @return 查询结果列表
   */
  <E> List<E> selectList(String statement, Object parameter);

  /**
   * 根据指定的SQL ID和参数查询多条记录，并进行分页
   *
   * @param <E> 返回列表元素类型
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @param rowBounds 分页参数
   * @return 查询结果列表
   */
  <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds);

  /**
   * 将查询结果转换为Map，其中Map的key为指定的属性值
   *
   * @param <K> Map键类型
   * @param <V> Map值类型
   * @param statement SQL语句的唯一标识
   * @param mapKey 作为Map键的属性名
   * @return 转换后的Map对象
   */
  <K, V> Map<K, V> selectMap(String statement, String mapKey);

  /**
   * 根据参数查询并将结果转换为Map，其中Map的key为指定的属性值
   *
   * @param <K> Map键类型
   * @param <V> Map值类型
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @param mapKey 作为Map键的属性名
   * @return 转换后的Map对象
   */
  <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey);

  /**
   * 根据参数查询并将结果转换为Map，支持分页，其中Map的key为指定的属性值
   *
   * @param <K> Map键类型
   * @param <V> Map值类型
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @param mapKey 作为Map键的属性名
   * @param rowBounds 分页参数
   * @return 转换后的Map对象
   */
  <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds);

  /**
   * 查询数据并返回Cursor对象，用于流式查询
   *
   * @param <T> 返回对象类型
   * @param statement SQL语句的唯一标识
   * @return Cursor对象，用于遍历查询结果
   */
  <T> Cursor<T> selectCursor(String statement);

  /**
   * 根据参数查询数据并返回Cursor对象，用于流式查询
   *
   * @param <T> 返回对象类型
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @return Cursor对象，用于遍历查询结果
   */
  <T> Cursor<T> selectCursor(String statement, Object parameter);

  /**
   * 根据参数查询数据并返回Cursor对象，支持分页，用于流式查询
   *
   * @param <T> 返回对象类型
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @param rowBounds 分页参数
   * @return Cursor对象，用于遍历查询结果
   */
  <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds);

  /**
   * 使用自定义ResultHandler处理查询结果
   *
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @param handler 结果处理器
   */
  void select(String statement, Object parameter, ResultHandler handler);

  /**
   * 使用自定义ResultHandler处理查询结果
   *
   * @param statement SQL语句的唯一标识
   * @param handler 结果处理器
   */
  void select(String statement, ResultHandler handler);

  /**
   * 使用自定义ResultHandler处理查询结果，支持分页
   *
   * @param statement SQL语句的唯一标识
   * @param parameter 查询参数
   * @param rowBounds 分页参数
   * @param handler 结果处理器
   */
  void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler);

  /**
   * 执行插入语句
   *
   * @param statement SQL语句的唯一标识
   * @return 受影响的行数
   */
  int insert(String statement);

  /**
   * 执行带参数的插入语句
   *
   * @param statement SQL语句的唯一标识
   * @param parameter 插入参数
   * @return 受影响的行数
   */
  int insert(String statement, Object parameter);

  /**
   * 执行更新语句
   *
   * @param statement SQL语句的唯一标识
   * @return 受影响的行数
   */
  int update(String statement);

  /**
   * 执行带参数的更新语句
   *
   * @param statement SQL语句的唯一标识
   * @param parameter 更新参数
   * @return 受影响的行数
   */
  int update(String statement, Object parameter);

  /**
   * 执行删除语句
   *
   * @param statement SQL语句的唯一标识
   * @return 受影响的行数
   */
  int delete(String statement);

  /**
   * 执行带参数的删除语句
   *
   * @param statement SQL语句的唯一标识
   * @param parameter 删除参数
   * @return 受影响的行数
   */
  int delete(String statement, Object parameter);

  /**
   * 提交事务
   */
  void commit();

  /**
   * 提交事务
   *
   * @param force 是否强制提交
   */
  void commit(boolean force);

  /**
   * 回滚事务
   */
  void rollback();

  /**
   * 回滚事务
   *
   * @param force 是否强制回滚
   */
  void rollback(boolean force);

  /**
   * 刷新批处理语句
   *
   * @return 批处理结果列表
   */
  List<BatchResult> flushStatements();

  /**
   * 关闭Session
   */
  @Override
  void close();

  /**
   * 清空本地缓存
   */
  void clearCache();

  /**
   * 获取配置对象
   *
   * @return Configuration对象
   */
  Configuration getConfiguration();

  /**
   * 获取指定类型的Mapper接口实现
   *
   * @param <T> Mapper接口类型
   * @param type Mapper接口类
   * @return Mapper接口实现对象
   */
  <T> T getMapper(Class<T> type);

  /**
   * 获取数据库连接
   *
   * @return 数据库连接对象
   */
  Connection getConnection();
}
