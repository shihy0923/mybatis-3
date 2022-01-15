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
 * 用于描述动态SQL中的静态文本内容。生成RawSqlSource对象时候，它的SQL片段就是这个对象表示的.
 * StaticTextSqlNode中使用text字段(String 类型)记录了对应的非动态SQL语句节点， 其
 * apply()方法直接将 text 宇段追加到 DynamicContext叫!Builder字段中。
 *
 * 例子：
 * <select id="findAllUser" parameterType="User" resultType="User">
 *         select * from tbl_user
 *         <where>
 *             <if test="id != null">
 *                 and id = #{id}
 *             </if>
 *             <if test="department.id != null">
 *                 and department_id = #{department.id}
 *             </if>
 *             <if test="name != null and name.trim() != ''">
 *                 and name like concat('%', #{name}, '%')
 *             </if>
 *         </where>
 *     </select>
 * 那么StaticTextSqlNode这个里面，的text属性存放的就是上面的“select * from tbl_user”这段纯文本
 * 以及and id = #{id}  、and department_id = #{department.id}等。。。。
 */
public class StaticTextSqlNode implements SqlNode {
  private final String text;

  public StaticTextSqlNode(String text) {
    this.text = text;
  }

  @Override
  public boolean apply(DynamicContext context) {
    context.appendSql(text);
    return true;
  }

}
