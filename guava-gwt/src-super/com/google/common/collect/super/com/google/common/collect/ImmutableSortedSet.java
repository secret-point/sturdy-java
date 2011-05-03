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

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * GWT emulation of {@link ImmutableSortedSet}.
 *
 * @author Hayward Chan
 */
public abstract class ImmutableSortedSet<E>
    extends ImmutableSet<E> implements SortedSet<E> {

  // In the non-emulated source, this is in ImmutableSortedSetFauxverideShim,
  // which overrides ImmutableSet & which ImmutableSortedSet extends.
  // It is necessary here because otherwise the builder() method
  // would be inherited from the emulated ImmutableSet.
  @Deprecated public static <E> ImmutableSortedSet.Builder<E> builder() {
    throw new UnsupportedOperationException();
  }

  // TODO: Can we find a way to remove this @SuppressWarnings even for eclipse?
  @SuppressWarnings("unchecked")
  private static final Comparator NATURAL_ORDER = Ordering.natural();

  @SuppressWarnings("unchecked")
  private static final ImmutableSortedSet<Object> NATURAL_EMPTY_SET =
      new EmptyImmutableSortedSet<Object>(NATURAL_ORDER);

  @SuppressWarnings("unchecked")
  private static <E> ImmutableSortedSet<E> emptySet() {
    return (ImmutableSortedSet<E>) NATURAL_EMPTY_SET;
  }

  static <E> ImmutableSortedSet<E> emptySet(
      Comparator<? super E> comparator) {
    checkNotNull(comparator);
    if (NATURAL_ORDER.equals(comparator)) {
      return emptySet();
    } else {
      return new EmptyImmutableSortedSet<E>(comparator);
    }
  }

  public static <E> ImmutableSortedSet<E> of() {
    return emptySet();
  }

  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> of(
      E element) {
    return ofInternal(Ordering.natural(), element);
  }

  @SuppressWarnings("unchecked")
  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> of(
      E e1, E e2) {
    return ofInternal(Ordering.natural(), e1, e2);
  }

  @SuppressWarnings("unchecked")
  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> of(
      E e1, E e2, E e3) {
    return ofInternal(Ordering.natural(), e1, e2, e3);
  }

  @SuppressWarnings("unchecked")
  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> of(
      E e1, E e2, E e3, E e4) {
    return ofInternal(Ordering.natural(), e1, e2, e3, e4);
  }

  @SuppressWarnings("unchecked")
  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> of(
      E e1, E e2, E e3, E e4, E e5) {
    return ofInternal(Ordering.natural(), e1, e2, e3, e4, e5);
  }

  @SuppressWarnings("unchecked")
  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> of(
      E e1, E e2, E e3, E e4, E e5, E e6, E... remaining) {
    int size = remaining.length + 6;
    List<E> all = new ArrayList<E>(size);
    Collections.addAll(all, e1, e2, e3, e4, e5, e6);
    Collections.addAll(all, remaining);
    // This is messed up. See TODO at top of file.
    return ofInternal(Ordering.natural(), (E[]) all.toArray(new Comparable[0]));
  }

  /** @Deprecated */
  @Deprecated
  public
  static <E extends Comparable<? super E>> ImmutableSortedSet<E> of(
      E[] elements) {
    return copyOf(elements);
  }

  private static <E> ImmutableSortedSet<E> ofInternal(
      Comparator<? super E> comparator, E... elements) {
    checkNotNull(elements);
    switch (elements.length) {
      case 0:
        return emptySet(comparator);
      default:
        SortedSet<E> delegate = new TreeSet<E>(comparator);
        for (E element : elements) {
          checkNotNull(element);
          delegate.add(element);
        }
        return new RegularImmutableSortedSet<E>(delegate, false);
    }
  }

  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> copyOf(
      Collection<? extends E> elements) {
    return copyOfInternal(Ordering.natural(), elements, false);
  }

  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> copyOf(
      Iterable<? extends E> elements) {
    return copyOfInternal(Ordering.natural(), elements, false);
  }

  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> copyOf(
      Iterator<? extends E> elements) {
    return copyOfInternal(Ordering.natural(), elements);
  }

  public static <E extends Comparable<? super E>> ImmutableSortedSet<E> copyOf(
      E[] elements) {
    return ofInternal(Ordering.natural(), elements);
  }

  public static <E> ImmutableSortedSet<E> copyOf(
      Comparator<? super E> comparator, Iterable<? extends E> elements) {
    checkNotNull(comparator);
    return copyOfInternal(comparator, elements, false);
  }

  public static <E> ImmutableSortedSet<E> copyOf(
      Comparator<? super E> comparator, Collection<? extends E> elements) {
    checkNotNull(comparator);
    return copyOfInternal(comparator, elements, false);
  }

  public static <E> ImmutableSortedSet<E> copyOf(
      Comparator<? super E> comparator, Iterator<? extends E> elements) {
    checkNotNull(comparator);
    return copyOfInternal(comparator, elements);
  }

  @SuppressWarnings("unchecked")
  public static <E> ImmutableSortedSet<E> copyOfSorted(SortedSet<E> sortedSet) {
    Comparator<? super E> comparator = sortedSet.comparator();
    if (comparator == null) {
      comparator = NATURAL_ORDER;
    }
    return copyOfInternal(comparator, sortedSet.iterator());
  }

  private static <E> ImmutableSortedSet<E> copyOfInternal(
      Comparator<? super E> comparator, Iterable<? extends E> elements,
      boolean fromSortedSet) {
    checkNotNull(comparator);

    boolean hasSameComparator
        = fromSortedSet || hasSameComparator(elements, comparator);
    if (hasSameComparator && (elements instanceof ImmutableSortedSet)) {
      @SuppressWarnings("unchecked")
      ImmutableSortedSet<E> result = (ImmutableSortedSet<E>) elements;
      boolean isSubset = (result instanceof RegularImmutableSortedSet)
          && ((RegularImmutableSortedSet) result).isSubset;
      if (!isSubset) {
        // Only return the original copy if this immutable sorted set isn't
        // a subset of another, to avoid memory leak.
        return result;
      }
    }
    return copyOfInternal(comparator, elements.iterator());
  }

  private static <E> ImmutableSortedSet<E> copyOfInternal(
      Comparator<? super E> comparator, Iterator<? extends E> elements) {
    checkNotNull(comparator);
    if (!elements.hasNext()) {
      return emptySet(comparator);
    }
    SortedSet<E> delegate = new TreeSet<E>(comparator);
    while (elements.hasNext()) {
      E element = elements.next();
      checkNotNull(element);
      delegate.add(element);
    }
    return new RegularImmutableSortedSet<E>(delegate, false);
  }

  private static boolean hasSameComparator(
      Iterable<?> elements, Comparator<?> comparator) {
    if (elements instanceof SortedSet) {
      SortedSet<?> sortedSet = (SortedSet<?>) elements;
      Comparator<?> comparator2 = sortedSet.comparator();
      return (comparator2 == null)
          ? comparator == Ordering.natural()
          : comparator.equals(comparator2);
    }
    return false;
  }

  // Assumes that delegate doesn't have null elements and comparator.
  static <E> ImmutableSortedSet<E> unsafeDelegateSortedSet(
      SortedSet<E> delegate, boolean isSubset) {
    return delegate.isEmpty()
        ? emptySet(delegate.comparator())
        : new RegularImmutableSortedSet<E>(delegate, isSubset);
  }

  @Deprecated
  public static <E> ImmutableSortedSet<E> withExplicitOrder(List<E> elements) {
    Ordering<E> ordering = Ordering.explicit(elements);
    return elements.isEmpty()
        ? new EmptyImmutableSortedSet<E>(ordering)
        : new ExplicitOrderedImmutableSortedSet<E>(elements, ordering);
  }

  @Deprecated
  public static <E> ImmutableSortedSet<E> withExplicitOrder(
      E firstElement, E... remainingElementsInOrder) {
    checkNotNull(firstElement);
    checkNotNull(remainingElementsInOrder);
    return withExplicitOrder(
        Lists.asList(firstElement, remainingElementsInOrder));
  }

  // This reference is only used by GWT compiler to infer the elements of the
  // set that needs to be serialized.
  private Comparator<E> unusedComparatorForSerialization;
  private E unusedElementForSerialization;

  private transient final SortedSet<E> sortedDelegate;

  ImmutableSortedSet(Comparator<? super E> comparator) {
    this(Sets.newTreeSet(comparator));
  }

  ImmutableSortedSet(SortedSet<E> sortedDelegate) {
    super(sortedDelegate);
    this.sortedDelegate = Collections.unmodifiableSortedSet(sortedDelegate);
  }

  public Comparator<? super E> comparator() {
    return sortedDelegate.comparator();
  }

  @Override public boolean contains(@Nullable Object object) {
    try {
      // This set never contains null.  We need to explicitly check here
      // because some comparator might throw NPE (e.g. the natural ordering).
      return object != null && sortedDelegate.contains(object);
    } catch (ClassCastException e) {
      return false;
    }
  }

  @Override public boolean containsAll(Collection<?> targets) {
    for (Object target : targets) {
      if (target == null) {
        // This set never contains null.  We need to explicitly check here
        // because some comparator might throw NPE (e.g. the natural ordering).
        return false;
      }
    }
    try {
      return sortedDelegate.containsAll(targets);
    } catch (ClassCastException e) {
      return false;
    }
  }

  public E first() {
    return sortedDelegate.first();
  }

  public ImmutableSortedSet<E> headSet(E toElement) {
    checkNotNull(toElement);
    return unsafeDelegateSortedSet(sortedDelegate.headSet(toElement), true);
  }

  public E last() {
    return sortedDelegate.last();
  }

  public ImmutableSortedSet<E> subSet(E fromElement, E toElement) {
    checkNotNull(fromElement);
    checkNotNull(toElement);
    return unsafeDelegateSortedSet(
        sortedDelegate.subSet(fromElement, toElement), true);
  }

  public ImmutableSortedSet<E> tailSet(E fromElement) {
    checkNotNull(fromElement);
    return unsafeDelegateSortedSet(sortedDelegate.tailSet(fromElement), true);
  }

  public static <E> Builder<E> orderedBy(Comparator<E> comparator) {
    return new Builder<E>(comparator);
  }

  public static <E extends Comparable<E>> Builder<E> reverseOrder() {
    return new Builder<E>(Ordering.natural().reverse());
  }

  public static <E extends Comparable<E>> Builder<E> naturalOrder() {
    return new Builder<E>(Ordering.natural());
  }

  public static final class Builder<E> extends ImmutableSet.Builder<E> {
    private final Comparator<? super E> comparator;

    public Builder(Comparator<? super E> comparator) {
      this.comparator = checkNotNull(comparator);
    }

    @Override public Builder<E> add(E element) {
      super.add(element);
      return this;
    }

    @Override public Builder<E> add(E... elements) {
      super.add(elements);
      return this;
    }

    @Override public Builder<E> addAll(Iterable<? extends E> elements) {
      super.addAll(elements);
      return this;
    }

    @Override public Builder<E> addAll(Iterator<? extends E> elements) {
      super.addAll(elements);
      return this;
    }

    @Override public ImmutableSortedSet<E> build() {
      return copyOfInternal(comparator, contents.iterator());
    }
  }
}
