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

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Clinton Begin
 * 映射的语句，每个 <select />、<insert />、<update />、<delete /> 或者@Select，@Update等注解配饰的SQL信息，对应一个 MappedStatement 对象
 * 另外，比较特殊的是，`<selectKey />` 解析后，也会对应一个 MappedStatement 对象
 * https://github.com/YunaiV/mybatis-3/blob/master/src/main/java/org/apache/ibatis/mapping/MappedStatement.java
 * 《Mybatis源码深度解析》
 */
public final class MappedStatement {

  //资源引用的地址，该sql所在的mapper文件，如mapper/department3.xml
  private String resource;
  //Configuration 对象
  private Configuration configuration;
  //在命名空间中唯一的标识符，可以被用来引用这条配置信息，规则：mapper.xml文件的namespace值+sql标签中我们自定义的的id值，如：com.linkedbear.mybatis.mapper.DepartmentMapper.findById
  private String id;
  // 用于设置JDBC中 Statement对象的fetchSize属性，该属性用于指定SQL执行后返回的最大行数。
  private Integer fetchSize;
  //驱动程序等待数据库返回请求结果的秒数，超时将会抛出异常
  private Integer timeout;
  // 参数可选值为STATEMENT、PREPARED或CALLABLE，这会让MyBatis分别使用Statement、PreparedStatement或CallableStatement与数据库交互，默认值为PREPARED。
  private StatementType statementType;
  // 参数可选值为FORWARD_ONLY、SCROLL_SENSITIVE或SCROLL_INSENSITIVE，用于设置ResultSet对象的特征，具体可参考第2章JDBC规范的相关内容。默认未设置，由JDBC驱动决定。
  private ResultSetType resultSetType;
  //MyBatis中的SqlSource用于描述SQL资源，通过前面章节的介绍，我们知道MyBatis可以通过两种方式配置SQL信息，一种是通过@Selelect、@Insert、@Delete、@Update或者
  //@SelectProvider、@InsertProvider、@DeleteProvider、@UpdateProvider等注解；另一种是通过XML配置文件。SqlSource就代表Java注解或者XML文件配置的SQL资源。把它类比成springn的BeanDefinition
  private SqlSource sqlSource;
  //二级缓存的实例，根据Mapper中的<Cache>标签配置信息创建对应的Cache实现
  private Cache cache;
  //该属性已经废弃
  private ParameterMap parameterMap;
  //用于引用通过<resultMap>标签配置的实体属性与数据库字段之间建立的结果集的映射（注意：resultType最终也是会解析成resultMap）
  private List<ResultMap> resultMaps;
  // 用于控制是否刷新缓存。如果将其设置为 true，则任何时候只要语句被调用，都会导致本地缓存和二级缓存被清空，默认值为false。
  private boolean flushCacheRequired;
  // useCache： 是否使用二级缓存。如果将其设置为 true，则会导致本条语句的结果被缓存在MyBatis的二级缓存中，对应<select>标签，该属性的默认值为true。
  private boolean useCache;
  //这个设置仅针对嵌套结果 select语句适用，如果为 true，就是假定嵌套结果包含在一起或分组在一起，这样的话，当返回一个主结果行的时候，就不会发生对前面结果集引用的情况。这就使得在获取嵌套结果集的时候不至于导致内存不够用，默认值为false。
  private boolean resultOrdered;
  //SQL 语句类型
  private SqlCommandType sqlCommandType;
  //主键生成策略，默认为Jdbc3KeyGenerator，即数据库自增主键。当配置了<selectKey>时，使用SelectKeyGenerator生成主键。
  private KeyGenerator keyGenerator;
  //该属性仅对<update>和<insert>标签有用，用于将数据库自增主键或者<insert>标签中<selectKey>标签返回的值填充到实体的属性中，如果有多个属性，则使用逗号分隔。
  private String[] keyProperties;
  //该属性仅对<update>和<insert>标签有用，通过生成的键值设置表中的列名，这个设置仅在某些数据库（例如PostgreSQL）中是必需的，当主键列不是表中的第一列时需要设置，如果有多个字段，则使用逗号分隔
  private String[] keyColumns;
  //<select>标签中通过resultMap属性指定ResultMap是不是嵌套的ResultMap。
  private boolean hasNestedResultMaps;
  //如果配置了 databaseIdProvider，MyBatis会加载所有不带databaseId或匹配当前 databaseId 的语句。
  private String databaseId;
  //用于输出日志
  private Log statementLog;
  // 该属性用于指定LanguageDriver实现，MyBatis中的LanguageDriver用于解析<select|update|insert|delete>标签中的SQL语句，生成SqlSource对象。
  private LanguageDriver lang;
  //这个设置仅对多结果集的情况适用，它将列出语句执行后返回的结果集并每个结果集给一个名称，名称使用逗号分隔。
  private String[] resultSets;

  MappedStatement() {
    // constructor disabled
  }

  public static class Builder {
    private MappedStatement mappedStatement = new MappedStatement();

