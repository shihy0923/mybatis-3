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
import org.apache.ibatis.session.Configuration;

import java.util.Map;
import java.util.Optional;

/**
 * @author Clinton Begin
 *  用于描述动态SQL配置中的<foreach>标签，<foreach>标签配置信息在Mapper解析时会转换为ForEachSqlNode对象。
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  private final ExpressionEvaluator evaluator;
  private final String collectionExpression;
  private final Boolean nullable;
  private final SqlNode contents;
  private final String open;
  private final String close;
  private final String separator;
  private final String item;
  private final String index;
  private final Configuration configuration;

  /**
   * @deprecated Since 3.5.9, use the {@link #ForEachSqlNode(Configuration, SqlNode, String, Boolean, String, String, String, String, String)}.
   */
  @Deprecated
  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this(configuration, contents, collectionExpression, null, index, item, open, close, separator);
  }

  /**
   * @since 3.5.9
   */
  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, Boolean nullable, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.nullable = nullable;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }
 //这个方法走完，原来的语句
//  <select id="findAllDepartmentUseForeach" parameterType="list" resultType="Department">
//  select * from tbl_department
//  where id in
//    <foreach collection="ids" item="id" open="(" close=")" separator=",">
//    #{id}
//    </foreach>
//  </select>
 //
 // 最终解析出来的sql是 select * from tbl_department
 //        where id in
 //         (
 //            #{__frch_id_0}
 //         ,
 //            #{__frch_id_1}
 //         )
  //且context中的bindings，里面放了，key是"__frch_id_0" ，value是我们创建的集合的第一个值，key是“__frch_id_1”，value是我们创建的集合的第二个值。等等。。。。。。。。

  //如果原来的语句是想遍历Map
  //<select id='testMap' parameterType="map" resultType="Department">
  //    select * from tbl_department where id in
  //    <foreach collection="_parameter.entrySet()" index="index" open="(" separator="," close=")" item="item">
  //      #{item.id}
  //    </foreach>
  //  </select>

  //最终解析完的sql语句是
  // select * from tbl_department where id in
  //     (
  //      #{__frch_item_0.id}
  //     ,
  //      #{__frch_item_1.id}
  //     )
  @Override
  public boolean apply(DynamicContext context) {
    //获取用户实参，如list，这个实参是包装过的，是org.apache.ibatis.scripting.xmltags.DynamicContext.ContextMap类型的，key是“_parameter”，value是我们的List类型入参
    Map<String, Object> bindings = context.getBindings();
    //collectionExpression表达式表示的是，我们@Param注解定义的，或者是我们在<foreach>标签里面的collection属性里面定义的值，比如上面两个示例语句中的“ids”和"_parameter.entrySet()"。
    // 这句代码表示的就是从bindings这个总的集合中，找到key表达式符合collectionExpression的value
    //iterable其实就是我们用户实参本身，就是我们自己new 的那个List或Map对象。
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings,
      Optional.ofNullable(nullable).orElseGet(configuration::isNullableOnForEach));
    if (iterable == null || !iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    applyOpen(context);
    int i = 0;
    for (Object o : iterable) {
      DynamicContext oldContext = context;
      if (first || separator == null) {
        context = new PrefixedContext(context, "");
      } else {
        context = new PrefixedContext(context, separator);
      }
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      if (o instanceof Map.Entry) {//这里是处理的是Map类型的，比如下面
        //<select id='testMap' parameterType="map" resultType="Department">
        //    select * from tbl_department where id in
        //    <foreach collection="_parameter.entrySet()" index="index" open="(" separator="," close=")" item="item">
        //      #{item.id}
        //    </foreach>
        //  </select>
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        applyIndex(context, i, uniqueNumber);
        //一般走这里
        applyItem(context, o, uniqueNumber);
      }
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    applyClose(context);
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      //item就是我们在mapper文件中配置的item
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }
  //根据我们在mapper文件中配置的item，以及一些特殊的标识生成我们的key，如__frch_id_0
  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  private static class FilteredDynamicContext extends DynamicContext {
    private final DynamicContext delegate;
    private final int index;
    private final String itemIndex;
    private final String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });

      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  private class PrefixedContext extends DynamicContext {
    private final DynamicContext delegate;
    private final String prefix;
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
