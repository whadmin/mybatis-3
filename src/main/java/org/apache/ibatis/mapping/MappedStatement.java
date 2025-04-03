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
 * MyBatis的SQL映射语句类，包含了SQL映射的所有信息
 * 每个 <select|insert|update|delete> 标签都会被解析为一个 MappedStatement 对象
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
   */
  private Integer fetchSize;

  /**
   * SQL执行超时时间，单位为秒
   * 可通过 <select timeout="3000"> 设置
   */
  private Integer timeout;

  /**
   * SQL语句类型，对应 JDBC 的 Statement 类型
   * - STATEMENT: 普通 Statement
   * - PREPARED: 预编译 PreparedStatement（默认）
   * - CALLABLE: 存储过程 CallableStatement
   */
  private StatementType statementType;

  /**
   * 结果集类型，控制数据库的游标操作
   * - DEFAULT: 数据库默认设置
   * - FORWARD_ONLY: 只允许向前访问
   * - SCROLL_SENSITIVE: 支持滚动，对数据库变化敏感
   * - SCROLL_INSENSITIVE: 支持滚动，对数据库变化不敏感
   */
  private ResultSetType resultSetType;

  /**
   * SQL源对象，包含了SQL语句及其参数信息
   */
  private SqlSource sqlSource;

  /**
   * 二级缓存对象
   */
  private Cache cache;

  /**
   * 参数映射信息
   */
  private ParameterMap parameterMap;

  /**
   * 结果映射列表，包含了数据库结果集到Java对象的映射关系
   */
  private List<ResultMap> resultMaps;

  /**
   * 是否需要清空缓存
   * 默认情况下，insert/update/delete 语句会清空缓存
   */
  private boolean flushCacheRequired;

  /**
   * 是否使用缓存
   * 默认情况下，select 语句会使用缓存
   */
  private boolean useCache;

  /**
   * 结果是否需要保持顺序
   * 当使用嵌套结果映射时可能需要设置为 true
   */
  private boolean resultOrdered;

  /**
   * SQL命令类型
   * - SELECT: 查询
   * - INSERT: 插入
   * - UPDATE: 更新
   * - DELETE: 删除
   */
  private SqlCommandType sqlCommandType;

  /**
   * 主键生成器
   * - NoKeyGenerator: 不生成主键
   * - Jdbc3KeyGenerator: 使用JDBC3的方式生成主键
   * - SelectKeyGenerator: 使用select语句生成主键
   */
  private KeyGenerator keyGenerator;

  /**
   * 主键属性名数组
   * 用于设置生成的主键值到Java对象的哪个属性
   * 例如: "id" 或 ["id", "uuid"]
   */
  private String[] keyProperties;

  /**
   * 主键列名数组
   * 指定数据库中的主键列
   * 例如: "id" 或 ["id", "uuid_column"]
   */
  private String[] keyColumns;

  private boolean hasNestedResultMaps;
  private String databaseId;
  private Log statementLog;
  private LanguageDriver lang;
  private String[] resultSets;
  private boolean dirtySelect;

  MappedStatement() {
    // constructor disabled
  }

  /**
   * MappedStatement构建器类
   * 使用建造者模式构建MappedStatement对象
   */
  public static class Builder {
    private final MappedStatement mappedStatement = new MappedStatement();

    /**
     * 构造函数
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

    public Builder resource(String resource) {
      mappedStatement.resource = resource;
      return this;
    }

    public String id() {
      return mappedStatement.id;
    }

    public Builder parameterMap(ParameterMap parameterMap) {
      mappedStatement.parameterMap = parameterMap;
      return this;
    }

    public Builder resultMaps(List<ResultMap> resultMaps) {
      mappedStatement.resultMaps = resultMaps;
      for (ResultMap resultMap : resultMaps) {
        mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
      }
      return this;
    }

    public Builder fetchSize(Integer fetchSize) {
      mappedStatement.fetchSize = fetchSize;
      return this;
    }

    public Builder timeout(Integer timeout) {
      mappedStatement.timeout = timeout;
      return this;
    }

    public Builder statementType(StatementType statementType) {
      mappedStatement.statementType = statementType;
      return this;
    }

    public Builder resultSetType(ResultSetType resultSetType) {
      mappedStatement.resultSetType = resultSetType == null ? ResultSetType.DEFAULT : resultSetType;
      return this;
    }

    public Builder cache(Cache cache) {
      mappedStatement.cache = cache;
      return this;
    }

    public Builder flushCacheRequired(boolean flushCacheRequired) {
      mappedStatement.flushCacheRequired = flushCacheRequired;
      return this;
    }

    public Builder useCache(boolean useCache) {
      mappedStatement.useCache = useCache;
      return this;
    }

    public Builder resultOrdered(boolean resultOrdered) {
      mappedStatement.resultOrdered = resultOrdered;
      return this;
    }

    public Builder keyGenerator(KeyGenerator keyGenerator) {
      mappedStatement.keyGenerator = keyGenerator;
      return this;
    }

    public Builder keyProperty(String keyProperty) {
      mappedStatement.keyProperties = delimitedStringToArray(keyProperty);
      return this;
    }

    public Builder keyColumn(String keyColumn) {
      mappedStatement.keyColumns = delimitedStringToArray(keyColumn);
      return this;
    }

    public Builder databaseId(String databaseId) {
      mappedStatement.databaseId = databaseId;
      return this;
    }

    public Builder lang(LanguageDriver driver) {
      mappedStatement.lang = driver;
      return this;
    }

    public Builder resultSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    public Builder dirtySelect(boolean dirtySelect) {
      mappedStatement.dirtySelect = dirtySelect;
      return this;
    }

    /**
     * Resul sets.
     *
     * @param resultSet
     *          the result set
     *
     * @return the builder
     *
     * @deprecated Use {@link #resultSets}
     */
    @Deprecated
    public Builder resulSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    public MappedStatement build() {
      assert mappedStatement.configuration != null;
      assert mappedStatement.id != null;
      assert mappedStatement.sqlSource != null;
      assert mappedStatement.lang != null;
      mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
      return mappedStatement;
    }
  }

  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  public SqlCommandType getSqlCommandType() {
    return sqlCommandType;
  }

  public String getResource() {
    return resource;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public Integer getFetchSize() {
    return fetchSize;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public StatementType getStatementType() {
    return statementType;
  }

  public ResultSetType getResultSetType() {
    return resultSetType;
  }

  public SqlSource getSqlSource() {
    return sqlSource;
  }

  public ParameterMap getParameterMap() {
    return parameterMap;
  }

  public List<ResultMap> getResultMaps() {
    return resultMaps;
  }

  public Cache getCache() {
    return cache;
  }

  public boolean isFlushCacheRequired() {
    return flushCacheRequired;
  }

  public boolean isUseCache() {
    return useCache;
  }

  public boolean isResultOrdered() {
    return resultOrdered;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  public String[] getKeyProperties() {
    return keyProperties;
  }

  public String[] getKeyColumns() {
    return keyColumns;
  }

  public Log getStatementLog() {
    return statementLog;
  }

  public LanguageDriver getLang() {
    return lang;
  }

  public String[] getResultSets() {
    return resultSets;
  }

  public boolean isDirtySelect() {
    return dirtySelect;
  }

  /**
   * Gets the resul sets.
   *
   * @return the resul sets
   *
   * @deprecated Use {@link #getResultSets()}
   */
  @Deprecated
  public String[] getResulSets() {
    return resultSets;
  }

  public BoundSql getBoundSql(Object parameterObject) {
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings == null || parameterMappings.isEmpty()) {
      boundSql = new BoundSql(configuration, boundSql.getSql(), parameterMap.getParameterMappings(), parameterObject);
    }

    // check for nested result maps in parameter mappings (issue #30)
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


  private static String[] delimitedStringToArray(String in) {
    if (in == null || in.trim().length() == 0) {
      return null;
    }
    return in.split(",");
  }

}
