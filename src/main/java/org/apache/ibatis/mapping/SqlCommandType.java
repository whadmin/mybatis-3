package org.apache.ibatis.mapping;


/**
 * SQL命令类型枚举
 *
 * <p>主要用途：</p>
 * <ul>
 *   <li>标识Mapper方法对应的SQL操作类型</li>
 *   <li>决定SqlSession执行的具体方法</li>
 *   <li>影响方法执行结果的处理方式</li>
 * </ul>
 */
public enum SqlCommandType {

  /**
   * 未知类型，通常表示暂未解析或不支持的操作类型
   */
  UNKNOWN,

  /**
   * 插入操作
   * <p>对应SqlSession.insert()方法</p>
   * <p>通常返回影响的行数</p>
   */
  INSERT,

  /**
   * 更新操作
   * <p>对应SqlSession.update()方法</p>
   * <p>通常返回影响的行数</p>
   */
  UPDATE,

  /**
   * 删除操作
   * <p>对应SqlSession.delete()方法</p>
   * <p>通常返回影响的行数</p>
   */
  DELETE,

  /**
   * 查询操作
   * <p>对应SqlSession.select()系列方法</p>
   * <p>返回值根据方法签名决定：</p>
   * <ul>
   *   <li>selectOne：返回单个对象</li>
   *   <li>selectList：返回对象集合</li>
   *   <li>selectMap：返回Map结构</li>
   *   <li>selectCursor：返回Cursor对象</li>
   * </ul>
   */
  SELECT,

  /**
   * 刷新批处理语句
   * <p>对应SqlSession.flushStatements()方法</p>
   * <p>用于强制执行批量更新语句</p>
   */
  FLUSH

}
