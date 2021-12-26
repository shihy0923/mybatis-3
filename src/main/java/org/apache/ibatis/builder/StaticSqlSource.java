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
package org.apache.ibatis.builder;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * @author Clinton Begin
 * 用于描述ProviderSqlSource、DynamicSqlSource及RawSqlSource解析后得到的静态SQL资源。
 * 无论是Java注解还是XML文件配置的SQL信息，在Mapper调用时都会根据用户传入的参数将Mapper配置转换为StaticSqlSource类。
 *
 */
public class StaticSqlSource implements SqlSource {
  //Mapper.xml解析后的sql内容，如：insert into tbl_department (id, name, tel) values (?, ?, ?)或者select * from tbl_department
  //所以《Mybatis技术内幕》说：StaticSqlSource中记录的SQL语句中可能含有“？”占位符，但是可以直接提交给数据库执行；
  private final String sql;
  //参数信息
  private final List<ParameterMapping> parameterMappings;
  private final Configuration configuration;

  public StaticSqlSource(Configuration configuration, String sql) {
    this(configuration, sql, null);
  }

  public StaticSqlSource(Configuration configuration, String sql, List<ParameterMapping> parameterMappings) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.configuration = configuration;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    return new BoundSql(configuration, sql, parameterMappings, parameterObject);
  }

}
