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

package com.google.common.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MutableClassToInstanceMap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.TypeToken;
import com.google.common.testing.NullPointerTester.Visibility;
import com.google.common.testing.RelationshipTester.Item;
import com.google.common.testing.RelationshipTester.ItemReporter;

import junit.framework.Assert;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Tester that runs automated sanity tests for any given class.
 *
 * @author Ben Yu
 * @since 14.0
 */
@Beta
public final class ClassSanityTester {

  private static final Ordering<Invokable<?, ?>> BY_METHOD_NAME =
      new Ordering<Invokable<?, ?>>() {
        @Override public int compare(Invokable<?, ?> left, Invokable<?, ?> right) {
          return left.getName().compareTo(right.getName());
        }
      };

  private static final Ordering<Invokable<?, ?>> BY_PARAMETERS =
      new Ordering<Invokable<?, ?>>() {
        @Override public int compare(Invokable<?, ?> left, Invokable<?, ?> right) {
          return Ordering.usingToString().compare(left.getParameters(), right.getParameters());
        }
      };

  private static final Ordering<Invokable<?, ?>> BY_NUMBER_OF_PARAMETERS =
      new Ordering<Invokable<?, ?>>() {
        @Override public int compare(Invokable<?, ?> left, Invokable<?, ?> right) {
          return Ints.compare(left.getParameters().size(), right.getParameters().size());
        }
      };

  private final MutableClassToInstanceMap<Object> defaultValues =
      MutableClassToInstanceMap.create();
  private final ListMultimap<Class<?>, Object> sampleInstances = ArrayListMultimap.create();
  private final NullPointerTester nullPointerTester = new NullPointerTester();

  public ClassSanityTester() {
    // TODO(benyu): bake these into ArbitraryInstances.
    setDefault(byte.class, (byte) 1);
    setDefault(Byte.class, (byte) 1);
    setDefault(short.class, (short) 1);
    setDefault(Short.class, (short) 1);
    setDefault(int.class, 1);
    setDefault(Integer.class, 1);
    setDefault(long.class, 1L);
    setDefault(Long.class, 1L);
    setDefault(float.class, 1F);
    setDefault(Float.class, 1F);
    setDefault(double.class, 1D);
    setDefault(Double.class, 1D);
    setDefault(Class.class, Class.class);
  }

  /** 
   * Sets the default value for {@code type}. The default value isn't used in testing {@link
   * Object#equals} because more than one sample instances are needed for testing inequality.
   * To set sample instances for equality testing, use {@link #setSampleInstances} instead.
   */
  public <T> ClassSanityTester setDefault(Class<T> type, T value) {
    nullPointerTester.setDefault(type, value);
    defaultValues.putInstance(type, value);
    return this;
  }

  /**
   * Sets sample instances for {@code type} for purpose of {@code equals} testing, where different
   * values are needed to test inequality.
   * 
   * <p>Used for types that {@link ClassSanityTester} doesn't already know how to sample.
   * It's usually necessary to add two unequal instances for each type, with the exception that if
   * the sample instance is to be passed to a {@link Nullable} parameter,  one non-null sample is
   * sufficient. Setting an empty list will clear sample instances for {@code type}.
   */
  public <T> ClassSanityTester setSampleInstances(Class<T> type, Iterable<? extends T> instances) {
    ImmutableList<? extends T> samples = ImmutableList.copyOf(instances);
    sampleInstances.putAll(checkNotNull(type), samples);
    if (!samples.isEmpty()) {
      setDefault(type, samples.get(0));
    }
    return this;
  }

