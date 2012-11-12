/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.hash;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * {@link HashFunction} adapter for {@link MessageDigest} instances.
 *
 * @author Kevin Bourrillion
 * @author Dimitris Andreou
 */
final class MessageDigestHashFunction extends AbstractStreamingHashFunction
    implements Serializable {
  private final MessageDigest prototype;
  private final int bytes;
  private final boolean supportsClone;

  MessageDigestHashFunction(String algorithmName) {
    this.prototype = getMessageDigest(algorithmName);
    this.bytes = prototype.getDigestLength();
    this.supportsClone = supportsClone();
  }

  MessageDigestHashFunction(String algorithmName, int bytes) {
    this.prototype = getMessageDigest(algorithmName);
    int maxLength = prototype.getDigestLength();
    checkArgument(bytes >= 4 && bytes <= maxLength,
        "bytes (%s) must be >= 4 and < %s", bytes, maxLength);
    this.bytes = bytes;
    this.supportsClone = supportsClone();
  }

  private boolean supportsClone() {
    try {
      prototype.clone();
      return true;
    } catch (CloneNotSupportedException e) {
      return false;
    }
  }

  @Override public int bits() {
    return bytes * Byte.SIZE;
  }

  private static MessageDigest getMessageDigest(String algorithmName) {
    try {
      return MessageDigest.getInstance(algorithmName);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  @Override public Hasher newHasher() {
    if (supportsClone) {
      try {
        return new MessageDigestHasher((MessageDigest) prototype.clone(), bytes);
      } catch (CloneNotSupportedException e) {
        // falls through
      }
    }
    return new MessageDigestHasher(getMessageDigest(prototype.getAlgorithm()), bytes);
  }

  private static final class SerializedForm implements Serializable {
    private final String algorithmName;
    private final int bytes;

    private SerializedForm(String algorithmName, int bytes) {
      this.algorithmName = algorithmName;
      this.bytes = bytes;
    }

    private Object readResolve() {
      return new MessageDigestHashFunction(algorithmName, bytes);
    }

    private static final long serialVersionUID = 0;
  }

  Object writeReplace() {
    return new SerializedForm(prototype.getAlgorithm(), bytes);
  }

  /**
   * Hasher that updates a message digest.
   */
  private static final class MessageDigestHasher extends AbstractByteHasher {

    private final MessageDigest digest;
    private final int bytes;
    private boolean done;

    private MessageDigestHasher(MessageDigest digest, int bytes) {
      this.digest = digest;
      this.bytes = bytes;
    }

    @Override
    protected void update(byte b) {
      checkNotDone();
      digest.update(b);
    }

    @Override
    protected void update(byte[] b) {
      checkNotDone();
      digest.update(b);
    }

    @Override
    protected void update(byte[] b, int off, int len) {
      checkNotDone();
      digest.update(b, off, len);
    }

    private void checkNotDone() {
      checkState(!done, "Cannot use Hasher after calling #hash() on it");
    }

    @Override
    public HashCode hash() {
      done = true;
      return (bytes == digest.getDigestLength())
          ? HashCodes.fromBytesNoCopy(digest.digest())
          : HashCodes.fromBytesNoCopy(Arrays.copyOf(digest.digest(), bytes));
    }
  }
}
