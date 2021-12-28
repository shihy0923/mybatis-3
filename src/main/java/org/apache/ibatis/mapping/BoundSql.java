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
package org.apache.ibatis.mapping;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An actual SQL String got from an {@link SqlSource} after having processed any dynamic content.
 * The SQL may have SQL placeholders "?" and an list (ordered) of an parameter mappings
 * with the additional information for each parameter (at least the property name of the input object to read
 * the value from).
 * <p>
 * Can also have additional parameters that are created by the dynamic language (for loops, bind...).
 *
 * @author Clinton Begin
 *理解成spring的bean对象
 * 无论是DynamicSqlSource还是RawSqlSource，最终都会统一生成BoundSql对象，其中封装了完整的SQL语句（可能包含“？”占位符）、参数属性集合（parameterMappings集合）以及用户传入的实参（additionalParameters集合）。另外，DynamicSqlSource负责处理动态SQL语句，RawSqlSource负责处理静态SQL语句。除此之外，两者解析SQL语句的时机也不一样，前者的解析时机是在实际执行SQL语句之前，而后者则是在MyBatis初始化时完成SQL语句的解析。
 */
public class BoundSql {
  //记录了SQL语句，该SQL语句中可能含有“?”占位符，是可以直接拿来生成 PreparedStatement 的。
  // 而直接生成 PreparedStatement 的 SQL 语句，占位符都是 ? 吧！再回想一下 SqlSource 中存储的，都是 xml 或者注解中声明的 SQL 吧，
  // 那里面如果有需要传入参数的地方，是通过 #{} 传入的，那是什么时候将#{}解析成“?”的呢？
  //对于RawSqlSource，它里面的一个属性sqlSource，包装了StaticSqlSource，它的构造方法sqlSource = sqlSourceParser.parse(sql, clazz, new HashMap<>());会返回一个StaticSqlSource赋值给属性sqlSource
  //对于DynamicSqlSource，它的getBoundSql方法中，sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());这行代码会返回一个StaticSqlSource对象
  //所以解析的时机，逻辑是先将其他的SqlSource解析成StaticSqlSource，在生成StaticSqlSource对象前，在方法rg.apache.ibatis.builder.SqlSourceBuilder.parse，将 #{} 转换成“?”，然后生成StaticSqlSource对象，将转换好的sql放入StaticSqlSource对象的sql属性中。
  //那么调用org.apache.ibatis.builder.StaticSqlSource.getBoundSql方法，生成BoundSql对象时候，拿到的就是上面解析好的sql语句，#{} 已经被替换成“?”了
  //对于RawSqlSource，创建StaticSqlSource对象是在，Mapper.xml解析时机，因为它的sql是固定的，所以#{} 已经被替换成“?”的时机在Mapper.xml文件被解析的时候就完成了
  //对于DynamicSqlSource，与它相关的StaticSqlSource，是需要调用它的getBoundSql方法，该方法是在org.apache.ibatis.executor.CachingExecutor.query(org.apache.ibatis.mapping.MappedStatement, java.lang.Object, org.apache.ibatis.session.RowBounds, org.apache.ibatis.session.ResultHandler)执行的时候才会调用，因为是动态sql，只有在调用的时候根据具体的参数值，动态确定最终的完整的要执行的sql语句。所以#{} 已经被替换成“?”的时机在具体的SQL调用时候才完成。
  private final String sql;
  //SQL中的参数属性集合,在BoundSql中记录的SQL语句中可能包含“?”占位符，而每个“?”占位符都对应了BoundSql.parameterMappings集合中的一个元素
  private final List<ParameterMapping> parameterMappings;
  //客户端执行SQL语句的时候传入的实际参数
  private final Object parameterObject;
  //空的HashMap集合，之后会复制DynamicContext.bindings集合中的内容,其实也就是用户传入的实参的相关信息
  private final Map<String, Object> additionalParameters;
  //additionalParameters集合对应的MetaObject对象
  private final MetaObject metaParameters;

  public BoundSql(Configuration configuration, String sql, List<ParameterMapping> parameterMappings, Object parameterObject) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.parameterObject = parameterObject;
    this.additionalParameters = new HashMap<>();
    this.metaParameters = configuration.newMetaObject(additionalParameters);
  }

  public String getSql() {
    return sql;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

  public Object getParameterObject() {
    return parameterObject;
  }

  public boolean hasAdditionalParameter(String name) {
    String paramName = new PropertyTokenizer(name).getName();
    return additionalParameters.containsKey(paramName);
  }

  public void setAdditionalParameter(String name, Object value) {
    metaParameters.setValue(name, value);
  }

  public Object getAdditionalParameter(String name) {
    return metaParameters.getValue(name);
  }
}
