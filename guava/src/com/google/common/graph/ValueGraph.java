/*
 * Copyright (C) 2016 The Guava Authors
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

package com.google.common.graph;

import com.google.common.annotations.Beta;
import javax.annotation.Nullable;

/**
 * A subtype of {@link Graph} that associates a value with each edge.
 *
 * TODO(b/30133524) Flesh out class-level javadoc.
 *
 * @author James Sexton
 * @param <N> Node parameter type
 * @param <V> Value parameter type
 * @since 20.0
 */
@Beta
public interface ValueGraph<N, V> extends Graph<N> {
  /**
   * If there is an edge connecting {@code nodeA} to {@code nodeB}, returns the non-null value
   * associated with that edge.
   *
   * @throws IllegalArgumentException if there is no edge connecting {@code nodeA} to {@code nodeB}
   */
  V edgeValue(Object nodeA, Object nodeB);

  /**
   * If there is an edge connecting {@code nodeA} to {@code nodeB}, returns the non-null value
   * associated with that edge. Otherwise, returns {@code defaultValue}.
   */
  V edgeValueOrDefault(Object nodeA, Object nodeB, @Nullable V defaultValue);

  //
  // ValueGraph identity
  //

  /**
   * Returns {@code true} iff {@code object} is a {@link ValueGraph} that has the same structural
   * relationships as those in this graph.
   *
   * <p>Thus, two value graphs A and B are equal if <b>all</b> of the following are true:
   * <ul>
   * <li>A and B have equal {@link #isDirected() directedness}.
   * <li>A and B have equal {@link #nodes() node sets}.
   * <li>A and B have equal {@link #edges() edge sets}.
   * <li>Every edge in A and B are associated with equal {@link #edgeValue(Object, Object) values}.
   * </ul>
   *
   * <p>Graph properties besides {@link #isDirected() directedness} do <b>not</b> affect equality.
   * For example, two graphs may be considered equal even if one allows self-loops and the other
   * doesn't. Additionally, the order in which nodes or edges are added to the graph, and the order
   * in which they are iterated over, are irrelevant.
   *
   * <p>A reference implementation of this is provided by {@link AbstractValueGraph#equals(Object)}.
   */
  @Override
  boolean equals(@Nullable Object object);

  /**
   * Returns the hash code for this value graph. The hash code of a value graph is defined as
   * the hash code of a map from each of its {@link #edges() edges} to the associated {@link
   * #edgeValue(Object, Object) edge value}.
   *
   * <p>A reference implementation of this is provided by {@link AbstractValueGraph#hashCode()}.
   */
  @Override
  int hashCode();
}
