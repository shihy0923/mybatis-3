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
package org.apache.ibatis.scripting.defaults;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.session.Configuration;

import java.util.HashMap;

/**
 * Static SqlSource. It is faster than {@link DynamicSqlSource} because mappings are
 * calculated during startup.
 *
 * @since 3.2.0
 * @author Eduardo Macarron
 * RawSqlSource是SqlSource的另一个实现，其逻辑与DynamicSql
 * Source类似，但是执行时机不一样，处理的SQL语句类型也不一样。XMLScriptBuilder.parseDynamicTags()方法时提到过，如果节点只包含“#{}”占位符，而不包含动态SQL节点或未解析的“${}”占位符的话，
 * 则不是动态SQL语句，会创建相应的StaticTextSqlNode对象。在XMLScriptBuilder.parseScriptNode()方法中会判断整个SQL节点是否为动态的，如果不是动态的SQL节点，则创建相应的RawSqlSource对象。
 */
public class RawSqlSource implements SqlSource {

  //是一个StaticSqlSource对象
  private final SqlSource sqlSource;

  //RawSqlSource在构造方法中首先会调用getSql()方法，其中通过调用SqlNode.apply()方法完成SQL语句的拼装和初步处理；之后会使用
  //SqlSourceBuilder完成占位符的替换和ParameterMapping集合的创建，并返回StaticSqlSource对象。
  public RawSqlSource(Configuration configuration, SqlNode rootSqlNode, Class<?> parameterType) {
    this(configuration, getSql(configuration, rootSqlNode), parameterType);
  }

  public RawSqlSource(Configuration configuration, String sql, Class<?> parameterType) {
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> clazz = parameterType == null ? Object.class : parameterType;
    //返回一个StaticSqlSource对象
    sqlSource = sqlSourceParser.parse(sql, clazz, new HashMap<>());
  }

  private static String getSql(Configuration configuration, SqlNode rootSqlNode) {
    DynamicContext context = new DynamicContext(configuration, null);
    rootSqlNode.apply(context);
    return context.getSql();
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    return sqlSource.getBoundSql(parameterObject);
  }

}
