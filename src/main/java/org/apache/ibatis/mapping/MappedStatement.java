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
package org.apache.ibatis.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * MyBatis的SQL映射语句类，包含了SQL映射的所有信息。
 * 每个 <select|insert|update|delete> 标签都会被解析为一个 MappedStatement 对象。
 *
 * <p>MappedStatement 在 MyBatis 中的作用：</p>
 * <ul>
 *   <li>存储完整的 SQL 映射配置信息</li>
 *   <li>提供执行 SQL 所需的全部参数</li>
 *   <li>管理 SQL 语句的缓存行为</li>
 *   <li>连接配置信息与运行时执行</li>
 * </ul>
 *
 * @author Clinton Begin
 */
public final class MappedStatement {

  /**
   * 资源路径，通常是SQL映射文件的物理路径
   * 例如: "com/example/mapper/UserMapper.xml"
   */
  private String resource;

  /**
   * MyBatis配置对象，包含整个MyBatis的配置信息
   * 存储全局配置、已注册的映射器、类型处理器等信息
   */
  private Configuration configuration;

  /**
   * 映射语句的唯一标识
   * 格式通常为: "命名空间.SQL语句ID"
   * 例如: "com.example.mapper.UserMapper.selectById"
   */
  private String id;

  /**
   * 数据获取大小，决定一次从数据库获取多少条记录
   * 可通过 <select fetchSize="100"> 设置
   * 这会影响JDBC驱动程序如何加载结果集
   */
  private Integer fetchSize;

  /**
   * SQL执行超时时间，单位为秒
   * 可通过 <select timeout="3000"> 设置
   * 超过此时间未返回结果会抛出异常
   */
  private Integer timeout;

  /**
   * SQL语句类型，对应 JDBC 的 Statement 类型
   * - STATEMENT: 普通 Statement，不支持参数化
   * - PREPARED: 预编译 PreparedStatement（默认），支持参数化，可防SQL注入
   * - CALLABLE: 存储过程 CallableStatement，用于调用数据库存储过程
   */
  private StatementType statementType;

  /**
   * 结果集类型，控制数据库的游标操作
   * - DEFAULT: 使用数据库驱动默认设置
   * - FORWARD_ONLY: 结果集只能向前滚动，性能最佳
   * - SCROLL_SENSITIVE: 支持滚动，对数据库变化敏感（可感知其他会话对数据的修改）
   * - SCROLL_INSENSITIVE: 支持滚动，对数据库变化不敏感（数据快照，不反映其他会话的修改）
   */
  private ResultSetType resultSetType;

  /**
   * SQL源对象，包含了SQL语句及其参数信息
   * 可以是DynamicSqlSource(动态SQL)或RawSqlSource(静态SQL)
   * 负责生成最终执行的SQL语句和参数映射
   */
  private SqlSource sqlSource;

  /**
   * 二级缓存对象
   * 用于存储该语句的查询结果缓存
   * 多个MappedStatement可以共享同一个Cache实例
   */
  private Cache cache;

  /**
   * 参数映射信息
   * 描述SQL语句的参数如何映射到Java对象
   * 通常通过 <parameterMap> 标签定义（不推荐使用，已被 #{} 语法替代）
   */
  private ParameterMap parameterMap;

  /**
   * 结果映射列表，包含了数据库结果集到Java对象的映射关系
   * 通过 <resultMap> 标签或 resultType 属性定义
   * 一个SQL语句可以有多个结果映射（例如鉴别器discriminator的情况）
   */
  private List<ResultMap> resultMaps;

  /**
   * 是否需要清空缓存
   * 默认情况下，insert/update/delete 语句会清空缓存
   * select 语句默认不清空缓存，可通过 flushCache="true" 修改
   */
  private boolean flushCacheRequired;

  /**
   * 是否使用缓存
   * 默认情况下，select 语句会使用缓存
   * 可通过 useCache="false" 禁用特定查询的缓存
   */
  private boolean useCache;

