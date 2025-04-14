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
package org.apache.ibatis.executor;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 批处理执行器，用于批量执行SQL语句，以提高性能。
 *
 * 主要特点：
 * 1. 将多条相同SQL语句累积到批处理中，一次性发送到数据库执行
 * 2. 适用于大量数据插入、更新的场景
 * 3. 与JDBC的批处理机制集成
 *
 * 工作原理：
 * 1. 对于相同SQL语句，使用同一个Statement对象，将不同的参数添加到批处理中
 * 2. 对于不同SQL语句，创建新的Statement对象并添加到列表中
 * 3. 调用flushStatements方法时，执行所有累积的批处理语句
 *
 * 注意事项：
 * - 批处理执行时，无法立即获取影响的行数
 * - 如果发生异常，可能导致部分语句已执行而部分未执行
 *
 * @author Jeff Butler
 */
public class BatchExecutor extends BaseExecutor {

  /**
   * 批处理更新操作的返回值，用于表示更新操作已添加到批处理中，尚未执行。
   */
  public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

  /**
   * 保存所有当前批处理的Statement对象。
   */
  private final List<Statement> statementList = new ArrayList<>();

  /**
   * 保存所有批处理的结果信息。
   */
  private final List<BatchResult> batchResultList = new ArrayList<>();

  /**
   * 当前正在处理的SQL语句，用于判断新的SQL是否可以合并到当前批处理中。
   */
  private String currentSql;

  /**
   * 当前正在处理的MappedStatement对象。
   */
  private MappedStatement currentStatement;

  /**
   * 构造函数，初始化批处理执行器。
   *
   * @param configuration MyBatis配置对象
   * @param transaction 事务对象
   */
  public BatchExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  /**
   * 执行更新操作（包括insert、update、delete）。
   *
   * 执行流程：
   * 1. 如果当前SQL与之前的SQL相同，复用已有的Statement并添加到批处理中
   * 2. 如果当前SQL与之前的SQL不同，创建新的Statement并添加到批处理中
   * 3. 返回一个固定值表示已添加到批处理，实际执行将在flushStatements时进行
   *
   * @param ms 映射语句对象
   * @param parameterObject SQL参数
   * @return 固定值BATCH_UPDATE_RETURN_VALUE
   * @throws SQLException SQL异常
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
    final Configuration configuration = ms.getConfiguration();
    // 创建StatementHandler对象
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT,
        null, null);
    final BoundSql boundSql = handler.getBoundSql();
    final String sql = boundSql.getSql();
    final Statement stmt;

    // 判断当前SQL是否与之前的SQL相同
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      // SQL相同，复用最后一个Statement对象
      int last = statementList.size() - 1;
      stmt = statementList.get(last);
      applyTransactionTimeout(stmt);
      handler.parameterize(stmt);// 修复Issues 322
      // 将当前参数添加到对应的BatchResult中
      BatchResult batchResult = batchResultList.get(last);
      batchResult.addParameterObject(parameterObject);
    } else {
      // SQL不同，创建新的Statement对象
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt); // 修复Issues 322
      // 更新当前SQL和Statement信息
      currentSql = sql;
      currentStatement = ms;
      statementList.add(stmt);
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    // 将当前更新添加到批处理中
    handler.batch(stmt);
    return BATCH_UPDATE_RETURN_VALUE;
  }

  /**
   * 执行查询操作。
   *
   * 注意：执行查询前会先刷新所有批处理语句，确保查询结果的一致性。
   *
   * @param ms 映射语句对象
   * @param parameterObject SQL参数
   * @param rowBounds 分页参数
   * @param resultHandler 结果处理器
   * @param boundSql 绑定的SQL对象
   * @return 查询结果列表
   * @throws SQLException SQL异常
   */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds,
      ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      // 先刷新所有批处理语句，确保查询结果的一致性
      flushStatements();
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds,
          resultHandler, boundSql);
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      return handler.query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  /**
   * 执行游标查询操作。
   *
   * 注意：执行查询前会先刷新所有批处理语句，确保查询结果的一致性。
   *
   * @param ms 映射语句对象
   * @param parameter SQL参数
   * @param rowBounds 分页参数
   * @param boundSql 绑定的SQL对象
   * @return 查询结果游标
   * @throws SQLException SQL异常
   */
  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
      throws SQLException {
    // 先刷新所有批处理语句，确保查询结果的一致性
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * 刷新批处理语句，执行所有累积的批处理操作。
   *
   * 执行流程：
   * 1. 如果是回滚操作，直接返回空列表
   * 2. 依次执行每个Statement的批处理操作
   * 3. 处理自动生成的主键（如果有）
   * 4. 收集执行结果并返回
   * 5. 清空批处理状态
   *
   * @param isRollback 是否为回滚操作
   * @return 批处理结果列表
   * @throws SQLException SQL异常
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    try {
      List<BatchResult> results = new ArrayList<>();
      // 如果是回滚操作，直接返回空列表
      if (isRollback) {
        return Collections.emptyList();
      }

      // 依次执行每个Statement的批处理操作
      for (int i = 0, n = statementList.size(); i < n; i++) {
        Statement stmt = statementList.get(i);
        applyTransactionTimeout(stmt);
        BatchResult batchResult = batchResultList.get(i);
        try {
          // 执行批处理并获取更新计数
          batchResult.setUpdateCounts(stmt.executeBatch());
          MappedStatement ms = batchResult.getMappedStatement();
          List<Object> parameterObjects = batchResult.getParameterObjects();
          KeyGenerator keyGenerator = ms.getKeyGenerator();

          // 处理自动生成的主键
          if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
            // 使用JDBC3的方式获取生成的主键
            Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
            jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
          } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { // 修复issue #141
            // 使用自定义的主键生成器
            for (Object parameter : parameterObjects) {
              keyGenerator.processAfter(this, ms, stmt, parameter);
            }
          }
          // 关闭语句以关闭游标 #1109
          closeStatement(stmt);
        } catch (BatchUpdateException e) {
          // 处理批处理异常，提供详细的错误信息
          StringBuilder message = new StringBuilder();
          message.append(batchResult.getMappedStatement().getId()).append(" (batch index #").append(i + 1).append(")")
              .append(" failed.");
          if (i > 0) {
            message.append(" ").append(i)
                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
          }
          throw new BatchExecutorException(message.toString(), e, results, batchResult);
        }
        results.add(batchResult);
      }
      return results;
    } finally {
      // 清理资源，关闭所有Statement对象
      for (Statement stmt : statementList) {
        closeStatement(stmt);
      }
      // 重置状态
      currentSql = null;
      statementList.clear();
      batchResultList.clear();
    }
  }
}
