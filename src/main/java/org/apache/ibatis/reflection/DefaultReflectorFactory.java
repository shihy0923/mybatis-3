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
package org.apache.ibatis.reflection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ibatis.util.MapUtil;

public class DefaultReflectorFactory implements ReflectorFactory {
  //该字段决定是否开启对 Reflector 对象的缓存
  private boolean classCacheEnabled = true;
  //使用 ConcurrentMap 集合实现对 Reflector 对象的缓存
  private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

  public DefaultReflectorFactory() {
  }

  @Override
  public boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }

  @Override
  public void setClassCacheEnabled(boolean classCacheEnabled) {
    this.classCacheEnabled = classCacheEnabled;
  }

  //为指定的 Class 创建 Reflector对象，并将 Reflector 对象缓存到 reflectorMap 中
  @Override
  public Reflector findForClass(Class<?> type) {
    if (classCacheEnabled) {
      // synchronized (type) removed see issue #461
      return MapUtil.computeIfAbsent(reflectorMap, type, Reflector::new);
    } else {
      return new Reflector(type);
    }
  }

}
