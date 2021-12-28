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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * TransactionalCache实现了Cache接口，主要用于保存在某个SqlSession的某个事务中需要向某个二级缓存中添加的缓存数据
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);
  //底层封装的二级缓存所对应的Cache对象
  private final Cache delegate;
  //当该字段为true时，则表示当前TransactionalCache不可查询，且提交事务时候，会将底层Cahce清空
  private boolean clearOnCommit;
  //暂时记录添加到TransactionalCache中的数据。在事务提交时，会将其中的数据添加到二级缓存中
  private final Map<Object, Object> entriesToAddOnCommit;
  //记录缓存未命中的CacheKey对象
  //entriesMissedInCache集合的功能是什么？为什么要在事务提交和回滚时，调用二级缓存的putObject()方法处理该集合中记录的key呢？笔者认为，这与BlockingCache的支持相关。
  // 通过对CachingExecutor.query()方法的分析我们知道，查询二级缓存时会使用getObject()方法，如果二级缓存没有对应数据，则查询数据库并使用putObject()方法将查询结果放入二级缓存。
  // 如果底层使用了BlockingCache，则getObject()方法会有对应的加锁过程，putObject()方法则会有对应的解锁过程，如果在两者之间出现异常，则无法释放锁，导致该缓存项无法被其他SqlSession使用。
  // 为了避免出现这种情况，TransactionalCache使用entriesMissedInCache集合记录了未命中的CacheKey，也就是那些加了锁的缓存项，而entriesToAddOnCommit集合可以看作entriesMissedInCache集合子集，
  // 也就是那些正常解锁的缓存项。对于其他未正常解锁的缓存项，则会在事务提交或回滚时进行解锁操作。
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  //它首先会查询底层的二级缓存，并将未命中的key记录到entriesMissedInCache中，之后会根据clearOnCommit字段的值决定具体的返回值，具体实现如下：
  @Override
  public Object getObject(Object key) {
    // issue #116
    //查询底层的Cache是否包含指定的key
    Object object = delegate.getObject(key);
    if (object == null) {
      //底层缓存对象中不包含该缓存项
      entriesMissedInCache.add(key);
    }
    // issue #146
    //如果clearOnCommit为true，则当前的TransactionalCache不可查询，始终返回null
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  //TransactionalCache.putObject()方法并没有直接将结果对象记录到其封装的二级缓存中，而是暂时保存在entriesToAddOnCommit集合中，
  // 在事务提交时才会将这些结果对象从entriesToAddOnCommit集合添加到二级缓存中。
  @Override
  public void putObject(Object key, Object object) {
    //将缓存项暂时存在entriesToAddOnCommit集合中
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  //TransactionalCache.clear()方法会清空entriesToAddOnCommit集合，并设置clearOnCommit 为true
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  //TransactionalCache.commit()方法会根据clearOnCommit字段的值决定是否清空二级缓存，然后调用flushPendingEntries()方法将entriesToAddOnCommit集合中记录的结果对象保存到二级缓存中
  public void commit() {
    if (clearOnCommit) {//在事务提交前，清空二级缓存
      delegate.clear();
    }
    //将entriesToAddOnCommit集合中的数据保存到二级缓存
    flushPendingEntries();
    //重置clearOnCommit为false，并清空entriesToAddOnCommit和entriesMissedInCache
    reset();
  }

  //TransactionalCache.rollback()方法会将entriesMissedInCache集合中记录的缓存项从二级缓存中删除，并清空entriesToAddOnCommit集合和entriesMissedInCache集合。
  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  //重置clearOnCommit为false，并清空entriesToAddOnCommit和entriesMissedInCache
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
