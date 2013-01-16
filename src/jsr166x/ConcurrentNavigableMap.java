/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166x;

import java.util.*;
import java.util.concurrent.*;

/**
 * A {@link ConcurrentMap} supporting {@link NavigableMap} operations.
 *
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface ConcurrentNavigableMap<K,V> extends ConcurrentMap<K,V>, NavigableMap<K,V> {
    /**
     * Returns a view of the portion of this map whose keys range from
     * {@code fromKey}, inclusive, to {@code toKey}, exclusive.  (If
     * {@code fromKey} and {@code toKey} are equal, the returned sorted map
     * is empty.)  The returned sorted map is backed by this map, so changes
     * in the returned sorted map are reflected in this map, and vice-versa.
     *
     * @param fromKey low endpoint (inclusive) of the subMap
     * @param toKey high endpoint (exclusive) of the subMap
     *
     * @return a view of the portion of this map whose keys range from
     * {@code fromKey}, inclusive, to {@code toKey}, exclusive
     *
     * @throws ClassCastException if {@code fromKey} and
     * {@code toKey} cannot be compared to one another using this
     * map's comparator (or, if the map has no comparator, using
     * natural ordering)
     * @throws IllegalArgumentException if {@code fromKey} is greater
     * than {@code toKey}
     * @throws NullPointerException if {@code fromKey} or
     * {@code toKey} is {@code null} and this map does not support
     * {@code null} keys
     */
    public ConcurrentNavigableMap<K,V> subMap(K fromKey, K toKey);

    /**
     * Returns a view of the portion of this map whose keys are strictly less
     * than {@code toKey}.  The returned sorted map is backed by this map, so
     * changes in the returned sorted map are reflected in this map, and
     * vice-versa.
     * @param toKey high endpoint (exclusive) of the headMap
     * @return a view of the portion of this map whose keys are strictly
     *                less than {@code toKey}
     *
     * @throws ClassCastException if {@code toKey} is not compatible
     *         with this map's comparator (or, if the map has no comparator,
     *         if {@code toKey} does not implement {@code Comparable})
     * @throws NullPointerException if {@code toKey} is {@code null}
     * and this map does not support {@code null} keys
     */
    public ConcurrentNavigableMap<K,V> headMap(K toKey);

    /**
     * Returns a view of the portion of this map whose keys are
     * greater than or equal to {@code fromKey}.  The returned sorted
     * map is backed by this map, so changes in the returned sorted
     * map are reflected in this map, and vice-versa.
     * @param fromKey low endpoint (inclusive) of the tailMap
     * @return a view of the portion of this map whose keys are greater
     *                than or equal to {@code fromKey}
     * @throws ClassCastException if {@code fromKey} is not compatible
     *         with this map's comparator (or, if the map has no comparator,
     *         if {@code fromKey} does not implement {@code Comparable})
     * @throws NullPointerException if {@code fromKey} is {@code null}
     * and this map does not support {@code null} keys
     */
    public ConcurrentNavigableMap<K,V> tailMap(K fromKey);
}
