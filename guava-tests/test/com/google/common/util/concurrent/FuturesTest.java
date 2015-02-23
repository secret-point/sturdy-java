/*
 * Copyright (C) 2008 The Guava Authors
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

package com.google.common.util.concurrent;

import static com.google.common.base.Throwables.propagateIfInstanceOf;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.get;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.successfulAsList;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.TestLogHandler;
import com.google.common.util.concurrent.ForwardingFuture.SimpleForwardingFuture;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Unit tests for {@link Futures}.
 *
 * @author Nishant Thakkar
 */
@GwtCompatible(emulated = true)
public class FuturesTest extends TestCase {
  @GwtIncompatible("TestLogHandler")
  private static final Logger combinedFutureLogger = Logger.getLogger(
      "com.google.common.util.concurrent.CollectionFuture");
  @GwtIncompatible("TestLogHandler")
  private final TestLogHandler combinedFutureLogHandler = new TestLogHandler();

  private static final String DATA1 = "data";
  private static final String DATA2 = "more data";
  private static final String DATA3 = "most data";

  @GwtIncompatible("TestLogHandler, mocks")
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    combinedFutureLogger.addHandler(combinedFutureLogHandler);
  }

  @GwtIncompatible("TestLogHandler, interruption")
  @Override
  protected void tearDown() throws Exception {
    /*
     * Clear interrupt for future tests.
     *
     * (Ideally we would perform interrupts only in threads that we create, but
     * it's hard to imagine that anything will break in practice.)
     */
    Thread.interrupted();
    combinedFutureLogger.removeHandler(combinedFutureLogHandler);
    super.tearDown();
  }

  public void testImmediateFuture() throws Exception {
    ListenableFuture<String> future = Futures.immediateFuture(DATA1);

    // Verify that the proper object is returned without waiting
    assertSame(DATA1, future.get(0L, TimeUnit.MILLISECONDS));
  }

  public void testMultipleImmediateFutures() throws Exception {
    ListenableFuture<String> future1 = Futures.immediateFuture(DATA1);
    ListenableFuture<String> future2 = Futures.immediateFuture(DATA2);

    // Verify that the proper objects are returned without waiting
    assertSame(DATA1, future1.get(0L, TimeUnit.MILLISECONDS));
    assertSame(DATA2, future2.get(0L, TimeUnit.MILLISECONDS));
  }

  @GwtIncompatible("immediateFailedFuture")
  public void testImmediateFailedFuture() throws Exception {
    Exception exception = new Exception();
    ListenableFuture<String> future =
        Futures.immediateFailedFuture(exception);

    try {
      future.get(0L, TimeUnit.MILLISECONDS);
      fail("This call was supposed to throw an ExecutionException");
    } catch (ExecutionException expected) {
      // This is good and expected
      assertSame(exception, expected.getCause());
    }
  }

  @GwtIncompatible("immediateFailedFuture")
  public void testImmediateFailedFuture_cancellationException() throws Exception {
    CancellationException exception = new CancellationException();
    ListenableFuture<String> future =
        Futures.immediateFailedFuture(exception);

    try {
      future.get(0L, TimeUnit.MILLISECONDS);
      fail("This call was supposed to throw an ExecutionException");
    } catch (ExecutionException expected) {
      // This is good and expected
      assertSame(exception, expected.getCause());
      assertFalse(future.isCancelled());
    }
  }

  @GwtIncompatible("immediateCancelledFuture")
  public void testImmediateCancelledFuture() throws Exception {
    ListenableFuture<String> future = CallerClass1.immediateCancelledFuture();
    assertTrue(future.isCancelled());

    try {
      CallerClass2.get(future);
      fail();
    } catch (CancellationException e) {
      // There should be two CancellationException chained together.  The outer one should have the
      // stack trace of where the get() call was made, and the inner should have the stack trace of
      // where the immediateCancelledFuture() call was made.
      List<StackTraceElement> stackTrace = ImmutableList.copyOf(e.getStackTrace());
      assertFalse(Iterables.any(stackTrace, hasClassName(CallerClass1.class)));
      assertTrue(Iterables.any(stackTrace, hasClassName(CallerClass2.class)));

      assertTrue(e.getCause() instanceof CancellationException);
      stackTrace = ImmutableList.copyOf(e.getCause().getStackTrace());
      assertTrue(Iterables.any(stackTrace, hasClassName(CallerClass1.class)));
      assertFalse(Iterables.any(stackTrace, hasClassName(CallerClass2.class)));
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static Predicate<StackTraceElement> hasClassName(final Class<?> clazz) {
    return new Predicate<StackTraceElement>() {
      @Override
      public boolean apply(StackTraceElement element) {
        return element.getClassName().equals(clazz.getName());
      }
    };
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final class CallerClass1 {

    static ListenableFuture<String> immediateCancelledFuture() {
      return Futures.immediateCancelledFuture();
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final class CallerClass2 {

    static <V> V get(ListenableFuture<V> future) throws ExecutionException, InterruptedException {
      return future.get();
    }
  }

  private static class MyException extends Exception {

  }

  @GwtIncompatible("immediateCheckedFuture")
  public void testImmediateCheckedFuture() throws Exception {
    CheckedFuture<String, MyException> future = Futures.immediateCheckedFuture(
        DATA1);

    // Verify that the proper object is returned without waiting
    assertSame(DATA1, future.get(0L, TimeUnit.MILLISECONDS));
    assertSame(DATA1, future.checkedGet(0L, TimeUnit.MILLISECONDS));
  }

  @GwtIncompatible("immediateCheckedFuture")
  public void testMultipleImmediateCheckedFutures() throws Exception {
    CheckedFuture<String, MyException> future1 = Futures.immediateCheckedFuture(
        DATA1);
    CheckedFuture<String, MyException> future2 = Futures.immediateCheckedFuture(
        DATA2);

    // Verify that the proper objects are returned without waiting
    assertSame(DATA1, future1.get(0L, TimeUnit.MILLISECONDS));
    assertSame(DATA1, future1.checkedGet(0L, TimeUnit.MILLISECONDS));
    assertSame(DATA2, future2.get(0L, TimeUnit.MILLISECONDS));
    assertSame(DATA2, future2.checkedGet(0L, TimeUnit.MILLISECONDS));
  }

  @GwtIncompatible("immediateFailedCheckedFuture")
  public void testImmediateFailedCheckedFuture() throws Exception {
    MyException exception = new MyException();
    CheckedFuture<String, MyException> future =
        Futures.immediateFailedCheckedFuture(exception);

    try {
      future.get(0L, TimeUnit.MILLISECONDS);
      fail("This call was supposed to throw an ExecutionException");
    } catch (ExecutionException expected) {
      // This is good and expected
      assertSame(exception, expected.getCause());
    }

    try {
      future.checkedGet(0L, TimeUnit.MILLISECONDS);
      fail("This call was supposed to throw an MyException");
    } catch (MyException expected) {
      // This is good and expected
      assertSame(exception, expected);
    }
  }

  // Class hierarchy for generics sanity checks
  private static class Foo {

  }

  private static class FooChild extends Foo {

  }

  private static class Bar {

  }

  private static class BarChild extends Bar {

  }

  public void testTransform_genericsNull() throws Exception {
    ListenableFuture<?> nullFuture = Futures.immediateFuture(null);
    ListenableFuture<?> transformedFuture =
        Futures.transform(nullFuture, Functions.constant(null));
    assertNull(transformedFuture.get());
  }

  public void testTransform_genericsHierarchy() throws Exception {
    ListenableFuture<FooChild> future = Futures.immediateFuture(null);
    final BarChild barChild = new BarChild();
    Function<Foo, BarChild> function = new Function<Foo, BarChild>() {
      @Override
      public BarChild apply(Foo unused) {
        return barChild;
      }
    };
    Bar bar = Futures.transform(future, function).get();
    assertSame(barChild, bar);
  }

  public void testTransform_cancelPropagatesToInput() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        throw new AssertionFailedError("Unexpeted call to apply.");
      }
    };
    assertTrue(Futures.transform(input, function).cancel(false));
    assertTrue(input.isCancelled());
    assertFalse(input.wasInterrupted());
  }

  public void testTransform_interruptPropagatesToInput() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        throw new AssertionFailedError("Unexpeted call to apply.");
      }
    };
    assertTrue(Futures.transform(input, function).cancel(true));
    assertTrue(input.isCancelled());
    assertTrue(input.wasInterrupted());
  }

  public void testTransform_cancelPropagatesToAsyncOutput() throws Exception {
    ListenableFuture<Foo> immediate = Futures.immediateFuture(new Foo());
    final SettableFuture<Bar> secondary = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        return secondary;
      }
    };
    assertTrue(Futures.transform(immediate, function).cancel(false));
    assertTrue(secondary.isCancelled());
    assertFalse(secondary.wasInterrupted());
  }

  public void testTransform_interruptPropagatesToAsyncOutput()
      throws Exception {
    ListenableFuture<Foo> immediate = Futures.immediateFuture(new Foo());
    final SettableFuture<Bar> secondary = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        return secondary;
      }
    };
    assertTrue(Futures.transform(immediate, function).cancel(true));
    assertTrue(secondary.isCancelled());
    assertTrue(secondary.wasInterrupted());
  }

  public void testTransformAsync_cancelPropagatesToInput() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        throw new AssertionFailedError("Unexpeted call to apply.");
      }
    };
    assertTrue(Futures.transformAsync(input, function).cancel(false));
    assertTrue(input.isCancelled());
    assertFalse(input.wasInterrupted());
  }

  public void testTransformAsync_interruptPropagatesToInput() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        throw new AssertionFailedError("Unexpeted call to apply.");
      }
    };
    assertTrue(Futures.transformAsync(input, function).cancel(true));
    assertTrue(input.isCancelled());
    assertTrue(input.wasInterrupted());
  }

  public void testTransformAsync_cancelPropagatesToAsyncOutput() throws Exception {
    ListenableFuture<Foo> immediate = Futures.immediateFuture(new Foo());
    final SettableFuture<Bar> secondary = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        return secondary;
      }
    };
    assertTrue(Futures.transformAsync(immediate, function).cancel(false));
    assertTrue(secondary.isCancelled());
    assertFalse(secondary.wasInterrupted());
  }

  public void testTransformAsync_interruptPropagatesToAsyncOutput()
      throws Exception {
    ListenableFuture<Foo> immediate = Futures.immediateFuture(new Foo());
    final SettableFuture<Bar> secondary = SettableFuture.create();
    AsyncFunction<Foo, Bar> function = new AsyncFunction<Foo, Bar>() {
      @Override
      public ListenableFuture<Bar> apply(Foo unused) {
        return secondary;
      }
    };
    assertTrue(Futures.transformAsync(immediate, function).cancel(true));
    assertTrue(secondary.isCancelled());
    assertTrue(secondary.wasInterrupted());
  }

  @GwtIncompatible("TODO")
  public void testTransform_rejectionPropagatesToOutput()
      throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    ExecutorService executor = newDirectExecutorService();
    ListenableFuture<String> transformed =
        Futures.transform(input, Functions.toStringFunction(), executor);
    executor.shutdown();
    input.set(new Foo());
    try {
      transformed.get(5, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException expected) {
      assertTrue(expected.getCause() instanceof RejectedExecutionException);
    }
  }

  /**
   * Tests that the function is invoked only once, even if it throws an exception.
   */
  public void testTransformValueRemainsMemoized() throws Exception {
    class Holder {

      int value = 2;
    }
    final Holder holder = new Holder();

    // This function adds the holder's value to the input value.
    Function<Integer, Integer> adder = new Function<Integer, Integer>() {
      @Override
      public Integer apply(Integer from) {
        return from + holder.value;
      }
    };

    // Since holder.value is 2, applying 4 should yield 6.
    assertEquals(6, adder.apply(4).intValue());

    ListenableFuture<Integer> immediateFuture = Futures.immediateFuture(4);
    Future<Integer> transformedFuture = Futures.transform(immediateFuture, adder);

    // The composed future also yields 6.
    assertEquals(6, transformedFuture.get().intValue());

    // Repeated calls yield the same value even though the function's behavior
    // changes
    holder.value = 3;
    assertEquals(6, transformedFuture.get().intValue());
    assertEquals(7, adder.apply(4).intValue());

    // Once more, with feeling.
    holder.value = 4;
    assertEquals(6, transformedFuture.get().intValue());
    assertEquals(8, adder.apply(4).intValue());

    // Memoized get also retains the value.
    assertEquals(6, transformedFuture.get(1000, TimeUnit.SECONDS).intValue());

    // Unsurprisingly, recomposing the future will return an updated value.
    assertEquals(8, Futures.transform(immediateFuture, adder).get().intValue());

    // Repeating, with the timeout version
    assertEquals(8, Futures.transform(immediateFuture, adder).get(
        1000, TimeUnit.SECONDS).intValue());
  }

  static class MyError extends Error {

  }

  static class MyRuntimeException extends RuntimeException {

  }

  /**
   * Test that the function is invoked only once, even if it throws an exception. Also, test that
   * that function's result is wrapped in an ExecutionException.
   */
  @GwtIncompatible("reflection")
  public void testTransformExceptionRemainsMemoized() throws Throwable {
    // We need to test with two input futures since ExecutionList.execute
    // doesn't catch Errors and we cannot depend on the order that our
    // transformations run. (So it is possible that the Error being thrown
    // could prevent our second transformations from running).
    SettableFuture<Integer> exceptionInput = SettableFuture.create();
    ListenableFuture<Integer> exceptionComposedFuture =
        Futures.transform(exceptionInput, newOneTimeExceptionThrower());
    exceptionInput.set(0);
    runGetIdempotencyTest(exceptionComposedFuture, MyRuntimeException.class);

    SettableFuture<Integer> errorInput = SettableFuture.create();
    ListenableFuture<Integer> errorComposedFuture =
        Futures.transform(errorInput, newOneTimeErrorThrower());
    errorInput.set(0);

    runGetIdempotencyTest(errorComposedFuture, MyError.class);

    /*
     * Try again when the input's value is already filled in, since the flow is
     * slightly different in that case.
     */
    exceptionComposedFuture =
        Futures.transform(exceptionInput, newOneTimeExceptionThrower());
    runGetIdempotencyTest(exceptionComposedFuture, MyRuntimeException.class);

    runGetIdempotencyTest(Futures.transform(errorInput, newOneTimeErrorThrower()), MyError.class);
    runGetIdempotencyTest(errorComposedFuture, MyError.class);
  }

  @GwtIncompatible("reflection")
  private static void runGetIdempotencyTest(Future<Integer> transformedFuture,
      Class<? extends Throwable> expectedExceptionClass) throws Throwable {
    for (int i = 0; i < 5; i++) {
      try {
        transformedFuture.get();
        fail();
      } catch (ExecutionException expected) {
        if (!expectedExceptionClass.isInstance(expected.getCause())) {
          throw expected.getCause();
        }
      }
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static Function<Integer, Integer> newOneTimeExceptionThrower() {
    return new Function<Integer, Integer>() {
      int calls = 0;

      @Override
      public Integer apply(Integer from) {
        if (++calls > 1) {
          fail();
        }
        throw new MyRuntimeException();
      }
    };
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static Function<Integer, Integer> newOneTimeErrorThrower() {
    return new Function<Integer, Integer>() {
      int calls = 0;

      @Override
      public Integer apply(Integer from) {
        if (++calls > 1) {
          fail();
        }
        throw new MyError();
      }
    };
  }

  // TODO(cpovirk): top-level class?
  static class ExecutorSpy implements Executor {

    Executor delegate;
    boolean wasExecuted;

    public ExecutorSpy(Executor delegate) {
      this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
      delegate.execute(command);
      wasExecuted = true;
    }
  }

  public void testTransform_Executor() throws Exception {
    Object value = new Object();
    ExecutorSpy spy = new ExecutorSpy(directExecutor());

    assertFalse(spy.wasExecuted);

    ListenableFuture<Object> future = Futures.transform(
        Futures.immediateFuture(value),
        Functions.identity(), spy);

    assertSame(value, future.get());
    assertTrue(spy.wasExecuted);
  }

  @GwtIncompatible("lazyTransform")
  public void testLazyTransform() throws Exception {
    FunctionSpy<Object, String> spy =
        new FunctionSpy<Object, String>(Functions.constant("bar"));
    Future<String> input = Futures.immediateFuture("foo");
    Future<String> transformed = Futures.lazyTransform(input, spy);
    spy.verifyCallCount(0);
    assertEquals("bar", transformed.get());
    spy.verifyCallCount(1);
    assertEquals("bar", transformed.get());
    spy.verifyCallCount(2);
  }

  @GwtIncompatible("lazyTransform")
  public void testLazyTransform_exception() throws Exception {
    final RuntimeException exception = new RuntimeException("deliberate");
    Function<Integer, String> function = new Function<Integer, String>() {
      @Override
      public String apply(Integer input) {
        throw exception;
      }
    };
    Future<String> transformed = Futures.lazyTransform(Futures.immediateFuture(1), function);
    try {
      transformed.get();
      fail();
    } catch (ExecutionException expected) {
      assertSame(exception, expected.getCause());
    }
    try {
      transformed.get(1, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException expected) {
      assertSame(exception, expected.getCause());
    }
  }

  private static class FunctionSpy<I, O> implements Function<I, O> {
    private int applyCount;
    private final Function<I, O> delegate;

    public FunctionSpy(Function<I, O> delegate) {
      this.delegate = delegate;
    }

    @Override
    public O apply(I input) {
      applyCount++;
      return delegate.apply(input);
    }

    void verifyCallCount(int expected) {
      assertThat(applyCount).isEqualTo(expected);
    }
  }

  private static <I, O> FunctionSpy<I, O> spy(Function<I, O> delegate) {
    return new FunctionSpy<I, O>(delegate);
  }

  private static <X extends Throwable, V> Function<X, V> unexpectedFunction() {
    return new Function<X, V>() {
      @Override
      public V apply(X t) {
        throw newAssertionError("Unexpected fallback", t);
      }
    };
  }

  private static class FutureFallbackSpy<V> implements FutureFallback<V> {

    private int count;
    private final FutureFallback<V> delegate;

    public FutureFallbackSpy(FutureFallback<V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public final ListenableFuture<V> create(Throwable t) throws Exception {
      count++;
      return delegate.create(t);
    }

    void verifyCallCount(int expected) {
      assertThat(count).isEqualTo(expected);
    }
  }

  private static <V> FutureFallbackSpy<V> spy(FutureFallback<V> delegate) {
    return new FutureFallbackSpy<V>(delegate);
  }

  private static <V> FutureFallback<V> unexpectedFallback() {
    return new FutureFallback<V>() {
      @Override
      public ListenableFuture<V> create(Throwable t) {
        throw newAssertionError("Unexpected fallback", t);
      }
    };
  }

  private static class AsyncFunctionSpy<X extends Throwable, V> implements AsyncFunction<X, V> {
    private int count;
    private final AsyncFunction<X, V> delegate;

    public AsyncFunctionSpy(AsyncFunction<X, V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public final ListenableFuture<V> apply(X t) throws Exception {
      count++;
      return delegate.apply(t);
    }

    void verifyCallCount(int expected) {
      assertThat(count).isEqualTo(expected);
    }
  }

  private static <X extends Throwable, V> AsyncFunctionSpy<X, V> spy(AsyncFunction<X, V> delegate) {
    return new AsyncFunctionSpy<X, V>(delegate);
  }

  private static <X extends Throwable, V> AsyncFunction<X, V> unexpectedAsyncFunction() {
    return new AsyncFunction<X, V>() {
      @Override
      public ListenableFuture<V> apply(X t) {
        throw newAssertionError("Unexpected fallback", t);
      }
    };
  }

  /** Alternative to AssertionError(String, Throwable), which doesn't exist in GWT 2.6.1. */
  private static AssertionError newAssertionError(String message, Throwable cause) {
    AssertionError e = new AssertionError(message);
    e.initCause(cause);
    return e;
  }

  public void testWithFallback_inputDoesNotRaiseException() throws Exception {
    FutureFallback<Integer> fallback = unexpectedFallback();
    ListenableFuture<Integer> originalFuture = Futures.immediateFuture(7);
    ListenableFuture<Integer> faultToleranteFuture = Futures.withFallback(originalFuture, fallback);
    assertEquals(7, faultToleranteFuture.get().intValue());
  }

  public void testWithFallback_inputRaisesException() throws Exception {
    final RuntimeException raisedException = new RuntimeException();
    FutureFallbackSpy<Integer> fallback = spy(new FutureFallback<Integer>() {
      @Override
      public ListenableFuture<Integer> create(Throwable t) throws Exception {
        assertThat(t).isSameAs(raisedException);
        return Futures.immediateFuture(20);
      }
    });
    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(raisedException);
    ListenableFuture<Integer> faultTolerantFuture = Futures.withFallback(failingFuture, fallback);
    assertEquals(20, faultTolerantFuture.get().intValue());
    fallback.verifyCallCount(1);
  }

  public void testWithFallback_fallbackGeneratesRuntimeException() throws Exception {
    RuntimeException expectedException = new RuntimeException();
    runExpectedExceptionFallbackTest(expectedException, false);
  }

  public void testWithFallback_fallbackGeneratesCheckedException() throws Exception {
    Exception expectedException = new Exception() {
    };
    runExpectedExceptionFallbackTest(expectedException, false);
  }

  public void testWithFallback_fallbackGeneratesError() throws Exception {
    final Error error = new Error("deliberate");
    FutureFallback<Integer> fallback = new FutureFallback<Integer>() {
      @Override
      public ListenableFuture<Integer> create(Throwable t) throws Exception {
        throw error;
      }
    };
    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(new RuntimeException());
    try {
      Futures.withFallback(failingFuture, fallback).get();
      fail("An Exception should have been thrown!");
    } catch (ExecutionException expected) {
      assertSame(error, expected.getCause());
    }
  }

  public void testWithFallback_fallbackReturnsRuntimeException() throws Exception {
    RuntimeException expectedException = new RuntimeException();
    runExpectedExceptionFallbackTest(expectedException, true);
  }

  public void testWithFallback_fallbackReturnsCheckedException() throws Exception {
    Exception expectedException = new Exception() {
    };
    runExpectedExceptionFallbackTest(expectedException, true);
  }

  private void runExpectedExceptionFallbackTest(
      final Exception expectedException, final boolean wrapInFuture) throws Exception {
    FutureFallbackSpy<Integer> fallback = spy(new FutureFallback<Integer>() {
      @Override
      public ListenableFuture<Integer> create(Throwable t) throws Exception {
        if (!wrapInFuture) {
          throw expectedException;
        } else {
          return Futures.immediateFailedFuture(expectedException);
        }
      }
    });

    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(new RuntimeException());

    ListenableFuture<Integer> faultToleranteFuture = Futures.withFallback(failingFuture, fallback);
    try {
      faultToleranteFuture.get();
      fail("An Exception should have been thrown!");
    } catch (ExecutionException ee) {
      assertSame(expectedException, ee.getCause());
    }
    fallback.verifyCallCount(1);
  }

  public void testWithFallback_fallbackNotReady() throws Exception {
    ListenableFuture<Integer> primary = immediateFailedFuture(new Exception());
    final SettableFuture<Integer> secondary = SettableFuture.create();
    FutureFallback<Integer> fallback = new FutureFallback<Integer>() {
      @Override
      public ListenableFuture<Integer> create(Throwable t) {
        return secondary;
      }
    };
    ListenableFuture<Integer> derived = Futures.withFallback(primary, fallback);
    secondary.set(1);
    assertEquals(1, (int) derived.get());
  }

  public void testWithFallback_resultInterruptedBeforeFallback() throws Exception {
    SettableFuture<Integer> primary = SettableFuture.create();
    FutureFallback<Integer> fallback = unexpectedFallback();
    ListenableFuture<Integer> derived = Futures.withFallback(primary, fallback);
    derived.cancel(true);
    assertTrue(primary.isCancelled());
    assertTrue(primary.wasInterrupted());
  }

  public void testWithFallback_resultCancelledBeforeFallback() throws Exception {
    SettableFuture<Integer> primary = SettableFuture.create();
    FutureFallback<Integer> fallback = unexpectedFallback();
    ListenableFuture<Integer> derived = Futures.withFallback(primary, fallback);
    derived.cancel(false);
    assertTrue(primary.isCancelled());
    assertFalse(primary.wasInterrupted());
  }

  @GwtIncompatible("mocks")
  @SuppressWarnings("unchecked")
  public void testWithFallback_resultCancelledAfterFallback() throws Exception {
    final SettableFuture<Integer> secondary = SettableFuture.create();
    final RuntimeException raisedException = new RuntimeException();
    FutureFallbackSpy<Integer> fallback = spy(new FutureFallback<Integer>() {
      @Override
      public ListenableFuture<Integer> create(Throwable t) throws Exception {
        assertThat(t).isSameAs(raisedException);
        return secondary;
      }
    });

    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(raisedException);

    ListenableFuture<Integer> derived = Futures.withFallback(failingFuture, fallback);
    derived.cancel(false);
    assertTrue(secondary.isCancelled());
    assertFalse(secondary.wasInterrupted());
    fallback.verifyCallCount(1);
  }

  public void testWithFallback_nullInsteadOfFuture() throws Exception {
    ListenableFuture<Integer> inputFuture = immediateFailedFuture(new Exception());
    ListenableFuture<?> chainedFuture =
        Futures.withFallback(inputFuture, new FutureFallback<Integer>() {
          @Override
          public ListenableFuture<Integer> create(Throwable t) {
            return null;
          }
        });
    try {
      chainedFuture.get();
      fail();
    } catch (ExecutionException expected) {
      NullPointerException cause = (NullPointerException) expected.getCause();
      assertThat(cause).hasMessage("FutureFallback.create returned null instead of a Future. "
          + "Did you mean to return immediateFuture(null)?");
    }
  }

  // catchingAsync tests cloned from the old withFallback tests:

  public void testCatchingAsync_inputDoesNotRaiseException() throws Exception {
    AsyncFunction<Throwable, Integer> fallback = unexpectedAsyncFunction();
    ListenableFuture<Integer> originalFuture = Futures.immediateFuture(7);
    ListenableFuture<Integer> faultToleranteFuture =
        Futures.catchingAsync(originalFuture, Throwable.class, fallback);
    assertEquals(7, faultToleranteFuture.get().intValue());
  }

  public void testCatchingAsync_inputRaisesException() throws Exception {
    final RuntimeException raisedException = new RuntimeException();
    AsyncFunctionSpy<Throwable, Integer> fallback = spy(new AsyncFunction<Throwable, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(Throwable t) throws Exception {
        assertThat(t).isSameAs(raisedException);
        return Futures.immediateFuture(20);
      }
    });
    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(raisedException);
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catchingAsync(failingFuture, Throwable.class, fallback);
    assertEquals(20, faultTolerantFuture.get().intValue());
    fallback.verifyCallCount(1);
  }

  public void testCatchingAsync_fallbackGeneratesRuntimeException() throws Exception {
    RuntimeException expectedException = new RuntimeException();
    runExpectedExceptionCatchingAsyncTest(expectedException, false);
  }

  public void testCatchingAsync_fallbackGeneratesCheckedException() throws Exception {
    Exception expectedException = new Exception() {
    };
    runExpectedExceptionCatchingAsyncTest(expectedException, false);
  }

  public void testCatchingAsync_fallbackGeneratesError() throws Exception {
    final Error error = new Error("deliberate");
    AsyncFunction<Throwable, Integer> fallback = new AsyncFunction<Throwable, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(Throwable t) throws Exception {
        throw error;
      }
    };
    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(new RuntimeException());
    try {
      Futures.catchingAsync(failingFuture, Throwable.class, fallback).get();
      fail("An Exception should have been thrown!");
    } catch (ExecutionException expected) {
      assertSame(error, expected.getCause());
    }
  }

  public void testCatchingAsync_fallbackReturnsRuntimeException() throws Exception {
    RuntimeException expectedException = new RuntimeException();
    runExpectedExceptionCatchingAsyncTest(expectedException, true);
  }

  public void testCatchingAsync_fallbackReturnsCheckedException() throws Exception {
    Exception expectedException = new Exception() {
    };
    runExpectedExceptionCatchingAsyncTest(expectedException, true);
  }

  private void runExpectedExceptionCatchingAsyncTest(
      final Exception expectedException, final boolean wrapInFuture) throws Exception {
    AsyncFunctionSpy<Throwable, Integer> fallback = spy(new AsyncFunction<Throwable, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(Throwable t) throws Exception {
        if (!wrapInFuture) {
          throw expectedException;
        } else {
          return Futures.immediateFailedFuture(expectedException);
        }
      }
    });

    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(new RuntimeException());

    ListenableFuture<Integer> faultToleranteFuture =
        Futures.catchingAsync(failingFuture, Throwable.class, fallback);
    try {
      faultToleranteFuture.get();
      fail("An Exception should have been thrown!");
    } catch (ExecutionException ee) {
      assertSame(expectedException, ee.getCause());
    }
    fallback.verifyCallCount(1);
  }

  public void testCatchingAsync_fallbackNotReady() throws Exception {
    ListenableFuture<Integer> primary = immediateFailedFuture(new Exception());
    final SettableFuture<Integer> secondary = SettableFuture.create();
    AsyncFunction<Throwable, Integer> fallback = new AsyncFunction<Throwable, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(Throwable t) {
        return secondary;
      }
    };
    ListenableFuture<Integer> derived = Futures.catchingAsync(primary, Throwable.class, fallback);
    secondary.set(1);
    assertEquals(1, (int) derived.get());
  }

  public void testCatchingAsync_resultInterruptedBeforeFallback() throws Exception {
    SettableFuture<Integer> primary = SettableFuture.create();
    AsyncFunction<Throwable, Integer> fallback = unexpectedAsyncFunction();
    ListenableFuture<Integer> derived = Futures.catchingAsync(primary, Throwable.class, fallback);
    derived.cancel(true);
    assertTrue(primary.isCancelled());
    assertTrue(primary.wasInterrupted());
  }

  public void testCatchingAsync_resultCancelledBeforeFallback() throws Exception {
    SettableFuture<Integer> primary = SettableFuture.create();
    AsyncFunction<Throwable, Integer> fallback = unexpectedAsyncFunction();
    ListenableFuture<Integer> derived = Futures.catchingAsync(primary, Throwable.class, fallback);
    derived.cancel(false);
    assertTrue(primary.isCancelled());
    assertFalse(primary.wasInterrupted());
  }

  @GwtIncompatible("mocks")
  @SuppressWarnings("unchecked")
  public void testCatchingAsync_resultCancelledAfterFallback() throws Exception {
    final SettableFuture<Integer> secondary = SettableFuture.create();
    final RuntimeException raisedException = new RuntimeException();
    AsyncFunctionSpy<Throwable, Integer> fallback = spy(new AsyncFunction<Throwable, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(Throwable t) throws Exception {
        assertThat(t).isSameAs(raisedException);
        return secondary;
      }
    });

    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(raisedException);

    ListenableFuture<Integer> derived =
        Futures.catchingAsync(failingFuture, Throwable.class, fallback);
    derived.cancel(false);
    assertTrue(secondary.isCancelled());
    assertFalse(secondary.wasInterrupted());
    fallback.verifyCallCount(1);
  }

  public void testCatchingAsync_nullInsteadOfFuture() throws Exception {
    ListenableFuture<Integer> inputFuture = immediateFailedFuture(new Exception());
    ListenableFuture<?> chainedFuture = Futures.catchingAsync(inputFuture, Throwable.class,
        new AsyncFunction<Throwable, Integer>() {
          @Override
          @SuppressWarnings("AsyncFunctionReturnsNull")
          public ListenableFuture<Integer> apply(Throwable t) {
            return null;
          }
        });
    try {
      chainedFuture.get();
      fail();
    } catch (ExecutionException expected) {
      NullPointerException cause = (NullPointerException) expected.getCause();
      assertThat(cause).hasMessage("AsyncFunction.apply returned null instead of a Future. "
          + "Did you mean to return immediateFuture(null)?");
    }
  }

  // catching tests cloned from the old withFallback tests:

  public void testCatching_inputDoesNotRaiseException() throws Exception {
    Function<Throwable, Integer> fallback = unexpectedFunction();
    ListenableFuture<Integer> originalFuture = Futures.immediateFuture(7);
    ListenableFuture<Integer> faultToleranteFuture =
        Futures.catching(originalFuture, Throwable.class, fallback);
    assertEquals(7, faultToleranteFuture.get().intValue());
  }

  public void testCatching_inputRaisesException() throws Exception {
    final RuntimeException raisedException = new RuntimeException();
    FunctionSpy<Throwable, Integer> fallback = spy(new Function<Throwable, Integer>() {
      @Override
      public Integer apply(Throwable t) {
        assertThat(t).isSameAs(raisedException);
        return 20;
      }
    });
    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(raisedException);
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catching(failingFuture, Throwable.class, fallback);
    assertEquals(20, faultTolerantFuture.get().intValue());
    fallback.verifyCallCount(1);
  }

  public void testCatching_fallbackGeneratesRuntimeException() throws Exception {
    RuntimeException expectedException = new RuntimeException();
    runExpectedExceptionCatchingTest(expectedException);
  }

  /*
   * catching() uses a plain Function, so there's no
   * testCatching_fallbackGeneratesCheckedException().
   */

  public void testCatching_fallbackGeneratesError() throws Exception {
    final Error error = new Error("deliberate");
    Function<Throwable, Integer> fallback = new Function<Throwable, Integer>() {
      @Override
      public Integer apply(Throwable t) {
        throw error;
      }
    };
    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(new RuntimeException());
    try {
      Futures.catching(failingFuture, Throwable.class, fallback).get();
      fail("An Exception should have been thrown!");
    } catch (ExecutionException expected) {
      assertSame(error, expected.getCause());
    }
  }

  /*
   * catching() uses a plain Function, so there's no testCatching_fallbackReturnsRuntimeException()
   * or testCatching_fallbackReturnsCheckedException().
   */

  private void runExpectedExceptionCatchingTest(final RuntimeException expectedException)
      throws Exception {
    FunctionSpy<Throwable, Integer> fallback = spy(new Function<Throwable, Integer>() {
      @Override
      public Integer apply(Throwable t) {
        throw expectedException;
      }
    });

    ListenableFuture<Integer> failingFuture = Futures.immediateFailedFuture(new RuntimeException());

    ListenableFuture<Integer> faultToleranteFuture =
        Futures.catching(failingFuture, Throwable.class, fallback);
    try {
      faultToleranteFuture.get();
      fail("An Exception should have been thrown!");
    } catch (ExecutionException ee) {
      assertSame(expectedException, ee.getCause());
    }
    fallback.verifyCallCount(1);
  }

  // catching() uses a plain Function, so there's no testCatching_fallbackNotReady().

  public void testCatching_resultInterruptedBeforeFallback() throws Exception {
    SettableFuture<Integer> primary = SettableFuture.create();
    Function<Throwable, Integer> fallback = unexpectedFunction();
    ListenableFuture<Integer> derived = Futures.catching(primary, Throwable.class, fallback);
    derived.cancel(true);
    assertTrue(primary.isCancelled());
    assertTrue(primary.wasInterrupted());
  }

  public void testCatching_resultCancelledBeforeFallback() throws Exception {
    SettableFuture<Integer> primary = SettableFuture.create();
    Function<Throwable, Integer> fallback = unexpectedFunction();
    ListenableFuture<Integer> derived = Futures.catching(primary, Throwable.class, fallback);
    derived.cancel(false);
    assertTrue(primary.isCancelled());
    assertFalse(primary.wasInterrupted());
  }

  // catching() uses a plain Function, so there's no testCatching_resultCancelledAfterFallback().

  // catching() uses a plain Function, so there's no testCatching_nullInsteadOfFuture().

  // Some tests of the exceptionType parameter:

  public void testCatching_Throwable() throws Exception {
    Function<Throwable, Integer> fallback = functionReturningOne();
    ListenableFuture<Integer> originalFuture = immediateFailedFuture(new IOException());
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catching(originalFuture, Throwable.class, fallback);
    assertEquals(1, (int) faultTolerantFuture.get());
  }

  @GwtIncompatible("non-Throwable exceptionType")
  public void testCatching_customTypeMatch() throws Exception {
    Function<IOException, Integer> fallback = functionReturningOne();
    ListenableFuture<Integer> originalFuture = immediateFailedFuture(new FileNotFoundException());
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catching(originalFuture, IOException.class, fallback);
    assertEquals(1, (int) faultTolerantFuture.get());
  }

  @GwtIncompatible("non-Throwable exceptionType")
  public void testCatching_customTypeNoMatch() throws Exception {
    Function<IOException, Integer> fallback = functionReturningOne();
    ListenableFuture<Integer> originalFuture = immediateFailedFuture(new RuntimeException());
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catching(originalFuture, IOException.class, fallback);
    try {
      faultTolerantFuture.get();
      fail();
    } catch (ExecutionException expected) {
      assertTrue(expected.getCause() instanceof RuntimeException);
    }
  }

  public void testCatchingAsync_Throwable() throws Exception {
    AsyncFunction<Throwable, Integer> fallback = asyncFunctionReturningOne();
    ListenableFuture<Integer> originalFuture = immediateFailedFuture(new IOException());
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catchingAsync(originalFuture, Throwable.class, fallback);
    assertEquals(1, (int) faultTolerantFuture.get());
  }

  @GwtIncompatible("non-Throwable exceptionType")
  public void testCatchingAsync_customTypeMatch() throws Exception {
    AsyncFunction<IOException, Integer> fallback = asyncFunctionReturningOne();
    ListenableFuture<Integer> originalFuture = immediateFailedFuture(new FileNotFoundException());
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catchingAsync(originalFuture, IOException.class, fallback);
    assertEquals(1, (int) faultTolerantFuture.get());
  }

  @GwtIncompatible("non-Throwable exceptionType")
  public void testCatchingAsync_customTypeNoMatch() throws Exception {
    AsyncFunction<IOException, Integer> fallback = asyncFunctionReturningOne();
    ListenableFuture<Integer> originalFuture = immediateFailedFuture(new RuntimeException());
    ListenableFuture<Integer> faultTolerantFuture =
        Futures.catchingAsync(originalFuture, IOException.class, fallback);
    try {
      faultTolerantFuture.get();
      fail();
    } catch (ExecutionException expected) {
      assertTrue(expected.getCause() instanceof RuntimeException);
    }
  }

  private <X extends Throwable> Function<X, Integer> functionReturningOne() {
    return new Function<X, Integer>() {
      @Override
      public Integer apply(X t) {
        return 1;
      }
    };
  }

  private <X extends Throwable> AsyncFunction<X, Integer> asyncFunctionReturningOne() {
    return new AsyncFunction<X, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(X t) {
        return immediateFuture(1);
      }
    };
  }

  private static <I, O> AsyncFunction<I, O> constantAsyncFunction(
      final ListenableFuture<O> output) {
    return new AsyncFunction<I, O>() {
      @Override
      public ListenableFuture<O> apply(I input) {
        return output;
      }
    };
  }

  public void testTransform_genericsWildcard_AsyncFunction() throws Exception {
    ListenableFuture<?> nullFuture = immediateFuture(null);
    ListenableFuture<?> chainedFuture =
        Futures.transform(nullFuture, constantAsyncFunction(nullFuture));
    assertNull(chainedFuture.get());
  }

  public void testTransform_genericsHierarchy_AsyncFunction() throws Exception {
    ListenableFuture<FooChild> future = Futures.immediateFuture(null);
    final BarChild barChild = new BarChild();
    AsyncFunction<Foo, BarChild> function =
        new AsyncFunction<Foo, BarChild>() {
          @Override
          public AbstractFuture<BarChild> apply(Foo unused) {
            AbstractFuture<BarChild> future = new AbstractFuture<BarChild>() {
            };
            future.set(barChild);
            return future;
          }
        };
    Bar bar = Futures.transform(future, function).get();
    assertSame(barChild, bar);
  }

  @GwtIncompatible("get() timeout")
  public void testTransform_asyncFunction_timeout()
      throws InterruptedException, ExecutionException {
    AsyncFunction<String, Integer> function = constantAsyncFunction(Futures.immediateFuture(1));
    ListenableFuture<Integer> future = Futures.transform(
        SettableFuture.<String>create(), function);
    try {
      future.get(1, TimeUnit.MILLISECONDS);
      fail();
    } catch (TimeoutException expected) {
    }
  }

  public void testTransform_asyncFunction_error() throws InterruptedException {
    final Error error = new Error("deliberate");
    AsyncFunction<String, Integer> function = new AsyncFunction<String, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(String input) {
        throw error;
      }
    };
    SettableFuture<String> inputFuture = SettableFuture.create();
    ListenableFuture<Integer> outputFuture = Futures.transform(inputFuture, function);
    inputFuture.set("value");
    try {
      outputFuture.get();
      fail("should have thrown error");
    } catch (ExecutionException e) {
      assertSame(error, e.getCause());
    }
  }

  public void testTransform_asyncFunction_nullInsteadOfFuture() throws Exception {
    ListenableFuture<?> inputFuture = immediateFuture("a");
    ListenableFuture<?> chainedFuture =
        Futures.transform(inputFuture, constantAsyncFunction(null));
    try {
      chainedFuture.get();
      fail();
    } catch (ExecutionException expected) {
      NullPointerException cause = (NullPointerException) expected.getCause();
      assertThat(cause).hasMessage("AsyncFunction.apply returned null instead of a Future. "
          + "Did you mean to return immediateFuture(null)?");
    }
  }

  @GwtIncompatible("threads")

  public void testTransform_asyncFunction_cancelledWhileApplyingFunction()
      throws InterruptedException, ExecutionException {
    final CountDownLatch inFunction = new CountDownLatch(1);
    final CountDownLatch functionDone = new CountDownLatch(1);
    final SettableFuture<Integer> resultFuture = SettableFuture.create();
    AsyncFunction<String, Integer> function = new AsyncFunction<String, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(String input) throws Exception {
        inFunction.countDown();
        functionDone.await();
        return resultFuture;
      }
    };
    SettableFuture<String> inputFuture = SettableFuture.create();
    ListenableFuture<Integer> future = Futures.transform(
        inputFuture, function, Executors.newSingleThreadExecutor());
    inputFuture.set("value");
    inFunction.await();
    future.cancel(false);
    functionDone.countDown();
    try {
      future.get();
      fail();
    } catch (CancellationException expected) {
    }
    try {
      resultFuture.get();
      fail();
    } catch (CancellationException expected) {
    }
  }

  public void testTransformAsync_genericsWildcard_AsyncFunction() throws Exception {
    ListenableFuture<?> nullFuture = immediateFuture(null);
    ListenableFuture<?> chainedFuture =
        Futures.transformAsync(nullFuture, constantAsyncFunction(nullFuture));
    assertNull(chainedFuture.get());
  }

  public void testTransformAsync_genericsHierarchy_AsyncFunction() throws Exception {
    ListenableFuture<FooChild> future = Futures.immediateFuture(null);
    final BarChild barChild = new BarChild();
    AsyncFunction<Foo, BarChild> function =
        new AsyncFunction<Foo, BarChild>() {
          @Override
          public AbstractFuture<BarChild> apply(Foo unused) {
            AbstractFuture<BarChild> future = new AbstractFuture<BarChild>() {
            };
            future.set(barChild);
            return future;
          }
        };
    Bar bar = Futures.transformAsync(future, function).get();
    assertSame(barChild, bar);
  }

  @GwtIncompatible("get() timeout")
  public void testTransformAsync_asyncFunction_timeout()
      throws InterruptedException, ExecutionException {
    AsyncFunction<String, Integer> function = constantAsyncFunction(Futures.immediateFuture(1));
    ListenableFuture<Integer> future = Futures.transformAsync(
        SettableFuture.<String>create(), function);
    try {
      future.get(1, TimeUnit.MILLISECONDS);
      fail();
    } catch (TimeoutException expected) {
    }
  }

  public void testTransformAsync_asyncFunction_error() throws InterruptedException {
    final Error error = new Error("deliberate");
    AsyncFunction<String, Integer> function = new AsyncFunction<String, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(String input) {
        throw error;
      }
    };
    SettableFuture<String> inputFuture = SettableFuture.create();
    ListenableFuture<Integer> outputFuture = Futures.transformAsync(inputFuture, function);
    inputFuture.set("value");
    try {
      outputFuture.get();
      fail("should have thrown error");
    } catch (ExecutionException e) {
      assertSame(error, e.getCause());
    }
  }

  public void testTransformAsync_asyncFunction_nullInsteadOfFuture() throws Exception {
    ListenableFuture<?> inputFuture = immediateFuture("a");
    ListenableFuture<?> chainedFuture =
        Futures.transformAsync(inputFuture, constantAsyncFunction(null));
    try {
      chainedFuture.get();
      fail();
    } catch (ExecutionException expected) {
      NullPointerException cause = (NullPointerException) expected.getCause();
      assertThat(cause).hasMessage("AsyncFunction.apply returned null instead of a Future. "
          + "Did you mean to return immediateFuture(null)?");
    }
  }

  @GwtIncompatible("threads")

  public void testTransformAsync_asyncFunction_cancelledWhileApplyingFunction()
      throws InterruptedException, ExecutionException {
    final CountDownLatch inFunction = new CountDownLatch(1);
    final CountDownLatch functionDone = new CountDownLatch(1);
    final SettableFuture<Integer> resultFuture = SettableFuture.create();
    AsyncFunction<String, Integer> function = new AsyncFunction<String, Integer>() {
      @Override
      public ListenableFuture<Integer> apply(String input) throws Exception {
        inFunction.countDown();
        functionDone.await();
        return resultFuture;
      }
    };
    SettableFuture<String> inputFuture = SettableFuture.create();
    ListenableFuture<Integer> future = Futures.transformAsync(
        inputFuture, function, Executors.newSingleThreadExecutor());
    inputFuture.set("value");
    inFunction.await();
    future.cancel(false);
    functionDone.countDown();
    try {
      future.get();
      fail();
    } catch (CancellationException expected) {
    }
    try {
      resultFuture.get();
      fail();
    } catch (CancellationException expected) {
    }
  }

  public void testDereference_genericsWildcard() throws Exception {
    ListenableFuture<?> inner = Futures.immediateFuture(null);
    ListenableFuture<ListenableFuture<?>> outer =
        Futures.<ListenableFuture<?>>immediateFuture(inner);
    ListenableFuture<?> dereferenced = Futures.dereference(outer);
    assertNull(dereferenced.get());
  }

  public void testDereference_genericsHierarchy() throws Exception {
    FooChild fooChild = new FooChild();
    ListenableFuture<FooChild> inner = Futures.immediateFuture(fooChild);
    ListenableFuture<ListenableFuture<FooChild>> outer = Futures.immediateFuture(inner);
    ListenableFuture<Foo> dereferenced = Futures.<Foo>dereference(outer);
    assertSame(fooChild, dereferenced.get());
  }

  public void testDereference_resultCancelsOuter() throws Exception {
    ListenableFuture<ListenableFuture<Foo>> outer = SettableFuture.create();
    ListenableFuture<Foo> dereferenced = Futures.dereference(outer);
    dereferenced.cancel(true);
    assertTrue(outer.isCancelled());
  }

  public void testDereference_resultCancelsInner() throws Exception {
    ListenableFuture<Foo> inner = SettableFuture.create();
    ListenableFuture<ListenableFuture<Foo>> outer = Futures.immediateFuture(inner);
    ListenableFuture<Foo> dereferenced = Futures.dereference(outer);
    dereferenced.cancel(true);
    assertTrue(inner.isCancelled());
  }

  public void testDereference_outerCancelsResult() throws Exception {
    ListenableFuture<ListenableFuture<Foo>> outer = SettableFuture.create();
    ListenableFuture<Foo> dereferenced = Futures.dereference(outer);
    outer.cancel(true);
    assertTrue(dereferenced.isCancelled());
  }

  public void testDereference_innerCancelsResult() throws Exception {
    ListenableFuture<Foo> inner = SettableFuture.create();
    ListenableFuture<ListenableFuture<Foo>> outer = Futures.immediateFuture(inner);
    ListenableFuture<Foo> dereferenced = Futures.dereference(outer);
    inner.cancel(true);
    assertTrue(dereferenced.isCancelled());
  }

  /**
   * Runnable which can be called a single time, and only after {@link #expectCall} is called.
   */
  // TODO(cpovirk): top-level class?
  static class SingleCallListener implements Runnable {

    private boolean expectCall = false;
    private final CountDownLatch calledCountDown =
        new CountDownLatch(1);

    @Override
    public void run() {
      assertTrue("Listener called before it was expected", expectCall);
      assertFalse("Listener called more than once", wasCalled());
      calledCountDown.countDown();
    }

    public void expectCall() {
      assertFalse("expectCall is already true", expectCall);
      expectCall = true;
    }

    public boolean wasCalled() {
      return calledCountDown.getCount() == 0;
    }

    public void waitForCall() throws InterruptedException {
      assertTrue("expectCall is false", expectCall);
      calledCountDown.await();
    }
  }

  public void testAllAsList() throws Exception {
    // Create input and output
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    SettableFuture<String> future3 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2, future3);

    // Attach a listener
    SingleCallListener listener = new SingleCallListener();
    compound.addListener(listener, directExecutor());

    // Satisfy each input and check the output
    assertFalse(compound.isDone());
    future1.set(DATA1);
    assertFalse(compound.isDone());
    future2.set(DATA2);
    assertFalse(compound.isDone());
    listener.expectCall();
    future3.set(DATA3);
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());

    List<String> results = compound.get();
    assertThat(results).containsExactly(DATA1, DATA2, DATA3).inOrder();
  }

  @GwtIncompatible("allAsList")
  public void testAllAsList_emptyList() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    listener.expectCall();
    List<ListenableFuture<String>> futures = ImmutableList.of();
    ListenableFuture<List<String>> compound = Futures.allAsList(futures);
    compound.addListener(listener, directExecutor());
    assertTrue(compound.isDone());
    assertTrue(compound.get().isEmpty());
    assertTrue(listener.wasCalled());
  }

  public void testAllAsList_emptyArray() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    listener.expectCall();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound = Futures.allAsList();
    compound.addListener(listener, directExecutor());
    assertTrue(compound.isDone());
    assertTrue(compound.get().isEmpty());
    assertTrue(listener.wasCalled());
  }

  public void testAllAsList_failure() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2);
    compound.addListener(listener, directExecutor());

    listener.expectCall();
    Throwable exception = new Throwable("failed1");
    future1.setException(exception);
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());
    future2.set("result2");

    try {
      compound.get();
      fail("Expected exception not thrown");
    } catch (ExecutionException e) {
      assertSame(exception, e.getCause());
    }
  }

  @GwtIncompatible("allAsList")
  public void testAllAsList_singleFailure() throws Exception {
    Throwable exception = new Throwable("failed");
    ListenableFuture<String> future = Futures.immediateFailedFuture(exception);
    ListenableFuture<List<String>> compound = Futures.allAsList(ImmutableList.of(future));

    try {
      compound.get();
      fail("Expected exception not thrown");
    } catch (ExecutionException e) {
      assertSame(exception, e.getCause());
    }
  }

  @GwtIncompatible("allAsList")
  public void testAllAsList_immediateFailure() throws Exception {
    Throwable exception = new Throwable("failed");
    ListenableFuture<String> future1 = Futures.immediateFailedFuture(exception);
    ListenableFuture<String> future2 = Futures.immediateFuture("results");
    ListenableFuture<List<String>> compound = Futures.allAsList(ImmutableList.of(future1, future2));

    try {
      compound.get();
      fail("Expected exception not thrown");
    } catch (ExecutionException e) {
      assertSame(exception, e.getCause());
    }
  }

  @GwtIncompatible("allAsList")
  public void testAllAsList_error() throws Exception {
    Error error = new Error("deliberate");
    SettableFuture<String> future1 = SettableFuture.create();
    ListenableFuture<String> future2 = Futures.immediateFuture("results");
    ListenableFuture<List<String>> compound = Futures.allAsList(ImmutableList.of(future1, future2));

    future1.setException(error);
    try {
      compound.get();
      fail("Expected error not set in compound future.");
    } catch (ExecutionException ee) {
      assertSame(error, ee.getCause());
    }
  }

  public void testAllAsList_cancelled() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2);
    compound.addListener(listener, directExecutor());

    listener.expectCall();
    future1.cancel(true);
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());
    future2.setException(new Throwable("failed2"));

    try {
      compound.get();
      fail("Expected exception not thrown");
    } catch (CancellationException e) {
      // Expected
    }
  }

  public void testAllAsList_resultCancelled() throws Exception {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2);

    future2.set(DATA2);
    assertFalse(compound.isDone());
    assertTrue(compound.cancel(false));
    assertTrue(compound.isCancelled());
    assertTrue(future1.isCancelled());
    assertFalse(future1.wasInterrupted());
  }

  public void testAllAsList_resultCancelledInterrupted_withSecondaryListFuture()
      throws Exception {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2);
    // There was a bug where the event listener for the combined future would
    // result in the sub-futures being cancelled without being interrupted.
    ListenableFuture<List<String>> otherCompound =
        Futures.allAsList(future1, future2);

    assertTrue(compound.cancel(true));
    assertTrue(future1.isCancelled());
    assertTrue(future1.wasInterrupted());
    assertTrue(future2.isCancelled());
    assertTrue(future2.wasInterrupted());
    assertTrue(otherCompound.isCancelled());
  }

  public void testAllAsList_resultCancelled_withSecondaryListFuture()
      throws Exception {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2);
    // This next call is "unused," but it is an important part of the test. Don't remove it!
    Futures.allAsList(future1, future2);

    assertTrue(compound.cancel(false));
    assertTrue(future1.isCancelled());
    assertFalse(future1.wasInterrupted());
    assertTrue(future2.isCancelled());
    assertFalse(future2.wasInterrupted());
  }

  public void testAllAsList_resultInterrupted() throws Exception {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2);

    future2.set(DATA2);
    assertFalse(compound.isDone());
    assertTrue(compound.cancel(true));
    assertTrue(compound.isCancelled());
    assertTrue(future1.isCancelled());
    assertTrue(future1.wasInterrupted());
  }

  /**
   * Test the case where the futures are fulfilled prior to constructing the ListFuture.  There was
   * a bug where the loop that connects a Listener to each of the futures would die on the last
   * loop-check as done() on ListFuture nulled out the variable being looped over (the list of
   * futures).
   */
  public void testAllAsList_doneFutures() throws Exception {
    // Create input and output
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    SettableFuture<String> future3 = SettableFuture.create();

    // Satisfy each input prior to creating compound and check the output
    future1.set(DATA1);
    future2.set(DATA2);
    future3.set(DATA3);

    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.allAsList(future1, future2, future3);

    // Attach a listener
    SingleCallListener listener = new SingleCallListener();
    listener.expectCall();
    compound.addListener(listener, directExecutor());

    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());

    List<String> results = compound.get();
    assertThat(results).containsExactly(DATA1, DATA2, DATA3).inOrder();
  }

  /**
   * A single non-error failure is not logged because it is reported via the output future.
   */
  @GwtIncompatible("TestLogHandler")
  @SuppressWarnings("unchecked")
  public void testAllAsList_logging_exception() throws Exception {
    try {
      Futures.allAsList(immediateFailedFuture(new MyException())).get();
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof MyException);
      assertEquals("Nothing should be logged", 0,
          combinedFutureLogHandler.getStoredLogRecords().size());
    }
  }

  /**
   * Ensure that errors are always logged.
   */
  @GwtIncompatible("TestLogHandler")
  @SuppressWarnings("unchecked")
  public void testAllAsList_logging_error() throws Exception {
    try {
      Futures.allAsList(immediateFailedFuture(new MyError())).get();
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof MyError);
      List<LogRecord> logged = combinedFutureLogHandler.getStoredLogRecords();
      assertEquals(1, logged.size());  // errors are always logged
      assertTrue(logged.get(0).getThrown() instanceof MyError);
    }
  }

  /**
   * All as list will log extra exceptions that occur after failure.
   */
  @GwtIncompatible("TestLogHandler")
  @SuppressWarnings("unchecked")
  public void testAllAsList_logging_multipleExceptions() throws Exception {
    try {
      Futures.allAsList(immediateFailedFuture(new MyException()),
          immediateFailedFuture(new MyException())).get();
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof MyException);
      List<LogRecord> logged = combinedFutureLogHandler.getStoredLogRecords();
      assertEquals(1, logged.size());  // the second failure is logged
      assertTrue(logged.get(0).getThrown() instanceof MyException);
    }
  }

  /**
   * The same exception happening on multiple futures should not be logged.
   */
  @GwtIncompatible("TestLogHandler")
  @SuppressWarnings("unchecked")
  public void testAllAsList_logging_same_exception() throws Exception {
    try {
      MyException sameInstance = new MyException();
      Futures.allAsList(immediateFailedFuture(sameInstance),
          immediateFailedFuture(sameInstance)).get();
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof MyException);
      assertEquals("Nothing should be logged", 0,
          combinedFutureLogHandler.getStoredLogRecords().size());
    }
  }

  /**
   * Different exceptions happening on multiple futures with the same cause should not be logged.
   */
  @GwtIncompatible("TestLogHandler")
  @SuppressWarnings("unchecked")
  public void testAllAsList_logging_same_cause() throws Exception {
    try {
      MyException exception1 = new MyException();
      MyException exception2 = new MyException();
      MyException exception3 = new MyException();

      MyException sameInstance = new MyException();
      exception1.initCause(sameInstance);
      exception2.initCause(sameInstance);
      exception3.initCause(exception2);
      Futures.allAsList(immediateFailedFuture(exception1),
          immediateFailedFuture(exception3)).get();
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof MyException);
      assertEquals("Nothing should be logged", 0,
          combinedFutureLogHandler.getStoredLogRecords().size());
    }
  }

  private static String createCombinedResult(Integer i, Boolean b) {
    return "-" + i + "-" + b;
  }

  /*
   * TODO(cpovirk): maybe pass around TestFuture instances instead of
   * ListenableFuture instances
   */

  /**
   * A future in {@link TestFutureBatch} that also has a name for debugging purposes and a {@code
   * finisher}, a task that will complete the future in some fashion when it is called, allowing for
   * testing both before and after the completion of the future.
   */
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final class TestFuture {

    final ListenableFuture<String> future;
    final String name;
    final Runnable finisher;

    TestFuture(
        ListenableFuture<String> future, String name, Runnable finisher) {
      this.future = future;
      this.name = name;
      this.finisher = finisher;
    }
  }

  /**
   * A collection of several futures, covering cancellation, success, and failure (both {@link
   * ExecutionException} and {@link RuntimeException}), both immediate and delayed. We use each
   * possible pair of these futures in {@link FuturesTest#runExtensiveMergerTest}.
   *
   * <p>Each test requires a new {@link TestFutureBatch} because we need new delayed futures each
   * time, as the old delayed futures were completed as part of the old test.
   */
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final class TestFutureBatch {

    final ListenableFuture<String> doneSuccess = immediateFuture("a");
    final ListenableFuture<String> doneFailed =
        immediateFailedFuture(new Exception());
    final SettableFuture<String> doneCancelled = SettableFuture.create();

    {
      doneCancelled.cancel(true);
    }

    final ListenableFuture<String> doneRuntimeException =
        new ForwardingListenableFuture<String>() {
          final ListenableFuture<String> delegate =
              immediateFuture("Should never be seen");

          @Override
          protected ListenableFuture<String> delegate() {
            return delegate;
          }

          @Override
          public String get() {
            throw new RuntimeException();
          }

          @Override
          public String get(long timeout, TimeUnit unit) {
            throw new RuntimeException();
          }
        };

    final SettableFuture<String> delayedSuccess = SettableFuture.create();
    final SettableFuture<String> delayedFailed = SettableFuture.create();
    final SettableFuture<String> delayedCancelled = SettableFuture.create();

    final SettableFuture<String> delegateForDelayedRuntimeException =
        SettableFuture.create();
    final ListenableFuture<String> delayedRuntimeException =
        new ForwardingListenableFuture<String>() {
          @Override
          protected ListenableFuture<String> delegate() {
            return delegateForDelayedRuntimeException;
          }

          @Override
          public String get() throws ExecutionException, InterruptedException {
            delegateForDelayedRuntimeException.get();
            throw new RuntimeException();
          }

          @Override
          public String get(long timeout, TimeUnit unit) throws
              ExecutionException, InterruptedException, TimeoutException {
            delegateForDelayedRuntimeException.get(timeout, unit);
            throw new RuntimeException();
          }
        };

    final Runnable doNothing = new Runnable() {
      @Override
      public void run() {
      }
    };
    final Runnable finishSuccess = new Runnable() {
      @Override
      public void run() {
        delayedSuccess.set("b");
      }
    };
    final Runnable finishFailure = new Runnable() {
      @Override
      public void run() {
        delayedFailed.setException(new Exception());
      }
    };
    final Runnable finishCancelled = new Runnable() {
      @Override
      public void run() {
        delayedCancelled.cancel(true);
      }
    };
    final Runnable finishRuntimeException = new Runnable() {
      @Override
      public void run() {
        delegateForDelayedRuntimeException.set("Should never be seen");
      }
    };

    /**
     * All the futures, together with human-readable names for use by {@link #smartToString}.
     */
    final ImmutableList<TestFuture> allFutures =
        ImmutableList.of(new TestFuture(doneSuccess, "doneSuccess", doNothing),
            new TestFuture(doneFailed, "doneFailed", doNothing),
            new TestFuture(doneCancelled, "doneCancelled", doNothing),
            new TestFuture(
                doneRuntimeException, "doneRuntimeException", doNothing),
            new TestFuture(delayedSuccess, "delayedSuccess", finishSuccess),
            new TestFuture(delayedFailed, "delayedFailed", finishFailure),
            new TestFuture(
                delayedCancelled, "delayedCancelled", finishCancelled),
            new TestFuture(delayedRuntimeException, "delayedRuntimeException",
                finishRuntimeException));

    final Function<ListenableFuture<String>, String> nameGetter =
        new Function<ListenableFuture<String>, String>() {
          @Override
          public String apply(ListenableFuture<String> input) {
            for (TestFuture future : allFutures) {
              if (future.future == input) {
                return future.name;
              }
            }
            throw new IllegalArgumentException(input.toString());
          }
        };

    static boolean intersect(Set<?> a, Set<?> b) {
      return !Sets.intersection(a, b).isEmpty();
    }

    /**
     * Like {@code inputs.toString()}, but with the nonsense {@code toString} representations
     * replaced with the name of each future from {@link #allFutures}.
     */
    String smartToString(ImmutableSet<ListenableFuture<String>> inputs) {
      Iterable<String> inputNames = Iterables.transform(inputs, nameGetter);
      return Joiner.on(", ").join(inputNames);
    }

    void smartAssertTrue(ImmutableSet<ListenableFuture<String>> inputs,
        Exception cause, boolean expression) {
      if (!expression) {
        throw failureWithCause(cause, smartToString(inputs));
      }
    }

    boolean hasDelayed(ListenableFuture<String> a, ListenableFuture<String> b) {
      ImmutableSet<ListenableFuture<String>> inputs = ImmutableSet.of(a, b);
      return intersect(inputs, ImmutableSet.of(
          delayedSuccess, delayedFailed, delayedCancelled,
          delayedRuntimeException));
    }

    void assertHasDelayed(
        ListenableFuture<String> a, ListenableFuture<String> b, Exception e) {
      ImmutableSet<ListenableFuture<String>> inputs = ImmutableSet.of(a, b);
      smartAssertTrue(inputs, e, hasDelayed(a, b));
    }

    void assertHasFailure(
        ListenableFuture<String> a, ListenableFuture<String> b, Exception e) {
      ImmutableSet<ListenableFuture<String>> inputs = ImmutableSet.of(a, b);
      smartAssertTrue(inputs, e, intersect(inputs, ImmutableSet.of(doneFailed,
          doneRuntimeException, delayedFailed, delayedRuntimeException)));
    }

    void assertHasCancel(
        ListenableFuture<String> a, ListenableFuture<String> b, Exception e) {
      ImmutableSet<ListenableFuture<String>> inputs = ImmutableSet.of(a, b);
      smartAssertTrue(inputs, e,
          intersect(inputs, ImmutableSet.of(doneCancelled, delayedCancelled)));
    }

    void assertHasImmediateFailure(
        ListenableFuture<String> a, ListenableFuture<String> b, Exception e) {
      ImmutableSet<ListenableFuture<String>> inputs = ImmutableSet.of(a, b);
      smartAssertTrue(inputs, e, intersect(
          inputs, ImmutableSet.of(doneFailed, doneRuntimeException)));
    }

    void assertHasImmediateCancel(
        ListenableFuture<String> a, ListenableFuture<String> b, Exception e) {
      ImmutableSet<ListenableFuture<String>> inputs = ImmutableSet.of(a, b);
      smartAssertTrue(inputs, e,
          intersect(inputs, ImmutableSet.of(doneCancelled)));
    }
  }

  /**
   * {@link Futures#allAsList(Iterable)} or {@link Futures#successfulAsList(Iterable)}, hidden
   * behind a common interface for testing.
   */
  @GwtIncompatible("used only in GwtIncompatible tests")
  private interface Merger {

    ListenableFuture<List<String>> merged(
        ListenableFuture<String> a, ListenableFuture<String> b);

    Merger allMerger = new Merger() {
      @Override
      public ListenableFuture<List<String>> merged(
          ListenableFuture<String> a, ListenableFuture<String> b) {
        return allAsList(ImmutableSet.of(a, b));
      }
    };
    Merger successMerger = new Merger() {
      @Override
      public ListenableFuture<List<String>> merged(
          ListenableFuture<String> a, ListenableFuture<String> b) {
        return successfulAsList(ImmutableSet.of(a, b));
      }
    };
  }

  /**
   * Very rough equivalent of a timed get, produced by calling the no-arg get method in another
   * thread and waiting a short time for it.
   *
   * <p>We need this to test the behavior of no-arg get methods without hanging the main test thread
   * forever in the case of failure.
   */
  @GwtIncompatible("threads")
  static <V> V pseudoTimedGetUninterruptibly(
      final Future<V> input, long timeout, TimeUnit unit)
      throws ExecutionException, TimeoutException {
    ExecutorService executor = newSingleThreadExecutor();
    Future<V> waiter = executor.submit(new Callable<V>() {
      @Override
      public V call() throws Exception {
        return input.get();
      }
    });

    try {
      return getUninterruptibly(waiter, timeout, unit);
    } catch (ExecutionException e) {
      propagateIfInstanceOf(e.getCause(), ExecutionException.class);
      propagateIfInstanceOf(e.getCause(), CancellationException.class);
      throw failureWithCause(e, "Unexpected exception");
    } finally {
      executor.shutdownNow();
      // TODO(cpovirk: assertTrue(awaitTerminationUninterruptibly(executor, 10, SECONDS));
    }
  }

  /**
   * For each possible pair of futures from {@link TestFutureBatch}, for each possible completion
   * order of those futures, test that various get calls (timed before future completion, untimed
   * before future completion, and untimed after future completion) return or throw the proper
   * values.
   */
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static void runExtensiveMergerTest(Merger merger)
      throws InterruptedException {
    int inputCount = new TestFutureBatch().allFutures.size();

    for (int i = 0; i < inputCount; i++) {
      for (int j = 0; j < inputCount; j++) {
        for (boolean iBeforeJ : new boolean[]{true, false}) {
          TestFutureBatch inputs = new TestFutureBatch();
          ListenableFuture<String> iFuture = inputs.allFutures.get(i).future;
          ListenableFuture<String> jFuture = inputs.allFutures.get(j).future;
          ListenableFuture<List<String>> future =
              merger.merged(iFuture, jFuture);

          // Test timed get before we've completed any delayed futures.
          try {
            List<String> result = future.get(0, MILLISECONDS);
            assertTrue("Got " + result,
                Arrays.asList("a", null).containsAll(result));
          } catch (CancellationException e) {
            assertTrue(merger == Merger.allMerger);
            inputs.assertHasImmediateCancel(iFuture, jFuture, e);
          } catch (ExecutionException e) {
            assertTrue(merger == Merger.allMerger);
            inputs.assertHasImmediateFailure(iFuture, jFuture, e);
          } catch (TimeoutException e) {
            inputs.assertHasDelayed(iFuture, jFuture, e);
          }

          // Same tests with pseudoTimedGet.
          try {
            List<String> result = conditionalPseudoTimedGetUninterruptibly(
                inputs, iFuture, jFuture, future, 20, MILLISECONDS);
            assertTrue("Got " + result,
                Arrays.asList("a", null).containsAll(result));
          } catch (CancellationException e) {
            assertTrue(merger == Merger.allMerger);
            inputs.assertHasImmediateCancel(iFuture, jFuture, e);
          } catch (ExecutionException e) {
            assertTrue(merger == Merger.allMerger);
            inputs.assertHasImmediateFailure(iFuture, jFuture, e);
          } catch (TimeoutException e) {
            inputs.assertHasDelayed(iFuture, jFuture, e);
          }

          // Finish the two futures in the currently specified order:
          inputs.allFutures.get(iBeforeJ ? i : j).finisher.run();
          inputs.allFutures.get(iBeforeJ ? j : i).finisher.run();

          // Test untimed get now that we've completed any delayed futures.
          try {
            List<String> result = future.get();
            assertTrue("Got " + result,
                Arrays.asList("a", "b", null).containsAll(result));
          } catch (CancellationException e) {
            assertTrue(merger == Merger.allMerger);
            inputs.assertHasCancel(iFuture, jFuture, e);
          } catch (ExecutionException e) {
            assertTrue(merger == Merger.allMerger);
            inputs.assertHasFailure(iFuture, jFuture, e);
          }
        }
      }
    }
  }

  /**
   * Call the non-timed {@link Future#get()} in a way that allows us to abort if it's expected to
   * hang forever. More precisely, if it's expected to return, we simply call it[*], but if it's
   * expected to hang (because one of the input futures that we know makes it up isn't done yet),
   * then we call it in a separate thread (using pseudoTimedGet). The result is that we wait as long
   * as necessary when the method is expected to return (at the cost of hanging forever if there is
   * a bug in the class under test) but that we time out fairly promptly when the method is expected
   * to hang (possibly too quickly, but too-quick failures should be very unlikely, given that we
   * used to bail after 20ms during the expected-successful tests, and there we saw a failure rate
   * of ~1/5000, meaning that the other thread's get() call nearly always completes within 20ms if
   * it's going to complete at all).
   *
   * [*] To avoid hangs, I've disabled the in-thread calls. This makes the test take (very roughly)
   * 2.5s longer. (2.5s is also the maximum length of time we will wait for a timed get that is
   * expected to succeed; the fact that the numbers match is only a coincidence.) See the comment
   * below for how to restore the fast but hang-y version.
   */
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static List<String> conditionalPseudoTimedGetUninterruptibly(
      TestFutureBatch inputs,
      ListenableFuture<String> iFuture,
      ListenableFuture<String> jFuture,
      ListenableFuture<List<String>> future,
      int timeout,
      TimeUnit unit)
      throws ExecutionException, TimeoutException {
    /*
     * For faster tests (that may hang indefinitely if the class under test has
     * a bug!), switch the second branch to call untimed future.get() instead of
     * pseudoTimedGet.
     */
    return (inputs.hasDelayed(iFuture, jFuture))
        ? pseudoTimedGetUninterruptibly(future, timeout, unit)
        : pseudoTimedGetUninterruptibly(future, 2500, MILLISECONDS);
  }

  @GwtIncompatible("allAsList")
  public void testAllAsList_extensive() throws InterruptedException {
    runExtensiveMergerTest(Merger.allMerger);
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_extensive() throws InterruptedException {
    runExtensiveMergerTest(Merger.successMerger);
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList() throws Exception {
    // Create input and output
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    SettableFuture<String> future3 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2, future3);

    // Attach a listener
    SingleCallListener listener = new SingleCallListener();
    compound.addListener(listener, directExecutor());

    // Satisfy each input and check the output
    assertFalse(compound.isDone());
    future1.set(DATA1);
    assertFalse(compound.isDone());
    future2.set(DATA2);
    assertFalse(compound.isDone());
    listener.expectCall();
    future3.set(DATA3);
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());

    List<String> results = compound.get();
    assertThat(results).containsExactly(DATA1, DATA2, DATA3).inOrder();
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_emptyList() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    listener.expectCall();
    List<ListenableFuture<String>> futures = ImmutableList.of();
    ListenableFuture<List<String>> compound = Futures.successfulAsList(futures);
    compound.addListener(listener, directExecutor());
    assertTrue(compound.isDone());
    assertTrue(compound.get().isEmpty());
    assertTrue(listener.wasCalled());
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_emptyArray() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    listener.expectCall();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound = Futures.successfulAsList();
    compound.addListener(listener, directExecutor());
    assertTrue(compound.isDone());
    assertTrue(compound.get().isEmpty());
    assertTrue(listener.wasCalled());
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_partialFailure() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2);
    compound.addListener(listener, directExecutor());

    assertFalse(compound.isDone());
    future1.setException(new Throwable("failed1"));
    assertFalse(compound.isDone());
    listener.expectCall();
    future2.set(DATA2);
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());

    List<String> results = compound.get();
    assertThat(results).containsExactly(null, DATA2).inOrder();
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_totalFailure() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2);
    compound.addListener(listener, directExecutor());

    assertFalse(compound.isDone());
    future1.setException(new Throwable("failed1"));
    assertFalse(compound.isDone());
    listener.expectCall();
    future2.setException(new Throwable("failed2"));
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());

    List<String> results = compound.get();
    assertThat(results).containsExactly(null, null).inOrder();
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_cancelled() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2);
    compound.addListener(listener, directExecutor());

    assertFalse(compound.isDone());
    future1.cancel(true);
    assertFalse(compound.isDone());
    listener.expectCall();
    future2.set(DATA2);
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());

    List<String> results = compound.get();
    assertThat(results).containsExactly(null, DATA2).inOrder();
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_resultCancelled() throws Exception {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2);

    future2.set(DATA2);
    assertFalse(compound.isDone());
    assertTrue(compound.cancel(false));
    assertTrue(compound.isCancelled());
    assertTrue(future1.isCancelled());
    assertFalse(future1.wasInterrupted());
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_resultCancelledRacingInputDone()
      throws Exception {
    /*
     * The IllegalStateException that we're testing for is caught by
     * ExecutionList and logged rather than allowed to propagate. We need to
     * turn that back into a failure.
     */
    Handler throwingHandler = new Handler() {
      @Override
      public void publish(@Nullable LogRecord record) {
        AssertionFailedError error = new AssertionFailedError();
        error.initCause(record.getThrown());
        throw error;
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };

    ExecutionList.log.addHandler(throwingHandler);
    try {
      doTestSuccessfulAsList_resultCancelledRacingInputDone();
    } finally {
      ExecutionList.log.removeHandler(throwingHandler);
    }
  }

  @GwtIncompatible("successfulAsList")
  private static void doTestSuccessfulAsList_resultCancelledRacingInputDone()
      throws Exception {
    // Simple (combined.cancel -> input.cancel -> setOneValue):
    Futures.successfulAsList(ImmutableList.of(SettableFuture.create()))
        .cancel(true);

    /*
     * Complex (combined.cancel -> input.cancel -> other.set -> setOneValue),
     * to show that this isn't just about problems with the input future we just
     * cancelled:
     */
    final SettableFuture<String> future1 = SettableFuture.create();
    final SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2);

    future1.addListener(new Runnable() {
      @Override
      public void run() {
        assertTrue(future1.isCancelled());
        /*
         * This test relies on behavior that's unspecified but currently
         * guaranteed by the implementation: Cancellation of inputs is
         * performed in the order they were provided to the constructor. Verify
         * that as a sanity check:
         */
        assertFalse(future2.isCancelled());
        // Now attempt to trigger the exception:
        future2.set(DATA2);
      }
    }, directExecutor());
    assertTrue(compound.cancel(false));
    assertTrue(compound.isCancelled());
    assertTrue(future1.isCancelled());
    assertFalse(future2.isCancelled());

    try {
      compound.get();
      fail("Expected exception not thrown");
    } catch (CancellationException e) {
      // Expected
    }
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_resultInterrupted() throws Exception {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2);

    future2.set(DATA2);
    assertFalse(compound.isDone());
    assertTrue(compound.cancel(true));
    assertTrue(compound.isCancelled());
    assertTrue(future1.isCancelled());
    assertTrue(future1.wasInterrupted());
  }

  @GwtIncompatible("successfulAsList")
  public void testSuccessfulAsList_mixed() throws Exception {
    SingleCallListener listener = new SingleCallListener();
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    SettableFuture<String> future3 = SettableFuture.create();
    @SuppressWarnings("unchecked") // array is never modified
        ListenableFuture<List<String>> compound =
        Futures.successfulAsList(future1, future2, future3);
    compound.addListener(listener, directExecutor());

    // First is cancelled, second fails, third succeeds
    assertFalse(compound.isDone());
    future1.cancel(true);
    assertFalse(compound.isDone());
    future2.setException(new Throwable("failed2"));
    assertFalse(compound.isDone());
    listener.expectCall();
    future3.set(DATA3);
    assertTrue(compound.isDone());
    assertTrue(listener.wasCalled());

    List<String> results = compound.get();
    assertThat(results).containsExactly(null, null, DATA3).inOrder();
  }

  /**
   * Non-Error exceptions are never logged.
   */
  @GwtIncompatible("TestLogHandler")
  @SuppressWarnings("unchecked")
  public void testSuccessfulAsList_logging_exception() throws Exception {
    assertEquals(Lists.newArrayList((Object) null),
        Futures.successfulAsList(
            immediateFailedFuture(new MyException())).get());
    assertEquals("Nothing should be logged", 0,
        combinedFutureLogHandler.getStoredLogRecords().size());

    // Not even if there are a bunch of failures.
    assertEquals(Lists.newArrayList(null, null, null),
        Futures.successfulAsList(
            immediateFailedFuture(new MyException()),
            immediateFailedFuture(new MyException()),
            immediateFailedFuture(new MyException())).get());
    assertEquals("Nothing should be logged", 0,
        combinedFutureLogHandler.getStoredLogRecords().size());
  }

  /**
   * Ensure that errors are always logged.
   */
  @GwtIncompatible("TestLogHandler")
  @SuppressWarnings("unchecked")
  public void testSuccessfulAsList_logging_error() throws Exception {
    assertEquals(Lists.newArrayList((Object) null),
        Futures.successfulAsList(
            immediateFailedFuture(new MyError())).get());
    List<LogRecord> logged = combinedFutureLogHandler.getStoredLogRecords();
    assertEquals(1, logged.size());  // errors are always logged
    assertTrue(logged.get(0).getThrown() instanceof MyError);
  }

  @GwtIncompatible("nonCancellationPropagating")
  public void testNonCancellationPropagating_successful() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    ListenableFuture<Foo> wrapper = Futures.nonCancellationPropagating(input);
    Foo foo = new Foo();

    assertFalse(wrapper.isDone());
    input.set(foo);
    assertTrue(wrapper.isDone());
    assertSame(foo, wrapper.get());
  }

  @GwtIncompatible("nonCancellationPropagating")
  public void testNonCancellationPropagating_failure() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    ListenableFuture<Foo> wrapper = Futures.nonCancellationPropagating(input);
    Throwable failure = new Throwable("thrown");

    assertFalse(wrapper.isDone());
    input.setException(failure);
    assertTrue(wrapper.isDone());
    try {
      wrapper.get();
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertSame(failure, e.getCause());
    }
  }

  @GwtIncompatible("nonCancellationPropagating")
  public void testNonCancellationPropagating_delegateCancelled() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    ListenableFuture<Foo> wrapper = Futures.nonCancellationPropagating(input);

    assertFalse(wrapper.isDone());
    assertTrue(input.cancel(false));
    assertTrue(wrapper.isCancelled());
  }

  @GwtIncompatible("nonCancellationPropagating")
  public void testNonCancellationPropagating_doesNotPropagate() throws Exception {
    SettableFuture<Foo> input = SettableFuture.create();
    ListenableFuture<Foo> wrapper = Futures.nonCancellationPropagating(input);

    assertTrue(wrapper.cancel(true));
    assertTrue(wrapper.isCancelled());
    assertTrue(wrapper.isDone());
    assertFalse(input.isCancelled());
    assertFalse(input.isDone());
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static class TestException extends Exception {

    TestException(@Nullable Throwable cause) {
      super(cause);
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Function<Exception, TestException> mapper =
      new Function<Exception, TestException>() {
        @Override
        public TestException apply(Exception from) {
          if (from instanceof ExecutionException) {
            return new TestException(from.getCause());
          } else {
            assertTrue("got " + from.getClass(),
                from instanceof InterruptedException
                    || from instanceof CancellationException);
            return new TestException(from);
          }
        }
      };

  @GwtIncompatible("makeChecked")
  public void testMakeChecked_mapsExecutionExceptions() throws Exception {
    SettableFuture<String> future = SettableFuture.create();

    CheckedFuture<String, TestException> checked = Futures.makeChecked(
        future, mapper);

    future.setException(new IOException("checked"));

    assertTrue(checked.isDone());
    assertFalse(checked.isCancelled());

    try {
      checked.get();
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof IOException);
    }

    try {
      checked.get(5, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof IOException);
    }

    try {
      checked.checkedGet();
      fail();
    } catch (TestException e) {
      assertTrue(e.getCause() instanceof IOException);
    }

    try {
      checked.checkedGet(5, TimeUnit.SECONDS);
      fail();
    } catch (TestException e) {
      assertTrue(e.getCause() instanceof IOException);
    }
  }

  @GwtIncompatible("makeChecked")
  public void testMakeChecked_mapsInterruption() throws Exception {
    SettableFuture<String> future = SettableFuture.create();

    CheckedFuture<String, TestException> checked = Futures.makeChecked(
        future, mapper);

    Thread.currentThread().interrupt();

    try {
      checked.get();
      fail();
    } catch (InterruptedException e) {
      // Expected.
    }

    Thread.currentThread().interrupt();

    try {
      checked.get(5, TimeUnit.SECONDS);
      fail();
    } catch (InterruptedException e) {
      // Expected.
    }

    Thread.currentThread().interrupt();

    try {
      checked.checkedGet();
      fail();
    } catch (TestException e) {
      assertTrue(e.getCause() instanceof InterruptedException);
    }

    Thread.currentThread().interrupt();

    try {
      checked.checkedGet(5, TimeUnit.SECONDS);
      fail();
    } catch (TestException e) {
      assertTrue(e.getCause() instanceof InterruptedException);
    }
  }

  @GwtIncompatible("makeChecked")
  public void testMakeChecked_mapsCancellation() throws Exception {
    SettableFuture<String> future = SettableFuture.create();

    CheckedFuture<String, TestException> checked = Futures.makeChecked(
        future, mapper);

    assertTrue(future.cancel(true)); // argument is ignored

    try {
      checked.get();
      fail();
    } catch (CancellationException expected) {
    }

    try {
      checked.get(5, TimeUnit.SECONDS);
      fail();
    } catch (CancellationException expected) {
    }

    try {
      checked.checkedGet();
      fail();
    } catch (TestException expected) {
      assertTrue(expected.getCause() instanceof CancellationException);
    }

    try {
      checked.checkedGet(5, TimeUnit.SECONDS);
      fail();
    } catch (TestException expected) {
      assertTrue(expected.getCause() instanceof CancellationException);
    }
  }

  @GwtIncompatible("makeChecked")
  public void testMakeChecked_propagatesFailedMappers() throws Exception {
    SettableFuture<String> future = SettableFuture.create();

    CheckedFuture<String, TestException> checked = Futures.makeChecked(
        future, new Function<Exception, TestException>() {
          @Override
          public TestException apply(Exception from) {
            throw new NullPointerException();
          }
        });

    future.setException(new Exception("failed"));

    try {
      checked.checkedGet();
      fail();
    } catch (NullPointerException expected) {
    }

    try {
      checked.checkedGet(5, TimeUnit.SECONDS);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @GwtIncompatible("makeChecked")

  public void testMakeChecked_listenersRunOnceCompleted() throws Exception {
    SettableFuture<String> future = SettableFuture.create();

    CheckedFuture<String, TestException> checked = Futures.makeChecked(
        future, new Function<Exception, TestException>() {
          @Override
          public TestException apply(Exception from) {
            throw new NullPointerException();
          }
        });

    ListenableFutureTester tester = new ListenableFutureTester(checked);
    tester.setUp();
    future.set(DATA1);
    tester.testCompletedFuture(DATA1);
    tester.tearDown();
  }

  @GwtIncompatible("makeChecked")

  public void testMakeChecked_listenersRunOnCancel() throws Exception {
    SettableFuture<String> future = SettableFuture.create();

    CheckedFuture<String, TestException> checked = Futures.makeChecked(
        future, new Function<Exception, TestException>() {
          @Override
          public TestException apply(Exception from) {
            throw new NullPointerException();
          }
        });

    ListenableFutureTester tester = new ListenableFutureTester(checked);
    tester.setUp();
    future.cancel(true); // argument is ignored
    tester.testCancelledFuture();
    tester.tearDown();
  }

  @GwtIncompatible("makeChecked")

  public void testMakeChecked_listenersRunOnFailure() throws Exception {
    SettableFuture<String> future = SettableFuture.create();

    CheckedFuture<String, TestException> checked = Futures.makeChecked(
        future, new Function<Exception, TestException>() {
          @Override
          public TestException apply(Exception from) {
            throw new NullPointerException();
          }
        });

    ListenableFutureTester tester = new ListenableFutureTester(checked);
    tester.setUp();
    future.setException(new Exception("failed"));
    tester.testFailedFuture("failed");
    tester.tearDown();
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private interface MapperFunction extends Function<Throwable, Exception> {

  }

  private static final class OtherThrowable extends Throwable {

  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Exception CHECKED_EXCEPTION = new Exception("mymessage");
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Future<String> FAILED_FUTURE_CHECKED_EXCEPTION =
      immediateFailedFuture(CHECKED_EXCEPTION);
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final RuntimeException UNCHECKED_EXCEPTION =
      new RuntimeException("mymessage");
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Future<String> FAILED_FUTURE_UNCHECKED_EXCEPTION =
      immediateFailedFuture(UNCHECKED_EXCEPTION);
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final RuntimeException RUNTIME_EXCEPTION =
      new RuntimeException();
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final OtherThrowable OTHER_THROWABLE = new OtherThrowable();
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Future<String> FAILED_FUTURE_OTHER_THROWABLE =
      immediateFailedFuture(OTHER_THROWABLE);
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Error ERROR = new Error("mymessage");
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Future<String> FAILED_FUTURE_ERROR =
      immediateFailedFuture(ERROR);
  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final Future<String> RUNTIME_EXCEPTION_FUTURE =
      new SimpleForwardingFuture<String>(FAILED_FUTURE_CHECKED_EXCEPTION) {
        @Override
        public String get() {
          throw RUNTIME_EXCEPTION;
        }

        @Override
        public String get(long timeout, TimeUnit unit) {
          throw RUNTIME_EXCEPTION;
        }
      };

  // Boring untimed-get tests:

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_success()
      throws TwoArgConstructorException {
    assertEquals("foo",
        get(immediateFuture("foo"), TwoArgConstructorException.class));
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_interrupted() {
    SettableFuture<String> future = SettableFuture.create();
    Thread.currentThread().interrupt();
    try {
      get(future, TwoArgConstructorException.class);
      fail();
    } catch (TwoArgConstructorException expected) {
      assertTrue(expected.getCause() instanceof InterruptedException);
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_cancelled()
      throws TwoArgConstructorException {
    SettableFuture<String> future = SettableFuture.create();
    future.cancel(true);
    try {
      get(future, TwoArgConstructorException.class);
      fail();
    } catch (CancellationException expected) {
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_ExecutionExceptionChecked() {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION, TwoArgConstructorException.class);
      fail();
    } catch (TwoArgConstructorException expected) {
      assertEquals(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_ExecutionExceptionUnchecked()
      throws TwoArgConstructorException {
    try {
      get(FAILED_FUTURE_UNCHECKED_EXCEPTION, TwoArgConstructorException.class);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertEquals(UNCHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_ExecutionExceptionError()
      throws TwoArgConstructorException {
    try {
      get(FAILED_FUTURE_ERROR, TwoArgConstructorException.class);
      fail();
    } catch (ExecutionError expected) {
      assertEquals(ERROR, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_ExecutionExceptionOtherThrowable() {
    try {
      get(FAILED_FUTURE_OTHER_THROWABLE, TwoArgConstructorException.class);
      fail();
    } catch (TwoArgConstructorException expected) {
      assertEquals(OTHER_THROWABLE, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_RuntimeException()
      throws TwoArgConstructorException {
    try {
      get(RUNTIME_EXCEPTION_FUTURE, TwoArgConstructorException.class);
      fail();
    } catch (RuntimeException expected) {
      assertEquals(RUNTIME_EXCEPTION, expected);
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_badExceptionConstructor_wrapsOriginalChecked() throws Exception {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION, ExceptionWithBadConstructor.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertSame(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_withGoodAndBadExceptionConstructor() throws Exception {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION, ExceptionWithGoodAndBadConstructor.class);
      fail();
    } catch (ExceptionWithGoodAndBadConstructor expected) {
      assertSame(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  // Boring timed-get tests:

  @GwtIncompatible("Futures.get")
  public void testGetTimed_success()
      throws TwoArgConstructorException {
    assertEquals("foo", get(
        immediateFuture("foo"), 0, SECONDS, TwoArgConstructorException.class));
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_interrupted() {
    SettableFuture<String> future = SettableFuture.create();
    Thread.currentThread().interrupt();
    try {
      get(future, 0, SECONDS, TwoArgConstructorException.class);
      fail();
    } catch (TwoArgConstructorException expected) {
      assertTrue(expected.getCause() instanceof InterruptedException);
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_cancelled()
      throws TwoArgConstructorException {
    SettableFuture<String> future = SettableFuture.create();
    future.cancel(true);
    try {
      get(future, 0, SECONDS, TwoArgConstructorException.class);
      fail();
    } catch (CancellationException expected) {
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_ExecutionExceptionChecked() {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION, 0, SECONDS,
          TwoArgConstructorException.class);
      fail();
    } catch (TwoArgConstructorException expected) {
      assertEquals(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_ExecutionExceptionUnchecked()
      throws TwoArgConstructorException {
    try {
      get(FAILED_FUTURE_UNCHECKED_EXCEPTION, 0, SECONDS,
          TwoArgConstructorException.class);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertEquals(UNCHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_ExecutionExceptionError()
      throws TwoArgConstructorException {
    try {
      get(FAILED_FUTURE_ERROR, 0, SECONDS, TwoArgConstructorException.class);
      fail();
    } catch (ExecutionError expected) {
      assertEquals(ERROR, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_ExecutionExceptionOtherThrowable() {
    try {
      get(FAILED_FUTURE_OTHER_THROWABLE, 0, SECONDS,
          TwoArgConstructorException.class);
      fail();
    } catch (TwoArgConstructorException expected) {
      assertEquals(OTHER_THROWABLE, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_RuntimeException()
      throws TwoArgConstructorException {
    try {
      get(RUNTIME_EXCEPTION_FUTURE, 0, SECONDS,
          TwoArgConstructorException.class);
      fail();
    } catch (RuntimeException expected) {
      assertEquals(RUNTIME_EXCEPTION, expected);
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_TimeoutException() {
    SettableFuture<String> future = SettableFuture.create();
    try {
      get(future, 0, SECONDS, TwoArgConstructorException.class);
      fail();
    } catch (TwoArgConstructorException expected) {
      assertTrue(expected.getCause() instanceof TimeoutException);
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_badExceptionConstructor_wrapsOriginalChecked() throws Exception {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION, 1, TimeUnit.SECONDS, ExceptionWithBadConstructor.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertSame(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetTimed_withGoodAndBadExceptionConstructor() throws Exception {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION, 1, TimeUnit.SECONDS,
          ExceptionWithGoodAndBadConstructor.class);
      fail();
    } catch (ExceptionWithGoodAndBadConstructor expected) {
      assertSame(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  // Boring getUnchecked tests:

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_success() {
    assertEquals("foo", getUnchecked(immediateFuture("foo")));
  }

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_interrupted() {
    Thread.currentThread().interrupt();
    try {
      assertEquals("foo", getUnchecked(immediateFuture("foo")));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_cancelled() {
    SettableFuture<String> future = SettableFuture.create();
    future.cancel(true);
    try {
      getUnchecked(future);
      fail();
    } catch (CancellationException expected) {
    }
  }

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_ExecutionExceptionChecked() {
    try {
      getUnchecked(FAILED_FUTURE_CHECKED_EXCEPTION);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertEquals(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_ExecutionExceptionUnchecked() {
    try {
      getUnchecked(FAILED_FUTURE_UNCHECKED_EXCEPTION);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertEquals(UNCHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_ExecutionExceptionError() {
    try {
      getUnchecked(FAILED_FUTURE_ERROR);
      fail();
    } catch (ExecutionError expected) {
      assertEquals(ERROR, expected.getCause());
    }
  }

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_ExecutionExceptionOtherThrowable() {
    try {
      getUnchecked(FAILED_FUTURE_OTHER_THROWABLE);
      fail();
    } catch (UncheckedExecutionException expected) {
      assertEquals(OTHER_THROWABLE, expected.getCause());
    }
  }

  @GwtIncompatible("getUnchecked")
  public void testGetUnchecked_RuntimeException() {
    try {
      getUnchecked(RUNTIME_EXCEPTION_FUTURE);
      fail();
    } catch (RuntimeException expected) {
      assertEquals(RUNTIME_EXCEPTION, expected);
    }
  }

  // Edge case tests of the exception-construction code through untimed get():

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_exceptionClassIsRuntimeException() {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION,
          TwoArgConstructorRuntimeException.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_exceptionClassSomePublicConstructors() {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION,
          ExceptionWithSomePrivateConstructors.class);
      fail();
    } catch (ExceptionWithSomePrivateConstructors expected) {
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_exceptionClassNoPublicConstructor()
      throws ExceptionWithPrivateConstructor {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION,
          ExceptionWithPrivateConstructor.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_exceptionClassPublicConstructorWrongType()
      throws ExceptionWithWrongTypesConstructor {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION,
          ExceptionWithWrongTypesConstructor.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_exceptionClassPrefersStringConstructor() {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION,
          ExceptionWithManyConstructors.class);
      fail();
    } catch (ExceptionWithManyConstructors expected) {
      assertTrue(expected.usedExpectedConstructor);
    }
  }

  @GwtIncompatible("Futures.get")
  public void testGetUntimed_exceptionClassUsedInitCause() {
    try {
      get(FAILED_FUTURE_CHECKED_EXCEPTION,
          ExceptionWithoutThrowableConstructor.class);
      fail();
    } catch (ExceptionWithoutThrowableConstructor expected) {
      assertThat(expected.getMessage()).contains("mymessage");
      assertEquals(CHECKED_EXCEPTION, expected.getCause());
    }
  }

  @GwtIncompatible("inCompletionOrder")
  public void testCompletionOrder() throws Exception {
    SettableFuture<Long> future1 = SettableFuture.create();
    SettableFuture<Long> future2 = SettableFuture.create();
    SettableFuture<Long> future3 = SettableFuture.create();
    SettableFuture<Long> future4 = SettableFuture.create();
    SettableFuture<Long> future5 = SettableFuture.create();

    ImmutableList<ListenableFuture<Long>> futures = Futures.inCompletionOrder(
        ImmutableList.<ListenableFuture<Long>>of(future1, future2, future3, future4, future5));
    future2.set(1L);
    future5.set(2L);
    future1.set(3L);
    future3.set(4L);
    future4.set(5L);

    long expected = 1L;
    for (ListenableFuture<Long> future : futures) {
      assertEquals((Long) expected, future.get());
      expected++;
    }
  }

  @GwtIncompatible("inCompletionOrder")
  public void testCompletionOrderExceptionThrown() throws Exception {
    SettableFuture<Long> future1 = SettableFuture.create();
    SettableFuture<Long> future2 = SettableFuture.create();
    SettableFuture<Long> future3 = SettableFuture.create();
    SettableFuture<Long> future4 = SettableFuture.create();
    SettableFuture<Long> future5 = SettableFuture.create();

    ImmutableList<ListenableFuture<Long>> futures = Futures.inCompletionOrder(
        ImmutableList.<ListenableFuture<Long>>of(future1, future2, future3, future4, future5));
    future2.set(1L);
    future5.setException(new IllegalStateException("2L"));
    future1.set(3L);
    future3.set(4L);
    future4.set(5L);

    long expected = 1L;
    for (ListenableFuture<Long> future : futures) {
      if (expected != 2) {
        assertEquals((Long) expected, future.get());
      } else {
        try {
          future.get();
          fail();
        } catch (ExecutionException e) {
          // Expected
          assertEquals("2L", e.getCause().getMessage());
        }
      }
      expected++;
    }
  }

  @GwtIncompatible("inCompletionOrder")
  public void testCompletionOrderFutureCancelled() throws Exception {
    SettableFuture<Long> future1 = SettableFuture.create();
    SettableFuture<Long> future2 = SettableFuture.create();
    SettableFuture<Long> future3 = SettableFuture.create();
    SettableFuture<Long> future4 = SettableFuture.create();
    SettableFuture<Long> future5 = SettableFuture.create();

    ImmutableList<ListenableFuture<Long>> futures = Futures.inCompletionOrder(
        ImmutableList.<ListenableFuture<Long>>of(future1, future2, future3, future4, future5));
    future2.set(1L);
    future5.set(2L);
    future1.set(3L);
    future3.cancel(true);
    future4.set(5L);

    long expected = 1L;
    for (ListenableFuture<Long> future : futures) {
      if (expected != 4) {
        assertEquals((Long) expected, future.get());
      } else {
        try {
          future.get();
          fail();
        } catch (CancellationException e) {
          // Expected
        }
      }
      expected++;
    }
  }

  @GwtIncompatible("inCompletionOrder")
  public void testCancellingADelegateDoesNotPropagate() throws Exception {
    SettableFuture<Long> future1 = SettableFuture.create();
    SettableFuture<Long> future2 = SettableFuture.create();

    ImmutableList<ListenableFuture<Long>> delegates = Futures.inCompletionOrder(
        ImmutableList.<ListenableFuture<Long>>of(future1, future2));

    future1.set(1L);
    // Cannot cancel a complete delegate
    assertFalse(delegates.get(0).cancel(true));
    // Cancel the delegate before the input future is done
    assertTrue(delegates.get(1).cancel(true));
    // Setting the future still works since cancellation didn't propagate
    assertTrue(future2.set(2L));
    // Second check to ensure the input future was not cancelled
    assertEquals((Long) 2L, future2.get());
  }

  // Mostly an example of how it would look like to use a list of mixed types
  @GwtIncompatible("inCompletionOrder")
  public void testCompletionOrderMixedBagOTypes() throws Exception {
    SettableFuture<Long> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    SettableFuture<Integer> future3 = SettableFuture.create();

    ImmutableList<? extends ListenableFuture<?>> inputs =
        ImmutableList.<ListenableFuture<?>>of(future1, future2, future3);
    ImmutableList<ListenableFuture<Object>> futures = Futures.inCompletionOrder(inputs);
    future2.set("1L");
    future1.set(2L);
    future3.set(3);

    ImmutableList<?> expected = ImmutableList.of("1L", 2L, 3);
    for (int i = 0; i < expected.size(); i++) {
      assertEquals(expected.get(i), futures.get(i).get());
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  public static final class TwoArgConstructorException extends Exception {

    public TwoArgConstructorException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  public static final class TwoArgConstructorRuntimeException
      extends RuntimeException {

    public TwoArgConstructorRuntimeException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  public static final class ExceptionWithPrivateConstructor extends Exception {

    private ExceptionWithPrivateConstructor(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  @SuppressWarnings("unused") // we're testing that they're not used
  public static final class ExceptionWithSomePrivateConstructors
      extends Exception {

    private ExceptionWithSomePrivateConstructors(String a) {
    }

    private ExceptionWithSomePrivateConstructors(String a, String b) {
    }

    public ExceptionWithSomePrivateConstructors(
        String a, String b, String c) {
    }

    private ExceptionWithSomePrivateConstructors(
        String a, String b, String c, String d) {
    }

    private ExceptionWithSomePrivateConstructors(
        String a, String b, String c, String d, String e) {
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  public static final class ExceptionWithManyConstructors extends Exception {

    boolean usedExpectedConstructor;

    public ExceptionWithManyConstructors() {
    }

    public ExceptionWithManyConstructors(Integer i) {
    }

    public ExceptionWithManyConstructors(Throwable a) {
    }

    public ExceptionWithManyConstructors(Throwable a, Throwable b) {
    }

    public ExceptionWithManyConstructors(String s, Throwable b) {
      usedExpectedConstructor = true;
    }

    public ExceptionWithManyConstructors(
        Throwable a, Throwable b, Throwable c) {
    }

    public ExceptionWithManyConstructors(
        Throwable a, Throwable b, Throwable c, Throwable d) {
    }

    public ExceptionWithManyConstructors(
        Throwable a, Throwable b, Throwable c, Throwable d, Throwable e) {
    }

    public ExceptionWithManyConstructors(Throwable a, Throwable b, Throwable c,
        Throwable d, Throwable e, String s, Integer i) {
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  public static final class ExceptionWithoutThrowableConstructor
      extends Exception {

    public ExceptionWithoutThrowableConstructor(String s) {
      super(s);
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  public static final class ExceptionWithWrongTypesConstructor
      extends Exception {

    public ExceptionWithWrongTypesConstructor(Integer i, String s) {
      super(s);
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final class ExceptionWithGoodAndBadConstructor extends Exception {

    public ExceptionWithGoodAndBadConstructor(String message, Throwable cause) {
      throw new RuntimeException("bad constructor");
    }

    public ExceptionWithGoodAndBadConstructor(Throwable cause) {
      super(cause);
    }
  }

  @GwtIncompatible("used only in GwtIncompatible tests")
  private static final class ExceptionWithBadConstructor extends Exception {

    public ExceptionWithBadConstructor(String message, Throwable cause) {
      throw new RuntimeException("bad constructor");
    }
  }

  @GwtIncompatible("ClassSanityTester")
  public void testFutures_nullChecks() throws Exception {
    new ClassSanityTester()
        .forAllPublicStaticMethods(Futures.class)
        .thatReturn(Future.class)
        .testNulls();
  }

  static AssertionFailedError failureWithCause(Throwable cause, String message) {
    AssertionFailedError failure = new AssertionFailedError(message);
    failure.initCause(cause);
    return failure;
  }

  /**
   * A future that throws a runtime exception from get.
   */
  static class BuggyFuture extends AbstractFuture<String> {

    @Override
    public String get() {
      throw new RuntimeException();
    }

    @Override
    public boolean set(String v) {
      return super.set(v);
    }
  }

  // This test covers a bug where an Error thrown from a callback could cause the TimeoutFuture to
  // never complete when timing out.  Notably, nothing would get logged since the Error would get
  // stuck in the ScheduledFuture inside of TimeoutFuture and nothing ever calls get on it.

  // Simulate a timeout that fires before the call the SES.schedule returns but the future is
  // already completed.
}