  /**
   * Tests that {@code cls} properly checks null on all constructor and method parameters that
   * aren't annotated with {@link Nullable}. In details:
   * <ul>
   * <li>All public static methods are checked such that passing null for any parameter that's not
   *     annotated with {@link javax.annotation.Nullable} should throw {@link NullPointerException}.
   * <li>If there is any public constructor or public static factory method declared by {@code cls},
   *     all public instance methods will be checked too using the instance created by invoking the
   *     constructor or static factory method.
   * <li>If there is any public constructor or public static factory method declared by {@code cls}:
   *     <ul>
   *     <li>Test will fail if default value for a parameter cannot be determined.
   *     <li>Test will fail if the factory method returns null so testing instance methods is
   *         impossible.
   *     <li>Test will fail if the constructor or factory method throws exception.
   *     </ul>
   * <li>If there is no public constructor or public static factory method declared by {@code cls},
   *     instance methods are skipped for nulls test.
   * <li>Nulls test is not performed on method return values unless the method is a public static
   *     factory method whose return type is {@code cls} or {@code cls}'s subtype.
   * </ul>
   */
  public void testNulls(Class<?> cls) {
    try {
      doTestNulls(cls);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  void doTestNulls(Class<?> cls)
      throws ParameterNotInstantiableException, IllegalAccessException,
             InvocationTargetException, FactoryMethodReturnsNullException {
    if (!Modifier.isAbstract(cls.getModifiers())) {
      nullPointerTester.testConstructors(cls, Visibility.PACKAGE);
    }
    nullPointerTester.testStaticMethods(cls, Visibility.PACKAGE);
    Object instance = instantiate(cls);
    if (instance != null) {
      nullPointerTester.testInstanceMethods(instance, Visibility.PACKAGE);
    }
  }

  /**
   * Tests the {@link Object#equals} and {@link Object#hashCode} of {@code cls}. In details:
   * <ul>
   * <li>The public constructor or public static factory method with the most parameters is used to
   *     construct the sample instances. In case of tie, the candidate constructors or factories are
   *     tried one after another until one can be used to construct sample instances.
   * <li>For the constructor or static factory method used to construct instances, it's checked that
   *     when equal parameters are passed, the result instance should also be equal; and vice versa.
   * <li>If a public constructor or public static factory method exists: <ul>
   *     <li>Test will fail if default value for a parameter cannot be determined.
   *     <li>Test will fail if the factory method returns null so testing instance methods is
   *         impossible.
   *     <li>Test will fail if the constructor or factory method throws exception.
   *     </ul>
   * <li>If there is no public constructor or public static factory method declared by {@code cls},
   *     no test is performed.
   * <li>Equality test is not performed on method return values unless the method is a public static
   *     factory method whose return type is {@code cls} or {@code cls}'s subtype.
   * <li>Inequality check is not performed against state mutation methods such as {@link List#add},
   *     or functional update methods such as {@link com.google.common.base.Joiner#skipNulls}.
   * </ul>
   */
  public void testEquals(Class<?> cls) {
    try {
      doTestEquals(cls);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
  
  void doTestEquals(Class<?> cls)
      throws ParameterNotInstantiableException, IllegalAccessException,
             InvocationTargetException, FactoryMethodReturnsNullException {
    if (cls.isEnum()) {
      return;
    }
    List<? extends Invokable<?, ?>> factories = Lists.reverse(getFactories(TypeToken.of(cls)));
    if (factories.isEmpty()) {
      return;
    }
    int numberOfParameters = factories.get(0).getParameters().size();
    List<ParameterNotInstantiableException> paramErrors = Lists.newArrayList();
    List<InvocationTargetException> instantiationExceptions = Lists.newArrayList();
    List<FactoryMethodReturnsNullException> nullErrors = Lists.newArrayList();
    // Try factories with the greatest number of parameters first.
    for (Invokable<?, ?> factory : factories) {
      if (factory.getParameters().size() == numberOfParameters) {
        try {
          testEqualsUsing(factory);
          return;
        } catch (ParameterNotInstantiableException e) {
          paramErrors.add(e);
        } catch (InvocationTargetException e) {
          instantiationExceptions.add(e);
        } catch (FactoryMethodReturnsNullException e) {
          nullErrors.add(e);
        }
      }
    }
    throwFirst(paramErrors);
    throwFirst(instantiationExceptions);
    throwFirst(nullErrors);
  }

  /**
   * Instantiates {@code cls} by invoking one of its public constructors or public static factory
   * methods with the parameters automatically provided using dummy values.
   *
   * @return The instantiated instance, or {@code null} if the class has no public constructor
   *         or factory method to be constructed.
   */
  @Nullable <T> T instantiate(Class<T> cls)
      throws ParameterNotInstantiableException, IllegalAccessException,
             InvocationTargetException, FactoryMethodReturnsNullException {
    if (cls.isEnum()) {
      T[] constants = cls.getEnumConstants();
      if (constants.length > 0) {
        return constants[0];
      } else {
        return null;
      }
    }
    TypeToken<T> type = TypeToken.of(cls);
    List<ParameterNotInstantiableException> paramErrors = Lists.newArrayList();
    List<InvocationTargetException> instantiationExceptions = Lists.newArrayList();
    List<FactoryMethodReturnsNullException> nullErrors = Lists.newArrayList();
    for (Invokable<?, ? extends T> factory : getFactories(type)) {
      T instance;
      try {
        instance = instantiate(factory);
      } catch (ParameterNotInstantiableException e) {
        paramErrors.add(e);
        continue;
      } catch (InvocationTargetException e) {
        instantiationExceptions.add(e);
        continue;
      }
      if (instance == null) {
        nullErrors.add(new FactoryMethodReturnsNullException(factory));
      } else {
        return instance;
      }
    }
    throwFirst(paramErrors);
    throwFirst(instantiationExceptions);
    throwFirst(nullErrors);
    return null;
  }

  /**
   * Returns an object responsible for performing sanity tests against the return values
   * of all public static methods declared by {@code cls}, excluding superclasses.
   */
  public FactoryMethodReturnValueTester forAllPublicStaticMethods(Class<?> cls) {
    ImmutableList.Builder<Invokable<?, ?>> builder = ImmutableList.builder();
    for (Method method : cls.getDeclaredMethods()) {
      Invokable<?, ?> invokable = Invokable.from(method);
      if (invokable.isPublic() && invokable.isStatic() && !invokable.isSynthetic()) {
        Class<?> returnType = Primitives.unwrap(invokable.getReturnType().getRawType());
        if (returnType.isEnum() && returnType.isPrimitive()) {
          continue;
        }
        builder.add(invokable);
      }
    }
    return new FactoryMethodReturnValueTester(builder.build());
  }

  /** Runs sanity tests against return values of static factory methods declared by a class. */
  public final class FactoryMethodReturnValueTester {
    private final ImmutableList<Invokable<?, ?>> factories;

    private FactoryMethodReturnValueTester(ImmutableList<Invokable<?, ?>> factories) {
      this.factories = factories;
    }

    /**
     * Tests null checks against the instance methods of the return values, if any.
     * 
     * <p>Test fails if default value cannot be determined for a constructor or factory method
     * parameter, or if the constructor or factory method throws exception.
     *
     * @returns this tester
     */
    public FactoryMethodReturnValueTester testNulls() throws Exception {
      for (Invokable<?, ?> factory : factories) {
        Object instance = instantiate(factory);
        if (instance != null) {
          nullPointerTester.testAllPublicInstanceMethods(instance);
        }
      }
      return this;
    }

    /**
     * Tests {@link Object#equals} and {@link Object#hashCode} against the return values of the
     * static methods, by asserting that when equal parameters are passed to the same static method,
     * the return value should also be equal; and vice versa.
     * 
     * <p>Test fails if default value cannot be determined for a constructor or factory method
     * parameter, or if the constructor or factory method throws exception.
     *
     * @returns this tester
     */
    public FactoryMethodReturnValueTester testEquals() throws Exception {
      for (Invokable<?, ?> factory : factories) {
        try {
          testEqualsUsing(factory);
        } catch (FactoryMethodReturnsNullException e) {
          // If the factory returns null, we just skip it.
        }
      }
      return this;
    }

    /**
     * Runs serialization test on the return values of the static methods.
     * 
     * <p>Test fails if default value cannot be determined for a constructor or factory method
     * parameter, or if the constructor or factory method throws exception.
     *
     * @returns this tester
     */
    public FactoryMethodReturnValueTester testSerializable() throws Exception {
      for (Invokable<?, ?> factory : factories) {
        Object instance = instantiate(factory);
        if (instance != null) {
          SerializableTester.reserialize(instance);
        }
      }
      return this;
    }

    /**
     * Runs equals and serialization test on the return values.
     * 
     * <p>Test fails if default value cannot be determined for a constructor or factory method
     * parameter, or if the constructor or factory method throws exception.
     *
     * @returns this tester
     */
    public FactoryMethodReturnValueTester testEqualsAndSerializable() throws Exception {
      for (Invokable<?, ?> factory : factories) {
        try {
          testEqualsUsing(factory);
        } catch (FactoryMethodReturnsNullException e) {
          // If the factory returns null, we just skip it.
        }
        Object instance = instantiate(factory);
        if (instance != null) {
          SerializableTester.reserializeAndAssert(instance);
        }
      }
      return this;
    }
  }

  /**
   * Instantiates using {@code factory}. If {@code factory} is annotated with {@link Nullable} and
   * returns null, null will be returned.
   * 
   * @throws ParameterNotInstantiableException if the static methods cannot be invoked because
   *         the default value of a parameter cannot be determined.
   * @throws IllegalAccessException if the class isn't public or is nested inside a non-public
   *         class, preventing its methods from being accessible.
   * @throws InvocationTargetException if a static method threw exception.
   */
  @Nullable private <T> T instantiate(Invokable<?, ? extends T> factory)
      throws ParameterNotInstantiableException, InvocationTargetException,
      IllegalAccessException {
    return invoke(factory, getDummyArguments(factory));
  }

  private void testEqualsUsing(final Invokable<?, ?> factory) 
      throws ParameterNotInstantiableException, IllegalAccessException,
      InvocationTargetException, FactoryMethodReturnsNullException {
    List<Parameter> params = factory.getParameters();
    List<FreshValueGenerator> argGenerators = Lists.newArrayListWithCapacity(params.size());
    List<Object> args = Lists.newArrayListWithCapacity(params.size());
    for (Parameter param : params) {
      FreshValueGenerator generator = newFreshValueGenerator();
      argGenerators.add(generator);
      args.add(generateDummyArg(param, generator));
    }
    Object instance = createInstance(factory, args);
    // Each group is a List of items, each item has a list of factory args.
    final List<List<List<Object>>> argGroups = Lists.newArrayList();
    EqualsTester tester = new EqualsTester().setItemReporter(new ItemReporter() {
      @Override String reportItem(Item item) {
        List<Object> factoryArgs = argGroups.get(item.groupNumber).get(item.itemNumber);
        return factory.getName() + "(" + Joiner.on(", ").useForNull("null").join(factoryArgs) + ")";
      }
    });
    tester.addEqualityGroup(instance, createInstance(factory, args));
    argGroups.add(ImmutableList.of(args, args));
    for (int i = 0; i < params.size(); i++) {
      List<Object> newArgs = Lists.newArrayList(args);
      Object newArg = argGenerators.get(i).generate(params.get(i).getType().getRawType());
      if (Objects.equal(args.get(i), newArg)) {
        // no value variance, no equality group
        continue;
      }
      newArgs.set(i, newArg);
      tester.addEqualityGroup(createInstance(factory, newArgs));
      argGroups.add(ImmutableList.of(newArgs));
    }
    tester.testEquals();
  }

  // sampleInstances is a type-safe class-values mapping, but we don't have a type-safe data
  // data structure to hold the mappings.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private FreshValueGenerator newFreshValueGenerator() {
    FreshValueGenerator generator = new FreshValueGenerator() {
      @Override Object interfaceMethodCalled(Class<?> interfaceType, Method method) {
        return getDummyValue(TypeToken.of(interfaceType).method(method).getReturnType());
      }
    };
    for (Map.Entry<Class<?>, Collection<Object>> entry : sampleInstances.asMap().entrySet()) {
      generator.addSampleInstances((Class) entry.getKey(), entry.getValue());
    }
    return generator;
  }

  private static @Nullable Object generateDummyArg(Parameter param, FreshValueGenerator generator)
      throws ParameterNotInstantiableException {
    if (param.isAnnotationPresent(Nullable.class)) {
      return null;
    }
    Object arg = generator.generate(param.getType());
    if (arg == null) {
      throw new ParameterNotInstantiableException(param);
    }
    return arg;
  }

  private static <X extends Throwable> void throwFirst(List<X> exceptions) throws X {
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
  }

  /** Factories with the least number of parameters are listed first. */
  private static <T> ImmutableList<Invokable<?, ? extends T>> getFactories(TypeToken<T> type) {
    List<Invokable<?, ? extends T>> factories = Lists.newArrayList();
    for (Method method : type.getRawType().getDeclaredMethods()) {
      Invokable<?, ?> invokable = type.method(method);
      if (!invokable.isPrivate()
          && !invokable.isSynthetic()
          && invokable.isStatic()
          && type.isAssignableFrom(invokable.getReturnType())) {
        @SuppressWarnings("unchecked") // guarded by isAssignableFrom()
        Invokable<?, ? extends T> factory = (Invokable<?, ? extends T>) invokable;
        factories.add(factory);
      }
    }
    if (!Modifier.isAbstract(type.getRawType().getModifiers())) {
      for (Constructor<?> constructor : type.getRawType().getDeclaredConstructors()) {
        Invokable<T, T> invokable = type.constructor(constructor);
        if (!invokable.isPrivate() && !invokable.isSynthetic()) {
          factories.add(invokable);
        }
      }
    }
    for (Invokable<?, ?> factory : factories) {
      factory.setAccessible(true);
    }
    // Sorts methods/constructors with least number of parameters first since it's likely easier to
    // fill dummy parameter values for them. Ties are broken by name then by the string form of the
    // parameter list.
    return BY_NUMBER_OF_PARAMETERS.compound(BY_METHOD_NAME).compound(BY_PARAMETERS)
        .immutableSortedCopy(factories);
  }

  private List<Object> getDummyArguments(Invokable<?, ?> invokable)
      throws ParameterNotInstantiableException {
    List<Object> args = Lists.newArrayList();
    for (Parameter param : invokable.getParameters()) {
      if (param.isAnnotationPresent(Nullable.class)) {
        args.add(null);
        continue;
      }
      Object defaultValue = getDummyValue(param.getType());
      if (defaultValue == null) {
        throw new ParameterNotInstantiableException(param);
      }
      args.add(defaultValue);
    }
    return args;
  }

  private <T> T getDummyValue(TypeToken<T> type) {
    Class<? super T> rawType = type.getRawType();
    @SuppressWarnings("unchecked") // Assume all default values are generics safe.
    T defaultValue = (T) defaultValues.getInstance(rawType);
    if (defaultValue != null) {
      return defaultValue;
    }
    @SuppressWarnings("unchecked") // ArbitraryInstances always returns generics-safe dummies.
    T value = (T) ArbitraryInstances.get(rawType);
    if (value != null) {
      return value;
    }
    if (rawType.isInterface()) {
      return new SerializableDummyProxy(this).newProxy(type);
    }
    return null;
  }

  private static <T> T createInstance(Invokable<?, ? extends T> factory, List<?> args)
      throws FactoryMethodReturnsNullException, InvocationTargetException, IllegalAccessException {
    T instance = invoke(factory, args);
    if (instance == null) {
      throw new FactoryMethodReturnsNullException(factory);
    }
    return instance;
  }

  @Nullable private static <T> T invoke(Invokable<?, ? extends T> factory, List<?> args)
      throws InvocationTargetException, IllegalAccessException {
    T returnValue = factory.invoke(null, args.toArray());
    if (returnValue == null) {
      Assert.assertTrue(factory + " returns null but it's not annotated with @Nullable",
          factory.isAnnotationPresent(Nullable.class));
    }
    return returnValue;
  }

  /**
   * Thrown if the test tries to invoke a constructor or static factory method but failed because
   * the dummy value of a constructor or method parameter is unknown.
   */
  @VisibleForTesting static class ParameterNotInstantiableException extends Exception {
    public ParameterNotInstantiableException(Parameter parameter) {
      super("Cannot determine value for parameter " + parameter
          + " of " + parameter.getDeclaringInvokable());
    }
  }

  /**
   * Thrown if the test tries to invoke a static factory method to test instance methods but the
   * factory returned null.
   */
  @VisibleForTesting static class FactoryMethodReturnsNullException extends Exception {
    public FactoryMethodReturnsNullException(Invokable<?, ?> factory) {
      super(factory + " returns null and cannot be used to test instance methods.");
    }
  }

  private static abstract class AbstractSerializableDummyProxy extends DummyProxy
      implements Serializable {

    @Override public boolean equals(Object obj) {
      return obj instanceof AbstractSerializableDummyProxy;
    }

    @Override public int hashCode() {
      return 0;
    }
  }

  private static final class SerializableDummyProxy extends AbstractSerializableDummyProxy {

    private transient final ClassSanityTester tester;

    SerializableDummyProxy(ClassSanityTester tester) {
      this.tester = tester;
    }

    @Override <R> R dummyReturnValue(TypeToken<R> returnType) {
      return tester.getDummyValue(returnType);
    }
  }
}
