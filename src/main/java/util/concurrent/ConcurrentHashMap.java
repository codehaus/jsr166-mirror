/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

import java.util.*;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * A version of Hashtable supporting
 * concurrency for both retrievals and updates.
 *
 * <dl>
 * <dt> Retrievals
 *
 * <dd> Retrievals may overlap updates.  Successful retrievals using
 * get(key) and containsKey(key) usually run without
 * locking. Unsuccessful retrievals (i.e., when the key is not
 * present) do involve brief locking.  Because
 * retrieval operations can ordinarily overlap with update operations
 * (i.e., put, remove, and their derivatives), retrievals can only be
 * guaranteed to return the results of the most recently
 * <em>completed</em> operations holding upon their onset. Retrieval
 * operations may or may not return results reflecting in-progress
 * writing operations.  However, the retrieval operations do always
 * return consistent results -- either those holding before any single
 * modification or after it, but never a nonsense result.  For
 * aggregate operations such as putAll and clear, concurrent reads may
 * reflect insertion or removal of only some entries.  <p>
 *
 * Iterators and Enumerations (i.e., those returned by
 * keySet().iterator(), entrySet().iterator(), values().iterator(),
 * keys(), and elements()) return elements reflecting the state of the
 * hash table at some point at or since the creation of the
 * iterator/enumeration.  They will return at most one instance of
 * each element (via next()/nextElement()), but might or might not
 * reflect puts and removes that have been processed since
 * construction if the Iterator.  They do <em>not</em> throw
 * ConcurrentModificationException.  However, these iterators are
 * designed to be used by only one thread at a time. Passing an
 * iterator across multiple threads may lead to unpredictable traversal
 * if the table is being concurrently modified.  <p>
 *
 *
 * <dt> Updates
 *
 * <dd> This class supports a hard-wired preset <em>concurrency
 * level</em> of 32. This allows a maximum of 32 put and/or remove
 * operations to proceed concurrently. This level is an upper bound on
 * concurrency, not a guarantee, since it interacts with how
 * well-strewn elements are across bins of the table. (The preset
 * value in part reflects the fact that even on large multiprocessors,
 * factors other than synchronization tend to be bottlenecks when more
 * than 32 threads concurrently attempt updates.)
 * Additionally, operations triggering internal resizing and clearing
 * do not execute concurrently with any operation.
 * <p>
 *
 * There is <em>NOT</em> any support for locking the entire table to
 * prevent updates. 
 *
 * </dl>
 *
 *
 * This class may be used as a direct replacement for
 * java.util.Hashtable in any application that does not rely
 * on the ability to lock the entire table to prevent updates.
 * Like Hashtable but unlike java.util.HashMap,
 * this class does NOT allow <tt>null</tt> to be used as a key or
 * value.
 * <p>
 *
