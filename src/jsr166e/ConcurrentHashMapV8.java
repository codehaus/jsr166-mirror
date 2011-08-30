/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e;
import jsr166e.LongAdder;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.AbstractCollection;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentMap;
import java.io.Serializable;

/**
 * A hash table supporting full concurrency of retrievals and
 * high expected concurrency for updates. This class obeys the
 * same functional specification as {@link java.util.Hashtable}, and
 * includes versions of methods corresponding to each method of
 * {@code Hashtable}. However, even though all operations are
 * thread-safe, retrieval operations do <em>not</em> entail locking,
 * and there is <em>not</em> any support for locking the entire table
 * in a way that prevents all access.  This class is fully
 * interoperable with {@code Hashtable} in programs that rely on its
 * thread safety but not on its synchronization details.
 *
 * <p> Retrieval operations (including {@code get}) generally do not
 * block, so may overlap with update operations (including {@code put}
 * and {@code remove}). Retrievals reflect the results of the most
 * recently <em>completed</em> update operations holding upon their
 * onset.  For aggregate operations such as {@code putAll} and {@code
 * clear}, concurrent retrievals may reflect insertion or removal of
 * only some entries.  Similarly, Iterators and Enumerations return
 * elements reflecting the state of the hash table at some point at or
 * since the creation of the iterator/enumeration.  They do
 * <em>not</em> throw {@link ConcurrentModificationException}.
 * However, iterators are designed to be used by only one thread at a
 * time.  Bear in mind that the results of aggregate status methods
 * including {@code size}, {@code isEmpty}, and {@code containsValue}
 * are typically useful only when a map is not undergoing concurrent
 * updates in other threads.  Otherwise the results of these methods
 * reflect transient states that may be adequate for monitoring
 * purposes, but not for program control.
 *
 * <p> Resizing this or any other kind of hash table is a relatively
 * slow operation, so, when possible, it is a good idea to provide
 * estimates of expected table sizes in constructors. Also, for
 * compatibility with previous versions of this class, constructors
 * may optionally specify an expected {@code concurrencyLevel} as an
 * additional hint for internal sizing.
 *
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}
 * interfaces.
 *
 * <p> Like {@link Hashtable} but unlike {@link HashMap}, this class
 * does <em>not</em> allow {@code null} to be used as a key or value.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * <p><em>jsr166e note: This class is a candidate replacement for
 * java.util.concurrent.ConcurrentHashMap.<em>
 *
 * @since 1.5
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ConcurrentHashMapV8<K, V>
        implements ConcurrentMap<K, V>, Serializable {
    private static final long serialVersionUID = 7249069246763182397L;

    /**
     * A function computing a mapping from the given key to a value,
     * or {@code null} if there is no mapping. This is a place-holder
     * for an upcoming JDK8 interface.
     */
    public static interface MappingFunction<K, V> {
        /**
         * Returns a value for the given key, or null if there is no
         * mapping. If this function throws an (unchecked) exception,
         * the exception is rethrown to its caller, and no mapping is
         * recorded.  Because this function is invoked within
         * atomicity control, the computation should be short and
         * simple. The most common usage is to construct a new object
         * serving as an initial mapped value.
         *
         * @param key the (non-null) key
         * @return a value, or null if none
         */
        V map(K key);
    }

    /*
     * Overview:
     *
     * The primary design goal of this hash table is to maintain
     * concurrent readability (typically method get(), but also
     * iterators and related methods) while minimizing update
     * contention.
     *
     * Each key-value mapping is held in a Node.  Because Node fields
     * can contain special values, they are defined using plain Object
     * types. Similarly in turn, all internal methods that use them
     * work off Object types. All public generic-typed methods relay
     * in/out of these internal methods, supplying casts as needed.
     *
     * The table is lazily initialized to a power-of-two size upon the
     * first insertion.  Each bin in the table contains a (typically
     * short) list of Nodes.  Table accesses require volatile/atomic
     * reads, writes, and CASes.  Because there is no other way to
     * arrange this without adding further indirections, we use
     * intrinsics (sun.misc.Unsafe) operations.  The lists of nodes
     * within bins are always accurately traversable under volatile
     * reads, so long as lookups check hash code and non-nullness of
     * key and value before checking key equality. (All valid hash
     * codes are nonnegative. Negative values are reserved for special
     * forwarding nodes; see below.)
     *
     * A bin may be locked during update (insert, delete, and replace)
     * operations.  We do not want to waste the space required to
     * associate a distinct lock object with each bin, so instead use
     * the first node of a bin list itself as a lock, using builtin
     * "synchronized" locks. These save space and we can live with
     * only plain block-structured lock/unlock operations. Using the
     * first node of a list as a lock does not by itself suffice
     * though: When a node is locked, any update must first validate
     * that it is still the first node, and retry if not. (Because new
     * nodes are always appended to lists, once a node is first in a
     * bin, it remains first until deleted or the bin becomes
     * invalidated.)  However, update operations can and sometimes do
     * still traverse the bin until the point of update, which helps
     * reduce cache misses on retries.  This is a converse of sorts to
     * the lazy locking technique described by Herlihy & Shavit. If
     * there is no existing node during a put operation, then one can
     * be CAS'ed in (without need for lock except in computeIfAbsent);
     * the CAS serves as validation. This is on average the most
     * common case for put operations -- under random hash codes, the
     * distribution of nodes in bins follows a Poisson distribution
     * (see http://en.wikipedia.org/wiki/Poisson_distribution) with a
     * parameter of 0.5 on average under the default loadFactor of
     * 0.75.  The expected number of locks covering different elements
     * (i.e., bins with 2 or more nodes) is approximately 10% at
     * steady state under default settings.  Lock contention
     * probability for two threads accessing arbitrary distinct
     * elements is, roughly, 1 / (8 * #elements).
     *
     * The table is resized when occupancy exceeds a threshold.  Only
     * a single thread performs the resize (using field "resizing", to
     * arrange exclusion), but the table otherwise remains usable for
     * both reads and updates. Resizing proceeds by transferring bins,
     * one by one, from the table to the next table.  Upon transfer,
     * the old table bin contains only a special forwarding node (with
     * negative hash code ("MOVED")) that contains the next table as
     * its key. On encountering a forwarding node, access and update
     * operations restart, using the new table. To ensure concurrent
     * readability of traversals, transfers must proceed from the last
     * bin (table.length - 1) up towards the first.  Any traversal
     * starting from the first bin can then arrange to move to the new
     * table for the rest of the traversal without revisiting nodes.
     * This constrains bin transfers to a particular order, and so can
     * block indefinitely waiting for the next lock, and other threads
     * cannot help with the transfer. However, expected stalls are
     * infrequent enough to not warrant the additional overhead and
     * complexity of access and iteration schemes that could admit
     * out-of-order or concurrent bin transfers.
     *
     * A similar traversal scheme (not yet implemented) can apply to
     * partial traversals during partitioned aggregate operations.
     * Also, read-only operations give up if ever forwarded to a null
     * table, which provides support for shutdown-style clearing,
     * which is also not currently implemented.
     *
     * The element count is maintained using a LongAdder, which avoids
     * contention on updates but can encounter cache thrashing if read
     * too frequently during concurrent updates. To avoid reading so
     * often, resizing is normally attempted only upon adding to a bin
     * already holding two or more nodes. Under the default threshold
     * (0.75), and uniform hash distributions, the probability of this
     * occurring at threshold is around 13%, meaning that only about 1
     * in 8 puts check threshold (and after resizing, many fewer do
     * so). But this approximation has high variance for small table
     * sizes, so we check on any collision for sizes <= 64.  Further,
     * to increase the probability that a resize occurs soon enough, we
     * offset the threshold (see THRESHOLD_OFFSET) by the expected
     * number of puts between checks. This is currently set to 8, in
     * accord with the default load factor. In practice, this is
     * rarely overridden, and in any case is close enough to other
     * plausible values not to waste dynamic probability computation
     * for more precision.
     */

    /* ---------------- Constants -------------- */

    /**
     * The smallest allowed table capacity.  Must be a power of 2, at
     * least 2.
     */
    static final int MINIMUM_CAPACITY = 2;

    /**
     * The largest allowed table capacity.  Must be a power of 2, at
     * most 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The default initial table capacity.  Must be a power of 2, at
     * least MINIMUM_CAPACITY and at most MAXIMUM_CAPACITY.
     */
    static final int DEFAULT_CAPACITY = 16;

    /**
     * The default load factor for this table, used when not otherwise
     * specified in a constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The default concurrency level for this table. Unused, but
     * defined for compatibility with previous versions of this class.
     */
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    /**
     * The count value to offset thresholds to compensate for checking
     * for resizing only when inserting into bins with two or more
     * elements. See above for explanation.
     */
    static final int THRESHOLD_OFFSET = 8;

    /**
     * Special node hash value indicating to use table in node.key
     * Must be negative.
     */
    static final int MOVED = -1;

    /* ---------------- Fields -------------- */

    /**
     * The array of bins. Lazily initialized upon first insertion.
     * Size is always a power of two. Accessed directly by inner
     * classes.
     */
    transient volatile Node[] table;

    /** The counter maintaining number of elements. */
    private transient final LongAdder counter;
    /** Nonzero when table is being initialized or resized. Updated via CAS. */
    private transient volatile int resizing;
    /** The target load factor for the table. */
    private transient float loadFactor;
    /** The next element count value upon which to resize the table. */
    private transient int threshold;
    /** The initial capacity of the table. */
    private transient int initCap;

    // views
    transient Set<K> keySet;
    transient Set<Map.Entry<K,V>> entrySet;
    transient Collection<V> values;

    /** For serialization compatibility. Null unless serialized; see below */
    Segment<K,V>[] segments;

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  The result must
     * be non-negative, and for reasonable performance must have good
     * avalanche properties; i.e., that each bit of the argument
     * affects each bit (except sign bit) of the result.
     */
    private static final int spread(int h) {
        // Apply base step of MurmurHash; see http://code.google.com/p/smhasher/
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        return (h >>> 16) ^ (h & 0x7fffffff); // mask out sign bit
    }

    /**
     * Key-value entry. Note that this is never exported out as a
     * user-visible Map.Entry.
     */
    static final class Node {
        final int hash;
        final Object key;
        volatile Object val;
        volatile Node next;

        Node(int hash, Object key, Object val, Node next) {
            this.hash = hash;
            this.key = key;
            this.val = val;
            this.next = next;
        }
    }

    /*
     * Volatile access methods are used for table elements as well as
     * elements of in-progress next table while resizing.  Uses in
     * access and update methods are null checked by callers, and
     * implicitly bounds-checked, relying on the invariants that tab
     * arrays have non-zero size, and all indices are masked with
     * (tab.length - 1) which is never negative and always less than
     * length. The "relaxed" non-volatile forms are used only during
     * table initialization. The only other usage is in
     * HashIterator.advance, which performs explicit checks.
     */

    static final Node tabAt(Node[] tab, int i) { // used in HashIterator
        return (Node)UNSAFE.getObjectVolatile(tab, ((long)i<<ASHIFT)+ABASE);
    }

    private static final boolean casTabAt(Node[] tab, int i, Node c, Node v) {
        return UNSAFE.compareAndSwapObject(tab, ((long)i<<ASHIFT)+ABASE, c, v);
    }

    private static final void setTabAt(Node[] tab, int i, Node v) {
        UNSAFE.putObjectVolatile(tab, ((long)i<<ASHIFT)+ABASE, v);
    }

    private static final Node relaxedTabAt(Node[] tab, int i) {
        return (Node)UNSAFE.getObject(tab, ((long)i<<ASHIFT)+ABASE);
    }

    private static final void relaxedSetTabAt(Node[] tab, int i, Node v) {
        UNSAFE.putObject(tab, ((long)i<<ASHIFT)+ABASE, v);
    }

    /* ---------------- Access and update operations -------------- */

   /** Implementation for get and containsKey */
    private final Object internalGet(Object k) {
        int h = spread(k.hashCode());
        Node[] tab = table;
        retry: while (tab != null) {
            Node e = tabAt(tab, (tab.length - 1) & h);
            while (e != null) {
                int eh = e.hash;
                if (eh == h) {
                    Object ek = e.key, ev = e.val;
                    if (ev != null && ek != null && (k == ek || k.equals(ek)))
                        return ev;
                }
                else if (eh < 0) { // bin was moved during resize
                    tab = (Node[])e.key;
                    continue retry;
                }
                e = e.next;
            }
            break;
        }
        return null;
    }


    /** Implementation for put and putIfAbsent */
    private final Object internalPut(Object k, Object v, boolean replace) {
        int h = spread(k.hashCode());
        Object oldVal = null;  // the previous value or null if none
        Node[] tab = table;
        for (;;) {
            Node e; int i;
            if (tab == null)
                tab = grow(0);
            else if ((e = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                if (casTabAt(tab, i, null, new Node(h, k, v, null)))
                    break;
            }
            else if (e.hash < 0)
                tab = (Node[])e.key;
            else {
                boolean validated = false;
                boolean checkSize = false;
                synchronized (e) {
                    if (tabAt(tab, i) == e) {
                        validated = true;
                        for (Node first = e;;) {
                            Object ek, ev;
                            if (e.hash == h && 
                                (ek = e.key) != null &&
                                (ev = e.val) != null &&
                                (k == ek || k.equals(ek))) {
                                oldVal = ev;
                                if (replace)
                                    e.val = v;
                                break;
                            }
                            Node last = e;
                            if ((e = e.next) == null) {
                                last.next = new Node(h, k, v, null);
                                if (last != first || tab.length <= 64)
                                    checkSize = true;
                                break;
                            }
                        }
                    }
                }
                if (validated) {
                    if (checkSize && tab.length < MAXIMUM_CAPACITY &&
                        resizing == 0 && counter.sum() >= threshold)
                        grow(0);
                    break;
                }
            }
        }
        if (oldVal == null)
            counter.increment();
        return oldVal;
    }

    /**
     * Implementation for the four public remove/replace methods:
     * Replaces node value with v, conditional upon match of cv if
     * non-null.  If resulting value is null, delete.
     */
    private final Object internalReplace(Object k, Object v, Object cv) {
        int h = spread(k.hashCode());
        Object oldVal = null;
        Node e; int i;
        Node[] tab = table;
        while (tab != null &&
               (e = tabAt(tab, i = (tab.length - 1) & h)) != null) {
            if (e.hash < 0)
                tab = (Node[])e.key;
            else {
                boolean validated = false;
                boolean deleted = false;
                synchronized (e) {
                    if (tabAt(tab, i) == e) {
                        validated = true;
                        Node pred = null;
                        do {
                            Object ek, ev;
                            if (e.hash == h &&
                                (ek = e.key) != null &&
                                (ev = e.val) != null &&
                                (k == ek || k.equals(ek))) {
                                if (cv == null || cv == ev || cv.equals(ev)) {
                                    oldVal = ev;
                                    if ((e.val = v) == null) {
                                        deleted = true;
                                        Node en = e.next;
                                        if (pred != null)
                                            pred.next = en;
                                        else
                                            setTabAt(tab, i, en);
                                    }
                                }
                                break;
                            }
                            pred = e;
                        } while ((e = e.next) != null);
                    }
                }
                if (validated) {
                    if (deleted)
                        counter.decrement();
                    break;
                }
            }
        }
        return oldVal;
    }

    /** Implementation for computeIfAbsent and compute */
    @SuppressWarnings("unchecked")
    private final V internalCompute(K k,
                                    MappingFunction<? super K, ? extends V> f,
                                    boolean replace) {
        int h = spread(k.hashCode());
        V val = null;
        boolean added = false;
        Node[] tab = table;
        for(;;) {
            Node e; int i;
            if (tab == null)
                tab = grow(0);
            else if ((e = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                Node node = new Node(h, k, null, null);
                boolean validated = false;
                synchronized (node) {
                    if (casTabAt(tab, i, null, node)) {
                        validated = true;
                        try {
                            val = f.map(k);
                            if (val != null) {
                                node.val = val;
                                added = true;
                            }
                        } finally {
                            if (!added)
                                setTabAt(tab, i, null);
                        }
                    }
                }
                if (validated)
                    break;
            }
            else if (e.hash < 0)
                tab = (Node[])e.key;
            else if (Thread.holdsLock(e))
                throw new IllegalStateException("Recursive map computation");
            else {
                boolean validated = false;
                boolean checkSize = false;
                synchronized (e) {
                    if (tabAt(tab, i) == e) {
                        validated = true;
                        for (Node first = e;;) {
                            Object ek, ev, fv;
                            if (e.hash == h &&
                                (ek = e.key) != null &&
                                (ev = e.val) != null &&
                                (k == ek || k.equals(ek))) {
                                if (replace && (fv = f.map(k)) != null)
                                    ev = e.val = fv;
                                val = (V)ev;
                                break;
                            }
                            Node last = e;
                            if ((e = e.next) == null) {
                                if ((val = f.map(k)) != null) {
                                    last.next = new Node(h, k, val, null);
                                    added = true;
                                    if (last != first || tab.length <= 64)
                                        checkSize = true;
                                }
                                break;
                            }
                        }
                    }
                }
                if (validated) {
                    if (checkSize && tab.length < MAXIMUM_CAPACITY &&
                        resizing == 0 && counter.sum() >= threshold)
                        grow(0);
                    break;
                }
            }
        }
        if (added)
            counter.increment();
        return val;
    }

    /*
     * Reclassifies nodes in each bin to new table.  Because we are
     * using power-of-two expansion, the elements from each bin must
     * either stay at same index, or move with a power of two
     * offset. We eliminate unnecessary node creation by catching
     * cases where old nodes can be reused because their next fields
     * won't change.  Statistically, at the default threshold, only
     * about one-sixth of them need cloning when a table doubles. The
     * nodes they replace will be garbage collectable as soon as they
     * are no longer referenced by any reader thread that may be in
     * the midst of concurrently traversing table.
     *
     * Transfers are done from the bottom up to preserve iterator
     * traversability. On each step, the old bin is locked,
     * moved/copied, and then replaced with a forwarding node.
     */
    private static final void transfer(Node[] tab, Node[] nextTab) {
        int n = tab.length;
        int mask = nextTab.length - 1;
        Node fwd = new Node(MOVED, nextTab, null, null);
        for (int i = n - 1; i >= 0; --i) {
            for (Node e;;) {
                if ((e = tabAt(tab, i)) == null) {
                    if (casTabAt(tab, i, e, fwd))
                        break;
                }
                else {
                    int idx = e.hash & mask;
                    boolean validated = false;
                    synchronized (e) {
                        if (tabAt(tab, i) == e) {
                            validated = true;
                            Node lastRun = e;
                            for (Node p = e.next; p != null; p = p.next) {
                                int j = p.hash & mask;
                                if (j != idx) {
                                    idx = j;
                                    lastRun = p;
                                }
                            }
                            relaxedSetTabAt(nextTab, idx, lastRun);
                            for (Node p = e; p != lastRun; p = p.next) {
                                int h = p.hash;
                                int j = h & mask;
                                Node r = relaxedTabAt(nextTab, j);
                                relaxedSetTabAt(nextTab, j,
                                                new Node(h, p.key, p.val, r));
                            }
                            setTabAt(tab, i, fwd);
                        }
                    }
                    if (validated)
                        break;
                }
            }
        }
    }

    /**
     * If not already resizing, initializes or creates next table and
     * transfers bins. Rechecks occupancy after a transfer to see if
     * another resize is already needed because resizings are lagging
     * additions.
     *
     * @param sizeHint overridden capacity target (nonzero only from putAll)
     * @return current table
     */
    private final Node[] grow(int sizeHint) {
        if (resizing == 0 &&
            UNSAFE.compareAndSwapInt(this, resizingOffset, 0, 1)) {
            try {
                for (;;) {
                    int cap, n;
                    Node[] tab = table;
                    if (tab == null) {
                        int c = initCap;
                        if (c < sizeHint)
                            c = sizeHint;
                        if (c == DEFAULT_CAPACITY)
                            cap = c;
                        else if (c >= MAXIMUM_CAPACITY)
                            cap = MAXIMUM_CAPACITY;
                        else {
                            cap = MINIMUM_CAPACITY;
                            while (cap < c)
                                cap <<= 1;
                        }
                    }
                    else if ((n = tab.length) < MAXIMUM_CAPACITY &&
                             (sizeHint <= 0 || n < sizeHint))
                        cap = n << 1;
                    else
                        break;
                    threshold = (int)(cap * loadFactor) - THRESHOLD_OFFSET;
                    Node[] nextTab = new Node[cap];
                    if (tab != null)
                        transfer(tab, nextTab);
                    table = nextTab;
                    if (tab == null || cap >= MAXIMUM_CAPACITY ||
                        ((sizeHint > 0) ? cap >= sizeHint :
                         counter.sum() < threshold))
                        break;
                }
            } finally {
                resizing = 0;
            }
        }
        else if (table == null)
            Thread.yield(); // lost initialization race; just spin
        return table;
    }

    /**
     * Implementation for putAll and constructor with Map
     * argument. Tries to first override initial capacity or grow
     * based on map size to pre-allocate table space.
     */
    private final void internalPutAll(Map<? extends K, ? extends V> m) {
        int s = m.size();
        grow((s >= (MAXIMUM_CAPACITY >>> 1)) ? s : s + (s >>> 1));
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k == null || v == null)
                throw new NullPointerException();
            internalPut(k, v, true);
        }
    }

    /**
     * Implementation for clear. Steps through each bin, removing all nodes.
     */
    private final void internalClear() {
        long deletions = 0L;
        int i = 0;
        Node[] tab = table;
        while (tab != null && i < tab.length) {
            Node e = tabAt(tab, i);
            if (e == null)
                ++i;
            else if (e.hash < 0)
                tab = (Node[])e.key;
            else {
                boolean validated = false;
                synchronized (e) {
                    if (tabAt(tab, i) == e) {
                        validated = true;
                        do {
                            if (e.val != null) {
                                e.val = null;
                                ++deletions;
                            }
                        } while ((e = e.next) != null);
                        setTabAt(tab, i, null);
                    }
                }
                if (validated) {
                    ++i;
                    if (deletions > THRESHOLD_OFFSET) { // bound lag in counts
                        counter.add(-deletions);
                        deletions = 0L;
                    }
                }
            }
        }
        if (deletions != 0L)
            counter.add(-deletions);
    }

    /**
     * Base class for key, value, and entry iterators, plus internal
     * implementations of public traversal-based methods, to avoid
     * duplicating traversal code.
     */
    class HashIterator {
        private Node next;          // the next entry to return
        private Node[] tab;         // current table; updated if resized
        private Node lastReturned;  // the last entry returned, for remove
        private Object nextVal;     // cached value of next
        private int index;          // index of bin to use next
        private int baseIndex;      // current index of initial table
        private final int baseSize; // initial table size

        HashIterator() {
            Node[] t = tab = table;
            if (t == null)
                baseSize = 0;
            else {
                baseSize = t.length;
                advance(null);
            }
        }

        public final boolean hasNext()         { return next != null; }
        public final boolean hasMoreElements() { return next != null; }

        /**
         * Advances next.  Normally, iteration proceeds bin-by-bin
         * traversing lists.  However, if the table has been resized,
         * then all future steps must traverse both the bin at the
         * current index as well as at (index + baseSize); and so on
         * for further resizings. To paranoically cope with potential
         * (improper) sharing of iterators across threads, table reads
         * are bounds-checked.
         */
        final void advance(Node e) {
            for (;;) {
                Node[] t; int i; // for bounds checks
                if (e != null) {
                    Object ek = e.key, ev = e.val;
                    if (ev != null && ek != null) {
                        nextVal = ev;
                        next = e;
                        break;
                    }
                    e = e.next;
                }
                else if (baseIndex < baseSize && (t = tab) != null &&
                         t.length > (i = index) && i >= 0) {
                    if ((e = tabAt(t, i)) != null && e.hash < 0) {
                        tab = (Node[])e.key;
                        e = null;
                    }
                    else if (i + baseSize < t.length)
                        index += baseSize;    // visit forwarded upper slots
                    else
                        index = ++baseIndex;
                }
                else {
                    next = null;
                    break;
                }
            }
        }

        final Object nextKey() {
            Node e = next;
            if (e == null)
                throw new NoSuchElementException();
            Object k = e.key;
            advance((lastReturned = e).next);
            return k;
        }

        final Object nextValue() {
            Node e = next;
            if (e == null)
                throw new NoSuchElementException();
            Object v = nextVal;
            advance((lastReturned = e).next);
            return v;
        }

        final WriteThroughEntry nextEntry() {
            Node e = next;
            if (e == null)
                throw new NoSuchElementException();
            WriteThroughEntry entry =
                new WriteThroughEntry(e.key, nextVal);
            advance((lastReturned = e).next);
            return entry;
        }

        public final void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            ConcurrentHashMapV8.this.remove(lastReturned.key);
            lastReturned = null;
        }

        /** Helper for serialization */
        final void writeEntries(java.io.ObjectOutputStream s)
            throws java.io.IOException {
            Node e;
            while ((e = next) != null) {
                s.writeObject(e.key);
                s.writeObject(nextVal);
                advance(e.next);
            }
        }

        /** Helper for containsValue */
        final boolean containsVal(Object value) {
            if (value != null) {
                Node e;
                while ((e = next) != null) {
                    Object v = nextVal;
                    if (value == v || value.equals(v))
                        return true;
                    advance(e.next);
                }
            }
            return false;
        }

        /** Helper for Map.hashCode */
        final int mapHashCode() {
            int h = 0;
            Node e;
            while ((e = next) != null) {
                h += e.key.hashCode() ^ nextVal.hashCode();
                advance(e.next);
            }
            return h;
        }

        /** Helper for Map.toString */
        final String mapToString() {
            Node e = next;
            if (e == null)
                return "{}";
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            for (;;) {
                sb.append(e.key   == this ? "(this Map)" : e.key);
                sb.append('=');
                sb.append(nextVal == this ? "(this Map)" : nextVal);
                advance(e.next);
                if ((e = next) != null)
                    sb.append(',').append(' ');
                else
                    return sb.append('}').toString();
            }
        }
    }

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new, empty map with the specified initial
     * capacity, load factor and concurrency level.
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     * Resizing may be performed when the average number of elements per
     * bin exceeds this threshold.
     * @param concurrencyLevel the estimated number of concurrently
     * updating threads. The implementation may use this value as
     * a sizing hint.
     * @throws IllegalArgumentException if the initial capacity is
     * negative or the load factor or concurrencyLevel are
     * nonpositive.
     */
    public ConcurrentHashMapV8(int initialCapacity,
                               float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();
        this.initCap = initialCapacity;
        this.loadFactor = loadFactor;
        this.counter = new LongAdder();
    }

    /**
     * Creates a new, empty map with the specified initial capacity
     * and load factor and with the default concurrencyLevel (16).
     *
     * @param initialCapacity The implementation performs internal
     * sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     * Resizing may be performed when the average number of elements per
     * bin exceeds this threshold.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative or the load factor is nonpositive
     *
     * @since 1.6
     */
    public ConcurrentHashMapV8(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with the specified initial capacity,
     * and with default load factor (0.75) and concurrencyLevel (16).
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative.
     */
    public ConcurrentHashMapV8(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with a default initial capacity (16),
     * load factor (0.75) and concurrencyLevel (16).
     */
    public ConcurrentHashMapV8() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new map with the same mappings as the given map.
     * The map is created with a capacity of 1.5 times the number
     * of mappings in the given map or 16 (whichever is greater),
     * and a default load factor (0.75) and concurrencyLevel (16).
     *
     * @param m the map
     */
    public ConcurrentHashMapV8(Map<? extends K, ? extends V> m) {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
        if (m == null)
            throw new NullPointerException();
        internalPutAll(m);
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        return counter.sum() <= 0L; // ignore transient negative values
    }

    /**
     * Returns the number of key-value mappings in this map.  If the
     * map contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        long n = counter.sum();
        return ((n >>> 31) == 0) ? (int)n : (n < 0L) ? 0 : Integer.MAX_VALUE;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key.equals(k)},
     * then this method returns {@code v}; otherwise it returns
     * {@code null}.  (There can be at most one such mapping.)
     *
     * @throws NullPointerException if the specified key is null
     */
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        if (key == null)
            throw new NullPointerException();
        return (V)internalGet(key);
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param  key   possible key
     * @return {@code true} if and only if the specified object
     *         is a key in this table, as determined by the
     *         {@code equals} method; {@code false} otherwise.
     * @throws NullPointerException if the specified key is null
     */
    public boolean containsKey(Object key) {
        if (key == null)
            throw new NullPointerException();
        return internalGet(key) != null;
    }

    /**
     * Returns {@code true} if this map maps one or more keys to the
     * specified value. Note: This method requires a full internal
     * traversal of the hash table, and so is much slower than
     * method {@code containsKey}.
     *
     * @param value value whose presence in this map is to be tested
     * @return {@code true} if this map maps one or more keys to the
     *         specified value
     * @throws NullPointerException if the specified value is null
     */
    public boolean containsValue(Object value) {
        if (value == null)
            throw new NullPointerException();
        return new HashIterator().containsVal(value);
    }

    /**
     * Legacy method testing if some key maps into the specified value
     * in this table.  This method is identical in functionality to
     * {@link #containsValue}, and exists solely to ensure
     * full compatibility with class {@link java.util.Hashtable},
     * which supported this method prior to introduction of the
     * Java Collections framework.
     *
     * @param  value a value to search for
     * @return {@code true} if and only if some key maps to the
     *         {@code value} argument in this table as
     *         determined by the {@code equals} method;
     *         {@code false} otherwise
     * @throws NullPointerException if the specified value is null
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p> The value can be retrieved by calling the {@code get} method
     * with a key that is equal to the original key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();
        return (V)internalPut(key, value, true);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or {@code null} if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V putIfAbsent(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();
        return (V)internalPut(key, value, false);
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        if (m == null)
            throw new NullPointerException();
        internalPutAll(m);
    }

    /**
     * If the specified key is not already associated with a value,
     * computes its value using the given mappingFunction, and if
     * non-null, enters it into the map.  This is equivalent to
     *
     * <pre>
     *   if (map.containsKey(key))
     *       return map.get(key);
     *   value = mappingFunction.map(key);
     *   if (value != null)
     *      map.put(key, value);
     *   return value;
     * </pre>
     *
     * except that the action is performed atomically.  Some attempted
     * update operations on this map by other threads may be blocked
     * while computation is in progress, so the computation should be
     * short and simple, and must not attempt to update any other
     * mappings of this Map. The most appropriate usage is to
     * construct a new object serving as an initial mapped value, or
     * memoized result, as in:
     * <pre>{@code
     * map.computeIfAbsent(key, new MappingFunction<K, V>() {
     *   public V map(K k) { return new Value(f(k)); }};
     * }</pre>
     *
     * @param key key with which the specified value is to be associated
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with
     *         the specified key, or {@code null} if the computation
     *         returned {@code null}.
     * @throws NullPointerException if the specified key or mappingFunction
     *         is null,
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete.
     * @throws RuntimeException or Error if the mappingFunction does so,
     *         in which case the mapping is left unestablished.
     */
    public V computeIfAbsent(K key, MappingFunction<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null)
            throw new NullPointerException();
        return internalCompute(key, mappingFunction, false);
    }

    /**
     * Computes the value associated with the given key using the given
     * mappingFunction, and if non-null, enters it into the map.  This
     * is equivalent to
     *
     * <pre>
     *   value = mappingFunction.map(key);
     *   if (value != null)
     *      map.put(key, value);
     *   else
     *      value = map.get(key);
     *   return value;
     * </pre>
     *
     * except that the action is performed atomically.  Some attempted
     * update operations on this map by other threads may be blocked
     * while computation is in progress, so the computation should be
     * short and simple, and must not attempt to update any other
     * mappings of this Map.
     *
     * @param key key with which the specified value is to be associated
     * @param mappingFunction the function to compute a value
     * @return the current value associated with
     *         the specified key, or {@code null} if the computation
     *         returned {@code null} and the value was not otherwise present.
     * @throws NullPointerException if the specified key or mappingFunction
     *         is null,
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete.
     * @throws RuntimeException or Error if the mappingFunction does so,
     *         in which case the mapping is unchanged.
     */
    public V compute(K key, MappingFunction<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null)
            throw new NullPointerException();
        return internalCompute(key, mappingFunction, true);
    }

    /**
     * Removes the key (and its corresponding value) from this map.
     * This method does nothing if the key is not in the map.
     *
     * @param  key the key that needs to be removed
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}
     * @throws NullPointerException if the specified key is null
     */
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        if (key == null)
            throw new NullPointerException();
        return (V)internalReplace(key, null, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    public boolean remove(Object key, Object value) {
        if (key == null)
            throw new NullPointerException();
        if (value == null)
            return false;
        return internalReplace(key, null, value) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if any of the arguments are null
     */
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null)
            throw new NullPointerException();
        return internalReplace(key, newValue, oldValue) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or {@code null} if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V replace(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();
        return (V)internalReplace(key, value, null);
    }

    /**
     * Removes all of the mappings from this map.
     */
    public void clear() {
        internalClear();
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from this map,
     * via the {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or
     * {@code addAll} operations.
     *
     * <p>The view's {@code iterator} is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  The collection
     * supports element removal, which removes the corresponding
     * mapping from this map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll}, and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     *
     * <p>The view's {@code iterator} is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from the map,
     * via the {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or
     * {@code addAll} operations.
     *
     * <p>The view's {@code iterator} is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        return new KeyIterator();
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        return new ValueIterator();
    }

    /**
     * Returns the hash code value for this {@link Map}, i.e.,
     * the sum of, for each key-value pair in the map,
     * {@code key.hashCode() ^ value.hashCode()}.
     *
     * @return the hash code value for this map
     */
    public int hashCode() {
        return new HashIterator().mapHashCode();
    }

    /**
     * Returns a string representation of this map.  The string
     * representation consists of a list of key-value mappings (in no
     * particular order) enclosed in braces ("{@code {}}").  Adjacent
     * mappings are separated by the characters {@code ", "} (comma
     * and space).  Each key-value mapping is rendered as the key
     * followed by an equals sign ("{@code =}") followed by the
     * associated value.
     *
     * @return a string representation of this map
     */
    public String toString() {
        return new HashIterator().mapToString();
    }

    /**
     * Compares the specified object with this map for equality.
     * Returns {@code true} if the given object is a map with the same
     * mappings as this map.  This operation may return misleading
     * results if either map is concurrently modified during execution
     * of this method.
     *
     * @param o object to be compared for equality with this map
     * @return {@code true} if the specified object is equal to this map
     */
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Map))
            return false;
        Map<?,?> m = (Map<?,?>) o;
        try {
            for (Map.Entry<K,V> e : this.entrySet())
                if (! e.getValue().equals(m.get(e.getKey())))
                    return false;
            for (Map.Entry<?,?> e : m.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                if (k == null || v == null || !v.equals(get(k)))
                    return false;
            }
            return true;
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }
    }

    /**
     * Custom Entry class used by EntryIterator.next(), that relays
     * setValue changes to the underlying map.
     */
    final class WriteThroughEntry extends AbstractMap.SimpleEntry<K,V> {
        @SuppressWarnings("unchecked")
        WriteThroughEntry(Object k, Object v) {
            super((K)k, (V)v);
        }

        /**
         * Sets our entry's value and writes through to the map. The
         * value to return is somewhat arbitrary here. Since a
         * WriteThroughEntry does not necessarily track asynchronous
         * changes, the most recent "previous" value could be
         * different from what we return (or could even have been
         * removed in which case the put will re-establish). We do not
         * and cannot guarantee more.
         */
        public V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = super.setValue(value);
            ConcurrentHashMapV8.this.put(getKey(), value);
            return v;
        }
    }

    final class KeyIterator extends HashIterator
        implements Iterator<K>, Enumeration<K> {
        @SuppressWarnings("unchecked")
        public final K next()        { return (K)super.nextKey(); }
        @SuppressWarnings("unchecked")
        public final K nextElement() { return (K)super.nextKey(); }
    }

    final class ValueIterator extends HashIterator
        implements Iterator<V>, Enumeration<V> {
        @SuppressWarnings("unchecked")
        public final V next()        { return (V)super.nextValue(); }
        @SuppressWarnings("unchecked")
        public final V nextElement() { return (V)super.nextValue(); }
    }

    final class EntryIterator extends HashIterator
        implements Iterator<Entry<K,V>> {
        public final Map.Entry<K,V> next() { return super.nextEntry(); }
    }

    final class KeySet extends AbstractSet<K> {
        public int size() {
            return ConcurrentHashMapV8.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMapV8.this.isEmpty();
        }
        public void clear() {
            ConcurrentHashMapV8.this.clear();
        }
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMapV8.this.containsKey(o);
        }
        public boolean remove(Object o) {
            return ConcurrentHashMapV8.this.remove(o) != null;
        }
    }

    final class Values extends AbstractCollection<V> {
        public int size() {
            return ConcurrentHashMapV8.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMapV8.this.isEmpty();
        }
        public void clear() {
            ConcurrentHashMapV8.this.clear();
        }
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMapV8.this.containsValue(o);
        }
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public int size() {
            return ConcurrentHashMapV8.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMapV8.this.isEmpty();
        }
        public void clear() {
            ConcurrentHashMapV8.this.clear();
        }
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            V v = ConcurrentHashMapV8.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            return ConcurrentHashMapV8.this.remove(e.getKey(), e.getValue());
        }
    }

    /* ---------------- Serialization Support -------------- */

    /**
     * Helper class used in previous version, declared for the sake of
     * serialization compatibility
     */
    static class Segment<K,V> extends java.util.concurrent.locks.ReentrantLock
        implements Serializable {
        private static final long serialVersionUID = 2249069246763182397L;
        final float loadFactor;
        Segment(float lf) { this.loadFactor = lf; }
    }

    /**
     * Saves the state of the {@code ConcurrentHashMapV8} instance to a
     * stream (i.e., serializes it).
     * @param s the stream
     * @serialData
     * the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    @SuppressWarnings("unchecked")
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        if (segments == null) { // for serialization compatibility
            segments = (Segment<K,V>[])
                new Segment<?,?>[DEFAULT_CONCURRENCY_LEVEL];
            for (int i = 0; i < segments.length; ++i)
                segments[i] = new Segment<K,V>(loadFactor);
        }
        s.defaultWriteObject();
        new HashIterator().writeEntries(s);
        s.writeObject(null);
        s.writeObject(null);
        segments = null; // throw away
    }

    /**
     * Reconstitutes the instance from a stream (that is, deserializes it).
     * @param s the stream
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        // find load factor in a segment, if one exists
        if (segments != null && segments.length != 0)
            this.loadFactor = segments[0].loadFactor;
        else
            this.loadFactor = DEFAULT_LOAD_FACTOR;
        this.initCap = DEFAULT_CAPACITY;
        LongAdder ct = new LongAdder(); // force final field write
        UNSAFE.putObjectVolatile(this, counterOffset, ct);
        this.segments = null; // unneeded

        // Read the keys and values, and put the mappings in the table
        for (;;) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null)
                break;
            put(key, value);
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long counterOffset;
    private static final long resizingOffset;
    private static final long ABASE;
    private static final int ASHIFT;

    static {
        int ss;
        try {
            UNSAFE = getUnsafe();
            Class<?> k = ConcurrentHashMapV8.class;
            counterOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("counter"));
            resizingOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("resizing"));
            Class<?> sc = Node[].class;
            ABASE = UNSAFE.arrayBaseOffset(sc);
            ss = UNSAFE.arrayIndexScale(sc);
        } catch (Exception e) {
            throw new Error(e);
        }
        if ((ss & (ss-1)) != 0)
            throw new Error("data type scale not a power of two");
        ASHIFT = 31 - Integer.numberOfLeadingZeros(ss);
    }

    /**
     * Returns a sun.misc.Unsafe.  Suitable for use in a 3rd party package.
     * Replace with a simple call to Unsafe.getUnsafe when integrating
     * into a jdk.
     *
     * @return a sun.misc.Unsafe
     */
    private static sun.misc.Unsafe getUnsafe() {
        try {
            return sun.misc.Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                return java.security.AccessController.doPrivileged
                    (new java.security
                     .PrivilegedExceptionAction<sun.misc.Unsafe>() {
                        public sun.misc.Unsafe run() throws Exception {
                            java.lang.reflect.Field f = sun.misc
                                .Unsafe.class.getDeclaredField("theUnsafe");
                            f.setAccessible(true);
                            return (sun.misc.Unsafe) f.get(null);
                        }});
            } catch (java.security.PrivilegedActionException e) {
                throw new RuntimeException("Could not initialize intrinsics",
                                           e.getCause());
            }
        }
    }

}
