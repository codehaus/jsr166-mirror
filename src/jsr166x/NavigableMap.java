/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166x;

import java.util.*;

/**
 * A {@link SortedMap} extended with navigation methods returning the
 * closest matches for given search targets. Methods
 * {@code lowerEntry}, {@code floorEntry}, {@code ceilingEntry},
 * and {@code higherEntry} return {@code Map.Entry} objects
 * associated with keys respectively less than, less than or equal,
 * greater than or equal, and greater than a given key, returning
 * {@code null} if there is no such key.  Similarly, methods
 * {@code lowerKey}, {@code floorKey}, {@code ceilingKey}, and
 * {@code higherKey} return only the associated keys. All of these
 * methods are designed for locating, not traversing entries.
 *
 * <p>A {@code NavigableMap} may be viewed and traversed in either
 * ascending or descending key order.  The {@code Map} methods
 * {@code keySet} and {@code entrySet} return ascending views, and
 * the additional methods {@code descendingKeySet} and
 * {@code descendingEntrySet} return descending views. The
 * performance of ascending traversals is likely to be faster than
 * descending traversals.  Notice that it is possible to perform
 * subrannge traversals in either direction using {@code SubMap}.
 *
 * <p>This interface additionally defines methods {@code firstEntry},
 * {@code pollFirstEntry}, {@code lastEntry}, and
 * {@code pollLastEntry} that return and/or remove the least and
 * greatest mappings, if any exist, else returning {@code null}.
 *
 * <p>Implementations of entry-returning methods are expected to
 * return {@code Map.Entry} pairs representing snapshots of mappings
 * at the time they were produced, and thus generally do <em>not</em>
 * support the optional {@code Entry.setValue} method. Note however
 * that it is possible to change mappings in the associated map using
 * method {@code put}.
 *
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface NavigableMap<K,V> extends SortedMap<K,V> {
    /**
     * Returns a key-value mapping associated with the least key
     * greater than or equal to the given key, or {@code null} if there is
     * no such entry.
     *
     * @param key the key
     * @return an Entry associated with ceiling of given key, or {@code null}
     * if there is no such Entry
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public Map.Entry<K,V> ceilingEntry(K key);

    /**
     * Returns least key greater than or equal to the given key, or
     * {@code null} if there is no such key.
     *
     * @param key the key
     * @return the ceiling key, or {@code null}
     * if there is no such key
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public K ceilingKey(K key);

    /**
     * Returns a key-value mapping associated with the greatest
     * key strictly less than the given key, or {@code null} if there is no
     * such entry.
     *
     * @param key the key
     * @return an Entry with greatest key less than the given
     * key, or {@code null} if there is no such Entry
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public Map.Entry<K,V> lowerEntry(K key);

    /**
     * Returns the greatest key strictly less than the given key, or
     * {@code null} if there is no such key.
     *
     * @param key the key
     * @return the greatest key less than the given
     * key, or {@code null} if there is no such key
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public K lowerKey(K key);

    /**
     * Returns a key-value mapping associated with the greatest key
     * less than or equal to the given key, or {@code null} if there
     * is no such entry.
     *
     * @param key the key
     * @return an Entry associated with floor of given key, or {@code null}
     * if there is no such Entry
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public Map.Entry<K,V> floorEntry(K key);

    /**
     * Returns the greatest key
     * less than or equal to the given key, or {@code null} if there
     * is no such key.
     *
     * @param key the key
     * @return the floor of given key, or {@code null} if there is no
     * such key
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public K floorKey(K key);

    /**
     * Returns a key-value mapping associated with the least key
     * strictly greater than the given key, or {@code null} if there
     * is no such entry.
     *
     * @param key the key
     * @return an Entry with least key greater than the given key, or
     * {@code null} if there is no such Entry
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public Map.Entry<K,V> higherEntry(K key);

    /**
     * Returns the least key strictly greater than the given key, or
     * {@code null} if there is no such key.
     *
     * @param key the key
     * @return the least key greater than the given key, or
     * {@code null} if there is no such key
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map
     * @throws NullPointerException if key is {@code null} and this map
     * does not support {@code null} keys
     */
    public K higherKey(K key);

    /**
     * Returns a key-value mapping associated with the least
     * key in this map, or {@code null} if the map is empty.
     *
     * @return an Entry with least key, or {@code null}
     * if the map is empty
     */
    public Map.Entry<K,V> firstEntry();

    /**
     * Returns a key-value mapping associated with the greatest
     * key in this map, or {@code null} if the map is empty.
     *
     * @return an Entry with greatest key, or {@code null}
     * if the map is empty
     */
    public Map.Entry<K,V> lastEntry();

    /**
     * Removes and returns a key-value mapping associated with
     * the least key in this map, or {@code null} if the map is empty.
     *
     * @return the removed first entry of this map, or {@code null}
     * if the map is empty
     */
    public Map.Entry<K,V> pollFirstEntry();

    /**
     * Removes and returns a key-value mapping associated with
     * the greatest key in this map, or {@code null} if the map is empty.
     *
     * @return the removed last entry of this map, or {@code null}
     * if the map is empty
     */
    public Map.Entry<K,V> pollLastEntry();

    /**
     * Returns a set view of the keys contained in this map, in
     * descending key order.  The set is backed by the map, so changes
     * to the map are reflected in the set, and vice-versa.  If the
     * map is modified while an iteration over the set is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The set supports
     * element removal, which removes the corresponding mapping from
     * the map, via the {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll} {@code retainAll}, and {@code clear}
     * operations.  It does not support the add or {@code addAll}
     * operations.
     *
     * @return a set view of the keys contained in this map
     */
    Set<K> descendingKeySet();

    /**
     * Returns a set view of the mappings contained in this map, in
     * descending key order.  Each element in the returned set is a
     * {@code Map.Entry}.  The set is backed by the map, so changes to
     * the map are reflected in the set, and vice-versa.  If the map
     * is modified while an iteration over the set is in progress
     * (except through the iterator's own {@code remove} operation,
     * or through the {@code setValue} operation on a map entry
     * returned by the iterator) the results of the iteration are
     * undefined.  The set supports element removal, which removes the
     * corresponding mapping from the map, via the
     * {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll} and {@code clear}
     * operations.  It does not support the {@code add} or
     * {@code addAll} operations.
     *
     * @return a set view of the mappings contained in this map
     */
    Set<Map.Entry<K,V>> descendingEntrySet();

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
    public NavigableMap<K,V> subMap(K fromKey, K toKey);

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
    public NavigableMap<K,V> headMap(K toKey);

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
    public NavigableMap<K,V> tailMap(K fromKey);
}
