/*
 * Copyright (C) 2012 The Guava Authors
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

package com.google.common.reflect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;

import junit.framework.TestCase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.Collections;

/**
 * Unit tests for {@link Invokable}.
 *
 * @author Ben Yu
 */
public class InvokableTest extends TestCase {

  public void testConstructor_returnType() throws Exception {
    assertEquals(Prepender.class,
        Prepender.constructor().getReturnType().getType());
  }

  public void testConstructor_exceptionTypes() throws Exception {
    assertEquals(ImmutableList.of(TypeToken.of(NullPointerException.class)),
        Prepender.constructor(String.class, int.class).getExceptionTypes());
  }

  public void testConstructor_typeParameters() throws Exception {
    TypeVariable<?>[] variables =
        Prepender.constructor().getTypeParameters();
    assertEquals(1, variables.length);
    assertEquals("A", variables[0].getName());
  }

  public void testConstructor_parameters() throws Exception {
    Invokable<?, Prepender> delegate = Prepender.constructor(String.class, int.class);
    ImmutableList<Parameter<?>> parameters = delegate.getParameters();
    assertEquals(2, parameters.size());
    assertEquals(String.class, parameters.get(0).getType().getType());
    assertTrue(parameters.get(0).isAnnotationPresent(NotBlank.class));
    assertEquals(int.class, parameters.get(1).getType().getType());
    assertFalse(parameters.get(1).isAnnotationPresent(NotBlank.class));
    new EqualsTester()
        .addEqualityGroup(parameters.get(0))
        .addEqualityGroup(parameters.get(1))
        .testEquals();
  }

  public void testConstructor_call() throws Exception {
    Invokable<?, Prepender> delegate = Prepender.constructor(String.class, int.class);
    Prepender prepender = delegate.invoke(null, "a", 1);
    assertEquals("a", prepender.prefix);
    assertEquals(1, prepender.times);
  }

  public void testConstructor_returning() throws Exception {
    Invokable<?, Prepender> delegate = Prepender.constructor(String.class, int.class)
        .returning(Prepender.class);
    Prepender prepender = delegate.invoke(null, "a", 1);
    assertEquals("a", prepender.prefix);
    assertEquals(1, prepender.times);
  }

  public void testConstructor_invalidReturning() throws Exception {
    Invokable<?, Prepender> delegate = Prepender.constructor(String.class, int.class);
    try {
      delegate.returning(SubPrepender.class);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public void testStaticMethod_returnType() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", String.class, Iterable.class);
    assertEquals(new TypeToken<Iterable<String>>() {}, delegate.getReturnType());
  }

  public void testStaticMethod_exceptionTypes() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", String.class, Iterable.class);
    assertEquals(ImmutableList.of(), delegate.getExceptionTypes());
  }

