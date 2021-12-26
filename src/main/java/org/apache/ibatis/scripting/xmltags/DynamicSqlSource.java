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

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * 负责解析动态SQL语句，也是最常用的SqlSource实现之一。
 * SqlNode中使用了组合模式，形成了一个树状结构，DynamicSqlSource中使用rootSqlNode字段（SqlNode类型）记录了待解析的SqlNode树的根节点。
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    //创建DynamicContext对象，parameterObject是用户传入的实参
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    //通过调用rootSqlNode.apply方法调用整个树形结构中全部的SqlNode.apply()方法，
    //每个SqlNode的apply()方法都将解析得到的SQL语句片段追加到context中，最终通过context的getSql()方法得到完整的SQL语句。
    rootSqlNode.apply(context);
    //创建SqlSourceBuilder解析参数属性，并将SQL语句中的“#{}”占位符替换成“?”占位符
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    //创建BoundSql对象，
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    //并将DynamicContext.bindings中的参数信息复制到additionalParameters属性中
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
