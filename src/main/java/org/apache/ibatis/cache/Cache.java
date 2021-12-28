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
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * SPI for cache providers.
 * <p>
 * One instance of cache will be created for each namespace.
 * <p>
 * The cache implementation must have a constructor that receives the cache id as an String parameter.
 * <p>
 * MyBatis will pass the namespace as id to the constructor.
 *
 * <pre>
 * public MyCache(final String id) {
 *   if (id == null) {
 *     throw new IllegalArgumentException("Cache instances require an ID");
 *   }
 *   this.id = id;
 *   initialize();
 * }
 * </pre>
 *
 * Mybatis的一级缓存和二级缓存，都是用的该接口。它定义了所有缓存的基本行为。
 * Cache接口有多个实现类有多个，大部分都是装饰器，只有PerpetualCache提供了Cache接口的基本实现，它是一个没有任何修饰的、最单纯的缓存实现
 * Cache实现类的装饰者设计意义，究其根源，我们要先提一点 MyBatis 二级缓存的东西了，MyBatis 中的二级缓存本身是占用应用空间的，换句话说，MyBatis 中的二级缓存实际使用的是 JVM 的内存，
 * 那默认情况来讲，占用的内存太多不利于应用本身的正常运行，所以 MyBatis 会针对缓存的各种特性和过期策略，设计了一些能够修饰原本缓存件的装饰者，以此达到动态拼装缓存实现的目的。
 *
 * @author Clinton Begin
 */

public interface Cache {

  /**
   * @return The identifier of this cache
   * 该缓存对象的id
   */
  String getId();

  /**
   * @param key
   *          Can be any object but usually it is a {@link CacheKey}
   * @param value
   *          The result of a select.
   *          向缓存中添加数据，一般情况下，kye是CacheKey，value是查询结果
   *
   */
  void putObject(Object key, Object value);

  /**
   * @param key
   *          The key
   * @return The object stored in the cache.
   * 根据指定的key，在缓存中查找对应的结果对象
   */
  Object getObject(Object key);

  /**
   * As of 3.3.0 this method is only called during a rollback
   * for any previous value that was missing in the cache.
   * This lets any blocking cache to release the lock that
   * may have previously put on the key.
   * A blocking cache puts a lock when a value is null
   * and releases it when the value is back again.
   * This way other threads will wait for the value to be
   * available instead of hitting the database.
   *
   *
   * @param key
   *          The key
   * @return Not used
   * 删除key对应的缓存项
   */
  Object removeObject(Object key);

  /**
   * Clears this cache instance.
   * 清空缓存
   */
  void clear();

  /**
   * Optional. This method is not called by the core.
   *
   * @return The number of elements stored in the cache (not its capacity).
   * 缓存项的个数，该方法不会被Mybatis核心代码使用，所以可提供空实现
   */
  int getSize();

  /**
   * Optional. As of 3.2.6 this method is no longer called by the core.
   * <p>
   * Any locking needed by the cache must be provided internally by the cache provider.
   *
   * @return A ReadWriteLock
   * 获取读写锁，该方法不会被Mybatis核心代码使用，所以可提供空实现
   */
  default ReadWriteLock getReadWriteLock() {
    return null;
  }

}
