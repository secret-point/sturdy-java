/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect;

import com.google.common.annotations.GwtCompatible;

/**
 * A factory for extending paths in a binary search tree.
 *
 * @author Louis Wasserman
 * @param <K> The key type of nodes of type {@code N}.
 * @param <N> The type of binary search tree nodes used in the paths generated by this {@code
 *        BSTPathFactory}.
 * @param <P> The type of paths constructed by this {@code BSTPathFactory}.
 */
@GwtCompatible
interface BSTPathFactory<K, N extends BSTNode<K, N>, P extends BSTPath<K, N, P>> {
  /**
   * Returns this path extended by one node to the specified {@code side}.
   */
  P extension(P path, BSTSide side);

  /**
   * Returns the trivial path that starts at {@code root} and goes no further.
   */
  P initialPath(N root);
}
