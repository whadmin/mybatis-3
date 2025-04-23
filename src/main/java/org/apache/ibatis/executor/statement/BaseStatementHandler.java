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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * StatementHandler 的基础实现类，是一个模板方法模式的抽象类
 * 提供了 Statement 处理的通用功能，主要职责：
 * 1. Statement对象的创建和配置
 * 2. 参数的设置和处理
 * 3. 结果集的处理和映射
 * 4. 主键生成的处理
 *
 * 子类主要实现：
 * - SimpleStatementHandler: 处理普通Statement
 * - PreparedStatementHandler: 处理预编译PreparedStatement
 * - CallableStatementHandler: 处理存储过程CallableStatement
 *
 * @author Clinton Begin
 */
public abstract class BaseStatementHandler implements StatementHandler {

    /**
     * MyBatis配置对象，包含所有全局配置信息
     * 例如：默认超时时间、默认执行器类型、缓存配置等
     */
    protected final Configuration configuration;

    /**
     * 对象工厂，用于创建参数和结果对象实例
     * 可以通过配置自定义对象的创建过程
     */
    protected final ObjectFactory objectFactory;

    /**
     * 类型处理器注册表，管理所有的类型处理器
     * 负责Java类型和JDBC类型之间的转换
     */
    protected final TypeHandlerRegistry typeHandlerRegistry;

    /**
     * 结果集处理器，用于将数据库结果集映射为Java对象
     * 处理一对一、一对多等复杂映射关系
     */
    protected final ResultSetHandler resultSetHandler;

    /**
     * 参数处理器，用于设置预编译SQL的参数
     * 处理#{} 和 ${} 参数的替换和设置
     */
    protected final ParameterHandler parameterHandler;

    /**
     * SQL执行器，负责执行SQL语句
     * 可以是SIMPLE、REUSE或BATCH类型
     */
    protected final Executor executor;

    /**
     * MappedStatement对象，包含SQL相关的配置信息
     * 对应XML中的一条SQL语句的所有配置
     */
    protected final MappedStatement mappedStatement;

    /**
     * 分页参数对象，控制分页查询
     * 包含offset和limit信息
     */
    protected final RowBounds rowBounds;

    /**
     * 绑定的SQL对象，包含SQL语句及其参数信息
     * 经过参数解析后的最终要执行的SQL
     */
    protected BoundSql boundSql;

    /**
     * 构造函数，初始化基础配置和处理器
     * 执行顺序：
     * 1. 初始化基础配置
     * 2. 处理主键生成
     * 3. 创建参数处理器
     * 4. 创建结果集处理器
     *
     * @param executor SQL执行器
     * @param mappedStatement SQL相关配置信息
     * @param parameterObject 参数对象
     * @param rowBounds 分页参数
     * @param resultHandler 结果处理器
     * @param boundSql SQL语句对象
     */
    protected BaseStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject,
            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        this.configuration = mappedStatement.getConfiguration();
        this.executor = executor;
        this.mappedStatement = mappedStatement;
        this.rowBounds = rowBounds;

        this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        this.objectFactory = configuration.getObjectFactory();

        if (boundSql == null) { // issue #435, get the key before calculating the statement
            generateKeys(parameterObject);
            boundSql = mappedStatement.getBoundSql(parameterObject);
        }

        this.boundSql = boundSql;

        this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
        this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler,
                resultHandler, boundSql);
    }

    @Override
    public BoundSql getBoundSql() {
        return boundSql;
    }

    @Override
    public ParameterHandler getParameterHandler() {
        return parameterHandler;
    }

    /**
     * 准备Statement对象的模板方法
     * 执行步骤：
     * 1. 创建Statement实例（由子类实现）
     * 2. 设置超时时间
     * 3. 设置数据获取大小
     * 4. 错误处理和资源清理
     *
     * @param connection 数据库连接
     * @param transactionTimeout 事务超时时间（单位：秒）
     * @return 配置完成的Statement对象
     * @throws SQLException 当Statement创建或配置失败时抛出
     */
    @Override
    public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
        ErrorContext.instance().sql(boundSql.getSql());
        Statement statement = null;
        try {
            statement = instantiateStatement(connection);
            setStatementTimeout(statement, transactionTimeout);
            setFetchSize(statement);
            return statement;
        } catch (SQLException e) {
            closeStatement(statement);
            throw e;
        } catch (Exception e) {
            closeStatement(statement);
            throw new ExecutorException("Error preparing statement.  Cause: " + e, e);
        }
    }

    /**
     * 创建Statement实例，由子类实现具体逻辑
     *
     * @param connection 数据库连接
     * @return Statement对象
     * @throws SQLException SQL异常
     */
    protected abstract Statement instantiateStatement(Connection connection) throws SQLException;

    /**
     * 设置Statement的超时时间
     * 超时时间优先级（从高到低）：
     * 1. MappedStatement中的timeout
     * 2. 全局配置的defaultTimeout
     * 3. 事务timeout
     *
     * @param stmt Statement对象
     * @param transactionTimeout 事务超时时间（单位：秒）
     * @throws SQLException 当设置超时时间失败时抛出
     */
    protected void setStatementTimeout(Statement stmt, Integer transactionTimeout) throws SQLException {
        Integer queryTimeout = null;
        if (mappedStatement.getTimeout() != null) {
            queryTimeout = mappedStatement.getTimeout();
        } else if (configuration.getDefaultStatementTimeout() != null) {
            queryTimeout = configuration.getDefaultStatementTimeout();
        }
        if (queryTimeout != null) {
              stmt.setQueryTimeout(queryTimeout);
        }
        StatementUtil.applyTransactionTimeout(stmt, queryTimeout, transactionTimeout);
    }

    /**
     * 设置Statement的数据获取大小
     * 获取大小优先级（从高到低）：
     * 1. MappedStatement中的fetchSize
     * 2. 全局配置的defaultFetchSize
     *
     * 说明：较大的fetchSize适合大数据量查询，较小的fetchSize适合小数据量查询
     *
     * @param stmt Statement对象
     * @throws SQLException 当设置获取大小失败时抛出
     */
    protected void setFetchSize(Statement stmt) throws SQLException {
        Integer fetchSize = mappedStatement.getFetchSize();
        if (fetchSize != null) {
            stmt.setFetchSize(fetchSize);
            return;
        }
        Integer defaultFetchSize = configuration.getDefaultFetchSize();
        if (defaultFetchSize != null) {
              stmt.setFetchSize(defaultFetchSize);
        }
    }

    /**
     * 关闭Statement对象，释放数据库资源
     *
     * @param statement 需要关闭的Statement对象
     */
    protected void closeStatement(Statement statement) {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            // ignore
        }
    }

    /**
     * 生成主键值
     * 支持的主键生成方式：
     * 1. 自增主键（MySQL）
     * 2. 序列主键（Oracle）
     * 3. 自定义主键生成器
     *
     * @param parameter SQL参数对象
     */
    protected void generateKeys(Object parameter) {
        KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
        ErrorContext.instance().store();
        keyGenerator.processBefore(executor, mappedStatement, null, parameter);
        ErrorContext.instance().recall();
    }

}
