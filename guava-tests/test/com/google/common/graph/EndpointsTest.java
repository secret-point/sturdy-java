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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.EqualsTester;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Endpoints}.
 */
@RunWith(JUnit4.class)
public final class EndpointsTest {

  @Test
  public void testDirectedEndpoints() {
    Endpoints<String> directed = Endpoints.ofDirected("source", "target");
    assertThat(directed.source()).isEqualTo("source");
    assertThat(directed.target()).isEqualTo("target");
    assertThat(directed).containsExactly("source", "target").inOrder();
    assertThat(directed.toString()).isEqualTo("<source -> target>");
  }

  @Test
  public void testUndirectedEndpoints() {
    Endpoints<String> undirected = Endpoints.ofUndirected("chicken", "egg");
    assertThat(undirected).containsExactly("chicken", "egg");
    assertThat(undirected.toString()).contains("chicken");
    assertThat(undirected.toString()).contains("egg");
  }

  @Test
  public void testSelfLoop() {
    Endpoints<String> undirected = Endpoints.ofUndirected("node", "node");
    assertThat(undirected).hasSize(2);
    assertThat(undirected.toString()).isEqualTo("[node, node]");
  }

  @Test
  public void testEquals() {
    Endpoints<String> directed = Endpoints.ofDirected("a", "b");
    Endpoints<String> directedMirror = Endpoints.ofDirected("b", "a");
    Endpoints<String> undirected = Endpoints.ofUndirected("a", "b");
    Endpoints<String> undirectedMirror = Endpoints.ofUndirected("b", "a");

    new EqualsTester()
        .addEqualityGroup(directed)
        .addEqualityGroup(directedMirror)
        .addEqualityGroup(undirected, undirectedMirror)
        .testEquals();
  }
}
