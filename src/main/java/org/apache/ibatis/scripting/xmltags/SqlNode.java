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
package org.apache.ibatis.scripting.xmltags;

/**
 * @author Clinton Begin
 * SqlNode用于描述Mapper SQL配置中的SQL节点(注意，描述节点，不是特指动态SQL)，它是MyBatis框架实现动态SQL的基石。
 * 在使用动态SQL时，我们可以使用<if>、<where>、<trim>等标签，这些标签都对应一种具体的SqlNode实现类
 */
public interface SqlNode {
  //该方法会根据用户传入的实参，解析该SqlNode所记录的动态SQL节点，
  //并调用DynamicContext.appendSql()方法将解析后的SQL片段追加到DynamicContext.sqlBuilder中保存
  //当SQL节点下的所有SqlNode完成解析后，我们就可以从DynamicContext中获取一条动态生成的完整的SQL语句
  boolean apply(DynamicContext context);
}
