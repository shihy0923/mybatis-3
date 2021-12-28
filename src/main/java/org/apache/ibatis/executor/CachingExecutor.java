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

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * 扮演着装饰器模式，为Executor添加了二级缓存功能
 */
public class CachingExecutor implements Executor {

  private final Executor delegate;
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    delegate.setExecutorWrapper(this);
  }

  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      // issues #499, #524 and #573
      if (forceRollback) {
        tcm.rollback();
      } else {
        tcm.commit();
      }
    } finally {
      delegate.close(forceRollback);
    }
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  //CachingExecutor.update()方法并不会像BaseExecutor.update()方法处理一级存那样，直接清除缓存中的所有数据，
  // 而是与CachingExecutor.query()方法一样调用flushCacheIfRequired()方法检测SQL节点的配置后，决定是否清除二级缓存。
  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.update(ms, parameterObject);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    //获取BoundSql对象
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    //创建CacheKey对象
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
      throws SQLException {
    //获取查询语句所在命名空间对应的二级缓存
    Cache cache = ms.getCache();
    //是否开启了二级缓存的功能
    if (cache != null) {
      //根据<select>节点的配置，决定是否需要清空二级缓存
      flushCacheIfRequired(ms);
      //检查节点useCache配置以及是否使用了resultHandler
      if (ms.isUseCache() && resultHandler == null) {
        //二级缓存不能保存输出类型的参数，如果查询操作调用了包含输出参数的存储过程，则报错
        ensureNoOutParams(ms, boundSql);
        @SuppressWarnings("unchecked")
          //吃查询二级缓存
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          //二级缓存没有相应的结果对象，调用封装的Executor对象的query()方法，这里面会先查询一级缓存
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          //将查询结果保存到TransactionalCache.entriesToAddOnCommit集合中
          tcm.putObject(cache, key, list); // issue #578 and #116
        }
        return list;
      }
    }
    //没有启动二级缓存，调用封装的Executor对象的query()方法，这里面会先查询一级缓存
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  //看到这里，读者可能会提出这样的疑问：为什么要在事务提交时才将TransactionalCache.entriesToAddOnCommit集合中缓存的数据写入到二级缓存，而不是像一级缓存那样，将每次查询结果都直接写入二级缓存？笔者认为，这是为了防止出现“脏读”的情
  //况，最终实现的效果有点类似于“不可重复读”的事务隔离级别。假设当前数据库的隔离级别是“不可重复读”，
  // 先后开启T1、T2两个事务，在事务T1中添加了记录A，之后查询A记录，最后提交事务，
  // 事务T2会查询记录A。如果事务T1查询记录A时，就将A对应的结果对象放入二级缓存，则在事务T2第一次查询记录A时即可从二级缓存中直接获取其对应的结果对象。此时T1仍然未提交，这就出现了“脏读”的情况，显然不是用户期望的结果。
  //
  //按照CacheExecutor的本身实现，事务T1查询记录A时二级缓存未命中，会查询数据库，因为是同一事务，所以可以查询到记录A并得到相应的结果对象，
  // 并且会将记录A保存到TransactionalCache.entriesToAddOnCommit集合中。而事务T2第一次查询记录A时，
  // 二级缓存未命中，则会访问数据库，因为是不同的事务，数据库的“不可重复读”隔离级别会保证事务T2无法查询到记录A，
  // 这就避免了上面“脏读”的场景。事务T1提交时会将entriesToAddOnCommit集合中的数据添加到二级缓存中，所以事务T2第二次查询记录A时，二级缓存才会命中，这就导致了同一事务中多次读取的结果不一致，也就是“不可重复读”的场景。
  @Override
  public void commit(boolean required) throws SQLException {
    //调用底层的Executor提交事务
    delegate.commit(required);
    //遍历所有相关的TransactionalCache对象，执行commit()方法
    tcm.commit();
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      //调用底层的Executor回滚事务
      delegate.rollback(required);
    } finally {
      if (required) {
        //遍历所有相关的TransactionalCache对象，执行rollback()方法
        tcm.rollback();
      }
    }
  }

  private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        if (parameterMapping.getMode() != ParameterMode.IN) {
          throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
        }
      }
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  private void flushCacheIfRequired(MappedStatement ms) {
    Cache cache = ms.getCache();
    if (cache != null && ms.isFlushCacheRequired()) {
      tcm.clear(cache);
    }
  }

  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
