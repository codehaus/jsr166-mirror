/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166x; 
import java.util.*;
import java.util.concurrent.*;

/**
 * Submaps returned by {@link ConcurrentSkipListMap} submap operations
 * represent a subrange of mappings of their underlying
 * maps. Instances of this class support all methods of their
 * underlying maps, differing in that mappings outside their range are
 * ignored, and attempts to add mappings outside their ranges result
 * in {@link IllegalArgumentException}.  Instances of this class are
 * constructed only using the <tt>subMap</tt>, <tt>headMap</tt>, and
 * <tt>tailMap</tt> methods of their underlying maps.
 *
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values 
 */
public class ConcurrentSkipListSubMap<K,V> extends AbstractMap<K,V>
    implements SortedMap<K,V>, ConcurrentMap<K,V>, java.io.Serializable {

    private static final long serialVersionUID = -7647078645895051609L;

    /** Underlying map */
    private final ConcurrentSkipListMap<K,V> m;
    /** lower bound key, or null if from start */
    private final K least; 
    /** upper fence key, or null if to end */
    private final K fence;   
    // Lazily initialized view holders
    private transient Set<K> keySetView;
    private transient Set<Map.Entry<K,V>> entrySetView;
    private transient Collection<V> valuesView;

    /**
     * Creates a new submap. 
     * @param least inclusive least value, or null if from start
     * @param fence exclusive upper bound or null if to end
     * @throws IllegalArgumentException if least and fence nonnull
     *  and least greater than fence
     */
    ConcurrentSkipListSubMap(ConcurrentSkipListMap<K,V> map, 
                             K least, K fence) {
        if (least != null && fence != null && map.compare(least, fence) > 0)
            throw new IllegalArgumentException("inconsistent range");
        this.m = map;
        this.least = least;
        this.fence = fence;
    }

    /* ----------------  Utilities -------------- */

    boolean inHalfOpenRange(K key) {
        return m.inHalfOpenRange(key, least, fence);
    }

    boolean inOpenRange(K key) {
        return m.inOpenRange(key, least, fence);
    }

    ConcurrentSkipListMap.Node<K,V> firstNode() {
        return m.findCeiling(least);
    }

    ConcurrentSkipListMap.Node<K,V> lastNode() {
        return m.findLower(fence);
    }

    boolean isBeforeEnd(ConcurrentSkipListMap.Node<K,V> n) {
        return (n != null && 
                (fence == null || 
                 n.key == null || // pass by markers and headers
                 m.compare(fence, n.key) > 0));
    }

    void checkKey(K key) throws IllegalArgumentException {
        if (!inHalfOpenRange(key))
            throw new IllegalArgumentException("key out of range");
    }

    /**
     * Returns underlying map. Needed by ConcurrentSkipListSet
     * @return the backing map
     */
    ConcurrentSkipListMap<K,V> getMap() {
        return m;
    }

    /**
     * Returns least key. Needed by ConcurrentSkipListSet
     * @return least key or null if from start
     */
    K getLeast() {
        return least;
    }

    /**
     * Returns fence key. Needed by ConcurrentSkipListSet
     * @return fence key or null of to end
     */
    K getFence() {
        return fence;
    }

    /**
     * Non-exception throwing version of firstKey needed by
     * ConcurrentSkipListSubSet
     * @return first key, or null if empty
     */
    K lowestKey() {
        ConcurrentSkipListMap.Node<K,V> n = firstNode();
        if (isBeforeEnd(n))
            return n.key;
        else
            return null;
    }

    /* ----------------  Map API methods -------------- */

    /**
     * Returns <tt>true</tt> if this map contains a mapping for
     * the specified key.
     * @param key key whose presence in this map is to be tested.
     * @return <tt>true</tt> if this map contains a mapping for
     * the specified key.
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws NullPointerException if the key is <tt>null</tt>.
     */
    public boolean containsKey(Object key) {
        K k = (K)key;
        return inHalfOpenRange(k) && m.containsKey(k);
    }

    /**
     * Returns the value to which this map maps the specified key.
     * Returns <tt>null</tt> if the map contains no mapping for
     * this key.
     *
     * @param key key whose associated value is to be returned.
     * @return the value to which this map maps the specified key,
     * or <tt>null</tt> if the map contains no mapping for the
     * key.
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws NullPointerException if the key is <tt>null</tt>.
     */
    public V get(Object key) {
        K k = (K)key;
        return ((!inHalfOpenRange(k)) ? null : m.get(k));
    }

    /**
     * Associates the specified value with the specified key in
     * this map.  If the map previously contained a mapping for
     * this key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     *
     * @return previous value associated with specified key, or
     * <tt>null</tt> if there was no mapping for key.
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws IllegalArgumentException if key outside range of
     * this submap.
     * @throws NullPointerException if the key or value are <tt>null</tt>.
     */
    public V put(K key, V value) {
        checkKey(key);
        return m.put(key, value);
    }

    /**
     * Removes the mapping for this key from this Map if present.
     *
     * @param key key for which mapping should be removed
     * @return previous value associated with specified key, or
     * <tt>null</tt> if there was no mapping for key.
     *
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws NullPointerException if the key is <tt>null</tt>.
     */
    public V remove(Object key) {
        K k = (K)key;
        return (!inHalfOpenRange(k))? null : m.remove(k);
    }

    /**
     * Returns the number of elements in this map.  If this map
     * contains more than <tt>Integer.MAX_VALUE</tt> elements, it
     * returns <tt>Integer.MAX_VALUE</tt>.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these maps, determining the current
     * number of elements requires traversing them all to count them.
     * Additionally, it is possible for the size to change during
     * execution of this method, in which case the returned result
     * will be inaccurate. Thus, this method is typically not very
     * useful in concurrent applications.
     *
     * @return  the number of elements in this map.
     */
    public int size() {
        long count = 0;
        for (ConcurrentSkipListMap.Node<K,V> n = firstNode(); 
             isBeforeEnd(n); 
             n = n.next) {
            if (n.getValidValue() != null)
                ++count;
        }
        return count >= Integer.MAX_VALUE? Integer.MAX_VALUE : (int)count;
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     * @return <tt>true</tt> if this map contains no key-value mappings.
     */
    public boolean isEmpty() {
        return !isBeforeEnd(firstNode());
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.  This operation requires time linear in the
     * Map size.
     *
     * @param value value whose presence in this Map is to be tested.
     * @return  <tt>true</tt> if a mapping to <tt>value</tt> exists;
     *		<tt>false</tt> otherwise.
     * @throws  NullPointerException  if the value is <tt>null</tt>.
     */    
    public boolean containsValue(Object value) {
        if (value == null) 
            throw new NullPointerException();
        for (ConcurrentSkipListMap.Node<K,V> n = firstNode(); 
             isBeforeEnd(n); 
             n = n.next) {
            V v = n.getValidValue();
            if (v != null && value.equals(v))
                return true;
        }
        return false;
    }

    /**
     * Removes all mappings from this map.
     */
    public void clear() {
        for (ConcurrentSkipListMap.Node<K,V> n = firstNode(); 
             isBeforeEnd(n); 
             n = n.next) {
            if (n.getValidValue() != null)
                m.remove(n.key);
        }
    }

    /* ----------------  ConcurrentMap API methods -------------- */

    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * This is equivalent to
     * <pre>
     *   if (!map.containsKey(key)) 
     *      return map.put(key, value);
     *   else
     *      return map.get(key);
     * </pre>
     * Except that the action is performed atomically.
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or
     * <tt>null</tt> if there was no mapping for key.
     *
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws IllegalArgumentException if key outside range of
     * this submap.
     * @throws NullPointerException if the key or value are <tt>null</tt>.
     */
    public V putIfAbsent(K key, V value) {
        checkKey(key);
        return m.putIfAbsent(key, value);
    }

    /**
     * Remove entry for key only if currently mapped to given value.
     * Acts as
     * <pre> 
     *  if ((map.containsKey(key) && map.get(key).equals(value)) {
     *     map.remove(key);
     *     return true;
     * } else return false;
     * </pre>
     * except that the action is performed atomically.
     * @param key key with which the specified value is associated.
     * @param value value associated with the specified key.
     * @return true if the value was removed, false otherwise
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws NullPointerException if the key or value are
     * <tt>null</tt>.
     */
    public boolean remove(Object key, Object value) {
        K k = (K)key;
        return inHalfOpenRange(k) && m.remove(k, value);
    }

    /**
     * Replace entry for key only if currently mapped to given value.
     * Acts as
     * <pre> 
     *  if ((map.containsKey(key) && map.get(key).equals(oldValue)) {
     *     map.put(key, newValue);
     *     return true;
     * } else return false;
     * </pre>
     * except that the action is performed atomically.
     * @param key key with which the specified value is associated.
     * @param oldValue value expected to be associated with the specified key.
     * @param newValue value to be associated with the specified key.
     * @return true if the value was replaced
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws IllegalArgumentException if key outside range of
     * this submap.
     * @throws NullPointerException if key, oldValue or newValue
     * are <tt>null</tt>.
     */
    public boolean replace(K key, V oldValue, V newValue) {
        checkKey(key);
        return m.replace(key, oldValue, newValue);
    }

    /**
     * Replace entry for key only if currently mapped to some value.
     * Acts as
     * <pre> 
     *  if ((map.containsKey(key)) {
     *     return map.put(key, value);
     * } else return null;
     * </pre>
     * except that the action is performed atomically.
     * @param key key with which the specified value is associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or
     * <tt>null</tt> if there was no mapping for key.
     * @throws ClassCastException if the key cannot be compared
     * with the keys currently in the map.
     * @throws IllegalArgumentException if key outside range of
     * this submap.
     * @throws NullPointerException if the key or value are
     * <tt>null</tt>.
     */
    public V replace(K key, V value) {
        checkKey(key);
        return m.replace(key, value);
    }

    /* ----------------  SortedMap API methods -------------- */

    /**
     * Returns the comparator used to order this map, or <tt>null</tt>
     * if this map uses its keys' natural order.
     *
     * @return the comparator associated with this map, or
     * <tt>null</tt> if it uses its keys' natural sort method.
     */
    public Comparator<? super K> comparator() {
        return m.comparator();
    }

    /**
     * Returns the first (lowest) key currently in this map.
     *
     * @return the first (lowest) key currently in this map.
     * @throws    NoSuchElementException Map is empty.
     */
    public K firstKey() {
        ConcurrentSkipListMap.Node<K,V> n = firstNode();
        if (isBeforeEnd(n))
            return n.key;
        else
            throw new NoSuchElementException();
    }

    /**
     * Returns the last (highest) key currently in this map.
     *
     * @return the last (highest) key currently in this map.
     * @throws    NoSuchElementException Map is empty.
     */
    public K lastKey() {
        ConcurrentSkipListMap.Node<K,V> n = lastNode();
        if (n != null) {
            K last = n.key;
            if (inHalfOpenRange(last))
                return last;
        }
        throw new NoSuchElementException();
    }

    /**
     * Returns a view of the portion of this map whose keys range
     * from <tt>fromKey</tt>, inclusive, to <tt>toKey</tt>,
     * exclusive.  (If <tt>fromKey</tt> and <tt>toKey</tt> are
     * equal, the returned sorted map is empty.)  The returned
     * sorted map is backed by this map, so changes in the
     * returned sorted map are reflected in this map, and
     * vice-versa.

     * @param fromKey low endpoint (inclusive) of the subMap.
     * @param toKey high endpoint (exclusive) of the subMap.
     *
     * @return a view of the portion of this map whose keys range
     * from <tt>fromKey</tt>, inclusive, to <tt>toKey</tt>,
     * exclusive.
     *
     * @throws ClassCastException if <tt>fromKey</tt> and
     * <tt>toKey</tt> cannot be compared to one another using this
     * map's comparator (or, if the map has no comparator, using
     * natural ordering).
     * @throws IllegalArgumentException if <tt>fromKey</tt> is
     * greater than <tt>toKey</tt> or either key is outside of 
     * the range of this submap.
     * @throws NullPointerException if <tt>fromKey</tt> or
     * <tt>toKey</tt> is <tt>null</tt>.
     */
    public ConcurrentSkipListSubMap<K,V> subMap(K fromKey, K toKey) {
        if (fromKey == null || toKey == null)
            throw new NullPointerException();
        if (!inOpenRange(fromKey) || !inOpenRange(toKey))
            throw new IllegalArgumentException("key out of range");
        return new ConcurrentSkipListSubMap(m, fromKey, toKey);
    }

    /**
     * Returns a view of the portion of this map whose keys are
     * strictly less than <tt>toKey</tt>.  The returned sorted map
     * is backed by this map, so changes in the returned sorted
     * map are reflected in this map, and vice-versa.
     * @param toKey high endpoint (exclusive) of the headMap.
     * @return a view of the portion of this map whose keys are
     * strictly less than <tt>toKey</tt>.
     *
     * @throws ClassCastException if <tt>toKey</tt> is not
     * compatible with this map's comparator (or, if the map has
     * no comparator, if <tt>toKey</tt> does not implement
     * <tt>Comparable</tt>).
     * @throws IllegalArgumentException if <tt>toKey</tt> is
     * outside of the range of this submap.
     * @throws NullPointerException if <tt>toKey</tt> is
     * <tt>null</tt>.
     */
    public ConcurrentSkipListSubMap<K,V> headMap(K toKey) {
        if (toKey == null)
            throw new NullPointerException();
        if (!inOpenRange(toKey))
            throw new IllegalArgumentException("key out of range");
        return new ConcurrentSkipListSubMap(m, least, toKey);
    }

    /**
     * Returns a view of the portion of this map whose keys are
     * greater than or equal to <tt>fromKey</tt>.  The returned sorted
     * map is backed by this map, so changes in the returned sorted
     * map are reflected in this map, and vice-versa.
     * @param fromKey low endpoint (inclusive) of the tailMap.
     * @return a view of the portion of this map whose keys are
     * greater than or equal to <tt>fromKey</tt>.
     * @throws ClassCastException if <tt>fromKey</tt> is not
     * compatible with this map's comparator (or, if the map has
     * no comparator, if <tt>fromKey</tt> does not implement
     * <tt>Comparable</tt>).
     * @throws IllegalArgumentException if <tt>fromKey</tt> is
     * outside of the range of this submap.
     * @throws NullPointerException if <tt>fromKey</tt> is
     * <tt>null</tt>.
     */
    public  ConcurrentSkipListSubMap<K,V> tailMap(K fromKey) {
        if (fromKey == null)
            throw new NullPointerException();
        if (!inOpenRange(fromKey))
            throw new IllegalArgumentException("key out of range");
        return new ConcurrentSkipListSubMap(m, fromKey, fence);
    }

    /* ----------------  Relational methods -------------- */

    /**
     * Returns a key-value mapping associated with the least key
     * greater than or equal to the given key, or null if there is
     * no such entry. The returned entry does <em>not</em> support
     * the <tt>Entry.setValue</tt> method.
     * 
     * @param key the key.
     * @return an Entry associated with ceiling of given key, or null
     * if there is no such Entry.
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map.
     * @throws NullPointerException if key is <tt>null</tt>.
     */
    public Map.Entry<K,V> ceilingEntry(K key) {
        return m.getCeiling(key, least, fence);
    }

    /**
     * Returns a key-value mapping associated with the greatest
     * key strictly less than the given key, or null if there is no
     * such entry. The returned entry does <em>not</em> support
     * the <tt>Entry.setValue</tt> method.
     * 
     * @param key the key.
     * @return an Entry with greatest key less than the given
     * key, or null if there is no such Entry.
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map.
     * @throws NullPointerException if key is <tt>null</tt>.
     */
    public Map.Entry<K,V> lowerEntry(K key) {
        return m.getLower(key, least, fence);
    }

    /**
     * Returns a key-value mapping associated with the greatest
     * key less than or equal to the given key, or null if there is no
     * such entry. The returned entry does <em>not</em> support
     * the <tt>Entry.setValue</tt> method.
     * 
     * @param key the key.
     * @return an Entry associated with floor of given key, or null
     * if there is no such Entry.
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map.
     * @throws NullPointerException if key is <tt>null</tt>.
     */
    public Map.Entry<K,V> floorEntry(K key) {
        return m.getFloor(key, least, fence);
    }
        
    /**
     * Returns a key-value mapping associated with the least
     * key strictly greater than the given key, or null if there is no
     * such entry. The returned entry does <em>not</em> support
     * the <tt>Entry.setValue</tt> method.
     * 
     * @param key the key.
     * @return an Entry with least key greater than the given key, or
     * null if there is no such Entry.
     * @throws ClassCastException if key cannot be compared with the keys
     *            currently in the map.
     * @throws NullPointerException if key is <tt>null</tt>.
     */
    public Map.Entry<K,V> higherEntry(K key) {
        return m.getHigher(key, least, fence);
    }

    /**
     * Returns a key-value mapping associated with the least
     * key in this map, or null if the map is empty.
     * The returned entry does <em>not</em> support
     * the <tt>Entry.setValue</tt> method.
     * 
     * @return an Entry with least key, or null 
     * if the map is empty.
     */
    public Map.Entry<K,V> firstEntry() {
        for (;;) {
            ConcurrentSkipListMap.Node<K,V> n = firstNode();
            if (!isBeforeEnd(n)) 
                return null;
            Map.Entry<K,V> e = n.createSnapshot();
            if (e != null)
                return e;
        }
    }

    /**
     * Returns a key-value mapping associated with the greatest
     * key in this map, or null if the map is empty.
     * The returned entry does <em>not</em> support
     * the <tt>Entry.setValue</tt> method.
     * 
     * @return an Entry with greatest key, or null
     * if the map is empty.
     */
    public Map.Entry<K,V> lastEntry() {
        for (;;) {
            ConcurrentSkipListMap.Node<K,V> n = lastNode();
            if (n == null || !inHalfOpenRange(n.key))
                return null;
            Map.Entry<K,V> e = n.createSnapshot();
            if (e != null)
                return e;
        }
    }

    /**
     * Removes and returns a key-value mapping associated with
     * the least key in this map, or null if the map is empty.
     * The returned entry does <em>not</em> support
     * the <tt>Entry.setValue</tt> method.
     * 
     * @return the removed first entry of this map, or null
     * if the map is empty.
     */
    public Map.Entry<K,V> removeFirstEntry() {
        return m.removeFirstEntryOfSubrange(least, fence);
    }

    /* ---------------- Submap Views -------------- */

    /**
     * Returns a set view of the keys contained in this map.  The
     * set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports
     * element removal, which removes the corresponding mapping
     * from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt>, and <tt>clear</tt> operations.  It does
     * not support the <tt>add</tt> or <tt>addAll</tt> operations.
     * The view's <tt>iterator</tt> is a "weakly consistent"
     * iterator that will never throw {@link
     * java.util.ConcurrentModificationException}, and guarantees
     * to traverse elements as they existed upon construction of
     * the iterator, and may (but is not guaranteed to) reflect
     * any modifications subsequent to construction.
     *
     * @return a set view of the keys contained in this map.
     */
    public Set<K> keySet() {
        Set<K> ks = keySetView;
        return (ks != null) ? ks : (keySetView = new KeySetView());
    }

    class KeySetView extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return m.subMapKeyIterator(least, fence);
        }
        public int size() {
            return ConcurrentSkipListSubMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentSkipListSubMap.this.isEmpty();
        }
        public boolean contains(Object k) {
            return ConcurrentSkipListSubMap.this.containsKey(k);
        }
        public Object[] toArray() {
            Collection<K> c = new ArrayList<K>();
            for (Iterator<K> i = iterator(); i.hasNext(); )
                c.add(i.next());
            return c.toArray();
        }
        public <T> T[] toArray(T[] a) {
            Collection<K> c = new ArrayList<K>();
            for (Iterator<K> i = iterator(); i.hasNext(); )
                c.add(i.next());
            return c.toArray(a);
        }
    }

    /**
     * Returns a collection view of the values contained in this
     * map.  The collection is backed by the map, so changes to
     * the map are reflected in the collection, and vice-versa.
     * The collection supports element removal, which removes the
     * corresponding mapping from this map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.  The view's <tt>iterator</tt>
     * is a "weakly consistent" iterator that will never throw
     * {@link java.util.ConcurrentModificationException}, and
     * guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not
     * guaranteed to) reflect any modifications subsequent to
     * construction.
     *
     * @return a collection view of the values contained in this map.
     */
    public Collection<V> values() {
        Collection<V> vs = valuesView;
        return (vs != null) ? vs : (valuesView = new ValuesView());
    }

    class ValuesView extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return m.subMapValueIterator(least, fence);
        }
        public int size() {
            return ConcurrentSkipListSubMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentSkipListSubMap.this.isEmpty();
        }
        public boolean contains(Object v) {
            return ConcurrentSkipListSubMap.this.containsValue(v);
        }
        public Object[] toArray() {
            Collection<V> c = new ArrayList<V>();
            for (Iterator<V> i = iterator(); i.hasNext(); )
                c.add(i.next());
            return c.toArray();
        }
        public <T> T[] toArray(T[] a) {
            Collection<V> c = new ArrayList<V>();
            for (Iterator<V> i = iterator(); i.hasNext(); )
                c.add(i.next());
            return c.toArray(a);
        }
    }

    /**
     * Returns a collection view of the mappings contained in this
     * map.  Each element in the returned collection is a
     * <tt>Map.Entry</tt>.  The collection is backed by the map,
     * so changes to the map are reflected in the collection, and
     * vice-versa.  The collection supports element removal, which
     * removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.  The view's <tt>iterator</tt>
     * is a "weakly consistent" iterator that will never throw
     * {@link java.util.ConcurrentModificationException}, and
     * guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not
     * guaranteed to) reflect any modifications subsequent to
     * construction. The <tt>Map.Entry</tt> elements returned by
     * <tt>iterator.next()</tt> do <em>not</em> support the
     * <tt>setValue</tt> operation.
     *
     * @return a collection view of the mappings contained in this map.
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySetView;
        return (es != null) ? es : (entrySetView = new EntrySetView());
    }

    class EntrySetView extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return m.subMapEntryIterator(least, fence);
        }
        public int size() {
            return ConcurrentSkipListSubMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentSkipListSubMap.this.isEmpty();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K,V> e = (Map.Entry<K,V>) o;
            K key = e.getKey();
            if (!inHalfOpenRange(key))
                return false;
            V v = m.get(key);
            return v != null && v.equals(e.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K,V> e = (Map.Entry<K,V>) o;
            K key = e.getKey();
            if (!inHalfOpenRange(key))
                return false;
            return m.remove(key, e.getValue());
        }
        public Object[] toArray() {
            Collection<Map.Entry<K,V>> c = new ArrayList<Map.Entry<K,V>>();
            for (ConcurrentSkipListMap.Node<K,V> n = firstNode(); 
                 isBeforeEnd(n); 
                 n = n.next) {
                Map.Entry<K,V> e = n.createSnapshot();
                if (e != null) 
                    c.add(e);
            }
            return c.toArray();
        }
        public <T> T[] toArray(T[] a) {
            Collection<Map.Entry<K,V>> c = new ArrayList<Map.Entry<K,V>>();
            for (ConcurrentSkipListMap.Node<K,V> n = firstNode(); 
                 isBeforeEnd(n); 
                 n = n.next) {
                Map.Entry<K,V> e = n.createSnapshot();
                if (e != null) 
                    c.add(e);
            }
            return c.toArray(a);
        }
    }
}