  /**
   * 结果是否需要保持顺序
   * 当使用嵌套结果映射时可能需要设置为 true
   * 确保结果按照数据库返回的顺序构建对象关系
   */
  private boolean resultOrdered;

  /**
   * SQL命令类型
   * - SELECT: 查询操作
   * - INSERT: 插入操作
   * - UPDATE: 更新操作
   * - DELETE: 删除操作
   * - FLUSH: 刷新操作（用于存储过程）
   */
  private SqlCommandType sqlCommandType;

  /**
   * 主键生成器
   * - NoKeyGenerator: 不生成主键（默认）
   * - Jdbc3KeyGenerator: 使用JDBC3的getGeneratedKeys方式获取自增主键
   * - SelectKeyGenerator: 使用<selectKey>标签指定的SQL语句生成主键
   */
  private KeyGenerator keyGenerator;

  /**
   * 主键属性名数组
   * 用于设置生成的主键值到Java对象的哪个属性
   * 例如: "id" 或 ["id", "uuid"]
   * 对应XML中的 keyProperty 属性
   */
  private String[] keyProperties;

  /**
   * 主键列名数组
   * 指定数据库中的主键列
   * 例如: "id" 或 ["id", "uuid_column"]
   * 对应XML中的 keyColumn 属性
   */
  private String[] keyColumns;

  /**
   * 是否包含嵌套结果映射
   * 用于处理一对多、多对多关系的映射
   */
  private boolean hasNestedResultMaps;

  /**
   * 数据库厂商标识
   * 用于多数据库适配，匹配 <databaseIdProvider> 中的配置
   */
  private String databaseId;

  /**
   * 语句日志对象
   * 用于记录SQL执行的日志信息
   */
  private Log statementLog;

  /**
   * 脚本语言驱动
   * 用于解析和执行SQL脚本，默认为XMLLanguageDriver
   */
  private LanguageDriver lang;

  /**
   * 存储过程结果集名称数组
   * 用于处理存储过程返回多个结果集的情况
   */
  private String[] resultSets;

  /**
   * 是否为脏查询
   * 脏查询会导致二级缓存被忽略
   */
  private boolean dirtySelect;

  MappedStatement() {
    // 构造函数被禁用，使用Builder模式创建实例
  }

  /**
   * MappedStatement构建器类
   * 使用建造者模式构建MappedStatement对象
   * 通过链式调用设置各种属性
   */
  public static class Builder {
    private final MappedStatement mappedStatement = new MappedStatement();

    /**
     * 构造函数，设置必要的基础属性
     *
     * @param configuration MyBatis配置对象
     * @param id 映射语句的唯一标识
     * @param sqlSource SQL源对象
     * @param sqlCommandType SQL命令类型
     */
    public Builder(Configuration configuration, String id, SqlSource sqlSource, SqlCommandType sqlCommandType) {
        // 设置基本配置
        mappedStatement.configuration = configuration;
        mappedStatement.id = id;
        mappedStatement.sqlSource = sqlSource;

        // 设置默认的Statement类型为PREPARED
        mappedStatement.statementType = StatementType.PREPARED;

        // 设置默认的结果集类型
        mappedStatement.resultSetType = ResultSetType.DEFAULT;

        // 创建默认的参数映射
        mappedStatement.parameterMap = new ParameterMap.Builder(configuration,
            "defaultParameterMap", null, new ArrayList<>()).build();

        // 初始化结果映射列表
        mappedStatement.resultMaps = new ArrayList<>();

        // 设置SQL命令类型
        mappedStatement.sqlCommandType = sqlCommandType;

        // 配置主键生成器
        mappedStatement.keyGenerator = configuration.isUseGeneratedKeys()
            && SqlCommandType.INSERT.equals(sqlCommandType)
            ? Jdbc3KeyGenerator.INSTANCE   // 如果是INSERT且启用自动生成主键，使用JDBC3主键生成器
            : NoKeyGenerator.INSTANCE;     // 否则不使用主键生成器

        // 配置日志前缀
        String logId = id;
        if (configuration.getLogPrefix() != null) {
            logId = configuration.getLogPrefix() + id;
        }
        mappedStatement.statementLog = LogFactory.getLog(logId);

        // 设置默认的脚本语言驱动
        mappedStatement.lang = configuration.getDefaultScriptingLanguageInstance();
    }

