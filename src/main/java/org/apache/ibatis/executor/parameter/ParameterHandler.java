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
package org.apache.ibatis.executor.parameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A parameter handler sets the parameters of the {@code PreparedStatement}.
 *
 * @author Clinton
 * ParameterHandler接口提供了唯一一个实现类，DefaultParameterHandler
 */
public interface ParameterHandler {

  Object getParameterObject();

  //在ParameterHandler接口中只定义了一个setParameters()方法，该方法主要负责调用PreparedStatement.set()方法为SQL语句绑定实参。MyBatis只为ParameterHandler接口提供了唯一一个实现类
  void setParameters(PreparedStatement ps) throws SQLException;

}
