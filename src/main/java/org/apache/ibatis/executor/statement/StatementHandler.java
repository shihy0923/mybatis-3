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
package org.apache.ibatis.executor.statement;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * @author Clinton Begin
 * StatementHandler接口是MyBatis的核心接口之一，它完成了MyBatis中最核心的工作，也是后面要介绍的Executor接口实现的基础。
 * StatementHandler接口中的功能很多，例如创建Statement对象，为SQL语句绑定实参，执行select、insert、update、delete等多种类型的SQL语句，批量执行SQL语句，将结果集映射成结果对象。
 *
 * StatementHandler依赖 ParameterHandler 和 ResultSetHandler
 * 完成了MyBatis的核心功能，它控制着参数绑定 、SQL语句执行、结果集映射等一系列核心流程
 */
public interface StatementHandler {

  //从连接中获取一个Statement
  Statement prepare(Connection connection, Integer transactionTimeout)
      throws SQLException;
  //绑定Statement执行时所需的实参
  void parameterize(Statement statement)
      throws SQLException;
  //批量执行SQL语句
  void batch(Statement statement)
      throws SQLException;
  //执行update、insert、delete语句
  int update(Statement statement)
      throws SQLException;
  //执行select语句
  <E> List<E> query(Statement statement, ResultHandler resultHandler)
      throws SQLException;

  <E> Cursor<E> queryCursor(Statement statement)
      throws SQLException;

  BoundSql getBoundSql();
  //获取其中封装的ParameterHandler对象
  ParameterHandler getParameterHandler();

}
