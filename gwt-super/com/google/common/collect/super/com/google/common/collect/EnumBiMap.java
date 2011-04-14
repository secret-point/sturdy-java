/*
 * Copyright (C) 2007 The Guava Authors
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.GwtCompatible;

import java.util.EnumMap;
import java.util.Map;

/**
 * A {@code BiMap} backed by two {@code EnumMap} instances. Null keys and values
 * are not permitted. An {@code EnumBiMap} and its inverse are both
 * serializable.
 *
 * @author Mike Bostock
 * @since Guava release 02 (imported from Google Collections Library)
 */
@GwtCompatible(emulated = true)
public final class EnumBiMap<K extends Enum<K>, V extends Enum<V>>
    extends AbstractBiMap<K, V> {
  private transient Class<K> keyType;
  private transient Class<V> valueType;

  /**
   * Returns a new, empty {@code EnumBiMap} using the specified key and value
   * types.
   *
   * @param keyType the key type
   * @param valueType the value type
   */
  public static <K extends Enum<K>, V extends Enum<V>> EnumBiMap<K, V>
      create(Class<K> keyType, Class<V> valueType) {
    return new EnumBiMap<K, V>(keyType, valueType);
  }

  /**
   * Returns a new bimap with the same mappings as the specified map. If the
   * specified map is an {@code EnumBiMap}, the new bimap has the same types as
   * the provided map. Otherwise, the specified map must contain at least one
   * mapping, in order to determine the key and value types.
   *
   * @param map the map whose mappings are to be placed in this map
   * @throws IllegalArgumentException if map is not an {@code EnumBiMap}
   *     instance and contains no mappings
   */
  public static <K extends Enum<K>, V extends Enum<V>> EnumBiMap<K, V>
      create(Map<K, V> map) {
    EnumBiMap<K, V> bimap = create(inferKeyType(map), inferValueType(map));
    bimap.putAll(map);
    return bimap;
  }

  private EnumBiMap(Class<K> keyType, Class<V> valueType) {
    super(WellBehavedMap.wrap(new EnumMap<K, V>(keyType)),
        WellBehavedMap.wrap(new EnumMap<V, K>(valueType)));
    this.keyType = keyType;
    this.valueType = valueType;
  }

  static <K extends Enum<K>> Class<K> inferKeyType(Map<K, ?> map) {
    if (map instanceof EnumBiMap) {
      return ((EnumBiMap<K, ?>) map).keyType();
    }
    if (map instanceof EnumHashBiMap) {
      return ((EnumHashBiMap<K, ?>) map).keyType();
    }
    checkArgument(!map.isEmpty());
    return map.keySet().iterator().next().getDeclaringClass();
  }

  private static <V extends Enum<V>> Class<V> inferValueType(Map<?, V> map) {
    if (map instanceof EnumBiMap) {
      return ((EnumBiMap<?, V>) map).valueType;
    }
    checkArgument(!map.isEmpty());
    return map.values().iterator().next().getDeclaringClass();
  }

  /** Returns the associated key type. */
  public Class<K> keyType() {
    return keyType;
  }

  /** Returns the associated value type. */
  public Class<V> valueType() {
    return valueType;
  }
}

