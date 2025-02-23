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
package org.apache.ibatis.binding;

import org.apache.ibatis.exceptions.PersistenceException;

/**
 * MyBatis绑定异常，用于处理Mapper接口绑定过程中的错误
 *
 * <p>异常场景：</p>
 * <ul>
 *   <li>Mapper接口或方法未找到对应的SQL语句</li>
 *   <li>方法参数绑定错误</li>
 *   <li>返回值类型转换失败</li>
 *   <li>Mapper注册失败</li>
 *   <li>代理对象创建失败</li>
 * </ul>
 *
 * <p>常见原因：</p>
 * <ul>
 *   <li>XML映射文件中的SQL ID与Mapper方法不匹配</li>
 *   <li>方法参数名与SQL语句中的参数名不一致</li>
 *   <li>返回值类型与查询结果不兼容</li>
 *   <li>Mapper接口定义不规范（如：不是接口而是类）</li>
 *   <li>配置文件中未正确注册Mapper</li>
 * </ul>
 *
 * <p>注意事项：</p>
 * <ul>
 *   <li>继承自PersistenceException，属于运行时异常</li>
 *   <li>通常在MyBatis初始化或执行SQL时抛出</li>
 *   <li>异常信息通常包含详细的错误原因</li>
 *   <li>建议在应用启动时就处理此类异常</li>
 * </ul>
 */
public class BindingException extends PersistenceException {

  private static final long serialVersionUID = 4300802238789381562L;

  /**
   * 创建一个无参的BindingException
   */
  public BindingException() {
    super();
  }

  /**
   * 使用指定的错误消息创建BindingException
   *
   * @param message 错误消息
   */
  public BindingException(String message) {
    super(message);
  }

  /**
   * 使用指定的错误消息和原始异常创建BindingException
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public BindingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 使用原始异常创建BindingException
   *
   * @param cause 原始异常
   */
  public BindingException(Throwable cause) {
    super(cause);
  }
}
