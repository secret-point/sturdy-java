/*
 * Copyright (C) 2009 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.base;

import java.util.Arrays;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableMap;

/**
 * Tests for {@link Objects#toStringHelper(Object)}.
 *
 * @author Jason Lee
 */
@GwtCompatible
public class ToStringHelperTest extends TestCase {

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testConstructor_instance() {
    String toTest = Objects.toStringHelper(this).toString();
    assertEquals("ToStringHelperTest{}", toTest);
  }

  public void testConstructorLenient_instance() {
    String toTest = Objects.toStringHelper(this).toString();
    assertTrue(toTest, toTest.matches(".+\\{\\}"));
  }

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testConstructor_innerClass() {
    String toTest = Objects.toStringHelper(new TestClass()).toString();
    assertEquals("TestClass{}", toTest);
  }

  public void testConstructorLenient_innerClass() {
    String toTest = Objects.toStringHelper(new TestClass()).toString();
    assertTrue(toTest, toTest.matches(".+\\{\\}"));
  }

  public void testConstructor_anonymousClass() {
    String toTest = Objects.toStringHelper(new Object() {}).toString();
    assertTrue(toTest, toTest.matches("[0-9]+\\{\\}"));
  }

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testConstructor_classObject() {
    String toTest = Objects.toStringHelper(TestClass.class).toString();
    assertEquals("TestClass{}", toTest);
  }

  public void testConstructorLenient_classObject() {
    String toTest = Objects.toStringHelper(TestClass.class).toString();
    assertTrue(toTest.matches(".+\\{\\}"));
  }

  public void testConstructor_stringObject() {
    String toTest = Objects.toStringHelper("FooBar").toString();
    assertEquals("FooBar{}", toTest);
  }

  // all remaining test are on an inner class with various fields
  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testToString_oneField() {
    String toTest = Objects.toStringHelper(new TestClass())
        .add("field1", "Hello")
        .toString();
    assertEquals("TestClass{field1=Hello}", toTest);
  }

  public void testToStringLenient_oneField() {
    String toTest = Objects.toStringHelper(new TestClass())
        .add("field1", "Hello")
        .toString();
    assertTrue(toTest, toTest.matches(".+\\{field1\\=Hello\\}"));
  }

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testToString_complexFields() {

    Map<String, Integer> map = ImmutableMap.<String, Integer>builder()
        .put("abc", 1)
        .put("def", 2)
        .put("ghi", 3)
        .build();
    String toTest = Objects.toStringHelper(new TestClass())
        .add("field1", "This is string.")
        .add("field2", Arrays.asList("abc", "def", "ghi"))
        .add("field3", map)
        .toString();
    final String expected = "TestClass{"
        + "field1=This is string., field2=[abc, def, ghi], field3={abc=1, def=2, ghi=3}}";

    assertEquals(expected, toTest);
  }

  public void testToStringLenient_complexFields() {

    Map<String, Integer> map = ImmutableMap.<String, Integer>builder()
        .put("abc", 1)
        .put("def", 2)
        .put("ghi", 3)
        .build();
    String toTest = Objects.toStringHelper(new TestClass())
        .add("field1", "This is string.")
        .add("field2", Arrays.asList("abc", "def", "ghi"))
        .add("field3", map)
        .toString();
    final String expectedRegex = ".+\\{"
        + "field1\\=This is string\\., "
        + "field2\\=\\[abc, def, ghi\\], "
        + "field3=\\{abc\\=1, def\\=2, ghi\\=3\\}\\}";

    assertTrue(toTest, toTest.matches(expectedRegex));
  }

  public void testToString_addWithNullName() {
    Objects.ToStringHelper helper = Objects.toStringHelper(new TestClass());
    try {
      helper.add(null, "Hello");
      fail("No exception was thrown.");
    } catch (NullPointerException expected) {
    }
  }

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testToString_addWithNullValue() {
    final String result = Objects.toStringHelper(new TestClass())
        .add("Hello", null)
        .toString();

    assertEquals("TestClass{Hello=null}", result);
  }

  public void testToStringLenient_addWithNullValue() {
    final String result = Objects.toStringHelper(new TestClass())
        .add("Hello", null)
        .toString();
    assertTrue(result, result.matches(".+\\{Hello\\=null\\}"));
  }

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testToString_ToStringTwice() {
    Objects.ToStringHelper helper = Objects.toStringHelper(new TestClass())
        .add("field1", 1)
        .addValue("value1")
        .add("field2", "value2");
    final String expected = "TestClass{field1=1, value1, field2=value2}";

    assertEquals(expected, helper.toString());
    // Call toString again
    assertEquals(expected, helper.toString());

    // Make sure the cached value is reset when we modify the helper at all
    final String expected2 = "TestClass{field1=1, value1, field2=value2, 2}";
    helper.addValue(2);
    assertEquals(expected2, helper.toString());
  }

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testToString_addValue() {
    String toTest = Objects.toStringHelper(new TestClass())
        .add("field1", 1)
        .addValue("value1")
        .add("field2", "value2")
        .addValue(2)
        .toString();
    final String expected = "TestClass{field1=1, value1, field2=value2, 2}";

    assertEquals(expected, toTest);
  }

  public void testToStringLenient_addValue() {
    String toTest = Objects.toStringHelper(new TestClass())
        .add("field1", 1)
        .addValue("value1")
        .add("field2", "value2")
        .addValue(2)
        .toString();
    final String expected = ".+\\{field1\\=1, value1, field2\\=value2, 2\\}";

    assertTrue(toTest, toTest.matches(expected));
  }

  @GwtIncompatible("Class names are obfuscated in GWT")
  public void testToString_addValueWithNullValue() {
    final String result = Objects.toStringHelper(new TestClass())
        .addValue(null)
        .addValue("Hello")
        .addValue(null)
        .toString();
    final String expected = "TestClass{null, Hello, null}";

    assertEquals(expected, result);
  }

  public void testToStringLenient_addValueWithNullValue() {
    final String result = Objects.toStringHelper(new TestClass())
        .addValue(null)
        .addValue("Hello")
        .addValue(null)
        .toString();
    final String expected = ".+\\{null, Hello, null\\}";

    assertTrue(result, result.matches(expected));
  }

  /**
   * Test class for testing formatting of inner classes.
   */
  private static class TestClass {}

}