  public void testStaticMethod_typeParameters() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", String.class, Iterable.class);
    TypeVariable<?>[] variables = delegate.getTypeParameters();
    assertEquals(1, variables.length);
    assertEquals("T", variables[0].getName());
  }

  public void testStaticMethod_parameters() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", String.class, Iterable.class);
    ImmutableList<Parameter<?>> parameters = delegate.getParameters();
    assertEquals(2, parameters.size());
    assertEquals(String.class, parameters.get(0).getType().getType());
    assertTrue(parameters.get(0).isAnnotationPresent(NotBlank.class));
    assertEquals(new TypeToken<Iterable<String>>() {}, parameters.get(1).getType());
    assertFalse(parameters.get(1).isAnnotationPresent(NotBlank.class));
    new EqualsTester()
        .addEqualityGroup(parameters.get(0))
        .addEqualityGroup(parameters.get(1))
        .testEquals();
  }

  public void testStaticMethod_call() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", String.class, Iterable.class);
    @SuppressWarnings("unchecked") // prepend() returns Iterable<String>
    Iterable<String> result = (Iterable<String>)
        delegate.invoke(null, "a", ImmutableList.of("b", "c"));
    assertEquals(ImmutableList.of("a", "b", "c"), ImmutableList.copyOf(result));
  }

  public void testStaticMethod_returning() throws Exception {
    Invokable<Object, Iterable<String>> delegate = Prepender.method(
            "prepend", String.class, Iterable.class)
        .returning(new TypeToken<Iterable<String>>() {});
    assertEquals(new TypeToken<Iterable<String>>() {}, delegate.getReturnType());
    Iterable<String> result = delegate.invoke(null, "a", ImmutableList.of("b", "c"));
    assertEquals(ImmutableList.of("a", "b", "c"), ImmutableList.copyOf(result));
  }

  public void testStaticMethod_returningRawType() throws Exception {
    @SuppressWarnings("rawtypes") // the purpose is to test raw type
    Invokable<Object, Iterable> delegate = Prepender.method(
            "prepend", String.class, Iterable.class)
        .returning(Iterable.class);
    assertEquals(new TypeToken<Iterable<String>>() {}, delegate.getReturnType());
    @SuppressWarnings("unchecked") // prepend() returns Iterable<String>
    Iterable<String> result = delegate.invoke(null, "a", ImmutableList.of("b", "c"));
    assertEquals(ImmutableList.of("a", "b", "c"), ImmutableList.copyOf(result));
  }

  public void testStaticMethod_invalidReturning() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", String.class, Iterable.class);
    try {
      delegate.returning(new TypeToken<Iterable<Integer>>() {});
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public void testInstanceMethod_returnType() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", Iterable.class);
    assertEquals(new TypeToken<Iterable<String>>() {}, delegate.getReturnType());
  }

  public void testInstanceMethod_exceptionTypes() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", Iterable.class);
    assertEquals(
        ImmutableList.of(
            TypeToken.of(IllegalArgumentException.class),
            TypeToken.of(NullPointerException.class)),
        delegate.getExceptionTypes());
  }

  public void testInstanceMethod_typeParameters() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", Iterable.class);
    assertEquals(0, delegate.getTypeParameters().length);
  }

  public void testInstanceMethod_parameters() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", Iterable.class);
    ImmutableList<Parameter<?>> parameters = delegate.getParameters();
    assertEquals(1, parameters.size());
    assertEquals(new TypeToken<Iterable<String>>() {}, parameters.get(0).getType());
    assertEquals(0, parameters.get(0).getAnnotations().length);
    new EqualsTester()
        .addEqualityGroup(parameters.get(0))
        .testEquals();
  }

  public void testInstanceMethod_call() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", Iterable.class);
    @SuppressWarnings("unchecked") // prepend() returns Iterable<String>
    Iterable<String> result = (Iterable<String>)
        delegate.invoke(new Prepender("a", 2), ImmutableList.of("b", "c"));
    assertEquals(ImmutableList.of("a", "a", "b", "c"), ImmutableList.copyOf(result));
  }

  public void testInstanceMethod_returning() throws Exception {
    Invokable<Object, Iterable<String>> delegate = Prepender.method(
            "prepend", Iterable.class)
        .returning(new TypeToken<Iterable<String>>() {});
    assertEquals(new TypeToken<Iterable<String>>() {}, delegate.getReturnType());
    Iterable<String> result = delegate.invoke(new Prepender("a", 2), ImmutableList.of("b", "c"));
    assertEquals(ImmutableList.of("a", "a", "b", "c"), ImmutableList.copyOf(result));
  }

  public void testInstanceMethod_returningRawType() throws Exception {
    @SuppressWarnings("rawtypes") // the purpose is to test raw type
    Invokable<Object, Iterable> delegate = Prepender.method("prepend", Iterable.class)
        .returning(Iterable.class);
    assertEquals(new TypeToken<Iterable<String>>() {}, delegate.getReturnType());
    @SuppressWarnings("unchecked") // prepend() returns Iterable<String>
    Iterable<String> result = delegate.invoke(
        new Prepender("a", 2), ImmutableList.of("b", "c"));
    assertEquals(ImmutableList.of("a", "a", "b", "c"), ImmutableList.copyOf(result));
  }

  public void testInstanceMethod_invalidReturning() throws Exception {
    Invokable<Object, Object> delegate = Prepender.method("prepend", Iterable.class);
    try {
      delegate.returning(new TypeToken<Iterable<Integer>>() {});
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public void testPrivateInstanceMethod_isOverridable() throws Exception {
    Invokable<Object, ?> delegate = Prepender.method("privateMethod");
    assertTrue(delegate.isPrivate());
    assertFalse(delegate.isOverridable());
  }

  public void testPrivateFinalInstanceMethod_isOverridable() throws Exception {
    Invokable<Object, ?> delegate = Prepender.method("privateFinalMethod");
    assertTrue(delegate.isPrivate());
    assertTrue(delegate.isFinal());
    assertFalse(delegate.isOverridable());
  }

  public void testStaticMethod_isOverridable() throws Exception {
    Invokable<Object, ?> delegate = Prepender.method("staticMethod");
    assertTrue(delegate.isStatic());
    assertFalse(delegate.isOverridable());
  }

  public void testStaticFinalMethod_isFinal() throws Exception {
    Invokable<Object, ?> delegate = Prepender.method("staticFinalMethod");
    assertTrue(delegate.isStatic());
    assertTrue(delegate.isFinal());
    assertFalse(delegate.isOverridable());
  }

  static class Foo {}

  public void testConstructor_isOverridablel() throws Exception {
    Invokable<?, ?> delegate = Invokable.from(Foo.class.getDeclaredConstructor());
    assertFalse(delegate.isOverridable());
  }

  private static final class FinalClass {
    @SuppressWarnings("unused") // used by reflection
    void notFinalMethod() {}
  }
  public void testNonFinalMethodInFinalClass_isOverridable() throws Exception {
    Invokable<Object, ?> delegate = Invokable.from(
        FinalClass.class.getDeclaredMethod("notFinalMethod"));
    assertFalse(delegate.isOverridable());
  }

  public void testEquals() throws Exception {
    new EqualsTester()
        .addEqualityGroup(Prepender.constructor(), Prepender.constructor())
        .addEqualityGroup(Prepender.constructor(String.class, int.class))
        .addEqualityGroup(Prepender.method("privateMethod"), Prepender.method("privateMethod"))
        .addEqualityGroup(Prepender.method("privateFinalMethod"))
        .testEquals();
  }

  public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(Invokable.class);
    new NullPointerTester().testAllPublicInstanceMethods(Prepender.method("staticMethod"));
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface NotBlank {}

  /** Class for testing construcrtor, static method and instance method. */
  @SuppressWarnings("unused") // most are called by reflection
  private static class Prepender {

    private final String prefix;
    private final int times;

    Prepender(@NotBlank String prefix, int times) throws NullPointerException {
      this.prefix = prefix;
      this.times = times;
    }

    // just for testing
    private <A> Prepender() {
      this(null, 0);
    }

    static <T> Iterable<String> prepend(@NotBlank String first, Iterable<String> tail) {
      return Iterables.concat(ImmutableList.of(first), tail);
    }

    Iterable<String> prepend(Iterable<String> tail)
        throws IllegalArgumentException, NullPointerException {
      return Iterables.concat(Collections.nCopies(times, prefix), tail);
    }

    static Invokable<?, Prepender> constructor(Class<?>... parameterTypes) throws Exception {
      Constructor<Prepender> constructor = Prepender.class.getDeclaredConstructor(parameterTypes);
      return Invokable.from(constructor);
    }

    static Invokable<Object, Object> method(String name, Class<?>... parameterTypes) {
      try {
        Method method = Prepender.class.getDeclaredMethod(name, parameterTypes);
        return Invokable.from(method);
      } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException(e);
      }
    }

    private void privateMethod() {}

    private final void privateFinalMethod() {}

    static void staticMethod() {}

    static final void staticFinalMethod() {}
  }

  private static class SubPrepender extends Prepender {
    @SuppressWarnings("unused") // needed to satisfy compiler, never called
    public SubPrepender() throws NullPointerException {
      throw new AssertionError();
    }
  }
}
