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
package org.apache.ibatis.plugin;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {
  //被代理对象
  private final Object target;
  //Interceptor对象
  private final Interceptor interceptor;
  //getSignatureMap()的返回值，拦截器要拦截的方法
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }
  //在这了为我们的被代理对象，target就是我们的被代理对象
  public static Object wrap(Object target, Interceptor interceptor) {
    //获取用户自定的Interceptor中的@Signature注解信息，即，拦截器要拦截的方法。getSignatureMap()方法负责处理@Signature注解。代码并不复杂
    //signatureMap里面放的是，key为 @Signature里面设置的type属性，即四大对象所对应的类，value为 @Signature里面method和args两个属性所确定的那个方法
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    //获取被代理对象的类型
    Class<?> type = target.getClass();
    //返回符合该拦截器要拦截的接口
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    if (interfaces.length > 0) {//说明需要生产代理对象
      return Proxy.newProxyInstance(//返回一个JDK动态代理对象
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));//这理使用的InvocationHandler对象就是Plugin，所以看下他的invoker()方法
    }
    //直接返回被代理对象
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      //获取该拦截器所要拦截的所有方法
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      //判断当前方法是否是要被拦截的方法
      if (methods != null && methods.contains(method)) {
        //是要被拦截的方法，则，调用该拦截器的intercept()方法，这就到了拦截器的具体实现了
        //注意，这里包装了一个Invocation对象，看下它的proceed()方法
        return interceptor.intercept(new Invocation(target, method, args));
      }
      //否则直接调用被代理对象的目标方法
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : sigs) {
      Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type(), k -> new HashSet<>());
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }
  //返回符合该拦截器要拦截的接口
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<>();
    while (type != null) {
      //获取被代理对象所实现的接口
      for (Class<?> c : type.getInterfaces()) {
        //判断被代理对象所在的类所实现的接口，是否是在该拦截器的拦截范围内。即@Signature注解信息里面的type是否有被代理对象的类所实现的接口
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[0]);
  }

}
