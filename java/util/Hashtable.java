/*
 * @(#)Hashtable.java	1.83 00/12/13
 *
 * Copyright 1994-2002 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the proprietary information of Sun Microsystems, Inc.  
 * Use is subject to license terms.
 * 
 */

package java.util;
import java.io.*;

/**
 * This class implements a hashtable, which maps keys to values. Any 
 * non-<code>null</code> object can be used as a key or as a value. <p>
 *
 * To successfully store and retrieve objects from a hashtable, the
 * objects used as keys must implement the <code>hashCode</code>
 * method and the <code>equals</code> method. <p>
 *
 * An instance of <code>Hashtable</code> has two parameters that
 * affect its performance: <i>initial capacity</i> and <i>load
 * factor</i>.  The <i>capacity</i> is the number of <i>buckets</i> in
 * the hash table, and the <i>initial capacity</i> is simply the
 * capacity at the time the hash table is created.  Note that the hash
 * table is <i>open</i>: in the case a "hash collision", a single
 * bucket stores multiple entries, which must be searched
 * sequentially.  The <i>load factor</i> is a measure of how full the
 * hash table is allowed to get before its capacity is automatically
 * increased.  When the number of entries in the hashtable exceeds the
 * product of the load factor and the current capacity, the capacity
 * is increased by calling the <code>rehash</code> method.<p>
 *
 * Generally, the default load factor (.75) offers a good tradeoff
 * between time and space costs.  Higher values decrease the space
 * overhead but increase the time cost to look up an entry (which is
 * reflected in most <tt>Hashtable</tt> operations, including
 * <tt>get</tt> and <tt>put</tt>).<p>
 *
 * The initial capacity controls a tradeoff between wasted space and
 * the need for <code>rehash</code> operations, which are
 * time-consuming.  No <code>rehash</code> operations will <i>ever</i>
 * occur if the initial capacity is greater than the maximum number of
 * entries the <tt>Hashtable</tt> will contain divided by its load
 * factor.  However, setting the initial capacity too high can waste
 * space.<p>
 *
 * If many entries are to be made into a <code>Hashtable</code>,
 * creating it with a sufficiently large capacity may allow the
 * entries to be inserted more efficiently than letting it perform
 * automatic rehashing as needed to grow the table. <p>
 *
 * This example creates a hashtable of numbers. It uses the names of 
 * the numbers as keys:
 * <p><blockquote><pre>
 *     Hashtable numbers = new Hashtable();
 *     numbers.put("one", new Integer(1));
 *     numbers.put("two", new Integer(2));
 *     numbers.put("three", new Integer(3));
 * </pre></blockquote>
 * <p>
 * To retrieve a number, use the following code: 
 * <p><blockquote><pre>
 *     Integer n = (Integer)numbers.get("two");
 *     if (n != null) {
 *         System.out.println("two = " + n);
 *     }
 * </pre></blockquote>
 * <p>
 *
 * As of the Java 2 platform v1.2, this class has been retrofitted to
 * implement Map, so that it becomes a part of Java's collection
 * framework.  Unlike the new collection implementations, Hashtable is
 * fully thread-safe.<p>
 *
 * The Iterators returned by the iterator and listIterator methods of
 * the Collections returned by all of Hashtable's "collection view
 * methods" are <em>fail-fast</em>: if the Hashtable is structurally
 * modified at any time after the Iterator is created, in any way
 * except through the Iterator's own remove or add methods, the
 * Iterator will throw a ConcurrentModificationException.  Thus, in
 * the face of concurrent modification, the Iterator fails quickly and
 * cleanly, rather than risking arbitrary, non-deterministic behavior
 * at an undetermined time in the future.  The Enumerations returned
 * by Hashtable's keys and values methods are <em>not</em> fail-fast.
 * <p>
 *
 * @author  Arthur van Hoff
 * @author  Josh Bloch
 * @author  Doug Lea
 * @version 1.83, 12/13/00
 * @see     Object#equals(java.lang.Object)
 * @see     Object#hashCode()
 * @see     Hashtable#rehash()
 * @see     Collection
 * @see	    Map
 * @see	    HashMap
 * @see	    TreeMap
 * @since JDK1.0
 */