    public Builder(Configuration configuration, String id, SqlSource sqlSource, SqlCommandType sqlCommandType) {
      mappedStatement.configuration = configuration;
      mappedStatement.id = id;
      mappedStatement.sqlSource = sqlSource;
      mappedStatement.statementType = StatementType.PREPARED;
      mappedStatement.resultSetType = ResultSetType.DEFAULT;
      mappedStatement.parameterMap = new ParameterMap.Builder(configuration, "defaultParameterMap", null, new ArrayList<>()).build();
      mappedStatement.resultMaps = new ArrayList<>();
      mappedStatement.sqlCommandType = sqlCommandType;
      mappedStatement.keyGenerator = configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType) ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
      String logId = id;
      if (configuration.getLogPrefix() != null) {
        logId = configuration.getLogPrefix() + id;
      }
      mappedStatement.statementLog = LogFactory.getLog(logId);
      mappedStatement.lang = configuration.getDefaultScriptingLanguageInstance();
    }

    public Builder resource(String resource) {
      mappedStatement.resource = resource;
      return this;
    }

    public String id() {
      return mappedStatement.id;
    }

    public Builder parameterMap(ParameterMap parameterMap) {
      mappedStatement.parameterMap = parameterMap;
      return this;
    }

    public Builder resultMaps(List<ResultMap> resultMaps) {
      mappedStatement.resultMaps = resultMaps;
      for (ResultMap resultMap : resultMaps) {
        mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
      }
      return this;
    }

    public Builder fetchSize(Integer fetchSize) {
      mappedStatement.fetchSize = fetchSize;
      return this;
    }

    public Builder timeout(Integer timeout) {
      mappedStatement.timeout = timeout;
      return this;
    }

    public Builder statementType(StatementType statementType) {
      mappedStatement.statementType = statementType;
      return this;
    }

    public Builder resultSetType(ResultSetType resultSetType) {
      mappedStatement.resultSetType = resultSetType == null ? ResultSetType.DEFAULT : resultSetType;
      return this;
    }

    public Builder cache(Cache cache) {
      mappedStatement.cache = cache;
      return this;
    }

    public Builder flushCacheRequired(boolean flushCacheRequired) {
      mappedStatement.flushCacheRequired = flushCacheRequired;
      return this;
    }

    public Builder useCache(boolean useCache) {
      mappedStatement.useCache = useCache;
      return this;
    }

    public Builder resultOrdered(boolean resultOrdered) {
      mappedStatement.resultOrdered = resultOrdered;
      return this;
    }

    public Builder keyGenerator(KeyGenerator keyGenerator) {
      mappedStatement.keyGenerator = keyGenerator;
      return this;
    }

    public Builder keyProperty(String keyProperty) {
      mappedStatement.keyProperties = delimitedStringToArray(keyProperty);
      return this;
    }

    public Builder keyColumn(String keyColumn) {
      mappedStatement.keyColumns = delimitedStringToArray(keyColumn);
      return this;
    }

    public Builder databaseId(String databaseId) {
      mappedStatement.databaseId = databaseId;
      return this;
    }

    public Builder lang(LanguageDriver driver) {
      mappedStatement.lang = driver;
      return this;
    }

    public Builder resultSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    /**
     * Resul sets.
     *
     * @param resultSet
     *          the result set
     * @return the builder
     * @deprecated Use {@link #resultSets}
     */
    @Deprecated
    public Builder resulSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    public MappedStatement build() {
      assert mappedStatement.configuration != null;
      assert mappedStatement.id != null;
      assert mappedStatement.sqlSource != null;
      assert mappedStatement.lang != null;
      mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
      return mappedStatement;
    }
  }

  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  public SqlCommandType getSqlCommandType() {
    return sqlCommandType;
  }

  public String getResource() {
    return resource;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public Integer getFetchSize() {
    return fetchSize;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public StatementType getStatementType() {
    return statementType;
  }

  public ResultSetType getResultSetType() {
    return resultSetType;
  }

  public SqlSource getSqlSource() {
    return sqlSource;
  }

  public ParameterMap getParameterMap() {
    return parameterMap;
  }

  public List<ResultMap> getResultMaps() {
    return resultMaps;
  }

  public Cache getCache() {
    return cache;
  }

  public boolean isFlushCacheRequired() {
    return flushCacheRequired;
  }

  public boolean isUseCache() {
    return useCache;
  }

  public boolean isResultOrdered() {
    return resultOrdered;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  public String[] getKeyProperties() {
    return keyProperties;
  }

  public String[] getKeyColumns() {
    return keyColumns;
  }

  public Log getStatementLog() {
    return statementLog;
  }

  public LanguageDriver getLang() {
    return lang;
  }

  public String[] getResultSets() {
    return resultSets;
  }

  /**
   * Gets the resul sets.
   *
   * @return the resul sets
   * @deprecated Use {@link #getResultSets()}
   */
  @Deprecated
  public String[] getResulSets() {
    return resultSets;
  }

  public BoundSql getBoundSql(Object parameterObject) {
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings == null || parameterMappings.isEmpty()) {
      boundSql = new BoundSql(configuration, boundSql.getSql(), parameterMap.getParameterMappings(), parameterObject);
    }

    // check for nested result maps in parameter mappings (issue #30)
    for (ParameterMapping pm : boundSql.getParameterMappings()) {
      String rmId = pm.getResultMapId();
      if (rmId != null) {
        ResultMap rm = configuration.getResultMap(rmId);
        if (rm != null) {
          hasNestedResultMaps |= rm.hasNestedResultMaps();
        }
      }
    }

    return boundSql;
  }

  private static String[] delimitedStringToArray(String in) {
    if (in == null || in.trim().length() == 0) {
      return null;
    } else {
      return in.split(",");
    }
  }

}
