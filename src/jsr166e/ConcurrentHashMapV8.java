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
     * work off Object types. And similarly, so do the internal
     * methods of auxiliary iterator and view classes.  All public
     * generic typed methods relay in/out of these internal methods,
     * supplying null-checks and casts as needed.
     *
     * The table is lazily initialized to a power-of-two size upon the
     * first insertion.  Each bin in the table contains a list of
     * Nodes (most often, zero or one Node).  Table accesses require
     * volatile/atomic reads, writes, and CASes.  Because there is no
     * other way to arrange this without adding further indirections,
     * we use intrinsics (sun.misc.Unsafe) operations.  The lists of
     * nodes within bins are always accurately traversable under
     * volatile reads, so long as lookups check hash code and
     * non-nullness of value before checking key equality. (All valid
     * hash codes are nonnegative. Negative values are reserved for
     * special forwarding nodes; see below.)
     *
     * Insertion (via put or putIfAbsent) of the first node in an
     * empty bin is performed by just CASing it to the bin.  This is
     * on average by far the most common case for put operations.
     * Other update operations (insert, delete, and replace) require
     * locks.  We do not want to waste the space required to associate
     * a distinct lock object with each bin, so instead use the first
     * node of a bin list itself as a lock, using plain "synchronized"
     * locks. These save space and we can live with block-structured
     * lock/unlock operations. Using the first node of a list as a
     * lock does not by itself suffice though: When a node is locked,
     * any update must first validate that it is still the first node,
     * and retry if not. Because new nodes are always appended to
     * lists, once a node is first in a bin, it remains first until
     * deleted or the bin becomes invalidated.  However, operations
     * that only conditionally update can and sometimes do inspect
     * nodes until the point of update. This is a converse of sorts to
     * the lazy locking technique described by Herlihy & Shavit.
     *
     * The main disadvantage of this approach is that most update
     * operations on other nodes in a bin list protected by the same
     * lock can stall, for example when user equals() or mapping
     * functions take a long time.  However, statistically, this is
     * not a common enough problem to outweigh the time/space overhead
     * of alternatives: Under random hash codes, the frequency of
     * nodes in bins follows a Poisson distribution
     * (http://en.wikipedia.org/wiki/Poisson_distribution) with a
     * parameter of 0.5 on average under the default loadFactor of
     * 0.75. The expected number of locks covering different elements
     * (i.e., bins with 2 or more nodes) is approximately 10% at
     * steady state.  Lock contention probability for two threads
     * accessing distinct elements is roughly 1 / (8 * #elements).
     * Function "spread" performs hashCode randomization that improves
     * the likelihood that these assumptions hold unless users define
     * exactly the same value for too many hashCodes.
     *
     * The table is resized when occupancy exceeds a threshold.  Only
     * a single thread performs the resize (using field "resizing", to
     * arrange exclusion), but the table otherwise remains usable for
     * reads and updates. Resizing proceeds by transferring bins, one
     * by one, from the table to the next table.  Upon transfer, the
     * old table bin contains only a special forwarding node (with
     * negative hash field) that contains the next table as its
     * key. On encountering a forwarding node, access and update
     * operations restart, using the new table. To ensure concurrent
     * readability of traversals, transfers must proceed from the last
     * bin (table.length - 1) up towards the first.  Upon seeing a
     * forwarding node, traversals (see class InternalIterator)
     * arrange to move to the new table for the rest of the traversal
     * without revisiting nodes.  This constrains bin transfers to a
     * particular order, and so can block indefinitely waiting for the
     * next lock, and other threads cannot help with the transfer.
     * However, expected stalls are infrequent enough to not warrant
     * the additional overhead of access and iteration schemes that
     * could admit out-of-order or concurrent bin transfers.
     *
     * This traversal scheme also applies to partial traversals of
     * ranges of bins (via an alternate InternalIterator constructor)
     * to support partitioned aggregate operations (that are not
     * otherwise implemented yet).  Also, read-only operations give up
     * if ever forwarded to a null table, which provides support for
     * shutdown-style clearing, which is also not currently
     * implemented.
     *
     * Lazy table initialization minimizes footprint until first use,
     * and also avoids resizings when the first operation is from a
     * putAll, constructor with map argument, or deserialization.
     * These cases attempt to override the targetCapacity used in
     * growTable (which may harmlessly fail to take effect in cases of
     * races with other ongoing resizings).
     *
     * The element count is maintained using a LongAdder, which avoids
     * contention on updates but can encounter cache thrashing if read
     * too frequently during concurrent access. To avoid reading so
     * often, resizing is normally attempted only upon adding to a bin
     * already holding two or more nodes. Under the default load
     * factor and uniform hash distributions, the probability of this
     * occurring at threshold is around 13%, meaning that only about 1
     * in 8 puts check threshold (and after resizing, many fewer do
     * so). But this approximation has high variance for small table
     * sizes, so we check on any collision for sizes <= 64.  Further,
     * to increase the probability that a resize occurs soon enough,
     * we offset the threshold (see THRESHOLD_OFFSET) by the expected
     * number of puts between checks. This is currently set to 8, in
     * accord with the default load factor. In practice, this default
     * is rarely overridden, and in any case is close enough to other
     * plausible values not to waste dynamic probability computation
     * for the sake of more precision.
     *
     * Maintaining API and serialization compatibility with previous
     * versions of this class introduces several oddities. Mainly: We
     * leave untouched but unused constructor arguments refering to
     * concurrencyLevel. We also declare an unused "Segment" class
     * that is instantiated in minimal form only when serializing.
     */

    /* ---------------- Constants -------------- */

    /**
     * The largest allowed table capacity.  Must be a power of 2, at
     * most 1<<30 to stay within Java array size limits.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The default initial table capacity.  Must be a power of 2
     * (i.e., at least 1) and at most MAXIMUM_CAPACITY.
     */
    private static final int DEFAULT_CAPACITY = 16;

    /**
     * The default load factor for this table, used when not otherwise
     * specified in a constructor.
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The default concurrency level for this table. Unused, but
     * defined for compatibility with previous versions of this class.
     */
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    /**
     * The count value to offset thresholds to compensate for checking
     * for the need to resize only when inserting into bins with two
     * or more elements. See above for explanation.
     */
    private static final int THRESHOLD_OFFSET = 8;

    /* ---------------- Nodes -------------- */

    /**
     * Key-value entry. Note that this is never exported out as a
     * user-visible Map.Entry. Nodes with a negative hash field are
     * special, and do not contain user keys or values.  Otherwise,
     * keys are never null, and null val fields indicate that a node
     * is in the process of being deleted or created. For purposes of
     * read-only, access, a key may be read before a val, but can only
     * be used after checking val.  (For an update operation, when a
     * lock is held on a node, order doesn't matter.)
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

    /**
     * Sign bit of node hash value indicating to use table in node.key.
     */
    private static final int SIGN_BIT = 0x80000000;

    /* ---------------- Fields -------------- */

    /**
     * The array of bins. Lazily initialized upon first insertion.
     * Size is always a power of two. Accessed directly by iterators.
     */
    transient volatile Node[] table;

    /** The counter maintaining number of elements. */
    private transient final LongAdder counter;
    /** Nonzero when table is being initialized or resized. Updated via CAS. */
    private transient volatile int resizing;
    /** The next element count value upon which to resize the table. */
    private transient int threshold;
    /** The target capacity; volatile to cover initialization races. */
    private transient volatile int targetCapacity;
    /** The target load factor for the table */
    private transient final float loadFactor;

    // views
    private transient KeySet<K,V> keySet;
    private transient Values<K,V> values;
    private transient EntrySet<K,V> entrySet;

    /** For serialization compatibility. Null unless serialized; see below */
    private Segment<K,V>[] segments;

    /* ---------------- Table element access -------------- */

    /*
     * Volatile access methods are used for table elements as well as
     * elements of in-progress next table while resizing.  Uses are
     * null checked by callers, and implicitly bounds-checked, relying
     * on the invariants that tab arrays have non-zero size, and all
     * indices are masked with (tab.length - 1) which is never
     * negative and always less than length. Note that, to be correct
     * wrt arbitrary concurrency errors by users, bounds checks must
     * operate on local variables, which accounts for some odd-looking
     * inline assignments below.
     */

    static final Node tabAt(Node[] tab, int i) { // used by InternalIterator
        return (Node)UNSAFE.getObjectVolatile(tab, ((long)i<<ASHIFT)+ABASE);
    }

    private static final boolean casTabAt(Node[] tab, int i, Node c, Node v) {
        return UNSAFE.compareAndSwapObject(tab, ((long)i<<ASHIFT)+ABASE, c, v);
    }

    private static final void setTabAt(Node[] tab, int i, Node v) {
        UNSAFE.putObjectVolatile(tab, ((long)i<<ASHIFT)+ABASE, v);
    }

    /* ----------------Table Initialization and Resizing -------------- */

    /**
     * Returns a power of two table size for the given desired capacity.
     * See Hackers Delight, sec 3.2
     */
    private static final int tableSizeFor(int c) {
        int n = c - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /**
     * If not already resizing, initializes or creates next table and
     * transfers bins. Initial table size uses the capacity recorded
     * in targetCapacity.  Rechecks occupancy after a transfer to see
     * if another resize is already needed because resizings are
     * lagging additions.
     *
     * @return current table
     */
    private final Node[] growTable() {
        if (resizing == 0 &&
            UNSAFE.compareAndSwapInt(this, resizingOffset, 0, 1)) {
            try {
                for (;;) {
                    Node[] tab = table;
                    int n, c;
                    if (tab == null)
                        n = (c = targetCapacity) > 0 ? c : DEFAULT_CAPACITY;
                    else if ((n = tab.length) < MAXIMUM_CAPACITY &&
                             counter.sum() >= threshold)
                        n <<= 1;
                    else
                        break;
                    Node[] nextTab = new Node[n];
                    threshold = (int)(n * loadFactor) - THRESHOLD_OFFSET;
                    if (tab != null)
                        transfer(tab, nextTab,
                                 new Node(SIGN_BIT, nextTab, null, null));
                    table = nextTab;
                    if (tab == null)
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

    /*
     * Reclassifies nodes in each bin to new table.  Because we are
     * using power-of-two expansion, the elements from each bin must
     * either stay at same index, or move with a power of two
     * offset. We eliminate unnecessary node creation by catching
     * cases where old nodes can be reused because their next fields
     * won't change.  Statistically, at the default loadFactor, only
     * about one-sixth of them need cloning when a table doubles. The
     * nodes they replace will be garbage collectable as soon as they
     * are no longer referenced by any reader thread that may be in
     * the midst of concurrently traversing table.
     *
     * Transfers are done from the bottom up to preserve iterator
     * traversability. On each step, the old bin is locked,
     * moved/copied, and then replaced with a forwarding node.
     */
    private static final void transfer(Node[] tab, Node[] nextTab, Node fwd) {
        int n = tab.length;
        Node ignore = nextTab[n + n - 1]; // force bounds check
        for (int i = n - 1; i >= 0; --i) {
            for (Node e;;) {
                if ((e = tabAt(tab, i)) != null) {
                    boolean validated = false;
                    synchronized (e) {
                        if (tabAt(tab, i) == e) {
                            validated = true;
                            Node lo = null, hi = null, lastRun = e;
                            int runBit = e.hash & n;
                            for (Node p = e.next; p != null; p = p.next) {
                                int b = p.hash & n;
                                if (b != runBit) {
                                    runBit = b;
                                    lastRun = p;
                                }
                            }
                            if (runBit == 0)
                                lo = lastRun;
                            else
                                hi = lastRun;
                            for (Node p = e; p != lastRun; p = p.next) {
                                int ph = p.hash;
                                Object pk = p.key, pv = p.val;
                                if ((ph & n) == 0)
                                    lo = new Node(ph, pk, pv, lo);
                                else
                                    hi = new Node(ph, pk, pv, hi);
                            }
                            setTabAt(nextTab, i, lo);
                            setTabAt(nextTab, i + n, hi);
                            setTabAt(tab, i, fwd);
                        }
                    }
                    if (validated)
                        break;
                }
                else if (casTabAt(tab, i, e, fwd))
                    break;
            }
        }
    }

    /* ---------------- Internal access and update methods -------------- */

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

    /** Implementation for get and containsKey */
    private final Object internalGet(Object k) {
        int h = spread(k.hashCode());
        retry: for (Node[] tab = table; tab != null;) {
            Node e; Object ek, ev; int eh;  // locals to read fields once
            for (e = tabAt(tab, (tab.length - 1) & h); e != null; e = e.next) {
                if ((eh = e.hash) == h) {
                    if ((ev = e.val) != null &&
                        ((ek = e.key) == k || k.equals(ek)))
                        return ev;
                }
                else if (eh < 0) {          // sign bit set
                    tab = (Node[])e.key;    // bin was moved during resize
                    continue retry;
                }
            }
            break;
        }
        return null;
    }

    /** Implementation for put and putIfAbsent */
    private final Object internalPut(Object k, Object v, boolean replace) {
        int h = spread(k.hashCode());
        Object oldVal = null;               // previous value or null if none
        for (Node[] tab = table;;) {
            Node e; int i; Object ek, ev;
            if (tab == null)
                tab = growTable();
            else if ((e = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                if (casTabAt(tab, i, null, new Node(h, k, v, null)))
                    break;                   // no lock when adding to empty bin
            }
            else if (e.hash < 0)             // resized -- restart with new table
                tab = (Node[])e.key;
            else if (!replace && e.hash == h && (ev = e.val) != null &&
                     ((ek = e.key) == k || k.equals(ek))) {
                if (tabAt(tab, i) == e) {    // inspect and validate 1st node
                    oldVal = ev;             // without lock for putIfAbsent
                    break;
                }
            }
            else {
                boolean validated = false;
                boolean checkSize = false;
                synchronized (e) {           // lock the 1st node of bin list
                    if (tabAt(tab, i) == e) {
                        validated = true;    // retry if 1st already deleted
                        for (Node first = e;;) {
                            if (e.hash == h &&
                                ((ek = e.key) == k || k.equals(ek)) &&
                                (ev = e.val) != null) {
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
                        growTable();
                    break;
                }
            }
        }
        if (oldVal == null)
            counter.increment();             // update counter outside of locks
        return oldVal;
    }

    /**
     * Implementation for the four public remove/replace methods:
     * Replaces node value with v, conditional upon match of cv if
     * non-null.  If resulting value is null, delete.
     */
    private final Object internalReplace(Object k, Object v, Object cv) {
        int h = spread(k.hashCode());
        for (Node[] tab = table;;) {
            Node e; int i;
            if (tab == null ||
                (e = tabAt(tab, i = (tab.length - 1) & h)) == null)
                return null;
            else if (e.hash < 0)
                tab = (Node[])e.key;
            else {
                Object oldVal = null;
                boolean validated = false;
                boolean deleted = false;
                synchronized (e) {
                    if (tabAt(tab, i) == e) {
                        validated = true;
                        Node pred = null;
                        do {
                            Object ek, ev;
                            if (e.hash == h &&
                                ((ek = e.key) == k || k.equals(ek)) &&
                                ((ev = e.val) != null)) {
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
                        } while ((e = (pred = e).next) != null);
                    }
                }
                if (validated) {
                    if (deleted)
                        counter.decrement();
                    return oldVal;
                }
            }
        }
    }

    /** Implementation for computeIfAbsent and compute. Like put, but messier. */
    @SuppressWarnings("unchecked")
    private final V internalCompute(K k,
                                    MappingFunction<? super K, ? extends V> f,
                                    boolean replace) {
        int h = spread(k.hashCode());
        V val = null;
        boolean added = false;
        Node[] tab = table;
        outer:for (;;) {
            Node e; int i; Object ek, ev;
            if (tab == null)
                tab = growTable();
            else if ((e = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                Node node = new Node(h, k, null, null);
                boolean validated = false;
                synchronized (node) {  // must lock while computing value
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
            else if (!replace && e.hash == h && (ev = e.val) != null &&
                     ((ek = e.key) == k || k.equals(ek))) {
                if (tabAt(tab, i) == e) {
                    val = (V)ev;
                    break;
                }
            }
            else if (Thread.holdsLock(e))
                throw new IllegalStateException("Recursive map computation");
            else {
                boolean validated = false;
                boolean checkSize = false;
                synchronized (e) {
                    if (tabAt(tab, i) == e) {
                        validated = true;
                        for (Node first = e;;) {
                            if (e.hash == h &&
                                ((ek = e.key) == k || k.equals(ek)) &&
                                ((ev = e.val) != null)) {
                                Object fv;
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
                        growTable();
                    break;
                }
            }
        }
        if (added)
            counter.increment();
        return val;
    }

    /**
     * Implementation for clear. Steps through each bin, removing all nodes.
     */
    private final void internalClear() {
        long delta = 0L; // negative number of deletions
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
                        Node en;
                        do {
                            en = e.next;
                            if (e.val != null) { // currently always true
                                e.val = null;
                                --delta;
                            }
                        } while ((e = en) != null);
                        setTabAt(tab, i, null);
                    }
                }
                if (validated)
                    ++i;
            }
        }
        counter.add(delta);
    }

    /* ----------------Table Traversal -------------- */

    /**
     * Encapsulates traversal for methods such as containsValue; also
     * serves as a base class for other iterators.
     *
     * At each step, the iterator snapshots the key ("nextKey") and
     * value ("nextVal") of a valid node (i.e., one that, at point of
     * snapshot, has a nonnull user value). Because val fields can
     * change (including to null, indicating deletion), field nextVal
     * might not be accurate at point of use, but still maintains the
     * weak consistency property of holding a value that was once
     * valid.
     *
     * Internal traversals directly access these fields, as in:
     * {@code while (it.next != null) { process(nextKey); it.advance(); }}
     *
     * Exported iterators (subclasses of ViewIterator) extract key,
     * value, or key-value pairs as return values of Iterator.next(),
     * and encapulate the it.next check as hasNext();
     *
     * The iterator visits each valid node that was reachable upon
     * iterator construction once. It might miss some that were added
     * to a bin after the bin was visited, which is OK wrt consistency
     * guarantees. Maintaining this property in the face of possible
     * ongoing resizes requires a fair amount of bookkeeping state
     * that is difficult to optimize away amidst volatile accesses.
     * Even so, traversal maintains reasonable throughput.
     *
     * Normally, iteration proceeds bin-by-bin traversing lists.
     * However, if the table has been resized, then all future steps
     * must traverse both the bin at the current index as well as at
     * (index + baseSize); and so on for further resizings. To
     * paranoically cope with potential sharing by users of iterators
     * across threads, iteration terminates if a bounds checks fails
     * for a table read.
     *
     * The range-based constructor enables creation of parallel
     * range-splitting traversals. (Not yet implemented.)
     */
    static class InternalIterator {
        Node next;           // the next entry to use
        Node last;           // the last entry used
        Object nextKey;      // cached key field of next
        Object nextVal;      // cached val field of next
        Node[] tab;          // current table; updated if resized
        int index;           // index of bin to use next
        int baseIndex;       // current index of initial table
        final int baseLimit; // index bound for initial table
        final int baseSize;  // initial table size

        /** Creates iterator for all entries in the table. */
        InternalIterator(Node[] tab) {
            this.tab = tab;
            baseLimit = baseSize = (tab == null) ? 0 : tab.length;
            index = baseIndex = 0;
            next = null;
            advance();
        }

        /** Creates iterator for the given range of the table */
        InternalIterator(Node[] tab, int lo, int hi) {
            this.tab = tab;
            baseSize = (tab == null) ? 0 : tab.length;
            baseLimit = (hi <= baseSize) ? hi : baseSize;
            index = baseIndex = lo;
            next = null;
            advance();
        }

        /** Advances next. See above for explanation. */
        final void advance() {
            Node e = last = next;
            outer: do {
                if (e != null)                   // pass used or skipped node
                    e = e.next;
                while (e == null) {              // get to next non-null bin
                    Node[] t; int b, i, n;       // checks must use locals
                    if ((b = baseIndex) >= baseLimit || (i = index) < 0 ||
                        (t = tab) == null || i >= (n = t.length))
                        break outer;
                    else if ((e = tabAt(t, i)) != null && e.hash < 0)
                        tab = (Node[])e.key;     // restarts due to null val
                    else                         // visit upper slots if present
                        index = (i += baseSize) < n ? i : (baseIndex = b + 1);
                }
                nextKey = e.key;
            } while ((nextVal = e.val) == null); // skip deleted or special nodes
            next = e;
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
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();
        int cap = tableSizeFor(initialCapacity);
        this.counter = new LongAdder();
        this.loadFactor = loadFactor;
        this.targetCapacity = cap;
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
        putAll(m);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return counter.sum() <= 0L; // ignore transient negative values
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        long n = counter.sum();
        return ((n < 0L) ? 0 :
                (n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
                (int)n);
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
     * specified value. Note: This method may require a full traversal
     * of the map, and is much slower than method {@code containsKey}.
     *
     * @param value value whose presence in this map is to be tested
     * @return {@code true} if this map maps one or more keys to the
     *         specified value
     * @throws NullPointerException if the specified value is null
     */
    public boolean containsValue(Object value) {
        if (value == null)
            throw new NullPointerException();
        Object v;
        InternalIterator it = new InternalIterator(table);
        while (it.next != null) {
            if ((v = it.nextVal) == value || value.equals(v))
                return true;
            it.advance();
        }
        return false;
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
        /*
         * If uninitialized, try to adjust targetCapacity to
         * accommodate the given number of elements.
         */
        if (table == null) {
            int size = m.size();
            int cap = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY :
                tableSizeFor(size + (size >>> 1));
            if (cap > targetCapacity)
                targetCapacity = cap;
        }
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * If the specified key is not already associated with a value,
     * computes its value using the given mappingFunction, and if
     * non-null, enters it into the map.  This is equivalent to
     *  <pre> {@code
     * if (map.containsKey(key))
     *   return map.get(key);
     * value = mappingFunction.map(key);
     * if (value != null)
     *   map.put(key, value);
     * return value;}</pre>
     *
     * except that the action is performed atomically.  Some attempted
     * update operations on this map by other threads may be blocked
     * while computation is in progress, so the computation should be
     * short and simple, and must not attempt to update any other
     * mappings of this Map. The most appropriate usage is to
     * construct a new object serving as an initial mapped value, or
     * memoized result, as in:
     *  <pre> {@code
     * map.computeIfAbsent(key, new MappingFunction<K, V>() {
     *   public V map(K k) { return new Value(f(k)); }});}</pre>
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
     *  <pre> {@code
     * value = mappingFunction.map(key);
     * if (value != null)
     *   map.put(key, value);
     * else
     *   value = map.get(key);
     * return value;}</pre>
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
        KeySet<K,V> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet<K,V>(this));
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
        Values<K,V> vs = values;
        return (vs != null) ? vs : (values = new Values<K,V>(this));
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
        EntrySet<K,V> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet<K,V>(this));
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        return new KeyIterator<K,V>(this);
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        return new ValueIterator<K,V>(this);
    }

    /**
     * Returns the hash code value for this {@link Map}, i.e.,
     * the sum of, for each key-value pair in the map,
     * {@code key.hashCode() ^ value.hashCode()}.
     *
     * @return the hash code value for this map
     */
    public int hashCode() {
        int h = 0;
        InternalIterator it = new InternalIterator(table);
        while (it.next != null) {
            h += it.nextKey.hashCode() ^ it.nextVal.hashCode();
            it.advance();
        }
        return h;
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
        InternalIterator it = new InternalIterator(table);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        if (it.next != null) {
            for (;;) {
                Object k = it.nextKey, v = it.nextVal;
                sb.append(k == this ? "(this Map)" : k);
                sb.append('=');
                sb.append(v == this ? "(this Map)" : v);
                it.advance();
                if (it.next == null)
                    break;
                sb.append(',').append(' ');
            }
        }
        return sb.append('}').toString();
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
        if (o != this) {
            if (!(o instanceof Map))
                return false;
            Map<?,?> m = (Map<?,?>) o;
            InternalIterator it = new InternalIterator(table);
            while (it.next != null) {
                Object val = it.nextVal;
                Object v = m.get(it.nextKey);
                if (v == null || (v != val && !v.equals(val)))
                    return false;
                it.advance();
            }
            for (Map.Entry<?,?> e : m.entrySet()) {
                Object mk, mv, v;
                if ((mk = e.getKey()) == null ||
                    (mv = e.getValue()) == null ||
                    (v = internalGet(mk)) == null ||
                    (mv != v && !mv.equals(v)))
                    return false;
            }
        }
        return true;
    }

    /* ----------------Iterators -------------- */

    /**
     * Base class for key, value, and entry iterators.  Adds a map
     * reference to InternalIterator to support Iterator.remove.
     */
    static abstract class ViewIterator<K,V> extends InternalIterator {
        final ConcurrentHashMapV8<K, V> map;
        ViewIterator(ConcurrentHashMapV8<K, V> map) {
            super(map.table);
            this.map = map;
        }

        public final void remove() {
            if (last == null)
                throw new IllegalStateException();
            map.remove(last.key);
            last = null;
        }

        public final boolean hasNext()         { return next != null; }
        public final boolean hasMoreElements() { return next != null; }
    }

    static final class KeyIterator<K,V> extends ViewIterator<K,V>
        implements Iterator<K>, Enumeration<K> {
        KeyIterator(ConcurrentHashMapV8<K, V> map) { super(map); }

        @SuppressWarnings("unchecked")
        public final K next() {
            if (next == null)
                throw new NoSuchElementException();
            Object k = nextKey;
            advance();
            return (K)k;
        }

        public final K nextElement() { return next(); }
    }

    static final class ValueIterator<K,V> extends ViewIterator<K,V>
        implements Iterator<V>, Enumeration<V> {
        ValueIterator(ConcurrentHashMapV8<K, V> map) { super(map); }

        @SuppressWarnings("unchecked")
        public final V next() {
            if (next == null)
                throw new NoSuchElementException();
            Object v = nextVal;
            advance();
            return (V)v;
        }

        public final V nextElement() { return next(); }
    }

    static final class EntryIterator<K,V> extends ViewIterator<K,V>
        implements Iterator<Map.Entry<K,V>> {
        EntryIterator(ConcurrentHashMapV8<K, V> map) { super(map); }

        @SuppressWarnings("unchecked")
        public final Map.Entry<K,V> next() {
            if (next == null)
                throw new NoSuchElementException();
            Object k = nextKey;
            Object v = nextVal;
            advance();
            return new WriteThroughEntry<K,V>(map, (K)k, (V)v);
        }
    }

    /**
     * Custom Entry class used by EntryIterator.next(), that relays
     * setValue changes to the underlying map.
     */
    static final class WriteThroughEntry<K,V> implements Map.Entry<K, V> {
        final ConcurrentHashMapV8<K, V> map;
        final K key; // non-null
        V val;       // non-null
        WriteThroughEntry(ConcurrentHashMapV8<K, V> map, K key, V val) {
            this.map = map; this.key = key; this.val = val;
        }

        public final K getKey()       { return key; }
        public final V getValue()     { return val; }
        public final int hashCode()   { return key.hashCode() ^ val.hashCode(); }
        public final String toString(){ return key + "=" + val; }

        public final boolean equals(Object o) {
            Object k, v; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    (k == key || k.equals(key)) &&
                    (v == val || v.equals(val)));
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
        public final V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = val;
            val = value;
            map.put(key, value);
            return v;
        }
    }

    /* ----------------Views -------------- */

    /*
     * These currently just extend java.util.AbstractX classes, but
     * may need a new custom base to support partitioned traversal.
     */

    static final class KeySet<K,V> extends AbstractSet<K> {
        final ConcurrentHashMapV8<K, V> map;
        KeySet(ConcurrentHashMapV8<K, V> map)   { this.map = map; }

        public final int size()                 { return map.size(); }
        public final boolean isEmpty()          { return map.isEmpty(); }
        public final void clear()               { map.clear(); }
        public final boolean contains(Object o) { return map.containsKey(o); }
        public final boolean remove(Object o)   { return map.remove(o) != null; }
        public final Iterator<K> iterator() {
            return new KeyIterator<K,V>(map);
        }
    }

    static final class Values<K,V> extends AbstractCollection<V> {
        final ConcurrentHashMapV8<K, V> map;
        Values(ConcurrentHashMapV8<K, V> map)   { this.map = map; }

        public final int size()                 { return map.size(); }
        public final boolean isEmpty()          { return map.isEmpty(); }
        public final void clear()               { map.clear(); }
        public final boolean contains(Object o) { return map.containsValue(o); }
        public final Iterator<V> iterator() {
            return new ValueIterator<K,V>(map);
        }
    }

    static final class EntrySet<K,V> extends AbstractSet<Map.Entry<K,V>> {
        final ConcurrentHashMapV8<K, V> map;
        EntrySet(ConcurrentHashMapV8<K, V> map) { this.map = map; }

        public final int size()                 { return map.size(); }
        public final boolean isEmpty()          { return map.isEmpty(); }
        public final void clear()               { map.clear(); }
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator<K,V>(map);
        }

        public final boolean contains(Object o) {
            Object k, v, r; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (r = map.get(k)) != null &&
                    (v = e.getValue()) != null &&
                    (v == r || v.equals(r)));
        }

        public final boolean remove(Object o) {
            Object k, v; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    map.remove(k, v));
        }
    }

    /* ---------------- Serialization Support -------------- */

    /**
     * Stripped-down version of helper class used in previous version,
     * declared for the sake of serialization compatibility
     */
    static class Segment<K,V> implements Serializable {
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
                segments[i] = new Segment<K,V>(DEFAULT_LOAD_FACTOR);
        }
        s.defaultWriteObject();
        InternalIterator it = new InternalIterator(table);
        while (it.next != null) {
            s.writeObject(it.nextKey);
            s.writeObject(it.nextVal);
            it.advance();
        }
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
        this.segments = null; // unneeded
        // initalize transient final fields
        UNSAFE.putObjectVolatile(this, counterOffset, new LongAdder());
        UNSAFE.putFloatVolatile(this, loadFactorOffset, DEFAULT_LOAD_FACTOR);
        this.targetCapacity = DEFAULT_CAPACITY;

        // Create all nodes, then place in table once size is known
        long size = 0L;
        Node p = null;
        for (;;) {
            K k = (K) s.readObject();
            V v = (V) s.readObject();
            if (k != null && v != null) {
                p = new Node(spread(k.hashCode()), k, v, p);
                ++size;
            }
            else
                break;
        }
        if (p != null) {
            boolean init = false;
            if (resizing == 0 &&
                UNSAFE.compareAndSwapInt(this, resizingOffset, 0, 1)) {
                try {
                    if (table == null) {
                        init = true;
                        int n;
                        if (size >= (long)(MAXIMUM_CAPACITY >>> 1))
                            n = MAXIMUM_CAPACITY;
                        else {
                            int sz = (int)size;
                            n = tableSizeFor(sz + (sz >>> 1));
                        }
                        threshold = (n - (n >>> 2)) - THRESHOLD_OFFSET;
                        Node[] tab = new Node[n];
                        int mask = n - 1;
                        while (p != null) {
                            int j = p.hash & mask;
                            Node next = p.next;
                            p.next = tabAt(tab, j);
                            setTabAt(tab, j, p);
                            p = next;
                        }
                        table = tab;
                        counter.add(size);
                    }
                } finally {
                    resizing = 0;
                }
            }
            if (!init) { // Can only happen if unsafely published.
                while (p != null) {
                    internalPut(p.key, p.val, true);
                    p = p.next;
                }
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long counterOffset;
    private static final long loadFactorOffset;
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
            loadFactorOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("loadFactor"));
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
