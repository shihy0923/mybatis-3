/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.apache.ibatis.executor.ExecutionPlaceholder.*;

/**
 * @author Clinton Begin
 *BaseExecutor是一个实现了Executor接口的抽象类，它实现了Executor接口的大部分方法，其中就使用了模板方法模式。
 * BaseExecutor中主要提供了缓存管理和事务管理的基本功能，继承BaseExecutor的子类只要实现四个基本方法来完成数据库的相关操作即可，
 * 这四个方法分别是：doUpdate()方法、doQuery()方法、doQueryCursor()方法、doFlushStatement()方法，其余的功能在BaseExecutor中实现。
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);
  //Transaction对象，实现事务的提交、回滚和关闭操作
  protected Transaction transaction;
  //其中封装的Executor对象
  protected Executor wrapper;
  //延迟加载队列
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
  //一级缓存，用于缓存该Executor对象查询结果集映射得到的结果对象
  protected PerpetualCache localCache;
  //一级缓存，用于缓存输出类型的参数，给存储过程用的
  protected PerpetualCache localOutputParameterCache;
  protected Configuration configuration;
  //用来记录嵌套查询的层数
  protected int queryStack;
  private boolean closed;

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }

  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore. There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  //BaseExecutor.update()方法负责执行insert、update、delete三类SQL语句，它是调用doUpdate()模板方法实现的。
  // 在调用doUpdate()方法之前会清空缓存，因为执行SQL语句之后，数据库中的数据已经更新，一级缓存的内容与数据库中的数据可能已经不一致了，所以需要调用clearLocalCache()方法清空一级缓存中的“脏数据”

  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    //清空一级缓存
    clearLocalCache();
    return doUpdate(ms, parameter);
  }

  //在BatchExecutor实现（具体实现后面详细介绍）中，可以缓存多条SQL语句，等待合适的时机将缓存的多条SQL语句一并发送到数据库执行。
  // Executor.flushStatements()方法主要是针对批处理多条SQL语句的，它会调用doFlushStatements()这个基本方法处理Executor中缓存的多条SQL 语句。
  // 在BaseExecutor.commit()、rollback()等方法中都会首先调用flushStatements()方法，然后再执行相关事
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    //调用doFlushStatements()这个基本方法，其参数isRollBack标识是否执行Executor中缓存的SQL语句，false表示执行，true标识不执行。
    //模板方法，一般用的SimpleExecutor，直接返回一个空集合
    return doFlushStatements(isRollBack);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    //获取BoundSql对象
    BoundSql boundSql = ms.getBoundSql(parameter);
    //创建CacheKey对象
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    //重载方法
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) {//检查Executor是否已经关闭
      throw new ExecutorException("Executor was closed.");
    }
    //非嵌套查询，并且<select>节点配置的flushCache属性为true时，才会清空一级缓存
    //flushCahce配置项是影响一级缓存中结果对象存活时长的第一个方面
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }
    List<E> list;
    try {
      queryStack++;//增加查询层数
      //查询一级缓存
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      if (list != null) {
        //针对存储过程调用的处理
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        //其中会调用doQuery()方法完成数据库查询，并得到映射后的结果对象
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      queryStack--;//当前查询完成，查询层数减少
    }
    if (queryStack == 0) {
      //处理延迟加载的相关内容
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // issue #601
      deferredLoads.clear();
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        clearLocalCache();
      }
    }
    return list;
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    if (deferredLoad.canLoad()) {
      deferredLoad.load();
    } else {
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    if (closed) {//检测当前的Executor是否已经关闭
      throw new ExecutorException("Executor was closed.");
    }
    //创建CacheKey对象
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(ms.getId());//将MappedStatement的id添加到CacheKey对象中
    cacheKey.update(rowBounds.getOffset());//将offset添加到CacheKey对象中
    cacheKey.update(rowBounds.getLimit());//将limit添加到CacheKey对象中
    cacheKey.update(boundSql.getSql());//将SQL语句添加到CacheKey对象中，（包含“?”占位符）
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    // mimic DefaultParameterHandler logic
    //获取用户传入的实参，并添加到CacheKey对象中
    for (ParameterMapping parameterMapping : parameterMappings) {
      if (parameterMapping.getMode() != ParameterMode.OUT) {//过滤掉输出类型的参数
        Object value;
        String propertyName = parameterMapping.getProperty();
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        cacheKey.update(value);//将实参添加到CacheKey对象中
      }
    }
    if (configuration.getEnvironment() != null) {//如果Environment的id不为空，则将其添加到CacheKey对象中
      // issue #176
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    //首先清空一级缓存
    clearLocalCache();
    //执行缓存的SQL语句
    flushStatements();
    //根据required参数决定是否提交事务
    if (required) {
      transaction.commit();
    }
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        //首先清空一级缓存
        clearLocalCache();
        //flushStatements的isRollBack为true，这就导致缓存的SQL语句全部被忽略(不会发送到数据库执行)
        flushStatements(true);
      } finally {
        //根据required参数决定是否执行数据库回滚和关闭底层的数据库连接
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  //清空一级缓存
  @Override
  public void clearLocalCache() {
    if (!closed) {
      localCache.clear();
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
      throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   *
   * @param statement
   *          a current statement
   * @throws SQLException
   *           if a database access error occurs, this method is called on a closed <code>Statement</code>
   * @since 3.4.0
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      if (cachedParameter != null && parameter != null) {
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          if (parameterMapping.getMode() != ParameterMode.IN) {
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }

  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    //在缓存中添加占位符
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      //调用doQuery()方法，完成数据库查询操作，并返回结果对象
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      //删除占位符
      localCache.removeObject(key);
    }
    //将真正的结果对象添加到一级缓存中
    localCache.putObject(key, list);
    if (ms.getStatementType() == StatementType.CALLABLE) {//是否为存储过程调用
      localOutputParameterCache.putObject(key, parameter);//缓存输出类型的参数
    }
    return list;
  }

  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      return connection;
    }
  }

  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  private static class DeferredLoad {

    private final MetaObject resultObject;
    private final String property;
    private final Class<?> targetType;
    private final CacheKey key;
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    public boolean canLoad() {
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
    }

    public void load() {
      @SuppressWarnings("unchecked")
      // we suppose we get back a List
      List<Object> list = (List<Object>) localCache.getObject(key);
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      resultObject.setValue(property, value);
    }

  }

}