**/
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V>
        implements ConcurrentMap<K, V>, Cloneable, Serializable {

    /*
      The basic strategy is an optimistic-style scheme based on
      the guarantee that the hash table and its lists are always
      kept in a consistent enough state to be read without locking:

      * Read operations first proceed without locking, by traversing the
         apparently correct list of the apparently correct bin. If an
         entry is found, but not invalidated (value field null), it is
         returned. If not found, operations must recheck (after a memory
         barrier) to make sure they are using both the right list and
         the right table (which can change under resizes). If
         invalidated, reads must acquire main update lock to wait out
         the update, and then re-traverse.

      * All list additions are at the front of each bin, making it easy
         to check changes, and also fast to traverse.  Entry next
         pointers are never assigned. Remove() builds new nodes when
         necessary to preserve this.

      * Remove() (also clear()) invalidates removed nodes to alert read
         operations that they must wait out the full modifications.

      * Locking for puts, removes (and, when necessary gets, etc)
        is controlled by Segments, each covering a portion of the
        table. During operations requiring global exclusivity (mainly
        resize and clear), ALL of these locks are acquired at once.
        Note that these segments are NOT contiguous -- they are based
        on the least 5 bits of hashcodes. This ensures that the same
        segment controls the same slots before and after resizing, which
        is necessary for supporting concurrent retrievals. This
        comes at the price of a mismatch of logical vs physical locality,
        but this seems not to be a performance problem in practice.

    */

    /**
     * The hash table data.
     */
    private transient Entry<K,V>[] table;


    /**
     * The number of concurrency control segments.
     * The value can be at most 32 since ints are used
     * as bitsets over segments. Emprically, it doesn't
     * seem to pay to decrease it either, so the value should be at least 32.
     * In other words, do not redefine this :-)
     **/
    private static final int CONCURRENCY_LEVEL = 32;

    /**
     * Mask value for indexing into segments
     **/
    private static final int SEGMENT_MASK = CONCURRENCY_LEVEL - 1;

    /**
     * Bookkeeping for each concurrency control segment.
     * Each segment contains a local count of the number of
     * elements in its region.
     * However, the main use of a Segment is for its lock.
     **/
    private final static class Segment extends ReentrantLock {
        /**
         * The number of elements in this segment's region.
         **/
        private int count;

        /**
         * Get the count under synch.
         **/
        private int getCount() { 
            lock();
            try {
                return count; 
            }
            finally {
                unlock();
            }
        }

    }

    /**
     * The array of concurrency control segments.
     **/
    private transient final Segment[] segments = new Segment[CONCURRENCY_LEVEL];


    /**
     * The default initial number of table slots for this table (32).
     * Used when not otherwise specified in constructor.
     **/
    public static int DEFAULT_INITIAL_CAPACITY = 32;


    /**
     * The minimum capacity, used if a lower value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two.
     */
    private static final int MINIMUM_CAPACITY = 32;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The default load factor for this table (0.75)
     * Used when not otherwise specified in constructor.
     **/
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The load factor for the hash table.
     *
     * @serial
     */
    private final float loadFactor;

    /**
     * Per-segment resize threshold.
     *
     * @serial
     */
    private int threshold;


    /**
     * Number of segments voting for resize. The table is
     * doubled when 1/4 of the segments reach threshold.
     * Volatile but updated without synch since this is just a heuristic.
     **/
    private transient volatile int votesForResize;


    /**
     * Return the number of set bits in w.
     * For a derivation of this algorithm, see
     * "Algorithms and data structures with applications to
     *  graphics and geometry", by Jurg Nievergelt and Klaus Hinrichs,
     *  Prentice Hall, 1993.
     * See also notes by Torsten Sillke at
     * http://www.mathematik.uni-bielefeld.de/~sillke/PROBLEMS/bitcount
     **/
    private static int bitcount(int w) {
        w -= (0xaaaaaaaa & w) >>> 1;
        w = (w & 0x33333333) + ((w >>> 2) & 0x33333333);
        w = (w + (w >>> 4)) & 0x0f0f0f0f;
        w += w >>> 8;
        w += w >>> 16;
        return w & 0xff;
    }

    /**
     * Returns the appropriate capacity (power of two) for the specified
     * initial capacity argument.
     */
    private int p2capacity(int initialCapacity) {
        int cap = initialCapacity;

        // Compute the appropriate capacity
        int result;
        if (cap > MAXIMUM_CAPACITY || cap < 0) {
            result = MAXIMUM_CAPACITY;
        } else {
            result = MINIMUM_CAPACITY;
            while (result < cap)
                result <<= 1;
        }
        return result;
    }

    /**
     * Return hash code for Object x. Since we are using power-of-two
     * tables, it is worth the effort to improve hashcode via
     * the same multiplicative scheme as used in IdentityHashMap.
     */
    private static int hash(Object x) {
        int h = x.hashCode();
        // Multiply by 127 (quickly, via shifts), and mix in some high
        // bits to help guard against bunching of codes that are
        // consecutive or equally spaced.
        return ((h << 7) - h + (h >>> 9) + (h >>> 17));
    }


    /**
     * Check for equality of non-null references x and y.
     **/
    private boolean eq(Object x, Object y) {
        return x == y || x.equals(y);
    }

    /** Create table array and set the per-segment threshold **/
    private Entry<K,V>[] newTable(int capacity) {
        threshold = (int)(capacity * loadFactor / CONCURRENCY_LEVEL) + 1;
        return new Entry<K,V>[capacity];
    }

    /**
     * Constructs a new, empty map with the specified initial
     * capacity and the specified load factor.
     *
     * @param initialCapacity the initial capacity.
     *  The actual initial capacity is rounded up to the nearest power of two.
     * @param loadFactor  the load factor threshold, used to control resizing.
     *   This value is used in an approximate way: When at least
     *   a quarter of the segments of the table reach per-segment threshold, or
     *   one of the segments itself exceeds overall threshold,
     *   the table is doubled.
     *   This will on average cause resizing when the table-wide
     *   load factor is slightly less than the threshold. If you'd like
     *   to avoid resizing, you can set this to a ridiculously large
     *   value.
     * @throws IllegalArgumentException  if the load factor is nonpositive.
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        if (!(loadFactor > 0))
            throw new IllegalArgumentException("Illegal Load factor: "+ loadFactor);
        this.loadFactor = loadFactor;
        for (int i = 0; i < segments.length; ++i)
            segments[i] = new Segment();
        int cap = p2capacity(initialCapacity);
        table = newTable(cap);
    }

    /**
     * Constructs a new, empty map with the specified initial
     * capacity and default load factor.
     *
     * @param   initialCapacity   the initial capacity of the
     *                            ConcurrentHashMap.
     * @throws    IllegalArgumentException if the initial maximum number
     *              of elements is less
     *              than zero.
     */
    public ConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new, empty map with a default initial capacity
     * and default load factor.
     */
    public ConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new map with the same mappings as the given map.  The
     * map is created with a capacity of twice the number of mappings in
     * the given map or 32 (whichever is greater), and a default load factor.
     */
    public <A extends K, B extends V> ConcurrentHashMap(Map<A,B> t) {
        this(Math.max((int) (t.size() / DEFAULT_LOAD_FACTOR) + 1,
            MINIMUM_CAPACITY),
            DEFAULT_LOAD_FACTOR);
            putAll(t);
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map.
     */
    public int size() {
        int c = 0;
        for (int i = 0; i < segments.length; ++i)
            c += segments[i].getCount();
        return c;
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings.
     */
    public boolean isEmpty() {
        for (int i = 0; i < segments.length; ++i)
            if (segments[i].getCount() != 0)
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
     * @exception  NullPointerException  if the key is
     *               <code>null</code>.
     * @see     #put(Object, Object)
     */
    public V get(Object key) {
        int hash = hash(key);     // throws null pointer exception if key null

        // Try first without locking...
        Entry<K,V>[] tab = table;
        int index = hash & (tab.length - 1);
        Entry<K,V> first = tab[index];
        Entry<K,V> e;

        for (e = first; e != null; e = e.next) {
            if (e.hash == hash && eq(key, e.key)) {
                V value = e.value;
                if (value != null)
                    return value;
                else
                    break;
            }
        }

        // Recheck under synch if key apparently not there or interference
        Segment seg = segments[hash & SEGMENT_MASK];
        seg.lock();
        try {
            tab = table;
            index = hash & (tab.length - 1);
            Entry<K,V> newFirst = tab[index];
            if (e != null || first != newFirst) {
                for (e = newFirst; e != null; e = e.next) {
                    if (e.hash == hash && eq(key, e.key))
                        return e.value;
                }
            }
            return null;
        }
        finally {
            seg.unlock();
        }
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param   key   possible key.
     * @return  <code>true</code> if and only if the specified object
     *          is a key in this table, as determined by the
     *          <tt>equals</tt> method; <code>false</code> otherwise.
     * @exception  NullPointerException  if the key is
     *               <code>null</code>.
     * @see     #contains(Object)
     */
    public boolean containsKey(Object key) {
        return get(key) != null;
    }


    /**
     * Maps the specified <code>key</code> to the specified
     * <code>value</code> in this table. Neither the key nor the
     * value can be <code>null</code>. (Note that this policy is
     * the same as for java.util.Hashtable, but unlike java.util.HashMap,
     * which does accept nulls as valid keys and values.)<p>
     *
     * The value can be retrieved by calling the <code>get</code> method
     * with a key that is equal to the original key.
     *
     * @param      key     the table key.
     * @param      value   the value.
     * @return     the previous value of the specified key in this table,
     *             or <code>null</code> if it did not have one.
     * @exception  NullPointerException  if the key or value is
     *               <code>null</code>.
     * @see     Object#equals(Object)
     * @see     #get(Object)
     */
    public V put(K key, V value) {
        if (value == null)
            throw new NullPointerException();

        int hash = hash(key);
        Segment seg = segments[hash & SEGMENT_MASK];
        int segcount;
        Entry<K,V>[] tab;
        int votes;

        seg.lock();
        try {
            tab = table;
            int index = hash & (tab.length-1);
            Entry<K,V> first = tab[index];

            for (Entry<K,V> e = first; e != null; e = e.next) {
                if (e.hash == hash && eq(key, e.key)) {
                    V oldValue = e.value;
                    e.value = value;
                    return oldValue;
                }
            }

            //  Add to front of list
            Entry<K,V> newEntry = new Entry<K,V>(hash, key, value, first);
            tab[index] = newEntry;

            if ((segcount = ++seg.count) < threshold)
                return null;

            int bit = (1 << (hash & SEGMENT_MASK));
            votes = votesForResize;
            if ((votes & bit) == 0)
                votes = votesForResize |= bit;
        }
        finally {
            seg.unlock();
        }

        // Attempt resize if 1/4 segs vote,
        // or if this seg itself reaches the overall threshold.
        // (The latter check is just a safeguard to avoid pathological cases.)
        if (bitcount(votes) >= CONCURRENCY_LEVEL / 4  ||
        segcount > (threshold * CONCURRENCY_LEVEL))
            resize(tab);

        return null;
    }

    public V putIfAbsent(K key, V value) {
        if (value == null)
            throw new NullPointerException();

        int hash = hash(key);
        Segment seg = segments[hash & SEGMENT_MASK];
        int segcount;
        Entry<K,V>[] tab;
        int votes;

        seg.lock();
        try {
            tab = table;
            int index = hash & (tab.length-1);
            Entry<K,V> first = tab[index];

            for (Entry<K,V> e = first; e != null; e = e.next) {
                if (e.hash == hash && eq(key, e.key)) {
                    V oldValue = e.value;
                    return oldValue;
                }
            }

            //  Add to front of list
            Entry<K,V> newEntry = new Entry<K,V>(hash, key, value, first);
            tab[index] = newEntry;

            if ((segcount = ++seg.count) < threshold)
                return null;

            int bit = (1 << (hash & SEGMENT_MASK));
            votes = votesForResize;
            if ((votes & bit) == 0)
                votes = votesForResize |= bit;
        }
        finally {
            seg.unlock();
        }

        // Attempt resize if 1/4 segs vote,
        // or if this seg itself reaches the overall threshold.
        // (The latter check is just a safeguard to avoid pathological cases.)
        if (bitcount(votes) >= CONCURRENCY_LEVEL / 4  ||
        segcount > (threshold * CONCURRENCY_LEVEL))
            resize(tab);

        return value;
    }

    /**
     * Gather all locks in order to call rehash, by
     * recursing within synch blocks for each segment index.
     * @param index the current segment. initially call value must be 0
     * @param assumedTab the state of table on first call to resize. If
     * this changes on any call, the attempt is aborted because the
     * table has already been resized by another thread.
     */
    private void resize(Entry<K,V>[] assumedTab) {
        boolean ok = true;
        int lastlocked = 0;
        for (int i = 0; i < segments.length; ++i) {
            segments[i].lock();
            lastlocked = i;
            if (table != assumedTab) {
                ok = false;
                break;
            }
        }
        try {
            if (ok)
                rehash();
        }
        finally {
            for (int i = lastlocked; i >= 0; --i) 
                segments[i].unlock();
        }
    }

    /**
     * Rehashes the contents of this map into a new table
     * with a larger capacity.
     */
    private void rehash() {
        votesForResize = 0; // reset

        Entry<K,V>[] oldTable = table;
        int oldCapacity = oldTable.length;

        if (oldCapacity >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE; // avoid retriggering
            return;
        }

        int newCapacity = oldCapacity << 1;
        Entry<K,V>[] newTable = newTable(newCapacity);
        int mask = newCapacity - 1;

        /*
         * Reclassify nodes in each list to new Map.  Because we are
         * using power-of-two expansion, the elements from each bin
         * must either stay at same index, or move to
         * oldCapacity+index. We also eliminate unnecessary node
         * creation by catching cases where old nodes can be reused
         * because their next fields won't change. Statistically, at
         * the default threshhold, only about one-sixth of them need
         * cloning. (The nodes they replace will be garbage
         * collectable as soon as they are no longer referenced by any
         * reader thread that may be in the midst of traversing table
         * right now.)
         */

        for (int i = 0; i < oldCapacity ; i++) {
            // We need to guarantee that any existing reads of old Map can
            //  proceed. So we cannot yet null out each bin.
            Entry<K,V> e = oldTable[i];

            if (e != null) {
                int idx = e.hash & mask;
                Entry<K,V> next = e.next;

                //  Single node on list
                if (next == null)
                    newTable[idx] = e;

                else {
                    // Reuse trailing consecutive sequence of all same bit
                    Entry<K,V> lastRun = e;
                    int lastIdx = idx;
                    for (Entry<K,V> last = next; last != null; last = last.next) {
                        int k = last.hash & mask;
                        if (k != lastIdx) {
                            lastIdx = k;
                            lastRun = last;
                        }
                    }
                    newTable[lastIdx] = lastRun;

                    // Clone all remaining nodes
                    for (Entry<K,V> p = e; p != lastRun; p = p.next) {
                        int k = p.hash & mask;
                        newTable[k] = new Entry<K,V>(p.hash, p.key,
                        p.value, newTable[k]);
                    }
                }
            }
        }

        table = newTable;
    }


    /**
     * Removes the key (and its corresponding value) from this
     * table. This method does nothing if the key is not in the table.
     *
     * @param   key   the key that needs to be removed.
     * @return  the value to which the key had been mapped in this table,
     *          or <code>null</code> if the key did not have a mapping.
     * @exception  NullPointerException  if the key is
     *               <code>null</code>.
     */
    public V remove(Object key) {
        return remove(key, null);
    }


    /**
     * Removes the (key, value) pair from this
     * table. This method does nothing if the key is not in the table,
     * or if the key is associated with a different value. This method
     * is needed by EntrySet.
     *
     * @param   key   the key that needs to be removed.
     * @param   value   the associated value. If the value is null,
     *                   it means "any value".
     * @return  the value to which the key had been mapped in this table,
     *          or <code>null</code> if the key did not have a mapping.
     * @exception  NullPointerException  if the key is
     *               <code>null</code>.
     */
    private V remove(Object key, V value) {
        /*
          Find the entry, then
            1. Set value field to null, to force get() to retry
            2. Rebuild the list without this entry.
               All entries following removed node can stay in list, but
               all preceeding ones need to be cloned.  Traversals rely
               on this strategy to ensure that elements will not be
              repeated during iteration.
         */

        int hash = hash(key);
        Segment seg = segments[hash & SEGMENT_MASK];

        seg.lock();
        try {
            Entry<K,V>[] tab = table;
            int index = hash & (tab.length-1);
            Entry<K,V> first = tab[index];
            Entry<K,V> e = first;

            for (;;) {
                if (e == null)
                    return null;
                if (e.hash == hash && eq(key, e.key))
                    break;
                e = e.next;
            }

            V oldValue = e.value;
            if (value != null && !value.equals(oldValue))
                return null;

            e.value = null;

            Entry<K,V> head = e.next;
            for (Entry<K,V> p = first; p != e; p = p.next)
                head = new Entry<K,V>(p.hash, p.key, p.value, head);
            tab[index] = head;
            seg.count--;
            return oldValue;
        }
        finally {
            seg.unlock();
        }
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
     * @exception  NullPointerException  if the value is <code>null</code>.
     */
    public boolean containsValue(Object value) {

        if (value == null) throw new NullPointerException();

        for (int s = 0; s < segments.length; ++s) {
            Segment seg = segments[s];
            Entry<K,V>[] tab;
            seg.lock();
            try {
                tab = table; 
            }
            finally {
                seg.unlock();
            }
            for (int i = s; i < tab.length; i+= segments.length) {
                for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                    if (value.equals(e.value))
                        return true;
            }
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
     * @exception  NullPointerException  if the value is <code>null</code>.
     * @see        #containsKey(Object)
     * @see        #containsValue(Object)
     * @see        Map
     */
    public boolean contains(V value) {
        return containsValue(value);
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     *
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified Map.
     *
     * @param t Mappings to be stored in this map.
     */
    public <A extends K, B extends V> void putAll(Map<A, B> t) {
        int n = t.size();
        if (n == 0)
            return;

        // Expand enough to hold at least n elements without resizing.
        // We can only resize table by factor of two at a time.
        // It is faster to rehash with fewer elements, so do it now.
        for(;;) {
            Entry<K,V>[] tab;
            int max;
            // must synch on some segment. pick 0.
            segments[0].lock();
            try {
                tab = table;
                max = threshold * CONCURRENCY_LEVEL;
            }
            finally {
                segments[0].unlock();
            }
            if (n < max)
                break;
            resize(tab);
        }

        for (Iterator<Map.Entry<A,B>> it = t.entrySet().iterator(); it.hasNext();) {
            Map.Entry<A,B> entry = (Map.Entry<A,B>) it.next();
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Removes all mappings from this map.
     */
    public void clear() {
        // We don't need all locks at once so long as locks
        //   are obtained in low to high order
        for (int s = 0; s < segments.length; ++s) {
            Segment seg = segments[s];
            seg.lock();
            try {
                Entry<K,V>[] tab = table;
                for (int i = s; i < tab.length; i+= segments.length) {
                    for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                        e.value = null;
                    tab[i] = null;
                    seg.count = 0;
                }
            }
            finally {
                seg.unlock();
            }
        }
    }

    /**
     * Returns a shallow copy of this
     * <tt>ConcurrentHashMap</tt> instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map.
     */
    public Object clone() {
        // We cannot call super.clone, since it would share final segments array,
        // and there's no way to reassign finals.
        return new ConcurrentHashMap<K,V>(this);
    }

    // Views

    private transient Set<K> keySet = null;
    private transient Set<Map.Entry<K,V>> entrySet = null;
    private transient Collection<V> values = null;

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
        return (ks != null)? ks : (keySet = new KeySet());
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
        return (vs != null)? vs : (values = new Values());
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

    private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Map.Entry<K,V> entry) {
            V v = ConcurrentHashMap.this.get(entry.getKey());
            return v != null && v.equals(entry.getValue());
        }
        public boolean remove(Map.Entry<K,V> e) {
            return ConcurrentHashMap.this.remove(e.getKey(), e.getValue()) != null;
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
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
    public Enumeration keys() {
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
    public Enumeration elements() {
        return new ValueIterator();
    }

    /**
     * ConcurrentHashMap collision list entry.
     */
    private static class Entry<K,V> implements Map.Entry<K,V> {
        /*
           The use of volatile for value field ensures that
           we can detect status changes without synchronization.
           The other fields are never changed, and are
           marked as final.
         */

        private final K key;
        private volatile V value;
        private final int hash;
        private final Entry<K,V> next;

        Entry(int hash, K key, V value, Entry<K,V> next) {
            this.value = value;
            this.hash = hash;
            this.key = key;
            this.next = next;
        }

        // Map.Entry Ops

        public K getKey() {
            return key;
        }

        /**
         * Get the value.  Note: In an entrySet or entrySet.iterator,
         * unless you can guarantee lack of concurrent modification,
         * <tt>getValue</tt> <em>might</em> return null, reflecting the
         * fact that the entry has been concurrently removed. However,
         * there are no assurances that concurrent removals will be
         * reflected using this method.
         *
         * @return     the current value, or null if the entry has been
         * detectably removed.
         **/
        public V getValue() {
            return value;
        }

        /**
         * Set the value of this entry.  Note: In an entrySet or
         * entrySet.iterator), unless you can guarantee lack of concurrent
         * modification, <tt>setValue</tt> is not strictly guaranteed to
         * actually replace the value field obtained via the <tt>get</tt>
         * operation of the underlying hash table in multithreaded
         * applications.  If iterator-wide synchronization is not used,
         * and any other concurrent <tt>put</tt> or <tt>remove</tt>
         * operations occur, sometimes even to <em>other</em> entries,
         * then this change is not guaranteed to be reflected in the hash
         * table. (It might, or it might not. There are no assurances
         * either way.)
         *
         * @param      value   the new value.
         * @return     the previous value, or null if entry has been detectably
         * removed.
         * @exception  NullPointerException  if the value is <code>null</code>.
         *
         **/
        public V setValue(V value) {
            if (value == null)
                throw new NullPointerException();
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry e = (Map.Entry)o;
            return (key.equals(e.getKey()) && value.equals(e.getValue()));
        }

        public int hashCode() {
            return  key.hashCode() ^ value.hashCode();
        }

        public String toString() {
            return key + "=" + value;
        }

    }

    private abstract class HashIterator<T> implements Iterator<T>, Enumeration {
        private final Entry<K,V>[] tab;      // snapshot of table
        private int index;                   // current slot
        Entry<K,V> entry = null;             // current node of slot
        K currentKey;                        // key for current node
        V currentValue;                      // value for current node
        private Entry lastReturned = null;   // last node returned by next

        private HashIterator() {
            // force all segments to synch
            for (int i = 0; i < segments.length; ++i) {
                segments[i].lock();
                segments[i].unlock();
            }
            tab = table; 
            index = tab.length - 1;
        }

        public boolean hasMoreElements() { return hasNext(); }
        public Object nextElement() { return next(); }

        public boolean hasNext() {
           /*
             currentkey and currentValue are set here to ensure that next()
             returns normally if hasNext() returns true. This avoids
             surprises especially when final element is removed during
             traversal -- instead, we just ignore the removal during
             current traversal.
            */

            while (true) {
                if (entry != null) {
                    V v = entry.value;
                    if (v != null) {
                        currentKey = entry.key;
                        currentValue = v;
                        return true;
                    }
                    else
                        entry = entry.next;
                }

                while (entry == null && index >= 0)
                    entry = tab[index--];

                if (entry == null) {
                    currentKey = null;
                    currentValue = null;
                    return false;
                }
            }
        }

        abstract T returnValueOfNext();

        public T next() {
            if (currentKey == null && !hasNext())
                throw new NoSuchElementException();

            T result = returnValueOfNext();
            lastReturned = entry;
            currentKey = null;
            currentValue = null;
            entry = entry.next;
            return result;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            ConcurrentHashMap.this.remove(lastReturned.key);
            lastReturned = null;
        }

    }

    private class KeyIterator extends HashIterator<K> {
        K returnValueOfNext() { return currentKey; }
        public K next() { return super.next(); }
    }

    private class ValueIterator extends HashIterator<V> {
        V returnValueOfNext() { return currentValue; }
        public V next() { return super.next(); }
    }

    private class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        Map.Entry<K,V> returnValueOfNext() { return entry; }
        public Map.Entry<K,V> next() { return super.next(); }
    }

    /**
     * Save the state of the <tt>ConcurrentHashMap</tt>
     * instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData
     * An estimate of the table size, followed by
     * the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException  {
        // Write out the loadfactor, and any hidden stuff
        s.defaultWriteObject();

        // Write out capacity estimate. It is OK if this
        // changes during the write, since it is only used by
        // readObject to set initial capacity, to avoid needless resizings.

        int cap;
        segments[0].lock();
        try {
            cap = table.length;
        }
        finally {
            segments[0].unlock();
        }
        s.writeInt(cap);

        // Write out keys and values (alternating)
        for (int k = 0; k < segments.length; ++k) {
            Segment seg = segments[k];
            Entry[] tab;
            seg.lock();
            try {
                tab = table; 
            }
            finally {
                seg.unlock();
            }
            for (int i = k; i < tab.length; i+= segments.length) {
                for (Entry e = tab[i]; e != null; e = e.next) {
                    s.writeObject(e.key);
                    s.writeObject(e.value);
                }
            }
        }

        s.writeObject(null);
        s.writeObject(null);
    }

    /**
     * Reconstitute the <tt>ConcurrentHashMap</tt>
     * instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException  {

        // Read in the threshold, loadfactor, and any hidden stuff
        s.defaultReadObject();

        int cap = s.readInt();
        table = newTable(cap);
        for (int i = 0; i < segments.length; ++i)
            segments[i] = new Segment();


        // Read the keys and values, and put the mappings in the table
        while (true) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null)
                break;
            put(key, value);
        }
    }
}
