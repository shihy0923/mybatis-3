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

/**
 * @author Clinton Begin
 * 第三 方日志组件都有各自的 Log 级别，且都有所不同，例如 java. uti l.logging
 * 提供了 All 、 F卧而ST 、 FINER、 FINE 、 CONFIG 、卧.WO 、 W成NING 等 9 种级别，而 Log句2 则
 * 只 有 trace 、 debug 、 info 、 warn 、 eηor 、 fatal 这 6 种日志级别。 MyBatis 统一提供了 trace 、 debug 、
 * warn 、 eηor 四个级别，这基本与主流日志框架的日志级别类似，可以满足绝大多数场景的日志需求
 */
public interface Log {

  boolean isDebugEnabled();

  boolean isTraceEnabled();

  void error(String s, Throwable e);

  void error(String s);

  void debug(String s);

  void trace(String s);

  void warn(String s);

}
