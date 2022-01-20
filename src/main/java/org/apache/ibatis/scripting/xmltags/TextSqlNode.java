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

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

import java.util.regex.Pattern;

/**
 * @author Clinton Begin
 * TextSqlNode 表示的是包含“${}”占位符的动态 SQL 节点。
 * 该类与StaticTextSqlNode类不同的是，当静态文本中包含${}占位符时，说明${}需要在Mapper调用时将${}替换为具体的参数值。因此，
 * 使用TextSqlNode类来描述。
 * 例子：
 * <update id="updateDepartmentByMap" parameterType="map">
 * update tbl_department
 * <foreach collection="beanMap" index="key" item="value" open="set " separator=",">
 * <if test="value != null">
 * ${key} = #{value}
 * </if>
 * </foreach>
 * where id = #{id}
 * </update>
 * 那么TextSqlNode这个对象的 text属性的内容就是"${key} = #{value}"
 */
public class TextSqlNode implements SqlNode {
  private final String text;
  private final Pattern injectionFilter;

  public TextSqlNode(String text) {
    this(text, null);
  }

  public TextSqlNode(String text, Pattern injectionFilter) {
    this.text = text;
    this.injectionFilter = injectionFilter;
  }

  public boolean isDynamic() {
    //DynamicCheckerTokenParser继承了TokenHandler接口,看他的handleToken()方法
    DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
    GenericTokenParser parser = createParser(checker);
    parser.parse(text);
    return checker.isDynamic();
  }

  //使用 GenericTokenParser 解析“${}”占位符，并直接替换成用户给定的实际参数值
  @Override
  public boolean apply(DynamicContext context) {
    //创建 GenericTokenParser解析器， GenericTokenParser介绍过了，这里重点来看BindingTokenParser的功能
    GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter));
    //将解析后的 SQL 片段添加到 DynamicContext 中
    context.appendSql(parser.parse(text));
    return true;
  }

  private GenericTokenParser createParser(TokenHandler handler) {
    //解析的是”${}”占位符
    return new GenericTokenParser("${", "}", handler);
  }

  //BindingTokenParser 是 TextSq!Node 中定义的内部类，继承了 TokenHandler 接口，它的 主要
  //功能是根据 DynamicContext.bindings 集合中的信息解析 SQL 语句节点中的“ ${}”占位符。
  //BindingTokenParser.context字段指向了对应的DynamicContext对象
  private static class BindingTokenParser implements TokenHandler {

    private DynamicContext context;
    private Pattern injectionFilter;

    public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
      this.context = context;
      this.injectionFilter = injectionFilter;
    }

    //这里通过一个示例简单描述该解析过程，假设用户传入的实参中包含了“ id->1”的对应关
    //系，在 TextSqlNode.apply()方法解析时，会将“ id=${id｝”中的“${id｝”占位符直接替换成“1”
    //得到“ id=1”， 并将其追加到 DynamicContext中
    @Override
    public String handleToken(String content) {
      //获取用户提供的实参
      Object parameter = context.getBindings().get("_parameter");
      if (parameter == null) {
        context.getBindings().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
        context.getBindings().put("value", parameter);
      }
      //通过OGNL解析content的值
      Object value = OgnlCache.getValue(content, context.getBindings());
      String srtValue = value == null ? "" : String.valueOf(value); // issue #274 return "" instead of "null"
      //检测合法性
      checkInjection(srtValue);
      return srtValue;
    }

    private void checkInjection(String value) {
      if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
        throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
      }
    }
  }

  private static class DynamicCheckerTokenParser implements TokenHandler {

    private boolean isDynamic;

    public DynamicCheckerTokenParser() {
      // Prevent Synthetic Access
    }

    public boolean isDynamic() {
      return isDynamic;
    }

    @Override
    public String handleToken(String content) {
      //标记该节点为动态节点
      this.isDynamic = true;
      return null;
    }
  }

}