    /**
     * 设置资源路径
     *
     * @param resource SQL映射文件的物理路径
     * @return 当前Builder实例，支持链式调用
     */
    public Builder resource(String resource) {
      mappedStatement.resource = resource;
      return this;
    }

    /**
     * 获取映射语句ID
     *
     * @return 映射语句的唯一标识
     */
    public String id() {
      return mappedStatement.id;
    }

    /**
     * 设置参数映射
     *
     * @param parameterMap 参数映射信息
     * @return 当前Builder实例，支持链式调用
     */
    public Builder parameterMap(ParameterMap parameterMap) {
      mappedStatement.parameterMap = parameterMap;
      return this;
    }

    /**
     * 设置结果映射列表
     * 同时检查是否包含嵌套结果映射
     *
     * @param resultMaps 结果映射列表
     * @return 当前Builder实例，支持链式调用
     */
    public Builder resultMaps(List<ResultMap> resultMaps) {
      mappedStatement.resultMaps = resultMaps;
      for (ResultMap resultMap : resultMaps) {
        mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
      }
      return this;
    }

    /**
     * 设置数据获取大小
     *
     * @param fetchSize 一次从数据库获取的记录数
     * @return 当前Builder实例，支持链式调用
     */
    public Builder fetchSize(Integer fetchSize) {
      mappedStatement.fetchSize = fetchSize;
      return this;
    }

    /**
     * 设置SQL执行超时时间
     *
     * @param timeout 超时时间，单位为秒
     * @return 当前Builder实例，支持链式调用
     */
    public Builder timeout(Integer timeout) {
      mappedStatement.timeout = timeout;
      return this;
    }

    /**
     * 设置SQL语句类型
     *
     * @param statementType JDBC的Statement类型
     * @return 当前Builder实例，支持链式调用
     */
    public Builder statementType(StatementType statementType) {
      mappedStatement.statementType = statementType;
      return this;
    }

    /**
     * 设置结果集类型
     *
     * @param resultSetType 结果集类型
     * @return 当前Builder实例，支持链式调用
     */
    public Builder resultSetType(ResultSetType resultSetType) {
      mappedStatement.resultSetType = resultSetType == null ? ResultSetType.DEFAULT : resultSetType;
      return this;
    }

    /**
     * 设置二级缓存对象
     *
     * @param cache 缓存实现
     * @return 当前Builder实例，支持链式调用
     */
    public Builder cache(Cache cache) {
      mappedStatement.cache = cache;
      return this;
    }

    /**
     * 设置是否需要清空缓存
     *
     * @param flushCacheRequired 是否清空缓存
     * @return 当前Builder实例，支持链式调用
     */
    public Builder flushCacheRequired(boolean flushCacheRequired) {
      mappedStatement.flushCacheRequired = flushCacheRequired;
      return this;
    }

    /**
     * 设置是否使用缓存
     *
     * @param useCache 是否使用缓存
     * @return 当前Builder实例，支持链式调用
     */
    public Builder useCache(boolean useCache) {
      mappedStatement.useCache = useCache;
      return this;
    }

    /**
     * 设置结果是否需要保持顺序
     *
     * @param resultOrdered 是否保持顺序
     * @return 当前Builder实例，支持链式调用
     */
    public Builder resultOrdered(boolean resultOrdered) {
      mappedStatement.resultOrdered = resultOrdered;
      return this;
    }

    /**
     * 设置主键生成器
     *
     * @param keyGenerator 主键生成器实现
     * @return 当前Builder实例，支持链式调用
     */
    public Builder keyGenerator(KeyGenerator keyGenerator) {
      mappedStatement.keyGenerator = keyGenerator;
      return this;
    }

