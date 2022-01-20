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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 * MetaClass 通过Reflector和PropertyTokenizer组合使用， 实现了对复杂的属性表达式的解
 * 析，并实现了获取指定属性描述信息的功能。
 */
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

  //MetaClass 的构造方法是使用 private 修饰的
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    //创建Reflector对象，详情查看org.apache.ibatis.reflection.ReflectorFactory.findForClass方法
    this.reflector = reflectorFactory.findForClass(type);
  }

  //使用静态方法创建 MetaClass 对象
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  public MetaClass metaClassForProperty(String name) {
    //查找指定属性对应的Class
    Class<?> propType = reflector.getGetterType(name);
    //为该属性创建对应的MetaClass对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  //MetaClass中比较重要的是findProperty()方法，它是通过调用 MetaClass.buildProperty()方法
  //实现的 ，而 buildProperty()方法会通过 PropertyTokenizer 解析复杂的属性表达式
  public String findProperty(String name) {
    //委托给 buildProperty()方法实现
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    //获取表达式所表示的属性的类型
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    //获取属性类型
    Class<?> type = reflector.getGetterType(prop.getName());
    //该表达式中是否使用 ”［］” 指定了下标，且是Collection子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      //通过 TypeParameterResolver 工具类解析属性的类型
      Type returnType = getGenericGetterType(prop.getName());
      //针对ParameterizedType 进行处理 ， 即针对泛型集合类型进行处理
      if (returnType instanceof ParameterizedType) {
        //获取实际的类型参数
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          //泛型 的 类 型
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        declaredMethod.setAccessible(true);
        Method method = (Method) declaredMethod.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        Field field = (Field) declaredField.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  public boolean hasSetter(String name) {
    //解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {//存在待处理的子表达式
      //PropertyTokenizer.name 指定的属性有 setter方法，才能处理子表达式
      if (reflector.hasSetter(prop.getName())) {
        //为该属性创建对应的MetaClass对象,就是在这个里面创建propertyName对应的Reflector对象
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());//递归入口
      } else {
        return false;
      }
    } else {//递归出口
      return reflector.hasSetter(prop.getName());
    }
  }

  public boolean hasGetter(String name) {
    //解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {//存在待处理的子表达式
      //PropertyTokenizer.name 指定的属性有 setter方法，才能处理子表达式
      if (reflector.hasGetter(prop.getName())) {
        //创建MetaClass对象
        //注意 ， 这里的 rnetaClassForProperty(PropertyTokenizer ）方法是上面介绍
        //的 rnetaClassForProperty(String ）方法的重载，但两者逻辑相差有点大
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());//递归入口
      } else {
        return false;
      }
    } else {//递归出口
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }
  //往reflectorFactory里面注册，name这个字符串表示的表达式所表达的属性的Class类型所对应的Reflector对象
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    //解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //是否还有子表达式
    if (prop.hasNext()) {
      //查找PropertyTokenizer.name对应的属性
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);//追加属性名
        builder.append(".");
        //为该属性创建对应的MetaClass对象,就是在这个里面创建propertyName对应的Reflector对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        //递归解析PropertyTokenizer.children 字段，并将解析结果添加到builder中保存
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {//递归出口
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    //builder表示解析哪些属性,比如richType.richProperty这个，解析到了RichType这个类里面的richType属性，以及richType属性对应的RichType类里面的richProperty属性
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
