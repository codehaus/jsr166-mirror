/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.io.*;

/** 
 * A version of Hashtable that supports mostly-concurrent reading, but
 * exclusive writing.  Because reads are not limited to periods
 * without writes, a concurrent reader policy is weaker than a classic
 * reader/writer policy, but is generally faster and allows more
 * concurrency. This class is a good choice especially for tables that
 * are mainly created by one thread during the start-up phase of a
 * program, and from then on, are mainly read (with perhaps occasional
 * additions or removals) in many threads.  If you also need concurrency
 * among writes, consider instead using ConcurrentHashMap.
 * <p>
 *
 * Successful retrievals using get(key) and containsKey(key) usually
 * run without locking. Unsuccessful ones (i.e., when the key is not
 * present) do involve brief synchronization (locking).  Also, the
 * size and isEmpty methods are always synchronized.
 *
 * <p> Because retrieval operations can ordinarily overlap with
 * writing operations (i.e., put, remove, and their derivatives),
 * retrievals can only be guaranteed to return the results of the most
 * recently <em>completed</em> operations holding upon their
 * onset. Retrieval operations may or may not return results
 * reflecting in-progress writing operations.  However, the retrieval
 * operations do always return consistent results -- either those
 * holding before any single modification or after it, but never a
 * nonsense result.  For aggregate operations such as putAll and
 * clear, concurrent reads may reflect insertion or removal of only
 * some entries. In those rare contexts in which you use a hash table
 * to synchronize operations across threads (for example, to prevent
 * reads until after clears), you should either encase operations
 * in synchronized blocks, or instead use java.util.Hashtable.
 *
 */