    /**
     * 设置主键属性名
     *
     * @param keyProperty 逗号分隔的属性名字符串
     * @return 当前Builder实例，支持链式调用
     */
    public Builder keyProperty(String keyProperty) {
      mappedStatement.keyProperties = delimitedStringToArray(keyProperty);
      return this;
    }

    /**
     * 设置主键列名
     *
     * @param keyColumn 逗号分隔的列名字符串
     * @return 当前Builder实例，支持链式调用
     */
    public Builder keyColumn(String keyColumn) {
      mappedStatement.keyColumns = delimitedStringToArray(keyColumn);
      return this;
    }

    /**
     * 设置数据库厂商标识
     *
     * @param databaseId 数据库厂商标识
     * @return 当前Builder实例，支持链式调用
     */
    public Builder databaseId(String databaseId) {
      mappedStatement.databaseId = databaseId;
      return this;
    }

    /**
     * 设置脚本语言驱动
     *
     * @param driver 脚本语言驱动实现
     * @return 当前Builder实例，支持链式调用
     */
    public Builder lang(LanguageDriver driver) {
      mappedStatement.lang = driver;
      return this;
    }

    /**
     * 设置存储过程结果集名称
     *
     * @param resultSet 逗号分隔的结果集名称字符串
     * @return 当前Builder实例，支持链式调用
     */
    public Builder resultSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    /**
     * 设置是否为脏查询
     *
     * @param dirtySelect 是否为脏查询
     * @return 当前Builder实例，支持链式调用
     */
    public Builder dirtySelect(boolean dirtySelect) {
      mappedStatement.dirtySelect = dirtySelect;
      return this;
    }

    /**
     * 设置存储过程结果集名称（已废弃）
     *
     * @param resultSet 结果集名称
     * @return 当前Builder实例
     * @deprecated 请使用 {@link #resultSets}
     */
    @Deprecated
    public Builder resulSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    /**
     * 构建MappedStatement实例
     * 检查必要的属性是否已设置
     *
     * @return 完整配置的MappedStatement对象
     */
    public MappedStatement build() {
      assert mappedStatement.configuration != null;
      assert mappedStatement.id != null;
      assert mappedStatement.sqlSource != null;
      assert mappedStatement.lang != null;
      mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
      return mappedStatement;
    }
  }

  /**
   * 获取主键生成器
   *
   * @return 主键生成器实现
   */
  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  /**
   * 获取SQL命令类型
   *
   * @return SQL命令类型（SELECT/INSERT/UPDATE/DELETE/FLUSH）
   */
  public SqlCommandType getSqlCommandType() {
    return sqlCommandType;
  }

  /**
   * 获取资源路径
   *
   * @return SQL映射文件的物理路径
   */
  public String getResource() {
    return resource;
  }

  /**
   * 获取MyBatis配置对象
   *
   * @return 全局配置对象
   */
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * 获取映射语句的唯一标识
   *
   * @return 语句ID
   */
  public String getId() {
    return id;
  }

  /**
   * 判断是否包含嵌套结果映射
   *
   * @return 是否包含嵌套结果映射
   */
  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  /**
   * 获取数据获取大小
   *
   * @return 一次从数据库获取的记录数
   */
  public Integer getFetchSize() {
    return fetchSize;
  }

  /**
   * 获取SQL执行超时时间
   *
   * @return 超时时间，单位为秒
   */
  public Integer getTimeout() {
    return timeout;
  }

  /**
   * 获取SQL语句类型
   *
   * @return JDBC的Statement类型
   */
  public StatementType getStatementType() {
    return statementType;
  }

  /**
   * 获取结果集类型
   *
   * @return 结果集类型
   */
  public ResultSetType getResultSetType() {
    return resultSetType;
  }