public class Hashtable  extends Dictionary 
    implements Map, Cloneable, Serializable {

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
     *     operations in Hashtable. For example, no operation
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
    private transient Entry[] table;

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
     * Set table to new Entry array. 
     * Call only while holding lock or in constructor.
     **/
    private void setTable(Entry[] newTable) {
        table = newTable;
        threshold = (int)(newTable.length * loadFactor);
        count = count; // write-volatile
    }    

    /**
     * Constructs a new, empty map with a default initial capacity
     * and load factor.
     */
    public Hashtable() {
        loadFactor = DEFAULT_LOAD_FACTOR;
        setTable(new Entry[DEFAULT_INITIAL_CAPACITY]);
    }

    /**
     * Constructs a new, empty map with the specified initial 
     * capacity and the specified load factor. 
     *
     * @param initialCapacity the initial capacity
     *  The actual initial capacity is rounded to the nearest power of two.
     * @param loadFactor  the load factor of the Hashtable
     * @throws IllegalArgumentException  if the initial capacity is less
     *               than zero, or if the load factor is nonpositive.
     */
    public Hashtable(int initialCapacity, float loadFactor) {
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
    
        setTable(new Entry[capacity]);
    }

    /**
     * Constructs a new, empty map with the specified initial 
     * capacity and default load factor.
     *
     * @param   initialCapacity   the initial capacity of the 
     *                            Hashtable.
     *  The actual initial capacity is rounded to the nearest power of two.
     * @throws    IllegalArgumentException if the initial maximum number 
     *              of elements is less
     *              than zero.
     */

    public Hashtable(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new map with the same mappings as the given map.  The
     * map is created with a default load factor.
     */

    public Hashtable(Map t) {
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
    public Object get(Object key) {
        int hash = hash(key); // throws NullPointerException if key null

        if (count != 0) { // read-volatile
            Entry[] tab = table;
            int index = indexFor(hash, tab.length);
            Entry e = tab[index]; 
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
            Entry[] tab = table;
            int index = indexFor(hash, tab.length);
            Entry e = tab[index]; 
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
            Entry tab[] = table;
            int len = tab.length;
            for (int i = 0 ; i < len; i++) 
                for (Entry e = tab[i] ; e != null ; e = e.next) 
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
    public synchronized Object put(Object key, Object value) { 
        if (value == null) 
            throw new NullPointerException();
        int hash = hash(key); 
        Entry[] tab = table;
        int index = indexFor(hash, tab.length);
        Entry first = tab[index];
            
        for (Entry e = first; e != null; e = e.next) {
            if (e.hash == hash && eq(key, e.key)) {
                Object oldValue = e.value; 
                e.value = value;
                count = count; // write-volatile
                return oldValue;
            }
        }
        
        tab[index] = new Entry(hash, key, value, first);
        modCount++;
        if (++count > threshold) // write-volatile
            rehash();
        return null;
    }

    public synchronized Object putIfAbsent(Object key, Object value) { 
        if (value == null) 
            throw new NullPointerException();
        int hash = hash(key); 
        Entry[] tab = table;
        int index = indexFor(hash, tab.length);
        Entry first = tab[index];
            
        for (Entry e = first; e != null; e = e.next) {
            if (e.hash == hash && eq(key, e.key)) {
                Object oldValue = e.value; 
                count = count; // write-volatile
                return oldValue;
            }
        }
        
        tab[index] = new Entry(hash, key, value, first);
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
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity < MAXIMUM_CAPACITY) {
            Entry[] newTable = new Entry[oldCapacity << 1];
            transfer(oldTable, newTable);
            setTable(newTable);
        }
    }

    /**
     * Transfer nodes from old table to new table.
     */
    private static void transfer(Entry[] oldTable, Entry[] newTable) {
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
            Entry e = oldTable[i];
            
            if (e != null) {
                Entry next = e.next;
                int idx = e.hash & mask;
                
                //  Single node on list
                if (next == null) 
                    newTable[idx] = e;

                else {    
                    // Reuse trailing consecutive sequence at same slot
                    Entry lastRun = e;
                    int lastIdx = idx;
                    for (Entry last = next; last != null; last = last.next) {
                        int k = last.hash & mask;
                        if (k != lastIdx) {
                            lastIdx = k;
                            lastRun = last;
                        }
                    }
                    newTable[lastIdx] = lastRun;

                    // Clone all remaining nodes
                    for (Entry p = e; p != lastRun; p = p.next) {
                        int k = p.hash & mask;
                        newTable[k] = new Entry(p.hash, p.key, 
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

    public void putAll(Map t) {
        int n = t.size();
        //  Expand enough to hold at least n elements without resizing.
        if (n >= threshold)
            resizeToFit(n);

	for (Iterator it = t.entrySet().iterator(); it.hasNext(); ) {
	    Map.Entry e = (Map.Entry) it.next();
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
        
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        int newCapacity = oldCapacity;
        while (newCapacity < newSize) 
            newCapacity <<= 1;
        
        if (newCapacity > oldCapacity) {
            Entry[] newTable = new Entry[newCapacity];
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
    public synchronized Object remove(Object key) {
        int hash = hash(key);
        Entry[] tab = table;
        int index = indexFor(hash, tab.length);
        Entry first = tab[index];

        Entry e = first;
        while (true) {
            if (e == null)
                return e;
            if (e.hash == hash && eq(key, e.key))
                break;
            e = e.next;
        }

        // All entries following removed node can stay in list, but
        // all preceeding ones need to be cloned.  
        Entry newFirst = e.next;
        for (Entry p = first; p != e; p = p.next) 
            newFirst = new Entry(p.hash, p.key, p.value, newFirst);
        tab[index] = newFirst;
        
        modCount++;
        count--; // write-volatile
        return e.value;
     }


    /**
     * Helper method for entrySet.remove
     */
    private synchronized boolean findAndRemoveEntry(Object key, 
                                                    Object value) {
        return key != null && value != null &&
            value.equals(get(key)) && (remove(key) != null);
    }

    /**
     * Removes all mappings from this map.
     */
    public synchronized void clear() {
        modCount++;
        Entry tab[] = table;
        int len = tab.length;
        for (int i = 0; i < len ; i++) 
            tab[i] = null;
        count = 0; // write-volatile
    }


    /**
     * Returns a string representation of this <tt>Hashtable</tt> object 
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

        Entry tab[] = table;
        int len = tab.length;
        int k = 0;
        for (int i = 0 ; i < len; i++) {
            for (Entry e = tab[i] ; e != null ; e = e.next) {
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

        Entry tab[] = table;
        int len = tab.length;
        for (int i = 0 ; i < len; i++) {
            for (Entry e = tab[i] ; e != null ; e = e.next) {
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
          JDK1.1 to allow computing hashCodes for Hashtables
          with reference cycles. This requires both synchronization
          and temporary abuse of the "loadFactor" field to signify
          that a hashCode is in the midst of being computed so
          to ignore recursive calls. It is embarassing
          to use loadFactor in this way, but this tactic permits
          handling the case without any other field changes.

          Even though hashCodes of cyclic structures can be computed,
          programs should NOT insert a Hashtable into itself. Because
          its hashCode changes as a result of entering itself, it is
          normally impossible to retrieve the embedded Hashtable using
          get().
        */
        int h = 0;
        float lf = loadFactor;
        if (count != 0 && lf > 0) {
            loadFactor = 0; // zero as recursion flag
            Entry tab[] = table;
            int len = tab.length;
            for (int i = 0 ; i < len; i++) 
                for (Entry e = tab[i] ; e != null ; e = e.next) 
                    h += e.key.hashCode() ^ e.value.hashCode();
            loadFactor = lf;
        }
	return h;
    }


    /**
     * Returns a shallow copy of this 
     * <tt>Hashtable</tt> instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map.
     */
    public synchronized Object clone() {
        Hashtable result = null;
        try { 
            result = (Hashtable)super.clone();
        }
        catch (CloneNotSupportedException e) { 
            // assert false;
        }
        result.count = 0;
        result.keySet = null;
        result.entrySet = null;
        result.values = null;
        result.modCount = 0;
        result.table = new Entry[table.length];
        result.putAll(this);
        return result;
    }

    /**
     * Hashtable collision list entry.
     */
    private static class Entry implements Map.Entry {
        private final Object key;
        private Object value;
        private final int hash;
        private final Entry next;

        Entry(int hash, Object key, Object value, Entry next) {
            this.value = value;
            this.hash = hash;
            this.key = key;
            this.next = next;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value; 
        }

        public Object setValue(Object newValue) {
            // We aren't required to, and don't provide any
            // visibility barriers for setting value.
            if (newValue == null)
                throw new NullPointerException();
            Object oldValue = this.value;
            this.value = newValue;
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

    /**
     * Support for Enumeration interface.  These Enumerations take a
     * snapshot of table, so can never encounter corrupted
     * representations in multithreaded programs. At worst, they will
     * report the presence of entries deleted since the enumeration
     * was constructed, or absence of those inserted.
     */
    private static class Enumerator implements  Enumeration {
        Entry next;                  // next entry to return
        final Entry[] tab;           // snapshot of table
        int index;                   // current slot 
        final boolean returnKeys;    

        Enumerator(boolean returnKeys, int size, Entry[] t) {
            this.returnKeys = returnKeys;
            tab = t;
            int i = t.length;
            Entry n = null;
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

        public Object nextElement() { 
            Entry e = next;
            if (e == null) 
                throw new NoSuchElementException("Hashtable Enumerator");
                
            Entry n = e.next;
            int i = index;
            while (n == null && i > 0)
                n = tab[--i];
            index = i;
            next = n;
            return returnKeys? e.key : e.value;
        }
    }

    private static final int KEYS = 0;
    private static final int VALUES = 1;
    private static final int ENTRIES = 2;

    /**
     * Support for Iterator interface. 
     */
    private class HashIterator implements Iterator {
        Entry next;                  // next entry to return
        int expectedModCount;        // For fast-fail 
        int index;                   // current slot 
        Entry current;               // current entry
        final int returnType;        // KEYS or VALUES or ENTRIES

        HashIterator(int returnType) {
            this.returnType = returnType;
            int size = count; // read-volatile
            Entry[] t = table;
            expectedModCount = modCount;
            int i = t.length;
            Entry n = null;
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

        public Object next() { 
            int ignore = count; // read-volatile
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            Entry e = next;
            if (e == null) 
                throw new NoSuchElementException("Hashtable Enumerator");
                
            Entry n = e.next;
            Entry[] t = table;
            int i = index;
            while (n == null && i > 0)
                n = t[--i];
            index = i;
            next = n;
            current = e;
            return (returnType == KEYS)? e.key :
                ((returnType == VALUES)? e.value : e);
        }

        public void remove() {
            if (current == null)
		throw new IllegalStateException("Hashtable Enumerator");
            Object k = current.key;
            current = null;
            if (Hashtable.this.remove(k) == null)
                throw new ConcurrentModificationException();
            expectedModCount = modCount;
        }
    }


    // Views

    private transient Set keySet = null;
    private transient Set entrySet = null;
    private transient Collection values = null;

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

    public Set keySet() {
        Set ks = keySet;
        return (ks != null)? ks : (keySet = new KeySet());
    }

    private class KeySet extends AbstractSet {
        public Iterator iterator() {
            return new HashIterator(KEYS);
        }
        public int size() {
            return Hashtable.this.size();
        }
        public boolean contains(Object o) {
            return Hashtable.this.containsKey(o);
        }
        public boolean remove(Object o) {
            return Hashtable.this.remove(o) != null;
        }
        public void clear() {
            Hashtable.this.clear();
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

    public Collection values() {
        Collection vs = values;
        return (vs != null)? vs : (values = new Values());
    }

    private class Values extends AbstractCollection {
        public Iterator iterator() {
            return new HashIterator(VALUES);
        }
        public int size() {
            return Hashtable.this.size();
        }
        public boolean contains(Object o) {
            return Hashtable.this.containsValue(o);
        }
        public void clear() {
            Hashtable.this.clear();
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
     * @see Map.Entry
     */

    public Set entrySet() {
        Set es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    private class EntrySet extends AbstractSet {
        public Iterator iterator() {
            return new HashIterator(ENTRIES);
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry entry = (Map.Entry)o;
            Object v = Hashtable.this.get(entry.getKey());
            return v != null && v.equals(entry.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry entry = (Map.Entry)o;
            return Hashtable.this.findAndRemoveEntry(entry.getKey(), 
                                                     entry.getValue());
        }
        public int size() {
            return Hashtable.this.size();
        }
        public void clear() {
            Hashtable.this.clear();
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
    public Enumeration keys() {
        int n = count; // read-volatile
        return new Enumerator(true, n, table); 
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
  
    public Enumeration elements() {
        int n = count; // read-volatile
        return new Enumerator(false, n, table); 
    }

    /**
     * Save the state of the <tt>Hashtable</tt> 
     * instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <i>capacity</i> of the 
     * Hashtable (the length of the
     * bucket array) is emitted (int), followed  by the
     * <i>size</i> of the Hashtable (the number of key-value
     * mappings), followed by the key (Object) and value (Object)
     * for each key-value mapping represented by the Hashtable
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
            Entry entry = table[index];
      
            while (entry != null) {
                s.writeObject(entry.key);
                s.writeObject(entry.value);
                entry = entry.next;
            }
        }
    }

    /**
     * Reconstitute the <tt>Hashtable</tt> 
     * instance from a stream (i.e.,
     * deserialize it).
     */
    private synchronized void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException  {
        // Read in the threshold, loadfactor, and any hidden stuff
        s.defaultReadObject();

        // Read in number of buckets and allocate the bucket array;
        int numBuckets = s.readInt();
        table = new Entry[numBuckets];
    
        // Read in size (number of Mappings)
        int size = s.readInt();
    
        // Read the keys and values, and put the mappings in the table
        for (int i=0; i<size; i++) {
            Object key = s.readObject();
            Object value = s.readObject();
            put(key, value);
        }
    }
  
}
