/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;
import java.util.*;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * A hash table supporting full concurrency of retrievals and
 * adjustable expected concurrency for updates. This class obeys the
 * same functional specification as
 * <tt>java.util.Hashtable</tt>. However, even though all operations
 * are thread-safe, retrieval operations do <em>not</em> entail
 * locking, and there is <em>not</em> any support for locking the
 * entire table in a way that prevents all access.  This class is
 * fully interoperable with Hashtable in programs that rely on its
 * thread safety but not on its synchronization details.
 *
 * <p> Retrieval operations (including <tt>get</tt>) ordinarily
 * overlap with update operations (including <tt>put</tt> and
 * <tt>remove</tt>). Retrievals reflect the results of the most
 * recently <em>completed</em> update operations holding upon their
 * onset.  For aggregate operations such as <tt>putAll</tt> and
 * <tt>clear</tt>, concurrent retrievals may reflect insertion or
 * removal of only some entries.  Similarly, Iterators and
 * Enumerations return elements reflecting the state of the hash table
 * at some point at or since the creation of the iterator/enumeration.
 * They do <em>not</em> throw ConcurrentModificationException.
 * However, Iterators are designed to be used by only one thread at a
 * time.
 *
 * <p> The allowed concurrency among update operations is controlled
 * by the optional <tt>segments</tt> constructor argument (default
 * 16). The table is divided into this many independent parts, each of
 * which can be updated concurrently. Because placement in hash tables
 * is essentially random, the actual concurrency will vary. As a rough
 * rule of thumb, you should choose at least as many segments as you
 * expect concurrent threads. However, using more segments than you
 * need can waste space and time. Using a value of 1 for
 * <tt>segments</tt> results in a table that is concurrently readable
 * but can only be updated by one thread at a time.
 *
 * <p> Like Hashtable but unlike java.util.HashMap, this class does
 * NOT allow <tt>null</tt> to be used as a key or value.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V>
        implements ConcurrentMap<K, V>, Cloneable, Serializable {

    /*
     * The basic strategy is to subdivide the table among Segments,
     * each of which itself is a concurrently readable hash table.
     */

    /* ---------------- Constants -------------- */

    /**
     * The default initial number of table slots for this table (32).
     * Used when not otherwise specified in constructor.
     */
    private static int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly
     * specified by either of the constructors with arguments.  MUST
     * be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The default load factor for this table.  Used when not
     * otherwise specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The default number of concurrency control segments.
     **/
    private static final int DEFAULT_SEGMENTS = 16;

    /* ---------------- Fields -------------- */

    /**
     * Mask value for indexing into segments. The upper bits of a
     * key's hash code are used to choose the segment.
     **/
    private final int segmentMask;

    /**
     * Shift value for indexing within segments.
     **/
    private final int segmentShift;

    /**
     * The segments, each of which is a specialized hash table
     */
    private final Segment[] segments;

    private transient Set<K> keySet;
    private transient Set/*<Map.Entry<K,V>>*/ entrySet;
    private transient Collection<V> values;

    /* ---------------- Small Utilities -------------- */

    /**
     * Return a hash code for non-null Object x.
     * Uses the same hash code spreader as most other j.u hash tables.
     * @param x the object serving as a key
     * @return the hash code
     */
    private static int hash(Object x) {
        int h = x.hashCode();
        h += ~(h << 9);
        h ^=  (h >>> 14);
        h +=  (h << 4);
        h ^=  (h >>> 10);
        return h;
    }

    /**
     * Return the segment that should be used for key with given hash
     */
    private Segment<K,V> segmentFor(int hash) {
        return segments[(hash >>> segmentShift) & segmentMask];
    }

    /* ---------------- Inner Classes -------------- */

    /**
     * Segments are specialized versions of hash tables.  This
     * subclasses from ReentrantLock opportunistically, just to
     * simplify some locking and avoid separate construction.
     **/
    private static final class Segment<K,V> extends ReentrantLock implements Serializable {
        /*
         * Segments maintain a table of entry lists that are ALWAYS
         * kept in a consistent state, so can be read without locking.
         * Next fields of nodes are immutable (final).  All list
         * additions are performed at the front of each bin. This
         * makes it easy to check changes, and also fast to traverse.
         * When nodes would otherwise be changed, new nodes are
         * created to replace them. This works well for hash tables
         * since the bin lists tend to be short. (The average length
         * is less than two for the default load factor threshold.)
         *
         * Read operations can thus proceed without locking, but rely
         * on a memory barrier to ensure that completed write
         * operations performed by other threads are
         * noticed. Conveniently, the "count" field, tracking the
         * number of elements, can also serve as the volatile variable
         * providing proper read/write barriers. This is convenient
         * because this field needs to be read in many read operations
         * anyway. The use of volatiles for this purpose is only
         * guaranteed to work in accord with reuirements in
         * multithreaded environments when run on JVMs conforming to
         * the clarified JSR133 memory model specification.  This true
         * for hotspot as of release 1.4.
         *
         * Implementors note. The basic rules for all this are:
         *
         *   - All unsynchronized read operations must first read the
         *     "count" field, and should not look at table entries if
         *     it is 0.
         *
         *   - All synchronized write operations should write to
         *     the "count" field after updating. The operations must not
         *     take any action that could even momentarily cause
         *     a concurrent read operation to see inconsistent
         *     data. This is made easier by the nature of the read
         *     operations in Map. For example, no operation
         *     can reveal that the table has grown but the threshold
         *     has not yet been updated, so there are no atomicity
         *     requirements for this with respect to reads.
         *
         * As a guide, all critical volatile reads and writes are marked
         * in code comments.
         */

        /**
         * The number of elements in this segment's region.
         **/
        transient volatile int count;

        /**
         * The table is rehashed when its size exceeds this threshold.
         * (The value of this field is always (int)(capacity *
         * loadFactor).)
         */
        private transient int threshold;

        /**
         * The per-segment table
         */
        transient HashEntry[] table;

        /**
         * The load factor for the hash table.  Even though this value
         * is same for all segments, it is replicated to avoid needing
         * links to outer object.
         * @serial
         */
        private final float loadFactor;

        Segment(int initialCapacity, float lf) {
            loadFactor = lf;
            setTable(new HashEntry[initialCapacity]);
        }

        /**
         * Set table to new HashEntry array.
         * Call only while holding lock or in constructor.
         **/
        private void setTable(HashEntry[] newTable) {
            table = newTable;
            threshold = (int)(newTable.length * loadFactor);
            count = count; // write-volatile
        }

        /* Specialized implementations of map methods */

        V get(K key, int hash) {
            if (count != 0) { // read-volatile
                HashEntry[] tab = table;
                int index = hash & (tab.length - 1);
                HashEntry<K,V> e = (HashEntry<K,V>) tab[index];
                while (e != null) {
                    if (e.hash == hash && key.equals(e.key))
                        return e.value;
                    e = e.next;
                }
            }
            return null;
        }

        boolean containsKey(Object key, int hash) {
            if (count != 0) { // read-volatile
                HashEntry[] tab = table;
                int index = hash & (tab.length - 1);
                HashEntry<K,V> e = (HashEntry<K,V>) tab[index];
                while (e != null) {
                    if (e.hash == hash && key.equals(e.key))
                        return true;
                    e = e.next;
                }
            }
            return false;
        }

        boolean containsValue(Object value) {
            if (count != 0) { // read-volatile
                HashEntry[] tab = table;
                int len = tab.length;
                for (int i = 0 ; i < len; i++)
                    for (HashEntry<K,V> e = tab[i] ; e != null ; e = e.next)
                        if (value.equals(e.value))
                            return true;
            }
            return false;
        }

        V put(K key, int hash, V value, boolean onlyIfAbsent) {
            lock();
            try {
                int c = count;
                HashEntry[] tab = table;
                int index = hash & (tab.length - 1);
                HashEntry<K,V> first = (HashEntry<K,V>) tab[index];

                for (HashEntry<K,V> e = first; e != null; e = (HashEntry<K,V>) e.next) {
                    if (e.hash == hash && key.equals(e.key)) {
                        V oldValue = e.value;
                        if (!onlyIfAbsent)
                            e.value = value;
                        count = c; // write-volatile
                        return oldValue;
                    }
                }

                tab[index] = new HashEntry<K,V>(hash, key, value, first);
                ++c;
                count = c; // write-volatile
                if (c > threshold)
                    setTable(rehash(tab));
                return null;
            }
            finally {
                unlock();
            }
        }

        private HashEntry[] rehash(HashEntry[] oldTable) {
            int oldCapacity = oldTable.length;
            if (oldCapacity >= MAXIMUM_CAPACITY)
                return oldTable;

            /*
             * Reclassify nodes in each list to new Map.  Because we are
             * using power-of-two expansion, the elements from each bin
             * must either stay at same index, or move with a power of two
             * offset. We eliminate unnecessary node creation by catching
             * cases where old nodes can be reused because their next
             * fields won't change. Statistically, at the default
             * threshhold, only about one-sixth of them need cloning when
             * a table doubles. The nodes they replace will be garbage
             * collectable as soon as they are no longer referenced by any
             * reader thread that may be in the midst of traversing table
             * right now.
             */

            HashEntry[] newTable = new HashEntry[oldCapacity << 1];
            int sizeMask = newTable.length - 1;
            for (int i = 0; i < oldCapacity ; i++) {
                // We need to guarantee that any existing reads of old Map can
                //  proceed. So we cannot yet null out each bin.
                HashEntry<K,V> e = oldTable[i];

                if (e != null) {
                    HashEntry<K,V> next = e.next;
                    int idx = e.hash & sizeMask;

                    //  Single node on list
                    if (next == null)
                        newTable[idx] = e;

                    else {
                        // Reuse trailing consecutive sequence at same slot
                        HashEntry<K,V> lastRun = e;
                        int lastIdx = idx;
                        for (HashEntry<K,V> last = next;
                             last != null;
                             last = last.next) {
                            int k = last.hash & sizeMask;
                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }
                        newTable[lastIdx] = lastRun;

                        // Clone all remaining nodes
                        for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
                            int k = p.hash & sizeMask;
                            newTable[k] = new HashEntry<K,V>(p.hash,
                                                             p.key,
                                                             p.value,
                                                             (HashEntry<K,V>) newTable[k]);
                        }
                    }
                }
            }
            return newTable;
        }

        /**
         * Remove; match on key only if value null, else match both.
         */
        V remove(Object key, int hash, Object value) {
            lock();
            try {
                int c = count;
                HashEntry[] tab = table;
                int index = hash & (tab.length - 1);
                HashEntry<K,V> first = tab[index];

                HashEntry<K,V> e = first;
                for (;;) {
                    if (e == null)
                        return null;
                    if (e.hash == hash && key.equals(e.key))
                        break;
                    e = e.next;
                }

                V oldValue = e.value;
                if (value != null && !value.equals(oldValue))
                    return null;

                // All entries following removed node can stay in list, but
                // all preceeding ones need to be cloned.
                HashEntry<K,V> newFirst = e.next;
                for (HashEntry<K,V> p = first; p != e; p = p.next)
                    newFirst = new HashEntry<K,V>(p.hash, p.key,
                                                  p.value, newFirst);
                tab[index] = newFirst;
                count = c-1; // write-volatile
                return oldValue;
            }
            finally {
                unlock();
            }
        }

        void clear() {
            lock();
            try {
                HashEntry[] tab = table;
                for (int i = 0; i < tab.length ; i++)
                    tab[i] = null;
                count = 0; // write-volatile
            }
            finally {
                unlock();
            }
        }
    }

    /**
     * ConcurrentReaderHashMap list entry.
     */
    private static class HashEntry<K,V> implements Entry<K,V> {
        private final K key;
        private V value;
        private final int hash;
        private final HashEntry<K,V> next;

        HashEntry(int hash, K key, V value, HashEntry<K,V> next) {
            this.value = value;
            this.hash = hash;
            this.key = key;
            this.next = next;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V newValue) {
            // We aren't required to, and don't provide any
            // visibility barriers for setting value.
            if (newValue == null)
                throw new NullPointerException();
            V oldValue = this.value;
            this.value = newValue;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry<K,V> e = (Entry)o;
            return (key.equals(e.getKey()) && value.equals(e.getValue()));
        }

        public int hashCode() {
            return  key.hashCode() ^ value.hashCode();
        }

        public String toString() {
            return key + "=" + value;
        }
    }


    /* ---------------- Public operations -------------- */

    /**
     * Constructs a new, empty map with the specified initial
     * capacity and the specified load factor.
     *
     * @param initialCapacity the initial capacity.  The actual
     * initial capacity is rounded up to the nearest power of two.
     * @param loadFactor  the load factor threshold, used to control resizing.
     * @param segments the number of concurrently accessible segments. the
     * actual number of segments is rounded to the next power of two.
     * @throws IllegalArgumentException if the initial capacity is
     * negative or the load factor or number of segments are
     * nonpositive.
     */
    public ConcurrentHashMap(int initialCapacity,
                             float loadFactor, int segments) {
        if (!(loadFactor > 0) || initialCapacity < 0 || segments <= 0)
            throw new IllegalArgumentException();

        // Find power-of-two sizes best matching arguments
        int sshift = 0;
        int ssize = 1;
        while (ssize < segments) {
            ++sshift;
            ssize <<= 1;
        }
        segmentShift = 32 - sshift;
        segmentMask = ssize - 1;
        this.segments = new Segment[ssize];

        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        int c = initialCapacity / ssize;
        if (c * ssize < initialCapacity)
            ++c;
        int cap = 1;
        while (cap < c)
            cap <<= 1;

        for (int i = 0; i < this.segments.length; ++i)
            this.segments[i] = new Segment<K,V>(cap, loadFactor);
    }

    /**
     * Constructs a new, empty map with the specified initial
     * capacity,  and with default load factor and segments.
     *
     * @param initialCapacity the initial capacity of the
     * ConcurrentHashMap.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative.
     */
    public ConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
    }

    /**
     * Constructs a new, empty map with a default initial capacity,
     * load factor, and number of segments
     */
    public ConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
    }

    /**
     * Constructs a new map with the same mappings as the given map.  The
     * map is created with a capacity of twice the number of mappings in
     * the given map or 11 (whichever is greater), and a default load factor.
     */
    public <A extends K, B extends V> ConcurrentHashMap(Map<A,B> t) {
        this(Math.max((int) (t.size() / DEFAULT_LOAD_FACTOR) + 1,
                      11),
             DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
        putAll(t);
    }

    // inherit Map javadoc
    public int size() {
        int c = 0;
        for (int i = 0; i < segments.length; ++i)
            c += segments[i].count;
        return c;
    }

    // inherit Map javadoc
    public boolean isEmpty() {
        for (int i = 0; i < segments.length; ++i)
            if (segments[i].count != 0)
                return false;
        return true;
    }

    /**
     * Returns the value to which the specified key is mapped in this table.
     *
     * @param   key   a key in the table.
     * @return  the value to which the key is mapped in this table;
     *          <code>null</code> if the key is not mapped to any value in
     *          this table.
     * @throws  NullPointerException  if the key is
     *               <code>null</code>.
     * @see     #put(Object, Object)
     */
    public V get(Object key) {
        int hash = hash(key); // throws NullPointerException if key null
        return segmentFor(hash).get((K) key, hash);
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param   key   possible key.
     * @return  <code>true</code> if and only if the specified object
     *          is a key in this table, as determined by the
     *          <tt>equals</tt> method; <code>false</code> otherwise.
     * @throws  NullPointerException  if the key is
     *               <code>null</code>.
     * @see     #contains(Object)
     */
    public boolean containsKey(Object key) {
        int hash = hash(key); // throws NullPointerException if key null
        return segmentFor(hash).containsKey(key, hash);
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value. Note: This method requires a full internal
     * traversal of the hash table, and so is much slower than
     * method <tt>containsKey</tt>.
     *
     * @param value value whose presence in this map is to be tested.
     * @return <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     * @throws  NullPointerException  if the value is <code>null</code>.
     */
    public boolean containsValue(Object value) {
        if (value == null)
            throw new NullPointerException();

        for (int i = 0; i < segments.length; ++i) {
            if (segments[i].containsValue(value))
                return true;
        }
        return false;
    }
    /**
     * Tests if some key maps into the specified value in this table.
     * This operation is more expensive than the <code>containsKey</code>
     * method.<p>
     *
     * Note that this method is identical in functionality to containsValue,
     * (which is part of the Map interface in the collections framework).
     *
     * @param      value   a value to search for.
     * @return     <code>true</code> if and only if some key maps to the
     *             <code>value</code> argument in this table as
     *             determined by the <tt>equals</tt> method;
     *             <code>false</code> otherwise.
     * @throws  NullPointerException  if the value is <code>null</code>.
     * @see        #containsKey(Object)
     * @see        #containsValue(Object)
     * @see   Map
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Maps the specified <code>key</code> to the specified
     * <code>value</code> in this table. Neither the key nor the
     * value can be <code>null</code>. <p>
     *
     * The value can be retrieved by calling the <code>get</code> method
     * with a key that is equal to the original key.
     *
     * @param      key     the table key.
     * @param      value   the value.
     * @return     the previous value of the specified key in this table,
     *             or <code>null</code> if it did not have one.
     * @throws  NullPointerException  if the key or value is
     *               <code>null</code>.
     * @see     Object#equals(Object)
     * @see     #get(Object)
     */
    public V put(K key, V value) {
        if (value == null)
            throw new NullPointerException();
        int hash = hash(key);
        return segmentFor(hash).put(key, hash, value, false);
    }

    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * This is equivalent to
     * <pre>
     *   if (!map.containsKey(key)) map.put(key, value);
     *   return get(key);
     * </pre>
     * Except that the action is performed atomically.
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or <tt>null</tt>
     *         if there was no mapping for key.  A <tt>null</tt> return can
     *         also indicate that the map previously associated <tt>null</tt>
     *         with the specified key, if the implementation supports
     *         <tt>null</tt> values.
     *
     * @throws NullPointerException this map does not permit <tt>null</tt>
     *            keys or values, and the specified key or value is
     *            <tt>null</tt>.
     *
     **/
    public V putIfAbsent(K key, V value) {
        if (value == null)
            throw new NullPointerException();
        int hash = hash(key);
        return segmentFor(hash).put(key, hash, value, true);
    }


    /**
     * Copies all of the mappings from the specified map to this one.
     *
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified Map.
     *
     * @param t Mappings to be stored in this map.
     */
    public void putAll(Map<? extends K, ? extends V> t) {
        Iterator it = t.entrySet().iterator();
        while (it.hasNext()) {
            Entry<K,V> e = (Entry<K, V>) it.next();
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * Removes the key (and its corresponding value) from this
     * table. This method does nothing if the key is not in the table.
     *
     * @param   key   the key that needs to be removed.
     * @return  the value to which the key had been mapped in this table,
     *          or <code>null</code> if the key did not have a mapping.
     * @throws  NullPointerException  if the key is
     *               <code>null</code>.
     */
    public V remove(Object key) {
        int hash = hash(key);
        return segmentFor(hash).remove(key, hash, null);
    }

    /**
     * Removes the (key, value) pair from this
     * table. This method does nothing if the key is not in the table,
     * or if the key is associated with a different value.
     *
     * @param   key   the key that needs to be removed.
     * @param   value   the associated value. If the value is null,
     *                   it means "any value".
     * @return  the value to which the key had been mapped in this table,
     *          or <code>null</code> if the key did not have a mapping.
     * @throws  NullPointerException  if the key is
     *               <code>null</code>.
     */
    public V remove(Object key, Object value) {
        int hash = hash(key);
        return segmentFor(hash).remove(key, hash, value);
    }

    /**
     * Removes all mappings from this map.
     */
    public void clear() {
        for (int i = 0; i < segments.length; ++i)
            segments[i].clear();
    }


    /**
     * Returns a shallow copy of this
     * <tt>ConcurrentHashMap</tt> instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map.
     */
    public Object clone() {
        // We cannot call super.clone, since it would share final
        // segments array, and there's no way to reassign finals.

        float lf = segments[0].loadFactor;
        int segs = segments.length;
        int cap = (int)(size() / lf);
        if (cap < segs) cap = segs;
        ConcurrentHashMap t = new ConcurrentHashMap(cap, lf, segs);
        t.putAll(this);
        return t;
    }

    /**
     * Returns a set view of the keys contained in this map.  The set is
     * backed by the map, so changes to the map are reflected in the set, and
     * vice-versa.  The set supports element removal, which removes the
     * corresponding mapping from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt>, and
     * <tt>clear</tt> operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * @return a set view of the keys contained in this map.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }


    /**
     * Returns a collection view of the values contained in this map.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from this map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the values contained in this map.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }


    /**
     * Returns a collection view of the mappings contained in this map.  Each
     * element in the returned collection is a <tt>Map.Entry</tt>.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the mappings contained in this map.
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }


    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return  an enumeration of the keys in this table.
     * @see     Enumeration
     * @see     #elements()
     * @see     #keySet()
     * @see     Map
     */
    public Enumeration<K> keys() {
        return new KeyIterator();
    }

    /**
     * Returns an enumeration of the values in this table.
     * Use the Enumeration methods on the returned object to fetch the elements
     * sequentially.
     *
     * @return  an enumeration of the values in this table.
     * @see     java.util.Enumeration
     * @see     #keys()
     * @see     #values()
     * @see     Map
     */
    public Enumeration<V> elements() {
        return new ValueIterator();
    }

    /* ---------------- Iterator Support -------------- */

    private abstract class HashIterator {
        private int nextSegmentIndex;
        private int nextTableIndex;
        private HashEntry[] currentTable;
        private HashEntry<K, V> nextEntry;
        private HashEntry<K, V> lastReturned;

        private HashIterator() {
            nextSegmentIndex = segments.length - 1;
            nextTableIndex = -1;
            advance();
        }

        public boolean hasMoreElements() { return hasNext(); }

        private void advance() {
            if (nextEntry != null && (nextEntry = nextEntry.next) != null)
                return;

            while (nextTableIndex >= 0) {
                if ( (nextEntry = currentTable[nextTableIndex--]) != null)
                    return;
            }

            while (nextSegmentIndex >= 0) {
                Segment<K,V> seg = segments[nextSegmentIndex--];
                if (seg.count != 0) {
                    currentTable = seg.table;
                    for (int j = currentTable.length - 1; j >= 0; --j) {
                        if ( (nextEntry = currentTable[j]) != null) {
                            nextTableIndex = j - 1;
                            return;
                        }
                    }
                }
            }
        }

        public boolean hasNext() { return nextEntry != null; }

        HashEntry<K,V> nextEntry() {
            if (nextEntry == null)
                throw new NoSuchElementException();
            lastReturned = nextEntry;
            advance();
            return lastReturned;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            ConcurrentHashMap.this.remove(lastReturned.key);
            lastReturned = null;
        }
    }

    private class KeyIterator extends HashIterator implements Iterator<K>, Enumeration<K> {
        public K next() { return super.nextEntry().key; }
        public K nextElement() { return super.nextEntry().key; }
    }

    private class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
        public V next() { return super.nextEntry().value; }
        public V nextElement() { return super.nextEntry().value; }
    }

    private class EntryIterator extends HashIterator implements Iterator<Entry<K,V>> {
        public Map.Entry<K,V> next() { return super.nextEntry(); }
    }

    private class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsKey(o);
        }
        public boolean remove(Object o) {
            return ConcurrentHashMap.this.remove(o) != null;
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    private class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsValue(o);
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    private class EntrySet extends AbstractSet {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K,V> e = (Map.Entry<K,V>)o;
            V v = ConcurrentHashMap.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K,V> e = (Map.Entry<K,V>)o;
            return ConcurrentHashMap.this.remove(e.getKey(), e.getValue()) != null;
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    /* ---------------- Serialization Support -------------- */

    /**
     * Save the state of the <tt>ConcurrentHashMap</tt>
     * instance to a stream (i.e.,
     * serialize it).
     * @param s the stream
     * @serialData
     * the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException  {
        s.defaultWriteObject();

        for (int k = 0; k < segments.length; ++k) {
            Segment<K,V> seg = segments[k];
            seg.lock();
            try {
                HashEntry[] tab = seg.table;
                for (int i = 0; i < tab.length; ++i) {
                    for (HashEntry<K,V> e = tab[i]; e != null; e = e.next) {
                        s.writeObject(e.key);
                        s.writeObject(e.value);
                    }
                }
            }
            finally {
                seg.unlock();
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }

    /**
     * Reconstitute the <tt>ConcurrentHashMap</tt>
     * instance from a stream (i.e.,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException  {
        s.defaultReadObject();

        // Initialize each segment to be minimally sized, and let grow.
        for (int i = 0; i < segments.length; ++i) {
            segments[i].setTable(new HashEntry[1]);
        }

        // Read the keys and values, and put the mappings in the table
        for (;;) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null)
                break;
            put(key, value);
        }
    }
}