  /**
   * 获取SQL源对象
   *
   * @return SQL源对象，包含SQL语句及参数信息
   */
  public SqlSource getSqlSource() {
    return sqlSource;
  }

  /**
   * 获取参数映射信息
   *
   * @return 参数映射对象
   */
  public ParameterMap getParameterMap() {
    return parameterMap;
  }

  /**
   * 获取结果映射列表
   *
   * @return 结果映射列表，包含结果集到Java对象的映射关系
   */
  public List<ResultMap> getResultMaps() {
    return resultMaps;
  }

  /**
   * 获取二级缓存对象
   *
   * @return 缓存实现
   */
  public Cache getCache() {
    return cache;
  }

  /**
   * 判断是否需要清空缓存
   *
   * @return 是否清空缓存
   */
  public boolean isFlushCacheRequired() {
    return flushCacheRequired;
  }

  /**
   * 判断是否使用缓存
   *
   * @return 是否使用缓存
   */
  public boolean isUseCache() {
    return useCache;
  }

  /**
   * 判断结果是否需要保持顺序
   *
   * @return 是否保持顺序
   */
  public boolean isResultOrdered() {
    return resultOrdered;
  }

  /**
   * 获取数据库厂商标识
   *
   * @return 数据库厂商标识
   */
  public String getDatabaseId() {
    return databaseId;
  }

  /**
   * 获取主键属性名数组
   *
   * @return 主键属性名数组
   */
  public String[] getKeyProperties() {
    return keyProperties;
  }

  /**
   * 获取主键列名数组
   *
   * @return 主键列名数组
   */
  public String[] getKeyColumns() {
    return keyColumns;
  }

  /**
   * 获取语句日志对象
   *
   * @return 日志对象
   */
  public Log getStatementLog() {
    return statementLog;
  }

  /**
   * 获取脚本语言驱动
   *
   * @return 脚本语言驱动实现
   */
  public LanguageDriver getLang() {
    return lang;
  }

  /**
   * 获取存储过程结果集名称数组
   *
   * @return 结果集名称数组
   */
  public String[] getResultSets() {
    return resultSets;
  }

  /**
   * 判断是否为脏查询
   *
   * @return 是否为脏查询
   */
  public boolean isDirtySelect() {
    return dirtySelect;
  }

  /**
   * 获取存储过程结果集名称数组（已废弃）
   *
   * @return 结果集名称数组
   * @deprecated 请使用 {@link #getResultSets()}
   */
  @Deprecated
  public String[] getResulSets() {
    return resultSets;
  }

  /**
   * 获取绑定SQL对象
   * 将SQL源对象中的SQL与参数对象绑定，生成可执行的SQL语句
   *
   * <p>此方法会：</p>
   * <ol>
   *   <li>从SqlSource获取BoundSql</li>
   *   <li>处理参数映射为空的情况</li>
   *   <li>检查嵌套结果映射</li>
   * </ol>
   *
   * @param parameterObject 参数对象
   * @return 绑定SQL对象，包含最终SQL语句和参数映射
   */
  public BoundSql getBoundSql(Object parameterObject) {
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings == null || parameterMappings.isEmpty()) {
      boundSql = new BoundSql(configuration, boundSql.getSql(), parameterMap.getParameterMappings(), parameterObject);
    }

    // 检查参数映射中的嵌套结果映射（issue #30）
    for (ParameterMapping pm : boundSql.getParameterMappings()) {
      String rmId = pm.getResultMapId();
      if (rmId != null) {
        ResultMap rm = configuration.getResultMap(rmId);
        if (rm != null) {
          hasNestedResultMaps |= rm.hasNestedResultMaps();
        }
      }
    }

    return boundSql;
  }

  /**
   * 将逗号分隔的字符串转换为字符串数组
   *
   * @param in 逗号分隔的字符串
   * @return 字符串数组，如果输入为空则返回null
   */
  private static String[] delimitedStringToArray(String in) {
    if (in == null || in.trim().length() == 0) {
      return null;
    }
    return in.split(",");
  }
}
