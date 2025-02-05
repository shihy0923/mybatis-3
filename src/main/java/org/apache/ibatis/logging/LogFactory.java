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
package org.apache.ibatis.logging;

import java.lang.reflect.Constructor;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * Mybatis对于日志模块，使用了适配器模式，这里
 * LogFactory工厂类负责创建对应的日志组件适配器.
 * 适配器中的目标接口，就是org.apache.ibatis.logging.Log
 */
public final class LogFactory {

  /**
   * Marker to be used by logging implementations that support markers.
   */
  public static final String MARKER = "MYBATIS";

  //记录当前使用的第三方日志组件所对应的适配器的构造方法
  private static Constructor<? extends Log> logConstructor;

  static {
    //下面会针对每种 日志组件调用 tryimplementation()方法进行尝试加载，具体调用顺序是：
    //useSlf4jLogging()-->useCommonsLogging()-->useLog4J2Logging()-->useLog4JLogging()--> useJdkLogging()-->useNoLogging()
    tryImplementation(LogFactory::useSlf4jLogging);
    tryImplementation(LogFactory::useCommonsLogging);
    tryImplementation(LogFactory::useLog4J2Logging);
    //我们以Log4jImpl为例子学习
    tryImplementation(LogFactory::useLog4JLogging);
    tryImplementation(LogFactory::useJdkLogging);
    tryImplementation(LogFactory::useNoLogging);
  }

  private LogFactory() {
    // disable construction
  }
  //提供给我们直接在代码里面直接调用的静态方法
  public static Log getLog(Class<?> clazz) {
    return getLog(clazz.getName());
  }
  //看这里
  public static Log getLog(String logger) {
    try {
      //直接利用反射，生成具体的适配器的对象
      return logConstructor.newInstance(logger);
    } catch (Throwable t) {
      throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
    }
  }

  public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
    setImplementation(clazz);
  }

  public static synchronized void useSlf4jLogging() {
    setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);//这些东西是适配器模式中的具体的适配器
  }

  public static synchronized void useCommonsLogging() {
    setImplementation(org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl.class);
  }

  /**
   * @deprecated Since 3.5.9 - See https://github.com/mybatis/mybatis-3/issues/1223. This method will remove future.
   */
  @Deprecated
  public static synchronized void useLog4JLogging() {
    setImplementation(org.apache.ibatis.logging.log4j.Log4jImpl.class);
  }

  public static synchronized void useLog4J2Logging() {
    setImplementation(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);
  }

  public static synchronized void useJdkLogging() {
    setImplementation(org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl.class);
  }

  public static synchronized void useStdOutLogging() {
    setImplementation(org.apache.ibatis.logging.stdout.StdOutImpl.class);
  }

  public static synchronized void useNoLogging() {
    setImplementation(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
  }

  private static void tryImplementation(Runnable runnable) {
    //如果已经找到了具体的适配器类，直接跳过。
    if (logConstructor == null) {
      try {
        //这里调用就是静态方法中传入的方法引用
        //其实就是调用下面的org.apache.ibatis.logging.LogFactory.setImplementation()方法，这个方法如果报了找不到指定的类型的错误，
        //直接catch到异常，然后啥也不处理。
        runnable.run();
      } catch (Throwable t) {
        // ignore
      }
    }
  }

  //在这里面去找到底是用哪个适配器
  private static void setImplementation(Class<? extends Log> implClass) {
    try {
      //获取指定适配器的构造方法，这里可能会报错，找不到指定的类，因为我们只是选择了某一种日志实现，所以其他的没有引用相关的jar包，所以会报找不到指定的类型的错误。
      Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
      //返回具体的适配器类，这里我们以org.apache.ibatis.logging.log4j2.Log4jImpl为例子
      Log log = candidate.newInstance(LogFactory.class.getName());
      if (log.isDebugEnabled()) {
        //打印出来具体用了哪一个适配器类
        log.debug("Logging initialized using '" + implClass + "' adapter.");
      }
      //给logConstructor赋值为具体的适配器的构造函数
      logConstructor = candidate;
    } catch (Throwable t) {//catch到上面找不到指定的类的异常
      throw new LogException("Error setting Log implementation.  Cause: " + t, t);
    }
  }

}