public class ConcurrentReaderHashMap<K,V>  extends Dictionary<K,V> 
    implements ConcurrentMap<K,V>, Cloneable, Serializable {

    /*
     * This implementation is thread-safe, but not heavily
     * synchronized.  The basic strategy is to ensure that the hash
     * table and its lists are ALWAYS kept in a consistent state, so
     * can be read without locking.  Next fields of nodes are
     * immutable (final).  All list additions are performed at the
     * front of each bin. This makes it easy to check changes, and
     * also fast to traverse.  When nodes would otherwise be changed,
     * new nodes are created to replace them. This works well for hash
     * tables since the bin lists tend to be short. (The average
     * length is less than two for the default load factor threshold.)
     *
     * Read operations can thus proceed without locking, but rely on a
     * memory barrier to ensure that COMPLETED write operations
     * performed by other threads are noticed. Conveniently, the
     * "count" field, tracking the number of elements, can also serve
     * as the volatile variable providing proper read/write
     * barriers. This is convenient because this field needs to be
     * read in many read operations anyway. The use of volatiles for
     * this purpose is only guaranteed to work in accord with normal
     * expectations in multithreaded environments when run on JVMs
     * conforming to the clarified JSR133 memory model specification.
     * This true for hotspot as of release 1.4.
     *
     * Implementors note. The basic rules for all this are:
     *   - All unsynchronized read operations must first read
     *     the "count" field, and generally, should not look at table if 0.
     *     
     *   - All synchronized write operations should write to
     *     the "count" field after updating. The operations may not
     *     take any action that could even momentarily cause
     *     a concurrent read operation to see inconsistent
     *     data. This is made easier by the nature of the read
     *     operations in ConcurrentReaderHashMap. For example, no operation
     *     can reveal that the table has grown but the threshold
     *     has not yet been updated, so there are no atomicity
     *     requirements for this with respect to reads.
     *
     * As a guide, all critical volatile reads and writes are marked
     * in the code as comments.
     */

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 1421746759512286392L;

    /**
     * The default initial number of table slots for this table (32).
     * Used when not otherwise specified in constructor.
     */
    static int DEFAULT_INITIAL_CAPACITY = 16; 
  
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
     * The total number of mappings in the hash table.
     * Also serves as the read-barrier variable.
     */
    private transient volatile int count;

    /**
     * The hash table data.
     */
    private transient HashEntry<K,V>[] table;

    /**
     * The load factor for the hash table.  This is also used as a
     * recursion flag in method hashCode.  (Sorry for the sleaze but
     * this maintains 1.1 compatibility.)
     *
     * @serial
     */
    private float loadFactor;

    /**
     * The table is rehashed when its size exceeds this threshold.
     * (The value of this field is always (int)(capacity *
     * loadFactor).)
     *
     * @serial
     */
    private int threshold;

    /**
     * The number of times this map has been structurally modified
     * Structural modifications are those that change the number of
     * mappings in the map or otherwise modify its internal structure
     * (e.g., rehash).  This field is used to make iterators on
     * Collection-views of the map fail-fast.  (See
     * ConcurrentModificationException).
     */
    private transient int modCount;

    // internal utilities

    /**
     * Return a hash code for non-null Object x.  
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
     * Check for equality of non-null references x and y. 
     **/
    private static boolean eq(Object x, Object y) {
        return x == y || x.equals(y);
    }

    /**
     * Return index for hash code h. 
     */
    private static int indexFor(int h, int length) {
        return h & (length-1);
    }

    /**
     * Set table to new HashEntry array. 
     * Call only while holding lock or in constructor.
     **/
    private void setTable(HashEntry<K,V>[] newTable) {
        table = newTable;
        threshold = (int)(newTable.length * loadFactor);
        count = count; // write-volatile
    }    

    /**
     * Constructs a new, empty map with a default initial capacity
     * and load factor.
     */
    public ConcurrentReaderHashMap() {
        loadFactor = DEFAULT_LOAD_FACTOR;
        setTable(new HashEntry[DEFAULT_INITIAL_CAPACITY]);
    }

    /**
     * Constructs a new, empty map with the specified initial 
     * capacity and the specified load factor. 
     *
     * @param initialCapacity the initial capacity
     *  The actual initial capacity is rounded to the nearest power of two.
     * @param loadFactor  the load factor of the ConcurrentReaderHashMap
     * @throws IllegalArgumentException  if the initial capacity is less
     *               than zero, or if the load factor is nonpositive.
     */
    public ConcurrentReaderHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal Load factor: "+
                                               loadFactor);
        this.loadFactor = loadFactor;

        int capacity;
        if (initialCapacity > MAXIMUM_CAPACITY)
            capacity = MAXIMUM_CAPACITY;
        else {
            capacity = 1;
            while (capacity < initialCapacity) 
                capacity <<= 1;
        }
    
        setTable(new HashEntry[capacity]);
    }

    /**
     * Constructs a new, empty map with the specified initial 
     * capacity and default load factor.
     *
     * @param   initialCapacity   the initial capacity of the 
     *                            ConcurrentReaderHashMap.
     *  The actual initial capacity is rounded to the nearest power of two.
     * @throws    IllegalArgumentException if the initial maximum number 
     *              of elements is less
     *              than zero.
     */

    public ConcurrentReaderHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new map with the same mappings as the given map.  The
     * map is created with a default load factor.
     */

    public ConcurrentReaderHashMap(Map<K,V> t) {
        this(Math.max((int) (t.size() / DEFAULT_LOAD_FACTOR) + 1, 16),
             DEFAULT_LOAD_FACTOR);
        putAll(t);
    }


    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map.
     */
    public int size() {
        return count;   // read-volatile
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings.
     */
    public boolean isEmpty() {
        return count == 0; // read-volatile
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
    public V get(K key) { 
        int hash = hash(key); // throws NullPointerException if key null

        if (count != 0) { // read-volatile
            HashEntry<K,V>[] tab = table;
            int index = indexFor(hash, tab.length);
            HashEntry<K,V> e = tab[index]; 
            while (e != null) {
                if (e.hash == hash && eq(key, e.key)) 
                    return e.value;
                e = e.next;
            }
        }
        return null;
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
        int hash = hash(key); // throws NullPointerException if key null

        if (count != 0) { // read-volatile
            HashEntry<K,V>[] tab = table;
            int index = indexFor(hash, tab.length);
            HashEntry<K,V> e = tab[index]; 
            while (e != null) {
                if (e.hash == hash && eq(key, e.key)) 
                    return true;
                e = e.next;
            }
        }
        return false;
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
        if (value == null) 
            throw new NullPointerException();

        if (count != 0) {
            HashEntry tab[] = table;
            int len = tab.length;
            for (int i = 0 ; i < len; i++) 
                for (HashEntry e = tab[i] ; e != null ; e = e.next) 
                    if (value.equals(e.value))
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
     * @exception  NullPointerException  if the value is <code>null</code>.
     * @see        #containsKey(Object)
     * @see        #containsValue(Object)
     * @see	   Map
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
     * @exception  NullPointerException  if the key or value is
     *               <code>null</code>.
     * @see     Object#equals(Object)
     * @see     #get(Object)
     */
    public synchronized V put(K key, V value) { 
        if (value == null) 
            throw new NullPointerException();
        int hash = hash(key); 
        HashEntry<K,V>[] tab = table;
        int index = indexFor(hash, tab.length);
        HashEntry<K,V> first = tab[index];
            
        for (HashEntry<K,V> e = first; e != null; e = e.next) {
            if (e.hash == hash && eq(key, e.key)) {
                V oldValue = e.value; 
                e.value = value;
                count = count; // write-volatile
                return oldValue;
            }
        }
        
        tab[index] = new HashEntry(hash, key, value, first);
        modCount++;
        if (++count > threshold) // write-volatile
            rehash();
        return null;
    }

    public synchronized V putIfAbsent(K key, V value) { 
        if (value == null) 
            throw new NullPointerException();
        int hash = hash(key); 
        HashEntry<K,V>[] tab = table;
        int index = indexFor(hash, tab.length);
        HashEntry<K,V> first = tab[index];
            
        for (HashEntry<K,V> e = first; e != null; e = e.next) {
            if (e.hash == hash && eq(key, e.key)) {
                V oldValue = e.value; 
                count = count; // write-volatile
                return oldValue;
            }
        }
        
        tab[index] = new HashEntry(hash, key, value, first);
        modCount++;
        if (++count > threshold) // write-volatile
            rehash();
        return value;
    }

    /**
     * Rehashes the contents of this map into a new table
     * with a larger capacity. This method is called automatically when the
     * number of keys in this map exceeds the load factor threshold.
     */
    private void rehash() { 
        HashEntry<K,V>[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity < MAXIMUM_CAPACITY) {
            HashEntry<K,V>[] newTable = new HashEntry<K,V>[oldCapacity << 1];
            transfer(oldTable, newTable);
            setTable(newTable);
        }
    }

    /**
     * Transfer nodes from old table to new table.
     */
    private void transfer(HashEntry<K,V>[] oldTable, HashEntry<K,V>[] newTable) {
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
        
        int oldCapacity = oldTable.length;
        int mask = newTable.length - 1;
        for (int i = 0; i < oldCapacity ; i++) {
            // We need to guarantee that any existing reads of old Map can
            //  proceed. So we cannot yet null out each bin.  
            HashEntry<K,V> e = oldTable[i];
            
            if (e != null) {
                HashEntry<K,V> next = e.next;
                int idx = e.hash & mask;
                
                //  Single node on list
                if (next == null) 
                    newTable[idx] = e;

                else {    
                    // Reuse trailing consecutive sequence at same slot
                    HashEntry<K,V> lastRun = e;
                    int lastIdx = idx;
                    for (HashEntry<K,V> last = next; last != null; last = last.next) {
                        int k = last.hash & mask;
                        if (k != lastIdx) {
                            lastIdx = k;
                            lastRun = last;
                        }
                    }
                    newTable[lastIdx] = lastRun;

                    // Clone all remaining nodes
                    for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
                        int k = p.hash & mask;
                        newTable[k] = new HashEntry(p.hash, p.key, 
                                                p.value, newTable[k]);
                    }
                }
            }
        }
    }        
        

    /**
     * Copies all of the mappings from the specified map to this one.
     * 
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified Map.
     *
     * @param t Mappings to be stored in this map.
     */

    public <K1 extends K, V1 extends V> void putAll(Map<K1,V1> t) {
        int n = t.size();
        //  Expand enough to hold at least n elements without resizing.
        if (n >= threshold)
            resizeToFit(n);
	Iterator<Map.Entry<K1,V1>> it = t.entrySet().iterator();
	while (it.hasNext()) {
	    Entry<K,V> e = (Entry) it.next();
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * Resize by enough to fit n elements.
     */
    private synchronized void resizeToFit(int n) {
        int newSize = (int)(n / loadFactor + 1);
        if (newSize > MAXIMUM_CAPACITY)
            newSize = MAXIMUM_CAPACITY;
        
        HashEntry[] oldTable = table;
        int oldCapacity = oldTable.length;
        int newCapacity = oldCapacity;
        while (newCapacity < newSize) 
            newCapacity <<= 1;
        
        if (newCapacity > oldCapacity) {
            HashEntry[] newTable = new HashEntry[newCapacity];
            if (count != 0) 
                transfer(oldTable, newTable);
            setTable(newTable);
        }
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
    public synchronized V remove(Object key) {
        int hash = hash(key);
        HashEntry[] tab = table;
        int index = indexFor(hash, tab.length);
        HashEntry<K,V> first = tab[index];

        HashEntry<K,V> e = first;
        while (true) {
            if (e == null)
                return null;
            if (e.hash == hash && eq(key, e.key))
                break;
            e = e.next;
        }

        // All entries following removed node can stay in list, but
        // all preceeding ones need to be cloned.  
        HashEntry<K,V> newFirst = e.next;
        for (HashEntry<K,V> p = first; p != e; p = p.next) 
            newFirst = new HashEntry(p.hash, p.key, p.value, newFirst);
        tab[index] = newFirst;
        
        modCount++;
        count--; // write-volatile
        return e.value;
     }


    /**
     * Helper method for entrySet.remove
     */
    private synchronized boolean findAndRemoveHashEntry(K key, 
                                                    V value) {
        return key != null && value != null &&
            value.equals(get(key)) && (remove(key) != null);
    }

    /**
     * Removes all mappings from this map.
     */
    public synchronized void clear() {
        modCount++;
        HashEntry<K,V> tab[] = table;
        int len = tab.length;
        for (int i = 0; i < len ; i++) 
            tab[i] = null;
        count = 0; // write-volatile
    }


    /**
     * Returns a string representation of this <tt>ConcurrentReaderHashMap</tt> object 
     * in the form of a set of entries, enclosed in braces and separated 
     * by the ASCII characters "<tt>,&nbsp;</tt>" (comma and space). Each 
     * entry is rendered as the key, an equals sign <tt>=</tt>, and the 
     * associated element, where the <tt>toString</tt> method is used to 
     * convert the key and element to strings. <p>Overrides to 
     * <tt>toString</tt> method of <tt>Object</tt>.
     *
     * @return  a string representation of this hashtable.
     */
    public String toString() {
        if (count == 0) // read-volatile
            return "{}";

	StringBuffer buf = new StringBuffer();
 	buf.append("{");

        HashEntry<K,V> tab[] = table;
        int len = tab.length;
        int k = 0;
        for (int i = 0 ; i < len; i++) {
            for (HashEntry<K,V> e = tab[i] ; e != null ; e = e.next) {
                if (k++ != 0)
                    buf.append(", ");
                Object key = e.getKey();
                Object value = e.getValue();

                buf.append((key == this ?  "(this Map)" : key) + "=" + 
                           (value == this ? "(this Map)": value));
            }
        }
	buf.append("}");
	return buf.toString();
    }

    /**
     * Compares the specified Object with this Map for equality,
     * as per the definition in the Map interface.
     *
     * @return true if the specified Object is equal to this Map.
     * @see Map#equals(Object)
     * @since 1.2
     */
    public boolean equals(Object o) {
	if (o == this)
	    return true;
	if (!(o instanceof Map))
	    return false;

	Map t = (Map) o;
	if (t.size() != count) // read-volatile
	    return false;

        HashEntry<K,V> tab[] = table;
        int len = tab.length;
        for (int i = 0 ; i < len; i++) {
            for (HashEntry<K,V> e = tab[i] ; e != null ; e = e.next) {
                Object v = t.get(e.key);
                if (v == null || !v.equals(e.value))
                    return false;
            }
        }
	return true;
    }

    /**
     * Returns the hash code value for this Map as per the definition in the
     * Map interface.
     *
     * @see Map#hashCode()
     * @since 1.2
     */
    public synchronized int hashCode() {
        /*
          This implementation maintains compatibility with
          JDK1.1 to allow computing hashCodes for ConcurrentReaderHashMaps
          with reference cycles. This requires both synchronization
          and temporary abuse of the "loadFactor" field to signify
          that a hashCode is in the midst of being computed so
          to ignore recursive calls. It is embarassing
          to use loadFactor in this way, but this tactic permits
          handling the case without any other field changes.

          Even though hashCodes of cyclic structures can be computed,
          programs should NOT insert a ConcurrentReaderHashMap into itself. Because
          its hashCode changes as a result of entering itself, it is
          normally impossible to retrieve the embedded ConcurrentReaderHashMap using
          get().
        */
        int h = 0;
        float lf = loadFactor;
        if (count != 0 && lf > 0) {
            loadFactor = 0; // zero as recursion flag
            HashEntry<K,V> tab[] = table;
            int len = tab.length;
            for (int i = 0 ; i < len; i++) 
                for (HashEntry<K,V> e = tab[i] ; e != null ; e = e.next) 
                    h += e.key.hashCode() ^ e.value.hashCode();
            loadFactor = lf;
        }
	return h;
    }


    /**
     * Returns a shallow copy of this 
     * <tt>ConcurrentReaderHashMap</tt> instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map.
     */
    public synchronized Object clone() {
        ConcurrentReaderHashMap result = null;
        try { 
            result = (ConcurrentReaderHashMap)super.clone();
        }
        catch (CloneNotSupportedException e) { 
            // assert false;
        }
        result.count = 0;
        result.keySet = null;
        result.entrySet = null;
        result.values = null;
        result.modCount = 0;
        result.table = new HashEntry[table.length];
        result.putAll(this);
        return result;
    }

    /**
     * ConcurrentReaderHashMap collision list entry.
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

    /**
     * Support for Enumeration interface.  These Enumerations take a
     * snapshot of table, so can never encounter corrupted
     * representations in multithreaded programs. At worst, they will
     * report the presence of entries deleted since the enumeration
     * was constructed, or absence of those inserted.
     */

    private abstract class HashEnumerator {


        HashEntry<K,V> next;                  // next entry to return
        final HashEntry[] tab;           // snapshot of table
        int index;                   // current slot 

        HashEnumerator(int size, HashEntry<K,V>[] t) {
            tab = t;
            int i = t.length;
            HashEntry<K,V> n = null;
            if (size != 0) { // advance to first entry
                while (i > 0 && (n = tab[--i]) == null)
                    ;
            }
            next = n;
            index = i;
        }

        public boolean hasMoreElements() {  
            return next != null;
        }

        HashEntry<K,V> nextHashEntry() { 
            HashEntry<K,V> e = next;
            if (e == null) 
                throw new NoSuchElementException("ConcurrentReaderHashMap Enumerator");
                
            HashEntry<K,V> n = e.next;
            int i = index;
            while (n == null && i > 0)
                n = tab[--i];
            index = i;
            next = n;
            return e;
        }
    }

    private class KeyEnumerator extends HashEnumerator implements  Enumeration<K> {
        KeyEnumerator(int size, HashEntry<K,V>[] t) { super(size, t); }
        public K nextElement() {
            return nextHashEntry().key;
        }
    }

    private class ValueEnumerator extends HashEnumerator implements  Enumeration<V> {
        ValueEnumerator(int size, HashEntry<K,V>[] t) { super(size, t); }
        public V nextElement() {
            return nextHashEntry().value;
        }
    }

    /**
     * Support for Iterator interface. 
     */
    private abstract class HashIterator {
        HashEntry<K,V> next;                  // next entry to return
        int expectedModCount;        // For fast-fail 
        int index;                   // current slot 
        HashEntry<K,V> current;               // current entry

        HashIterator() {
            int size = count; // read-volatile
            HashEntry[] t = table;
            expectedModCount = modCount;
            int i = t.length;
            HashEntry<K,V> n = null;
            if (size != 0) { // advance to first entry
                while (i > 0 && (n = t[--i]) == null)
                    ;
            }
            next = n;
            index = i;
        }

        public boolean hasNext() {
            return next != null;
        }

        HashEntry<K,V> nextHashEntry() { 
            int ignore = count; // read-volatile
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            HashEntry<K,V> e = next;
            if (e == null) 
                throw new NoSuchElementException("ConcurrentReaderHashMap Enumerator");
                
            HashEntry<K,V> n = e.next;
            HashEntry[] t = table;
            int i = index;
            while (n == null && i > 0)
                n = t[--i];
            index = i;
            next = n;
            current = e;
            return e;
        }

        public void remove() {
            if (current == null)
		throw new IllegalStateException("ConcurrentReaderHashMap Enumerator");
            K k = current.key;
            current = null;
            if (ConcurrentReaderHashMap.this.remove(k) == null)
                throw new ConcurrentModificationException();
            expectedModCount = modCount;
        }
    }

    private class KeyIterator extends HashIterator implements Iterator<K> {
        KeyIterator() {}
        public K next() {
            return nextHashEntry().key;
        }
    }

    private class ValueIterator extends HashIterator implements Iterator<V> {
        ValueIterator() {}
        public V next() {
            return nextHashEntry().value;
        }
    }

    private class HashEntryIterator extends HashIterator implements Iterator<Entry<K,V>> {
        HashEntryIterator() {}
        public Entry<K,V> next() {
            return nextHashEntry();
        }
    }


    // Views

    private transient Set<K> keySet = null;
    private transient Set/*<Entry<K,V>>*/ entrySet = null;
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
            return ConcurrentReaderHashMap.this.size();
        }
        public boolean contains(Object o) {
            return ConcurrentReaderHashMap.this.containsKey(o);
        }
        public boolean remove(Object o) {
            return ConcurrentReaderHashMap.this.remove(o) != null;
        }
        public void clear() {
            ConcurrentReaderHashMap.this.clear();
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
            return ConcurrentReaderHashMap.this.size();
        }
        public boolean contains(Object o) {
            return ConcurrentReaderHashMap.this.containsValue(o);
        }
        public void clear() {
            ConcurrentReaderHashMap.this.clear();
        }
    }

    /**
     * Returns a collection view of the mappings contained in this map.  Each
     * element in the returned collection is a <tt>Entry</tt>.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a collection view of the mappings contained in this map.
     * @see Entry
     */

    public Set<Entry<K,V>> entrySet() {
        Set<Entry<K,V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    private class EntrySet extends AbstractSet {
        public Iterator<Entry<K,V>> iterator() {
            return new HashEntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry<K,V> entry = (Entry)o;
            Object v = ConcurrentReaderHashMap.this.get(entry.getKey());
            return v != null && v.equals(entry.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry<K,V> entry = (Entry)o;
            return ConcurrentReaderHashMap.this.findAndRemoveHashEntry(entry.getKey(), 
                                                     entry.getValue());
        }
        public int size() {
            return ConcurrentReaderHashMap.this.size();
        }
        public void clear() {
            ConcurrentReaderHashMap.this.clear();
        }
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return  an enumeration of the keys in this table.
     * @see     Enumeration
     * @see     #elements()
     * @see	#keySet()
     * @see	Map
     */
    public Enumeration<K> keys() {
        int n = count; // read-volatile
        return new KeyEnumerator(n, table); 
    }

    /**
     * Returns an enumeration of the values in this table.
     * Use the Enumeration methods on the returned object to fetch the elements
     * sequentially.
     *
     * @return  an enumeration of the values in this table.
     * @see     java.util.Enumeration
     * @see     #keys()
     * @see	#values()
     * @see	Map
     */
  
    public Enumeration<V> elements() {
        int n = count; // read-volatile
        return new ValueEnumerator(n, table); 
    }

    /**
     * Save the state of the <tt>ConcurrentReaderHashMap</tt> 
     * instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <i>capacity</i> of the 
     * ConcurrentReaderHashMap (the length of the
     * bucket array) is emitted (int), followed  by the
     * <i>size</i> of the ConcurrentReaderHashMap (the number of key-value
     * mappings), followed by the key (Object) and value (Object)
     * for each key-value mapping represented by the ConcurrentReaderHashMap
     * The key-value mappings are emitted in no particular order.
     */

    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws IOException  {
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();
    
        // Write out number of buckets
        s.writeInt(table.length);
    
        // Write out size (number of Mappings)
        s.writeInt(count);
    
        // Write out keys and values (alternating)
        for (int index = table.length-1; index >= 0; index--) {
            HashEntry<K,V> entry = table[index];
      
            while (entry != null) {
                s.writeObject(entry.key);
                s.writeObject(entry.value);
                entry = entry.next;
            }
        }
    }

    /**
     * Reconstitute the <tt>ConcurrentReaderHashMap</tt> 
     * instance from a stream (i.e.,
     * deserialize it).
     */
    private synchronized void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException  {
        // Read in the threshold, loadfactor, and any hidden stuff
        s.defaultReadObject();

        // Read in number of buckets and allocate the bucket array;
        int numBuckets = s.readInt();
        table = new HashEntry[numBuckets];
    
        // Read in size (number of Mappings)
        int size = s.readInt();
    
        // Read the keys and values, and put the mappings in the table
        for (int i=0; i<size; i++) {
            K key = (K)(s.readObject());
            V value = (V)(s.readObject());
            put(key, value);
        }
    }
  
}
