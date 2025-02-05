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
package org.apache.ibatis.mapping;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.cache.decorators.BlockingCache;
import org.apache.ibatis.cache.decorators.LoggingCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.ScheduledCache;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.decorators.SynchronizedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * @author Clinton Begin
 * CacheBuilder 是 Cache 的建造者，CacheBuilder 中提供了很多设置属性的方法（对应于建造者中的建造方法）
 */
public class CacheBuilder {
  //Cache对象的唯一标识， 一般情况下对应映射文件中的配置namespace
  private final String id;
  //Cache 接口的真正实现类，默认是PerpetualCache
  private Class<? extends Cache> implementation;
  //装饰器集合，默认只包含LruCache.class
  private final List<Class<? extends Cache>> decorators;
  //Cache大小
  private Integer size;
  //清理时间周期
  private Long clearInterval;
  //是否可读写
  private boolean readWrite;
  //其他配置信息
  private Properties properties;
  //是否阻塞
  private boolean blocking;

  public CacheBuilder(String id) {
    this.id = id;
    this.decorators = new ArrayList<>();
  }

  public CacheBuilder implementation(Class<? extends Cache> implementation) {
    this.implementation = implementation;
    return this;
  }

  public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
    if (decorator != null) {
      this.decorators.add(decorator);
    }
    return this;
  }

  public CacheBuilder size(Integer size) {
    this.size = size;
    return this;
  }

  public CacheBuilder clearInterval(Long clearInterval) {
    this.clearInterval = clearInterval;
    return this;
  }

  public CacheBuilder readWrite(boolean readWrite) {
    this.readWrite = readWrite;
    return this;
  }

  public CacheBuilder blocking(boolean blocking) {
    this.blocking = blocking;
    return this;
  }

  public CacheBuilder properties(Properties properties) {
    this.properties = properties;
    return this;
  }
  //方法根据 CacheBuilder 中上述字段的值创建 Cache 对象并添加合适的装饰器
  public Cache build() {
    //如果Implementation字段和decorators集合为空，则为其设置默认值， implementation 默认是 PerpetualCache.class, decorators集合，默认只包含LruCache.class
    setDefaultImplementations();
    //根据implementation指定的类型，通过反射获取参数为String类型的构造方法，并通过该构造方法创建Cache对象
    Cache cache = newBaseCacheInstance(implementation, id);
    //根据<cache>节点下配置的<property>信息，初始化Cache对象
    setCacheProperties(cache);
    // issue #352, do not apply decorators to custom caches
    //检测cache对象的类型，如果是PerpetualCache类型，则为其添加decorators集合中的装饰器;如果是自定义类型的Cache接口实现，则不添加 decorators 集合中的装饰器
    if (PerpetualCache.class.equals(cache.getClass())) {
      for (Class<? extends Cache> decorator : decorators) {
        //通过反射获取参数为 Cache 类型的构造方法，并通过该构造方法创建装饰器
        cache = newCacheDecoratorInstance(decorator, cache);
        //配置cache对象的属性
        setCacheProperties(cache);
      }
      //添加 MyBatis 中提供的标准装饰器
      cache = setStandardDecorators(cache);
      //如果不是LoggingCache的子类，则添加LoggingCache装饰器
    } else if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
      cache = new LoggingCache(cache);
    }
    return cache;
  }

  private void setDefaultImplementations() {
    if (implementation == null) {
      implementation = PerpetualCache.class;
      if (decorators.isEmpty()) {
        decorators.add(LruCache.class);
      }
    }
  }

  //会根据CacheBuilder中各个字段的值，为cache对象添加对应的装饰器
  private Cache setStandardDecorators(Cache cache) {
    try {
      //创建cache对象对应的MetaObject对象
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      if (size != null && metaCache.hasSetter("size")) {
        metaCache.setValue("size", size);
      }
      if (clearInterval != null) {//检测是否指定了clearinterval字段
        //添加ScheduledCache装饰器
        cache = new ScheduledCache(cache);
        //设置ScheduledCache的clearInterval字段
        ((ScheduledCache) cache).setClearInterval(clearInterval);
      }
      if (readWrite) {//是否只读，对应添加SerializedCache装饰器
        cache = new SerializedCache(cache);
      }
      //默认添加LoggingCache和SynchronizedCache两个装饰器
      cache = new LoggingCache(cache);
      cache = new SynchronizedCache(cache);
      //是否阻塞，对应添加BlockingCache装饰器
      if (blocking) {
        cache = new BlockingCache(cache);
      }
      return cache;
    } catch (Exception e) {
      throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
    }
  }

  //根据<cache>节点下配置的<property>信息，初始化Cache 对象
  private void setCacheProperties(Cache cache) {
    if (properties != null) {
      //cache对应的创建MetaObject对象
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      for (Map.Entry<Object, Object> entry : properties.entrySet()) {
        //配置项的名称， 即Cache对应的属性名称
        String name = (String) entry.getKey();
        //配置项的值， 即cache对应的属性值
        String value = (String) entry.getValue();
        //检测cache是否有该属性对应的setter方法
        if (metaCache.hasSetter(name)) {
          //获取该属性的类型
          Class<?> type = metaCache.getSetterType(name);
          //进行类型转换 ，并设置该属性值
          if (String.class == type) {
            metaCache.setValue(name, value);
          } else if (int.class == type//对 int 、 double 、 long 、 byte 等基本类型的转换操作
              || Integer.class == type) {
            metaCache.setValue(name, Integer.valueOf(value));
          } else if (long.class == type
              || Long.class == type) {
            metaCache.setValue(name, Long.valueOf(value));
          } else if (short.class == type
              || Short.class == type) {
            metaCache.setValue(name, Short.valueOf(value));
          } else if (byte.class == type
              || Byte.class == type) {
            metaCache.setValue(name, Byte.valueOf(value));
          } else if (float.class == type
              || Float.class == type) {
            metaCache.setValue(name, Float.valueOf(value));
          } else if (boolean.class == type
              || Boolean.class == type) {
            metaCache.setValue(name, Boolean.valueOf(value));
          } else if (double.class == type
              || Double.class == type) {
            metaCache.setValue(name, Double.valueOf(value));
          } else {
            throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
          }
        }
      }
    }
    if (InitializingObject.class.isAssignableFrom(cache.getClass())) {
      try {
        ((InitializingObject) cache).initialize();
      } catch (Exception e) {
        throw new CacheException("Failed cache initialization for '"
          + cache.getId() + "' on '" + cache.getClass().getName() + "'", e);
      }
    }
  }

  private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
    Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(id);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
    }
  }

  private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(String.class);
    } catch (Exception e) {
      throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  "
        + "Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e, e);
    }
  }

  private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
    Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(base);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
    }
  }

  private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(Cache.class);
    } catch (Exception e) {
      throw new CacheException("Invalid cache decorator (" + cacheClass + ").  "
        + "Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
    }
  }
}
