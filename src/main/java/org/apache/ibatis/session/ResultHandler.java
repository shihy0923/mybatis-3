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
package org.apache.ibatis.session;

/**
 * @author Clinton Begin
 * DefaultResultHandler继承了 ResultHandler 接口，它底层使用 list 宇段暂存映射得到的结果对象。
 * 另外，ResultHandler接口还有另一个名 DefaultMapResultHandler 的实现，它底层使用 Map<K, V> mappedResults  暂存结果对象。
 */
public interface ResultHandler<T> {

  void handleResult(ResultContext<? extends T> resultContext);

}
