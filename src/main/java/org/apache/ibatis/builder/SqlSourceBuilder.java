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

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Clinton Begin
 * SqlSourceBuilder主要完成了两方面的操作，一方面是解析SQL语句中的“#{}”占位符中定义的属性，
 * 格式类似于#{__frc_item_0, javaType=int, jdbcType=NUMERIC, typeHandler=MyTypeHandler}，另一方面是将SQL语句中的“#{}”占位符替换成“？”占位符。
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }
  //第一个参数是xml中配置的sql语句
  //第二个参数是用户传入的实参类型
  //第三个参数记录了形参与实参的对应关系，其实就是经过SqlNode.apply()方法处理后的DynamicContext.bindings对象（这是对于DynamicSqlSource讲的，而StaticSqlSource，直接给的空Map）。貌似没什么卵用。。。。。。。。应该可以忽略吧。。。。。
  //该方法的作用，主要是根据“#{}”占位符中我们配置的参数的属性和用户传入的实参的类型，生成参数对应的ParameterMapping集合，有几个“#{}”占位符就会生成几个ParameterMapping对象。以及将“#{}”占位符替换为“?”属性。所以，
  //这就可以理解DynamicSqlSource生成StaticSqlSource的时机为啥是在调用时，而RawSqlSource在解析Mapper.xml就可以生成StaticSqlSource了，那是因为，动态SQL到底有几个“#{}”占位符，不在运行的时候咱不知道到底有几个呀。而静态SQL,有几个“#{}”占位符是固定滴。
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    //创建ParameterMappingTokenHandler对象，他是解析“#{}”占位符的参数的属性以及替换占位符的核心
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    //使用GenericTokenParser与ParameterMappingTokenHandler配合解析“#{}”占位符
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    String sql;
    if (configuration.isShrinkWhitespacesInSql()) {
      sql = parser.parse(removeExtraWhitespaces(originalSql));
    } else {
      //走到这一步后，sql语句被解析成了带“?”的预编译语句，且handler中的parameterMappings属性，也根据“?”的顺序，放入了List集合中
      sql = parser.parse(originalSql);
    }
    //创建StaticSqlSource，其中封装了占位符被替换成“?”的SQL语句以及参数对应的ParameterMapping集合
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  public static String removeExtraWhitespaces(String original) {
    StringTokenizer tokenizer = new StringTokenizer(original);
    StringBuilder builder = new StringBuilder();
    boolean hasMoreTokens = tokenizer.hasMoreTokens();
    while (hasMoreTokens) {
      builder.append(tokenizer.nextToken());
      hasMoreTokens = tokenizer.hasMoreTokens();
      if (hasMoreTokens) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {
    //用于记录解析得到的ParameterMapping集合
    private final List<ParameterMapping> parameterMappings = new ArrayList<>();
    //用户传入的实参的类型
    private final Class<?> parameterType;
    //DynamicContext.bindings集合对应的MetaObject对象
    private final MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }
    //解析参数属性，并将解析得到的ParameterMapping对象添加到parameterMappings集合中
    //例如：SQL语句是insert into tbl_department (id, name, tel) values (#{id}, #{name}, #{tel})
    //這裡的content的值是#{id}中的“id”，#{name}中的“name”这些。。
    @Override
    public String handleToken(String content) {
      parameterMappings.add(buildParameterMapping(content));
      //然后直接返回占位符“？”，用于替换掉#{id}, #{name}, #{tel}这些玩意儿
      return "?";
    }
    //负责解析参数属性，最主要的作用就是生成ParameterMapping对象
    private ParameterMapping buildParameterMapping(String content) {
      //将我们在“#{}”中配置的内容，解析为Map，如#{name,jdbcType=VARCHAR}，解析为Map中的两个元素，key为“property” value为“name” 和key为“jdbcType”  value为“VARCHAR”
      Map<String, String> propertiesMap = parseParameterMapping(content);
      //获取参数名称
      String property = propertiesMap.get("property");
      Class<?> propertyType;
      //确定参数的javaType属性
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        propertyType = Object.class;
      } else {
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        if (metaClass.hasGetter(property)) {
          //一般走这里获取
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }
      //创建ParameterMapping的建造者，并设置ParameterMapping的相关配置
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));//获取我们自定义的jdbcType
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      //创建ParameterMapping对象，如果没有指定TypeHandler，则会在build()方法中，根据javaType和jdbcType从TypeHandlerRegistry中获取对应的TypeHandler对象。
      //最终也还是从typeHandlerRegistry中根据javaType和jdbcType获取
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
