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
 * 错误上下文类
 * 作用：收集和管理MyBatis执行过程中的错误信息，提供丰富的上下文数据
 * 设计特点：
 * 1. 线程隔离：每个线程拥有独立的错误上下文实例
 * 2. 链式调用：支持流式API，方便设置各种错误信息
 * 3. 上下文嵌套：支持保存和恢复错误上下文，适用于嵌套调用场景
 * 4. 格式化输出：提供结构化的错误信息字符串
 *
 * 使用示例：
 * try {
 *   // 执行SQL操作
 * } catch (Exception e) {
 *   // 收集错误信息
 *   ErrorContext.instance()
 *     .resource("UserMapper.xml")
 *     .activity("执行查询操作")
 *     .object("com.example.UserMapper.findById")
 *     .message("查询用户数据失败")
 *     .sql("SELECT * FROM users WHERE id = ?")
 *     .cause(e);
 *   // 抛出带有详细错误信息的异常
 *   throw new PersistenceException("数据库操作异常", e);
 * }
 *
 * @author Clinton Begin
 */
public class ErrorContext {

  /**
   * 系统行分隔符，用于格式化错误信息
   */
  private static final String LINE_SEPARATOR = System.lineSeparator();

  /**
   * 线程本地变量，为每个线程提供独立的ErrorContext实例
   * 使用withInitial确保首次访问时自动创建新实例
   */
  private static final ThreadLocal<ErrorContext> LOCAL = ThreadLocal.withInitial(ErrorContext::new);

  /**
   * 存储的上下文，用于嵌套调用场景中保存当前上下文
   */
  private ErrorContext stored;

  /**
   * 资源信息，通常是配置文件路径，如"UserMapper.xml"
   */
  private String resource;

  /**
   * 当前活动，描述执行的操作，如"执行查询操作"
   */
  private String activity;

  /**
   * 对象信息，通常是Mapper方法名，如"com.example.UserMapper.findById"
   */
  private String object;

  /**
   * 错误消息，简要描述错误情况
   */
  private String message;

  /**
   * SQL语句，记录导致错误的SQL
   */
  private String sql;

  /**
   * 原始异常，记录触发错误的根本原因
   */
  private Throwable cause;

  /**
   * 私有构造函数，防止直接实例化
   * 强制通过instance()方法获取实例
   */
  private ErrorContext() {
  }

  /**
   * 获取当前线程的ErrorContext实例
   *
   * @return 当前线程的错误上下文实例
   */
  public static ErrorContext instance() {
    return LOCAL.get();
  }

  /**
   * 存储当前上下文并创建新的上下文
   * 使用场景：需要嵌套收集错误信息时，暂存当前上下文
   *
   * @return 新创建的错误上下文实例
   */
  public ErrorContext store() {
    ErrorContext newContext = new ErrorContext();
    newContext.stored = this;
    LOCAL.set(newContext);
    return LOCAL.get();
  }

  /**
   * 恢复之前存储的上下文
   * 使用场景：嵌套操作完成后，恢复原先的上下文
   *
   * @return 恢复后的错误上下文实例
   */
  public ErrorContext recall() {
    if (stored != null) {
      LOCAL.set(stored);
      stored = null;
    }
    return LOCAL.get();
  }

  /**
   * 设置资源信息
   *
   * @param resource 资源路径或名称
   * @return 当前错误上下文实例，支持链式调用
   */
  public ErrorContext resource(String resource) {
    this.resource = resource;
    return this;
  }

  /**
   * 设置当前活动信息
   *
   * @param activity 活动描述
   * @return 当前错误上下文实例，支持链式调用
   */
  public ErrorContext activity(String activity) {
    this.activity = activity;
    return this;
  }

  /**
   * 设置对象信息
   *
   * @param object 对象名称
   * @return 当前错误上下文实例，支持链式调用
   */
  public ErrorContext object(String object) {
    this.object = object;
    return this;
  }

  /**
   * 设置错误消息
   *
   * @param message 错误消息
   * @return 当前错误上下文实例，支持链式调用
   */
  public ErrorContext message(String message) {
    this.message = message;
    return this;
  }

  /**
   * 设置SQL语句
   *
   * @param sql SQL语句
   * @return 当前错误上下文实例，支持链式调用
   */
  public ErrorContext sql(String sql) {
    this.sql = sql;
    return this;
  }

  /**
   * 设置原始异常
   *
   * @param cause 异常对象
   * @return 当前错误上下文实例，支持链式调用
   */
  public ErrorContext cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  /**
   * 重置错误上下文
   * 清空所有信息并移除ThreadLocal中的引用，防止内存泄漏
   *
   * @return 重置后的错误上下文实例
   */
  public ErrorContext reset() {
    resource = null;
    activity = null;
    object = null;
    message = null;
    sql = null;
    cause = null;
    LOCAL.remove();
    return this;
  }

  /**
   * 生成格式化的错误信息字符串
   * 按固定顺序组织不同类型的错误信息
   *
   * @return 格式化的错误信息
   */
  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();

    // message - 错误消息
    if (this.message != null) {
      description.append(LINE_SEPARATOR);
      description.append("### ");
      description.append(this.message);
    }

    // resource - 资源信息
    if (resource != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may exist in ");
      description.append(resource);
    }

    // object - 对象信息
    if (object != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may involve ");
      description.append(object);
    }

    // activity - 活动信息
    if (activity != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error occurred while ");
      description.append(activity);
    }

    // sql - SQL语句（去除多余空白字符）
    if (sql != null) {
      description.append(LINE_SEPARATOR);
      description.append("### SQL: ");
      description.append(sql.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim());
    }

    // cause - 原始异常信息
    if (cause != null) {
      description.append(LINE_SEPARATOR);
      description.append("### Cause: ");
      description.append(cause.toString());
    }

    return description.toString();
  }

}
