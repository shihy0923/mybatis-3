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

import java.util.Properties;

/**
 * MyBatis允许用户使用自定义拦截器对SQL语句执行过程中的某一点进行拦截。默认情况下，MyBatis允许拦截器拦截Executor的方法、ParameterHandler的方法、ResultSetHandler的方法以及StatementHandler的方法。具体可拦截的方法如下：
 * · Executor中的update()方法、query()方法、flushStatements()方法、commit()方法、rollback()方法、getTransaction()方法、close()方法、isClosed()方法。
 * · ParameterHandler中的getParameterObject()方法、setParameters()方法。
 * · ResultSetHandler中的handleResultSets()方法、handleOutputParameters()方法。
 * · StatementHandler中的prepare()方法、parameterize()方法、batch()方法、update()方法、query()方法。
 *
 * MyBatis中使用的拦截器都需要实现Interceptor接口。Interceptor接口是MyBatis插件模块的核心，其定义如下：
 *
 * MyBatis通过拦截器可以改变Mybatis的默认行为，例如实现SQL重写之类的功能，由于拦截器会深入到Mybatis的核心，因此在编写自定义插件之前，最好了解它的原理，以便写出安全高效的插件。
 * 用户自定义的拦截器除了继承Interceptor接口，还需要使用@Intercepts和@Signature两个注解进行标识。@Intercepts注解中指定了一个@Signature注解列表，每个@Signature注解中都标识了该插件需要拦截的方法信息，
 * 其中@Signature注解的type属性指定需要拦截的类型，method属性指定需要拦截的方法，args属性指定了被拦截方法的参数列表。通过这三个属性值，@Signature注解就可以表示一个方法签名，唯一确定一个方法。
 * @author Clinton Begin
 */
public interface Interceptor {

  //执行拦截逻辑
  Object intercept(Invocation invocation) throws Throwable;
  //决定是否触发intercept()方法
  //用户自定义拦截器的plugin()方法，可以考虑使用MyBatis提供的Plugin工具类实现，它实现了InvocationHandler接口，并提供了一个wrap()静态方法用于创建代理对象。
  //Mybatis已经帮我们自定实现了。。。。。提供了Default方法。target是被代理对象
  default Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }
  //根据配置初始化Interceptor对象
  default void setProperties(Properties properties) {
    // NOP
  }

}
