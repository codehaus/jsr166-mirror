/*
 * File: MemoTest.java
 *
 * Written by Joseph Bowbeer and released to the public domain,
 * as explained at http://creativecommons.org/licenses/publicdomain
 */

package jsr166.memoize;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

interface Computable<A, V> {
  V compute(A arg) throws Exception;
}

class Function
implements Computable<String, BigInteger> {
  public BigInteger compute(String arg) {
    // after deep thought...
    return new BigInteger(arg);
  }
}

class Memoize1<A, V> implements Computable<A, V> {
  final Map<A, V> cache = new HashMap<A, V>();
  final Computable<A, V> c;
  Memoize1(Computable<A, V> c) { this.c = c; }
  public synchronized V compute(A arg)
  throws Exception {
    if (!cache.containsKey(arg))
      cache.put(arg, c.compute(arg));
    return cache.get(arg);
  }
}

class Memoize2<A, V> implements Computable<A, V> {
  final Map<A, V> cache =
    new ConcurrentHashMap<A, V>();
  final Computable<A, V> c;
  Memoize2(Computable<A, V> c) { this.c = c; }
  public V compute(A arg) throws Exception {
    if (!cache.containsKey(arg))
      cache.put(arg, c.compute(arg));
    return cache.get(arg);
  }
}

class Memoize3<A, V> implements Computable<A, V> {
  final Map<A, Future<V>> cache =
    new ConcurrentHashMap<A, Future<V>>();
  final Computable<A, V> c;
  Memoize3(Computable<A, V> c) { this.c = c; }
  public V compute(final A arg)
  throws Exception {
    if (!cache.containsKey(arg)) {
      Callable<V> eval = new Callable<V>() {
        public V call() throws Exception {
          return c.compute(arg);
        }
      };
      FutureTask<V> ft = new FutureTask<V>(eval);
      cache.put(arg, ft);
      ft.run();
    }
    return cache.get(arg).get();
  }
}

class Memoize<A, V> implements Computable<A, V> {
  final ConcurrentMap<A, Future<V>> cache =
    new ConcurrentHashMap<A, Future<V>>();
  final Computable<A, V> c;
  Memoize(Computable<A, V> c) { this.c = c; }
  public V compute(final A arg)
  throws Exception {
    Future<V> f = cache.get(arg);
    if (f == null) {
      Callable<V> eval = new Callable<V>() {
        public V call() throws Exception {
          return c.compute(arg);
        }
      };
      FutureTask<V> ft = new FutureTask<V>(eval);
      f = cache.putIfAbsent(arg, ft);
      if (f == null) {
        f = ft;
        ft.run();
      }
    }
    return f.get();
  }
}

class MemoTest {
  public static void main(String[] args) throws Exception {
    Computable<String, BigInteger> f, mf;
    f = new Function();
    mf = new Memoize<String, BigInteger>(f);
    assert f.compute("42").equals(mf.compute("42"));
    assert mf.compute("1"+"1") == mf.compute("1"+"1");
  }
}

