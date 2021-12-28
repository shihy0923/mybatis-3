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
package org.apache.ibatis.cache.impl;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Clinton Begin
 * 一级缓存和二级缓存都用的这个类
 * 一级缓存：
 * 既然有了 PerpetualCache ，那它一定是组合到某个位置，从而形成一级缓存的吧！在底层的BaseExecutor ，这个类名的设计比较类似于 AbstractXXX ，它本身也是一个抽象类，是它里面组合了 PerpetualCache，这就是一级缓存
 * 一级缓存是会话级别的缓存，在MyBatis中每创建一个SqlSession对象，就表示开启一次数据库会话。在一次会话中，应用程序可能会在短时间
 * 内，例如一个事务内，反复执行完全相同的查询语句，如果不对数据进行缓存，那么每一次查询都会执行一次数据库查询操作，而多次完全相同的、时间间隔较短的查询语句得到的结果集极有可能完全相同，这也就造成了数据库资源的浪费。
 * 一级缓存的生命周期与SqlSession相同，其实也就与SqlSession中封装的Executor对象的生命周期相同。当调用Executor对象的close()方法时，该Executor对象对应的一级缓存就变得不可用。一级缓存中对象的存活时间受很多方面的影响，例如，在调用Executor.update()方法时，也会先清空一级缓存。其他影响一级缓存中数据的行为，我们在分析BaseExecutor的具体实现时会详细介绍。一级缓存默认是开启的，一般情况下，不需要用户进行特殊配置。如果存在特殊需求，可以考虑使用插件功能来改变这个行为。
 *二级缓存：
 *
 */
public class PerpetualCache implements Cache {

  //Cache对象的唯一标识
  private final String id;
  //用于记录缓存项的Map对象
  //直接套一个 HashMap 就完事了？哎，还真就套一层就完事了！因为缓存本身就是 Map 类型的设计
  private final Map<Object, Object> cache = new HashMap<>();

  public PerpetualCache(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public int getSize() {
    return cache.size();
  }

  @Override
  public void putObject(Object key, Object value) {
    cache.put(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return cache.get(key);
  }

  @Override
  public Object removeObject(Object key) {
    return cache.remove(key);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public boolean equals(Object o) {
    if (getId() == null) {
      throw new CacheException("Cache instances require an ID.");
    }
    if (this == o) {
      return true;
    }
    if (!(o instanceof Cache)) {
      return false;
    }

    Cache otherCache = (Cache) o;
    return getId().equals(otherCache.getId());
  }

  @Override
  public int hashCode() {
    if (getId() == null) {
      throw new CacheException("Cache instances require an ID.");
    }
    return getId().hashCode();
  }

}
