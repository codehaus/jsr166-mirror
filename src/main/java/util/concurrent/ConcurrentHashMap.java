/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.CountedCompleter;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.Streams;
import java.util.function.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BiFunction;

import java.util.Comparator;
import java.util.Arrays;
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
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>Retrieval operations (including {@code get}) generally do not
 * block, so may overlap with update operations (including {@code put}
 * and {@code remove}). Retrievals reflect the results of the most
 * recently <em>completed</em> update operations holding upon their
 * onset. (More formally, an update operation for a given key bears a
 * <em>happens-before</em> relation with any (non-null) retrieval for
 * that key reporting the updated value.)  For aggregate operations
 * such as {@code putAll} and {@code clear}, concurrent retrievals may
 * reflect insertion or removal of only some entries.  Similarly,
 * Iterators and Enumerations return elements reflecting the state of
 * the hash table at some point at or since the creation of the
 * iterator/enumeration.  They do <em>not</em> throw {@link
 * ConcurrentModificationException}.  However, iterators are designed
 * to be used by only one thread at a time.  Bear in mind that the
 * results of aggregate status methods including {@code size}, {@code
 * isEmpty}, and {@code containsValue} are typically useful only when
 * a map is not undergoing concurrent updates in other threads.
 * Otherwise the results of these methods reflect transient states
 * that may be adequate for monitoring or estimation purposes, but not
 * for program control.
 *
 * <p>The table is dynamically expanded when there are too many
 * collisions (i.e., keys that have distinct hash codes but fall into
 * the same slot modulo the table size), with the expected average
 * effect of maintaining roughly two bins per mapping (corresponding
 * to a 0.75 load factor threshold for resizing). There may be much
 * variance around this average as mappings are added and removed, but
 * overall, this maintains a commonly accepted time/space tradeoff for
 * hash tables.  However, resizing this or any other kind of hash
 * table may be a relatively slow operation. When possible, it is a
 * good idea to provide a size estimate as an optional {@code
 * initialCapacity} constructor argument. An additional optional
 * {@code loadFactor} constructor argument provides a further means of
 * customizing initial table capacity by specifying the table density
 * to be used in calculating the amount of space to allocate for the
 * given number of elements.  Also, for compatibility with previous
 * versions of this class, constructors may optionally specify an
 * expected {@code concurrencyLevel} as an additional hint for
 * internal sizing.  Note that using many keys with exactly the same
 * {@code hashCode()} is a sure way to slow down performance of any
 * hash table.
 *
 * <p>A {@link Set} projection of a ConcurrentHashMap may be created
 * (using {@link #newKeySet()} or {@link #newKeySet(int)}), or viewed
 * (using {@link #keySet(Object)} when only keys are of interest, and the
 * mapped values are (perhaps transiently) not used or all take the
 * same mapping value.
 *
 * <p>A ConcurrentHashMap can be used as scalable frequency map (a
 * form of histogram or multiset) by using {@link
 * java.util.concurrent.atomic.LongAdder} values and initializing via
 * {@link #computeIfAbsent computeIfAbsent}. For example, to add a count
 * to a {@code ConcurrentHashMap<String,LongAdder> freqs}, you can use
 * {@code freqs.computeIfAbsent(k -> new LongAdder()).increment();}
 *
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}
 * interfaces.
 *
 * <p>Like {@link Hashtable} but unlike {@link HashMap}, this class
 * does <em>not</em> allow {@code null} to be used as a key or value.
 *
 * <p>ConcurrentHashMaps support sequential and parallel operations
 * bulk operations. (Parallel forms use the {@link
 * ForkJoinPool#commonPool()}). Tasks that may be used in other
 * contexts are available in class {@link ForkJoinTasks}. These
 * operations are designed to be safely, and often sensibly, applied
 * even with maps that are being concurrently updated by other
 * threads; for example, when computing a snapshot summary of the
 * values in a shared registry.  There are three kinds of operation,
 * each with four forms, accepting functions with Keys, Values,
 * Entries, and (Key, Value) arguments and/or return values. Because
 * the elements of a ConcurrentHashMap are not ordered in any
 * particular way, and may be processed in different orders in
 * different parallel executions, the correctness of supplied
 * functions should not depend on any ordering, or on any other
 * objects or values that may transiently change while computation is
 * in progress; and except for forEach actions, should ideally be
 * side-effect-free.
 *
 * <ul>
 * <li> forEach: Perform a given action on each element.
 * A variant form applies a given transformation on each element
 * before performing the action.</li>
 *
 * <li> search: Return the first available non-null result of
 * applying a given function on each element; skipping further
 * search when a result is found.</li>
 *
 * <li> reduce: Accumulate each element.  The supplied reduction
 * function cannot rely on ordering (more formally, it should be
 * both associative and commutative).  There are five variants:
 *
 * <ul>
 *
 * <li> Plain reductions. (There is not a form of this method for
 * (key, value) function arguments since there is no corresponding
 * return type.)</li>
 *
 * <li> Mapped reductions that accumulate the results of a given
 * function applied to each element.</li>
 *
 * <li> Reductions to scalar doubles, longs, and ints, using a
 * given basis value.</li>
 *
 * </ul>
 * </li>
 * </ul>
 *
 * <p>The concurrency properties of bulk operations follow
 * from those of ConcurrentHashMap: Any non-null result returned
 * from {@code get(key)} and related access methods bears a
 * happens-before relation with the associated insertion or
 * update.  The result of any bulk operation reflects the
 * composition of these per-element relations (but is not
 * necessarily atomic with respect to the map as a whole unless it
 * is somehow known to be quiescent).  Conversely, because keys
 * and values in the map are never null, null serves as a reliable
 * atomic indicator of the current lack of any result.  To
 * maintain this property, null serves as an implicit basis for
 * all non-scalar reduction operations. For the double, long, and
 * int versions, the basis should be one that, when combined with
 * any other value, returns that other value (more formally, it
 * should be the identity element for the reduction). Most common
 * reductions have these properties; for example, computing a sum
 * with basis 0 or a minimum with basis MAX_VALUE.
 *
 * <p>Search and transformation functions provided as arguments
 * should similarly return null to indicate the lack of any result
 * (in which case it is not used). In the case of mapped
 * reductions, this also enables transformations to serve as
 * filters, returning null (or, in the case of primitive
 * specializations, the identity basis) if the element should not
 * be combined. You can create compound transformations and
 * filterings by composing them yourself under this "null means
 * there is nothing there now" rule before using them in search or
 * reduce operations.
 *
 * <p>Methods accepting and/or returning Entry arguments maintain
 * key-value associations. They may be useful for example when
 * finding the key for the greatest value. Note that "plain" Entry
 * arguments can be supplied using {@code new
 * AbstractMap.SimpleEntry(k,v)}.
 *
 * <p>Bulk operations may complete abruptly, throwing an
 * exception encountered in the application of a supplied
 * function. Bear in mind when handling such exceptions that other
 * concurrently executing functions could also have thrown
 * exceptions, or would have done so if the first exception had
 * not occurred.
 *
 * <p>Speedups for parallel compared to sequential forms are common
 * but not guaranteed.  Parallel operations involving brief functions
 * on small maps may execute more slowly than sequential forms if the
 * underlying work to parallelize the computation is more expensive
 * than the computation itself.  Similarly, parallelization may not
 * lead to much actual parallelism if all processors are busy
 * performing unrelated tasks.
 *
 * <p>All arguments to all task methods must be non-null.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ConcurrentHashMap<K,V>
    implements ConcurrentMap<K,V>, Serializable {
    private static final long serialVersionUID = 7249069246763182397L;

    /*
     * Overview:
     *
     * The primary design goal of this hash table is to maintain
     * concurrent readability (typically method get(), but also
     * iterators and related methods) while minimizing update
     * contention. Secondary goals are to keep space consumption about
     * the same or better than java.util.HashMap, and to support high
     * initial insertion rates on an empty table by many threads.
     *
     * Each key-value mapping is held in a Node.  Because Node key
     * fields can contain special values, they are defined using plain
     * Object types (not type "K"). This leads to a lot of explicit
     * casting (and many explicit warning suppressions to tell
     * compilers not to complain about it). It also allows some of the
     * public methods to be factored into a smaller number of internal
     * methods (although sadly not so for the five variants of
     * put-related operations). The validation-based approach
     * explained below leads to a lot of code sprawl because
     * retry-control precludes factoring into smaller methods.
     *
     * The table is lazily initialized to a power-of-two size upon the
     * first insertion.  Each bin in the table normally contains a
     * list of Nodes (most often, the list has only zero or one Node).
     * Table accesses require volatile/atomic reads, writes, and
     * CASes.  Because there is no other way to arrange this without
     * adding further indirections, we use intrinsics
     * (sun.misc.Unsafe) operations.  The lists of nodes within bins
     * are always accurately traversable under volatile reads, so long
     * as lookups check hash code and non-nullness of value before
     * checking key equality.
     *
     * We use the top (sign) bit of Node hash fields for control
     * purposes -- it is available anyway because of addressing
     * constraints.  Nodes with negative hash fields are forwarding
     * nodes to either TreeBins or resized tables.  The lower 31 bits
     * of each normal Node's hash field contain a transformation of
     * the key's hash code.
     *
     * Insertion (via put or its variants) of the first node in an
     * empty bin is performed by just CASing it to the bin.  This is
     * by far the most common case for put operations under most
     * key/hash distributions.  Other update operations (insert,
     * delete, and replace) require locks.  We do not want to waste
     * the space required to associate a distinct lock object with
     * each bin, so instead use the first node of a bin list itself as
     * a lock. Locking support for these locks relies on builtin
     * "synchronized" monitors.
     *
     * Using the first node of a list as a lock does not by itself
     * suffice though: When a node is locked, any update must first
     * validate that it is still the first node after locking it, and
     * retry if not. Because new nodes are always appended to lists,
     * once a node is first in a bin, it remains first until deleted
     * or the bin becomes invalidated (upon resizing).  However,
     * operations that only conditionally update may inspect nodes
     * until the point of update. This is a converse of sorts to the
     * lazy locking technique described by Herlihy & Shavit.
     *
     * The main disadvantage of per-bin locks is that other update
     * operations on other nodes in a bin list protected by the same
     * lock can stall, for example when user equals() or mapping
     * functions take a long time.  However, statistically, under
     * random hash codes, this is not a common problem.  Ideally, the
     * frequency of nodes in bins follows a Poisson distribution
     * (http://en.wikipedia.org/wiki/Poisson_distribution) with a
     * parameter of about 0.5 on average, given the resizing threshold
     * of 0.75, although with a large variance because of resizing
     * granularity. Ignoring variance, the expected occurrences of
     * list size k are (exp(-0.5) * pow(0.5, k) / factorial(k)). The
     * first values are:
     *
     * 0:    0.60653066
     * 1:    0.30326533
     * 2:    0.07581633
     * 3:    0.01263606
     * 4:    0.00157952
     * 5:    0.00015795
     * 6:    0.00001316
     * 7:    0.00000094
     * 8:    0.00000006
     * more: less than 1 in ten million
     *
     * Lock contention probability for two threads accessing distinct
     * elements is roughly 1 / (8 * #elements) under random hashes.
     *
     * Actual hash code distributions encountered in practice
     * sometimes deviate significantly from uniform randomness.  This
     * includes the case when N > (1<<30), so some keys MUST collide.
     * Similarly for dumb or hostile usages in which multiple keys are
     * designed to have identical hash codes. Also, although we guard
     * against the worst effects of this (see method spread), sets of
     * hashes may differ only in bits that do not impact their bin
     * index for a given power-of-two mask.  So we use a secondary
     * strategy that applies when the number of nodes in a bin exceeds
     * a threshold, and at least one of the keys implements
     * Comparable.  These TreeBins use a balanced tree to hold nodes
     * (a specialized form of red-black trees), bounding search time
     * to O(log N).  Each search step in a TreeBin is around twice as
     * slow as in a regular list, but given that N cannot exceed
     * (1<<64) (before running out of addresses) this bounds search
     * steps, lock hold times, etc, to reasonable constants (roughly
     * 100 nodes inspected per operation worst case) so long as keys
     * are Comparable (which is very common -- String, Long, etc).
     * TreeBin nodes (TreeNodes) also maintain the same "next"
     * traversal pointers as regular nodes, so can be traversed in
     * iterators in the same way.
     *
     * The table is resized when occupancy exceeds a percentage
     * threshold (nominally, 0.75, but see below).  Any thread
     * noticing an overfull bin may assist in resizing after the
     * initiating thread allocates and sets up the replacement
     * array. However, rather than stalling, these other threads may
     * proceed with insertions etc.  The use of TreeBins shields us
     * from the worst case effects of overfilling while resizes are in
     * progress.  Resizing proceeds by transferring bins, one by one,
     * from the table to the next table. To enable concurrency, the
     * next table must be (incrementally) prefilled with place-holders
     * serving as reverse forwarders to the old table.  Because we are
     * using power-of-two expansion, the elements from each bin must
     * either stay at same index, or move with a power of two
     * offset. We eliminate unnecessary node creation by catching
     * cases where old nodes can be reused because their next fields
     * won't change.  On average, only about one-sixth of them need
     * cloning when a table doubles. The nodes they replace will be
     * garbage collectable as soon as they are no longer referenced by
     * any reader thread that may be in the midst of concurrently
     * traversing table.  Upon transfer, the old table bin contains
     * only a special forwarding node (with hash field "MOVED") that
     * contains the next table as its key. On encountering a
     * forwarding node, access and update operations restart, using
     * the new table.
     *
     * Each bin transfer requires its bin lock, which can stall
     * waiting for locks while resizing. However, because other
     * threads can join in and help resize rather than contend for
     * locks, average aggregate waits become shorter as resizing
     * progresses.  The transfer operation must also ensure that all
     * accessible bins in both the old and new table are usable by any
     * traversal.  This is arranged by proceeding from the last bin
     * (table.length - 1) up towards the first.  Upon seeing a
     * forwarding node, traversals (see class Traverser) arrange to
     * move to the new table without revisiting nodes.  However, to
     * ensure that no intervening nodes are skipped, bin splitting can
     * only begin after the associated reverse-forwarders are in
     * place.
     *
     * The traversal scheme also applies to partial traversals of
     * ranges of bins (via an alternate Traverser constructor)
     * to support partitioned aggregate operations.  Also, read-only
     * operations give up if ever forwarded to a null table, which
     * provides support for shutdown-style clearing, which is also not
     * currently implemented.
     *
     * Lazy table initialization minimizes footprint until first use,
     * and also avoids resizings when the first operation is from a
     * putAll, constructor with map argument, or deserialization.
     * These cases attempt to override the initial capacity settings,
     * but harmlessly fail to take effect in cases of races.
     *
     * The element count is maintained using a specialization of
     * LongAdder. We need to incorporate a specialization rather than
     * just use a LongAdder in order to access implicit
     * contention-sensing that leads to creation of multiple
     * Cells.  The counter mechanics avoid contention on
     * updates but can encounter cache thrashing if read too
     * frequently during concurrent access. To avoid reading so often,
     * resizing under contention is attempted only upon adding to a
     * bin already holding two or more nodes. Under uniform hash
     * distributions, the probability of this occurring at threshold
     * is around 13%, meaning that only about 1 in 8 puts check
     * threshold (and after resizing, many fewer do so). The bulk
     * putAll operation further reduces contention by only committing
     * count updates upon these size checks.
     *
     * Maintaining API and serialization compatibility with previous
     * versions of this class introduces several oddities. Mainly: We
     * leave untouched but unused constructor arguments refering to
     * concurrencyLevel. We accept a loadFactor constructor argument,
     * but apply it only to initial table capacity (which is the only
     * time that we can guarantee to honor it.) We also declare an
     * unused "Segment" class that is instantiated in minimal form
     * only when serializing.
     */

    /* ---------------- Constants -------------- */

    /**
     * The largest possible table capacity.  This value must be
     * exactly 1<<30 to stay within Java array allocation and indexing
     * bounds for power of two table sizes, and is further required
     * because the top two bits of 32bit hash fields are used for
     * control purposes.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The default initial table capacity.  Must be a power of 2
     * (i.e., at least 1) and at most MAXIMUM_CAPACITY.
     */
    private static final int DEFAULT_CAPACITY = 16;

    /**
     * The largest possible (non-power of two) array size.
     * Needed by toArray and related methods.
     */
    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * The default concurrency level for this table. Unused but
     * defined for compatibility with previous versions of this class.
     */
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    /**
     * The load factor for this table. Overrides of this value in
     * constructors affect only the initial table capacity.  The
     * actual floating point value isn't normally used -- it is
     * simpler to use expressions such as {@code n - (n >>> 2)} for
     * the associated resizing threshold.
     */
    private static final float LOAD_FACTOR = 0.75f;

    /**
     * The bin count threshold for using a tree rather than list for a
     * bin.  The value reflects the approximate break-even point for
     * using tree-based operations.
     */
    private static final int TREE_THRESHOLD = 8;

    /**
     * Minimum number of rebinnings per transfer step. Ranges are
     * subdivided to allow multiple resizer threads.  This value
     * serves as a lower bound to avoid resizers encountering
     * excessive memory contention.  The value should be at least
     * DEFAULT_CAPACITY.
     */
    private static final int MIN_TRANSFER_STRIDE = 16;

    /*
     * Encodings for Node hash fields. See above for explanation.
     */
    static final int MOVED     = 0x80000000; // hash field for forwarding nodes
    static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash

    /** Number of CPUS, to place bounds on some sizings */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /* ---------------- Counters -------------- */

    // Adapted from LongAdder and Striped64.
    // See their internal docs for explanation.

    // A padded cell for distributing counts
    static final class Cell {
        volatile long p0, p1, p2, p3, p4, p5, p6;
        volatile long value;
        volatile long q0, q1, q2, q3, q4, q5, q6;
        Cell(long x) { value = x; }
    }

    /* ---------------- Fields -------------- */

    /**
     * The array of bins. Lazily initialized upon first insertion.
     * Size is always a power of two. Accessed directly by iterators.
     */
    transient volatile Node<V>[] table;

    /**
     * The next table to use; non-null only while resizing.
     */
    private transient volatile Node<V>[] nextTable;

    /**
     * Base counter value, used mainly when there is no contention,
     * but also as a fallback during table initialization
     * races. Updated via CAS.
     */
    private transient volatile long baseCount;

    /**
     * Table initialization and resizing control.  When negative, the
     * table is being initialized or resized: -1 for initialization,
     * else -(1 + the number of active resizing threads).  Otherwise,
     * when table is null, holds the initial table size to use upon
     * creation, or 0 for default. After initialization, holds the
     * next element count value upon which to resize the table.
     */
    private transient volatile int sizeCtl;

    /**
     * The next table index (plus one) to split while resizing.
     */
    private transient volatile int transferIndex;

    /**
     * The least available table index to split while resizing.
     */
    private transient volatile int transferOrigin;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     */
    private transient volatile int cellsBusy;

    /**
     * Table of counter cells. When non-null, size is a power of 2.
     */
    private transient volatile Cell[] counterCells;

    // views
    private transient KeySetView<K,V> keySet;
    private transient ValuesView<K,V> values;
    private transient EntrySetView<K,V> entrySet;

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

    @SuppressWarnings("unchecked") static final <V> Node<V> tabAt
        (Node<V>[] tab, int i) { // used by Traverser
        return (Node<V>)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
    }

    private static final <V> boolean casTabAt
        (Node<V>[] tab, int i, Node<V> c, Node<V> v) {
        return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
    }

    private static final <V> void setTabAt
        (Node<V>[] tab, int i, Node<V> v) {
        U.putObjectVolatile(tab, ((long)i << ASHIFT) + ABASE, v);
    }

    /* ---------------- Nodes -------------- */

    /**
     * Key-value entry. Note that this is never exported out as a
     * user-visible Map.Entry (see MapEntry below). Nodes with a hash
     * field of MOVED are special, and do not contain user keys or
     * values.  Otherwise, keys are never null, and null val fields
     * indicate that a node is in the process of being deleted or
     * created. For purposes of read-only access, a key may be read
     * before a val, but can only be used after checking val to be
     * non-null.
     */
    static class Node<V> {
        final int hash;
        final Object key;
        volatile V val;
        volatile Node<V> next;

        Node(int hash, Object key, V val, Node<V> next) {
            this.hash = hash;
            this.key = key;
            this.val = val;
            this.next = next;
        }
    }

    /* ---------------- TreeBins -------------- */

    /**
     * Nodes for use in TreeBins
     */
    static final class TreeNode<V> extends Node<V> {
        TreeNode<V> parent;  // red-black tree links
        TreeNode<V> left;
        TreeNode<V> right;
        TreeNode<V> prev;    // needed to unlink next upon deletion
        boolean red;

        TreeNode(int hash, Object key, V val, Node<V> next, TreeNode<V> parent) {
            super(hash, key, val, next);
            this.parent = parent;
        }
    }

    /**
     * A specialized form of red-black tree for use in bins
     * whose size exceeds a threshold.
     *
     * TreeBins use a special form of comparison for search and
     * related operations (which is the main reason we cannot use
     * existing collections such as TreeMaps). TreeBins contain
     * Comparable elements, but may contain others, as well as
     * elements that are Comparable but not necessarily Comparable<T>
     * for the same T, so we cannot invoke compareTo among them. To
     * handle this, the tree is ordered primarily by hash value, then
     * by getClass().getName() order, and then by Comparator order
     * among elements of the same class.  On lookup at a node, if
     * elements are not comparable or compare as 0, both left and
     * right children may need to be searched in the case of tied hash
     * values. (This corresponds to the full list search that would be
     * necessary if all elements were non-Comparable and had tied
     * hashes.)  The red-black balancing code is updated from
     * pre-jdk-collections
     * (http://gee.cs.oswego.edu/dl/classes/collections/RBCell.java)
     * based in turn on Cormen, Leiserson, and Rivest "Introduction to
     * Algorithms" (CLR).
     *
     * TreeBins also maintain a separate locking discipline than
     * regular bins. Because they are forwarded via special MOVED
     * nodes at bin heads (which can never change once established),
     * we cannot use those nodes as locks. Instead, TreeBin
     * extends AbstractQueuedSynchronizer to support a simple form of
     * read-write lock. For update operations and table validation,
     * the exclusive form of lock behaves in the same way as bin-head
     * locks. However, lookups use shared read-lock mechanics to allow
     * multiple readers in the absence of writers.  Additionally,
     * these lookups do not ever block: While the lock is not
     * available, they proceed along the slow traversal path (via
     * next-pointers) until the lock becomes available or the list is
     * exhausted, whichever comes first. (These cases are not fast,
     * but maximize aggregate expected throughput.)  The AQS mechanics
     * for doing this are straightforward.  The lock state is held as
     * AQS getState().  Read counts are negative; the write count (1)
     * is positive.  There are no signalling preferences among readers
     * and writers. Since we don't need to export full Lock API, we
     * just override the minimal AQS methods and use them directly.
     */
    static final class TreeBin<V> extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 2249069246763182397L;
        transient TreeNode<V> root;  // root of tree
        transient TreeNode<V> first; // head of next-pointer list

        /* AQS overrides */
        public final boolean isHeldExclusively() { return getState() > 0; }
        public final boolean tryAcquire(int ignore) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
        public final boolean tryRelease(int ignore) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }
        public final int tryAcquireShared(int ignore) {
            for (int c;;) {
                if ((c = getState()) > 0)
                    return -1;
                if (compareAndSetState(c, c -1))
                    return 1;
            }
        }
        public final boolean tryReleaseShared(int ignore) {
            int c;
            do {} while (!compareAndSetState(c = getState(), c + 1));
            return c == -1;
        }

        /** From CLR */
        private void rotateLeft(TreeNode<V> p) {
            if (p != null) {
                TreeNode<V> r = p.right, pp, rl;
                if ((rl = p.right = r.left) != null)
                    rl.parent = p;
                if ((pp = r.parent = p.parent) == null)
                    root = r;
                else if (pp.left == p)
                    pp.left = r;
                else
                    pp.right = r;
                r.left = p;
                p.parent = r;
            }
        }

        /** From CLR */
        private void rotateRight(TreeNode<V> p) {
            if (p != null) {
                TreeNode<V> l = p.left, pp, lr;
                if ((lr = p.left = l.right) != null)
                    lr.parent = p;
                if ((pp = l.parent = p.parent) == null)
                    root = l;
                else if (pp.right == p)
                    pp.right = l;
                else
                    pp.left = l;
                l.right = p;
                p.parent = l;
            }
        }

        /**
         * Returns the TreeNode (or null if not found) for the given key
         * starting at given root.
         */
        @SuppressWarnings("unchecked") final TreeNode<V> getTreeNode
            (int h, Object k, TreeNode<V> p) {
            Class<?> c = k.getClass();
            while (p != null) {
                int dir, ph;  Object pk; Class<?> pc;
                if ((ph = p.hash) == h) {
                    if ((pk = p.key) == k || k.equals(pk))
                        return p;
                    if (c != (pc = pk.getClass()) ||
                        !(k instanceof Comparable) ||
                        (dir = ((Comparable)k).compareTo((Comparable)pk)) == 0) {
                        if ((dir = (c == pc) ? 0 :
                             c.getName().compareTo(pc.getName())) == 0) {
                            TreeNode<V> r = null, pl, pr; // check both sides
                            if ((pr = p.right) != null && h >= pr.hash &&
                                (r = getTreeNode(h, k, pr)) != null)
                                return r;
                            else if ((pl = p.left) != null && h <= pl.hash)
                                dir = -1;
                            else // nothing there
                                return null;
                        }
                    }
                }
                else
                    dir = (h < ph) ? -1 : 1;
                p = (dir > 0) ? p.right : p.left;
            }
            return null;
        }

        /**
         * Wrapper for getTreeNode used by CHM.get. Tries to obtain
         * read-lock to call getTreeNode, but during failure to get
         * lock, searches along next links.
         */
        final V getValue(int h, Object k) {
            Node<V> r = null;
            int c = getState(); // Must read lock state first
            for (Node<V> e = first; e != null; e = e.next) {
                if (c <= 0 && compareAndSetState(c, c - 1)) {
                    try {
                        r = getTreeNode(h, k, root);
                    } finally {
                        releaseShared(0);
                    }
                    break;
                }
                else if (e.hash == h && k.equals(e.key)) {
                    r = e;
                    break;
                }
                else
                    c = getState();
            }
            return r == null ? null : r.val;
        }

        /**
         * Finds or adds a node.
         * @return null if added
         */
        @SuppressWarnings("unchecked") final TreeNode<V> putTreeNode
            (int h, Object k, V v) {
            Class<?> c = k.getClass();
            TreeNode<V> pp = root, p = null;
            int dir = 0;
            while (pp != null) { // find existing node or leaf to insert at
                int ph;  Object pk; Class<?> pc;
                p = pp;
                if ((ph = p.hash) == h) {
                    if ((pk = p.key) == k || k.equals(pk))
                        return p;
                    if (c != (pc = pk.getClass()) ||
                        !(k instanceof Comparable) ||
                        (dir = ((Comparable)k).compareTo((Comparable)pk)) == 0) {
                        TreeNode<V> s = null, r = null, pr;
                        if ((dir = (c == pc) ? 0 :
                             c.getName().compareTo(pc.getName())) == 0) {
                            if ((pr = p.right) != null && h >= pr.hash &&
                                (r = getTreeNode(h, k, pr)) != null)
                                return r;
                            else // continue left
                                dir = -1;
                        }
                        else if ((pr = p.right) != null && h >= pr.hash)
                            s = pr;
                        if (s != null && (r = getTreeNode(h, k, s)) != null)
                            return r;
                    }
                }
                else
                    dir = (h < ph) ? -1 : 1;
                pp = (dir > 0) ? p.right : p.left;
            }

            TreeNode<V> f = first;
            TreeNode<V> x = first = new TreeNode<V>(h, k, v, f, p);
            if (p == null)
                root = x;
            else { // attach and rebalance; adapted from CLR
                TreeNode<V> xp, xpp;
                if (f != null)
                    f.prev = x;
                if (dir <= 0)
                    p.left = x;
                else
                    p.right = x;
                x.red = true;
                while (x != null && (xp = x.parent) != null && xp.red &&
                       (xpp = xp.parent) != null) {
                    TreeNode<V> xppl = xpp.left;
                    if (xp == xppl) {
                        TreeNode<V> y = xpp.right;
                        if (y != null && y.red) {
                            y.red = false;
                            xp.red = false;
                            xpp.red = true;
                            x = xpp;
                        }
                        else {
                            if (x == xp.right) {
                                rotateLeft(x = xp);
                                xpp = (xp = x.parent) == null ? null : xp.parent;
                            }
                            if (xp != null) {
                                xp.red = false;
                                if (xpp != null) {
                                    xpp.red = true;
                                    rotateRight(xpp);
                                }
                            }
                        }
                    }
                    else {
                        TreeNode<V> y = xppl;
                        if (y != null && y.red) {
                            y.red = false;
                            xp.red = false;
                            xpp.red = true;
                            x = xpp;
                        }
                        else {
                            if (x == xp.left) {
                                rotateRight(x = xp);
                                xpp = (xp = x.parent) == null ? null : xp.parent;
                            }
                            if (xp != null) {
                                xp.red = false;
                                if (xpp != null) {
                                    xpp.red = true;
                                    rotateLeft(xpp);
                                }
                            }
                        }
                    }
                }
                TreeNode<V> r = root;
                if (r != null && r.red)
                    r.red = false;
            }
            return null;
        }

        /**
         * Removes the given node, that must be present before this
         * call.  This is messier than typical red-black deletion code
         * because we cannot swap the contents of an interior node
         * with a leaf successor that is pinned by "next" pointers
         * that are accessible independently of lock. So instead we
         * swap the tree linkages.
         */
        final void deleteTreeNode(TreeNode<V> p) {
            TreeNode<V> next = (TreeNode<V>)p.next; // unlink traversal pointers
            TreeNode<V> pred = p.prev;
            if (pred == null)
                first = next;
            else
                pred.next = next;
            if (next != null)
                next.prev = pred;
            TreeNode<V> replacement;
            TreeNode<V> pl = p.left;
            TreeNode<V> pr = p.right;
            if (pl != null && pr != null) {
                TreeNode<V> s = pr, sl;
                while ((sl = s.left) != null) // find successor
                    s = sl;
                boolean c = s.red; s.red = p.red; p.red = c; // swap colors
                TreeNode<V> sr = s.right;
                TreeNode<V> pp = p.parent;
                if (s == pr) { // p was s's direct parent
                    p.parent = s;
                    s.right = p;
                }
                else {
                    TreeNode<V> sp = s.parent;
                    if ((p.parent = sp) != null) {
                        if (s == sp.left)
                            sp.left = p;
                        else
                            sp.right = p;
                    }
                    if ((s.right = pr) != null)
                        pr.parent = s;
                }
                p.left = null;
                if ((p.right = sr) != null)
                    sr.parent = p;
                if ((s.left = pl) != null)
                    pl.parent = s;
                if ((s.parent = pp) == null)
                    root = s;
                else if (p == pp.left)
                    pp.left = s;
                else
                    pp.right = s;
                replacement = sr;
            }
            else
                replacement = (pl != null) ? pl : pr;
            TreeNode<V> pp = p.parent;
            if (replacement == null) {
                if (pp == null) {
                    root = null;
                    return;
                }
                replacement = p;
            }
            else {
                replacement.parent = pp;
                if (pp == null)
                    root = replacement;
                else if (p == pp.left)
                    pp.left = replacement;
                else
                    pp.right = replacement;
                p.left = p.right = p.parent = null;
            }
            if (!p.red) { // rebalance, from CLR
                TreeNode<V> x = replacement;
                while (x != null) {
                    TreeNode<V> xp, xpl;
                    if (x.red || (xp = x.parent) == null) {
                        x.red = false;
                        break;
                    }
                    if (x == (xpl = xp.left)) {
                        TreeNode<V> sib = xp.right;
                        if (sib != null && sib.red) {
                            sib.red = false;
                            xp.red = true;
                            rotateLeft(xp);
                            sib = (xp = x.parent) == null ? null : xp.right;
                        }
                        if (sib == null)
                            x = xp;
                        else {
                            TreeNode<V> sl = sib.left, sr = sib.right;
                            if ((sr == null || !sr.red) &&
                                (sl == null || !sl.red)) {
                                sib.red = true;
                                x = xp;
                            }
                            else {
                                if (sr == null || !sr.red) {
                                    if (sl != null)
                                        sl.red = false;
                                    sib.red = true;
                                    rotateRight(sib);
                                    sib = (xp = x.parent) == null ?
                                        null : xp.right;
                                }
                                if (sib != null) {
                                    sib.red = (xp == null) ? false : xp.red;
                                    if ((sr = sib.right) != null)
                                        sr.red = false;
                                }
                                if (xp != null) {
                                    xp.red = false;
                                    rotateLeft(xp);
                                }
                                x = root;
                            }
                        }
                    }
                    else { // symmetric
                        TreeNode<V> sib = xpl;
                        if (sib != null && sib.red) {
                            sib.red = false;
                            xp.red = true;
                            rotateRight(xp);
                            sib = (xp = x.parent) == null ? null : xp.left;
                        }
                        if (sib == null)
                            x = xp;
                        else {
                            TreeNode<V> sl = sib.left, sr = sib.right;
                            if ((sl == null || !sl.red) &&
                                (sr == null || !sr.red)) {
                                sib.red = true;
                                x = xp;
                            }
                            else {
                                if (sl == null || !sl.red) {
                                    if (sr != null)
                                        sr.red = false;
                                    sib.red = true;
                                    rotateLeft(sib);
                                    sib = (xp = x.parent) == null ?
                                        null : xp.left;
                                }
                                if (sib != null) {
                                    sib.red = (xp == null) ? false : xp.red;
                                    if ((sl = sib.left) != null)
                                        sl.red = false;
                                }
                                if (xp != null) {
                                    xp.red = false;
                                    rotateRight(xp);
                                }
                                x = root;
                            }
                        }
                    }
                }
            }
            if (p == replacement && (pp = p.parent) != null) {
                if (p == pp.left) // detach pointers
                    pp.left = null;
                else if (p == pp.right)
                    pp.right = null;
                p.parent = null;
            }
        }
    }

    /* ---------------- Collision reduction methods -------------- */

    /**
     * Spreads higher bits to lower, and also forces top bit to 0.
     * Because the table uses power-of-two masking, sets of hashes
     * that vary only in bits above the current mask will always
     * collide. (Among known examples are sets of Float keys holding
     * consecutive whole numbers in small tables.)  To counter this,
     * we apply a transform that spreads the impact of higher bits
     * downward. There is a tradeoff between speed, utility, and
     * quality of bit-spreading. Because many common sets of hashes
     * are already reasonably distributed across bits (so don't benefit
     * from spreading), and because we use trees to handle large sets
     * of collisions in bins, we don't need excessively high quality.
     */
    private static final int spread(int h) {
        h ^= (h >>> 18) ^ (h >>> 12);
        return (h ^ (h >>> 10)) & HASH_BITS;
    }

    /**
     * Replaces a list bin with a tree bin if key is comparable.  Call
     * only when locked.
     */
    private final void replaceWithTreeBin(Node<V>[] tab, int index, Object key) {
        if (key instanceof Comparable) {
            TreeBin<V> t = new TreeBin<V>();
            for (Node<V> e = tabAt(tab, index); e != null; e = e.next)
                t.putTreeNode(e.hash, e.key, e.val);
            setTabAt(tab, index, new Node<V>(MOVED, t, null, null));
        }
    }

    /* ---------------- Internal access and update methods -------------- */

    /** Implementation for get and containsKey */
    @SuppressWarnings("unchecked") private final V internalGet(Object k) {
        int h = spread(k.hashCode());
        retry: for (Node<V>[] tab = table; tab != null;) {
            Node<V> e; Object ek; V ev; int eh; // locals to read fields once
            for (e = tabAt(tab, (tab.length - 1) & h); e != null; e = e.next) {
                if ((eh = e.hash) < 0) {
                    if ((ek = e.key) instanceof TreeBin)  // search TreeBin
                        return ((TreeBin<V>)ek).getValue(h, k);
                    else {                      // restart with new table
                        tab = (Node<V>[])ek;
                        continue retry;
                    }
                }
                else if (eh == h && (ev = e.val) != null &&
                         ((ek = e.key) == k || k.equals(ek)))
                    return ev;
            }
            break;
        }
        return null;
    }

    /**
     * Implementation for the four public remove/replace methods:
     * Replaces node value with v, conditional upon match of cv if
     * non-null.  If resulting value is null, delete.
     */
    @SuppressWarnings("unchecked") private final V internalReplace
        (Object k, V v, Object cv) {
        int h = spread(k.hashCode());
        V oldVal = null;
        for (Node<V>[] tab = table;;) {
            Node<V> f; int i, fh; Object fk;
            if (tab == null ||
                (f = tabAt(tab, i = (tab.length - 1) & h)) == null)
                break;
            else if ((fh = f.hash) < 0) {
                if ((fk = f.key) instanceof TreeBin) {
                    TreeBin<V> t = (TreeBin<V>)fk;
                    boolean validated = false;
                    boolean deleted = false;
                    t.acquire(0);
                    try {
                        if (tabAt(tab, i) == f) {
                            validated = true;
                            TreeNode<V> p = t.getTreeNode(h, k, t.root);
                            if (p != null) {
                                V pv = p.val;
                                if (cv == null || cv == pv || cv.equals(pv)) {
                                    oldVal = pv;
                                    if ((p.val = v) == null) {
                                        deleted = true;
                                        t.deleteTreeNode(p);
                                    }
                                }
                            }
                        }
                    } finally {
                        t.release(0);
                    }
                    if (validated) {
                        if (deleted)
                            addCount(-1L, -1);
                        break;
                    }
                }
                else
                    tab = (Node<V>[])fk;
            }
            else if (fh != h && f.next == null) // precheck
                break;                          // rules out possible existence
            else {
                boolean validated = false;
                boolean deleted = false;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        validated = true;
                        for (Node<V> e = f, pred = null;;) {
                            Object ek; V ev;
                            if (e.hash == h &&
                                ((ev = e.val) != null) &&
                                ((ek = e.key) == k || k.equals(ek))) {
                                if (cv == null || cv == ev || cv.equals(ev)) {
                                    oldVal = ev;
                                    if ((e.val = v) == null) {
                                        deleted = true;
                                        Node<V> en = e.next;
                                        if (pred != null)
                                            pred.next = en;
                                        else
                                            setTabAt(tab, i, en);
                                    }
                                }
                                break;
                            }
                            pred = e;
                            if ((e = e.next) == null)
                                break;
                        }
                    }
                }
                if (validated) {
                    if (deleted)
                        addCount(-1L, -1);
                    break;
                }
            }
        }
        return oldVal;
    }

    /*
     * Internal versions of insertion methods
     * All have the same basic structure as the first (internalPut):
     *  1. If table uninitialized, create
     *  2. If bin empty, try to CAS new node
     *  3. If bin stale, use new table
     *  4. if bin converted to TreeBin, validate and relay to TreeBin methods
     *  5. Lock and validate; if valid, scan and add or update
     *
     * The putAll method differs mainly in attempting to pre-allocate
     * enough table space, and also more lazily performs count updates
     * and checks.
     *
     * Most of the function-accepting methods can't be factored nicely
     * because they require different functional forms, so instead
     * sprawl out similar mechanics.
     */

    /** Implementation for put and putIfAbsent */
    @SuppressWarnings("unchecked") private final V internalPut
        (K k, V v, boolean onlyIfAbsent) {
        if (k == null || v == null) throw new NullPointerException();
        int h = spread(k.hashCode());
        int len = 0;
        for (Node<V>[] tab = table;;) {
            int i, fh; Node<V> f; Object fk; V fv;
            if (tab == null)
                tab = initTable();
            else if ((f = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                if (casTabAt(tab, i, null, new Node<V>(h, k, v, null)))
                    break;                   // no lock when adding to empty bin
            }
            else if ((fh = f.hash) < 0) {
                if ((fk = f.key) instanceof TreeBin) {
                    TreeBin<V> t = (TreeBin<V>)fk;
                    V oldVal = null;
                    t.acquire(0);
                    try {
                        if (tabAt(tab, i) == f) {
                            len = 2;
                            TreeNode<V> p = t.putTreeNode(h, k, v);
                            if (p != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = v;
                            }
                        }
                    } finally {
                        t.release(0);
                    }
                    if (len != 0) {
                        if (oldVal != null)
                            return oldVal;
                        break;
                    }
                }
                else
                    tab = (Node<V>[])fk;
            }
            else if (onlyIfAbsent && fh == h && (fv = f.val) != null &&
                     ((fk = f.key) == k || k.equals(fk))) // peek while nearby
                return fv;
            else {
                V oldVal = null;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        len = 1;
                        for (Node<V> e = f;; ++len) {
                            Object ek; V ev;
                            if (e.hash == h &&
                                (ev = e.val) != null &&
                                ((ek = e.key) == k || k.equals(ek))) {
                                oldVal = ev;
                                if (!onlyIfAbsent)
                                    e.val = v;
                                break;
                            }
                            Node<V> last = e;
                            if ((e = e.next) == null) {
                                last.next = new Node<V>(h, k, v, null);
                                if (len >= TREE_THRESHOLD)
                                    replaceWithTreeBin(tab, i, k);
                                break;
                            }
                        }
                    }
                }
                if (len != 0) {
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        addCount(1L, len);
        return null;
    }

    /** Implementation for computeIfAbsent */
    @SuppressWarnings("unchecked") private final V internalComputeIfAbsent
        (K k, Function<? super K, ? extends V> mf) {
        if (k == null || mf == null)
            throw new NullPointerException();
        int h = spread(k.hashCode());
        V val = null;
        int len = 0;
        for (Node<V>[] tab = table;;) {
            Node<V> f; int i; Object fk;
            if (tab == null)
                tab = initTable();
            else if ((f = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                Node<V> node = new Node<V>(h, k, null, null);
                synchronized (node) {
                    if (casTabAt(tab, i, null, node)) {
                        len = 1;
                        try {
                            if ((val = mf.apply(k)) != null)
                                node.val = val;
                        } finally {
                            if (val == null)
                                setTabAt(tab, i, null);
                        }
                    }
                }
                if (len != 0)
                    break;
            }
            else if (f.hash < 0) {
                if ((fk = f.key) instanceof TreeBin) {
                    TreeBin<V> t = (TreeBin<V>)fk;
                    boolean added = false;
                    t.acquire(0);
                    try {
                        if (tabAt(tab, i) == f) {
                            len = 1;
                            TreeNode<V> p = t.getTreeNode(h, k, t.root);
                            if (p != null)
                                val = p.val;
                            else if ((val = mf.apply(k)) != null) {
                                added = true;
                                len = 2;
                                t.putTreeNode(h, k, val);
                            }
                        }
                    } finally {
                        t.release(0);
                    }
                    if (len != 0) {
                        if (!added)
                            return val;
                        break;
                    }
                }
                else
                    tab = (Node<V>[])fk;
            }
            else {
                for (Node<V> e = f; e != null; e = e.next) { // prescan
                    Object ek; V ev;
                    if (e.hash == h && (ev = e.val) != null &&
                        ((ek = e.key) == k || k.equals(ek)))
                        return ev;
                }
                boolean added = false;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        len = 1;
                        for (Node<V> e = f;; ++len) {
                            Object ek; V ev;
                            if (e.hash == h &&
                                (ev = e.val) != null &&
                                ((ek = e.key) == k || k.equals(ek))) {
                                val = ev;
                                break;
                            }
                            Node<V> last = e;
                            if ((e = e.next) == null) {
                                if ((val = mf.apply(k)) != null) {
                                    added = true;
                                    last.next = new Node<V>(h, k, val, null);
                                    if (len >= TREE_THRESHOLD)
                                        replaceWithTreeBin(tab, i, k);
                                }
                                break;
                            }
                        }
                    }
                }
                if (len != 0) {
                    if (!added)
                        return val;
                    break;
                }
            }
        }
        if (val != null)
            addCount(1L, len);
        return val;
    }

    /** Implementation for compute */
    @SuppressWarnings("unchecked") private final V internalCompute
        (K k, boolean onlyIfPresent,
         BiFunction<? super K, ? super V, ? extends V> mf) {
        if (k == null || mf == null)
            throw new NullPointerException();
        int h = spread(k.hashCode());
        V val = null;
        int delta = 0;
        int len = 0;
        for (Node<V>[] tab = table;;) {
            Node<V> f; int i, fh; Object fk;
            if (tab == null)
                tab = initTable();
            else if ((f = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                if (onlyIfPresent)
                    break;
                Node<V> node = new Node<V>(h, k, null, null);
                synchronized (node) {
                    if (casTabAt(tab, i, null, node)) {
                        try {
                            len = 1;
                            if ((val = mf.apply(k, null)) != null) {
                                node.val = val;
                                delta = 1;
                            }
                        } finally {
                            if (delta == 0)
                                setTabAt(tab, i, null);
                        }
                    }
                }
                if (len != 0)
                    break;
            }
            else if ((fh = f.hash) < 0) {
                if ((fk = f.key) instanceof TreeBin) {
                    TreeBin<V> t = (TreeBin<V>)fk;
                    t.acquire(0);
                    try {
                        if (tabAt(tab, i) == f) {
                            len = 1;
                            TreeNode<V> p = t.getTreeNode(h, k, t.root);
                            if (p == null && onlyIfPresent)
                                break;
                            V pv = (p == null) ? null : p.val;
                            if ((val = mf.apply(k, pv)) != null) {
                                if (p != null)
                                    p.val = val;
                                else {
                                    len = 2;
                                    delta = 1;
                                    t.putTreeNode(h, k, val);
                                }
                            }
                            else if (p != null) {
                                delta = -1;
                                t.deleteTreeNode(p);
                            }
                        }
                    } finally {
                        t.release(0);
                    }
                    if (len != 0)
                        break;
                }
                else
                    tab = (Node<V>[])fk;
            }
            else {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        len = 1;
                        for (Node<V> e = f, pred = null;; ++len) {
                            Object ek; V ev;
                            if (e.hash == h &&
                                (ev = e.val) != null &&
                                ((ek = e.key) == k || k.equals(ek))) {
                                val = mf.apply(k, ev);
                                if (val != null)
                                    e.val = val;
                                else {
                                    delta = -1;
                                    Node<V> en = e.next;
                                    if (pred != null)
                                        pred.next = en;
                                    else
                                        setTabAt(tab, i, en);
                                }
                                break;
                            }
                            pred = e;
                            if ((e = e.next) == null) {
                                if (!onlyIfPresent &&
                                    (val = mf.apply(k, null)) != null) {
                                    pred.next = new Node<V>(h, k, val, null);
                                    delta = 1;
                                    if (len >= TREE_THRESHOLD)
                                        replaceWithTreeBin(tab, i, k);
                                }
                                break;
                            }
                        }
                    }
                }
                if (len != 0)
                    break;
            }
        }
        if (delta != 0)
            addCount((long)delta, len);
        return val;
    }

    /** Implementation for merge */
    @SuppressWarnings("unchecked") private final V internalMerge
        (K k, V v, BiFunction<? super V, ? super V, ? extends V> mf) {
        if (k == null || v == null || mf == null)
            throw new NullPointerException();
        int h = spread(k.hashCode());
        V val = null;
        int delta = 0;
        int len = 0;
        for (Node<V>[] tab = table;;) {
            int i; Node<V> f; Object fk; V fv;
            if (tab == null)
                tab = initTable();
            else if ((f = tabAt(tab, i = (tab.length - 1) & h)) == null) {
                if (casTabAt(tab, i, null, new Node<V>(h, k, v, null))) {
                    delta = 1;
                    val = v;
                    break;
                }
            }
            else if (f.hash < 0) {
                if ((fk = f.key) instanceof TreeBin) {
                    TreeBin<V> t = (TreeBin<V>)fk;
                    t.acquire(0);
                    try {
                        if (tabAt(tab, i) == f) {
                            len = 1;
                            TreeNode<V> p = t.getTreeNode(h, k, t.root);
                            val = (p == null) ? v : mf.apply(p.val, v);
                            if (val != null) {
                                if (p != null)
                                    p.val = val;
                                else {
                                    len = 2;
                                    delta = 1;
                                    t.putTreeNode(h, k, val);
                                }
                            }
                            else if (p != null) {
                                delta = -1;
                                t.deleteTreeNode(p);
                            }
                        }
                    } finally {
                        t.release(0);
                    }
                    if (len != 0)
                        break;
                }
                else
                    tab = (Node<V>[])fk;
            }
            else {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        len = 1;
                        for (Node<V> e = f, pred = null;; ++len) {
                            Object ek; V ev;
                            if (e.hash == h &&
                                (ev = e.val) != null &&
                                ((ek = e.key) == k || k.equals(ek))) {
                                val = mf.apply(ev, v);
                                if (val != null)
                                    e.val = val;
                                else {
                                    delta = -1;
                                    Node<V> en = e.next;
                                    if (pred != null)
                                        pred.next = en;
                                    else
                                        setTabAt(tab, i, en);
                                }
                                break;
                            }
                            pred = e;
                            if ((e = e.next) == null) {
                                val = v;
                                pred.next = new Node<V>(h, k, val, null);
                                delta = 1;
                                if (len >= TREE_THRESHOLD)
                                    replaceWithTreeBin(tab, i, k);
                                break;
                            }
                        }
                    }
                }
                if (len != 0)
                    break;
            }
        }
        if (delta != 0)
            addCount((long)delta, len);
        return val;
    }

    /** Implementation for putAll */
    @SuppressWarnings("unchecked") private final void internalPutAll
        (Map<? extends K, ? extends V> m) {
        tryPresize(m.size());
        long delta = 0L;     // number of uncommitted additions
        boolean npe = false; // to throw exception on exit for nulls
        try {                // to clean up counts on other exceptions
            for (Map.Entry<?, ? extends V> entry : m.entrySet()) {
                Object k; V v;
                if (entry == null || (k = entry.getKey()) == null ||
                    (v = entry.getValue()) == null) {
                    npe = true;
                    break;
                }
                int h = spread(k.hashCode());
                for (Node<V>[] tab = table;;) {
                    int i; Node<V> f; int fh; Object fk;
                    if (tab == null)
                        tab = initTable();
                    else if ((f = tabAt(tab, i = (tab.length - 1) & h)) == null){
                        if (casTabAt(tab, i, null, new Node<V>(h, k, v, null))) {
                            ++delta;
                            break;
                        }
                    }
                    else if ((fh = f.hash) < 0) {
                        if ((fk = f.key) instanceof TreeBin) {
                            TreeBin<V> t = (TreeBin<V>)fk;
                            boolean validated = false;
                            t.acquire(0);
                            try {
                                if (tabAt(tab, i) == f) {
                                    validated = true;
                                    TreeNode<V> p = t.getTreeNode(h, k, t.root);
                                    if (p != null)
                                        p.val = v;
                                    else {
                                        t.putTreeNode(h, k, v);
                                        ++delta;
                                    }
                                }
                            } finally {
                                t.release(0);
                            }
                            if (validated)
                                break;
                        }
                        else
                            tab = (Node<V>[])fk;
                    }
                    else {
                        int len = 0;
                        synchronized (f) {
                            if (tabAt(tab, i) == f) {
                                len = 1;
                                for (Node<V> e = f;; ++len) {
                                    Object ek; V ev;
                                    if (e.hash == h &&
                                        (ev = e.val) != null &&
                                        ((ek = e.key) == k || k.equals(ek))) {
                                        e.val = v;
                                        break;
                                    }
                                    Node<V> last = e;
                                    if ((e = e.next) == null) {
                                        ++delta;
                                        last.next = new Node<V>(h, k, v, null);
                                        if (len >= TREE_THRESHOLD)
                                            replaceWithTreeBin(tab, i, k);
                                        break;
                                    }
                                }
                            }
                        }
                        if (len != 0) {
                            if (len > 1) {
                                addCount(delta, len);
                                delta = 0L;
                            }
                            break;
                        }
                    }
                }
            }
        } finally {
            if (delta != 0L)
                addCount(delta, 2);
        }
        if (npe)
            throw new NullPointerException();
    }

    /**
     * Implementation for clear. Steps through each bin, removing all
     * nodes.
     */
    @SuppressWarnings("unchecked") private final void internalClear() {
        long delta = 0L; // negative number of deletions
        int i = 0;
        Node<V>[] tab = table;
        while (tab != null && i < tab.length) {
            Node<V> f = tabAt(tab, i);
            if (f == null)
                ++i;
            else if (f.hash < 0) {
                Object fk;
                if ((fk = f.key) instanceof TreeBin) {
                    TreeBin<V> t = (TreeBin<V>)fk;
                    t.acquire(0);
                    try {
                        if (tabAt(tab, i) == f) {
                            for (Node<V> p = t.first; p != null; p = p.next) {
                                if (p.val != null) { // (currently always true)
                                    p.val = null;
                                    --delta;
                                }
                            }
                            t.first = null;
                            t.root = null;
                            ++i;
                        }
                    } finally {
                        t.release(0);
                    }
                }
                else
                    tab = (Node<V>[])fk;
            }
            else {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        for (Node<V> e = f; e != null; e = e.next) {
                            if (e.val != null) {  // (currently always true)
                                e.val = null;
                                --delta;
                            }
                        }
                        setTabAt(tab, i, null);
                        ++i;
                    }
                }
            }
        }
        if (delta != 0L)
            addCount(delta, -1);
    }

    /* ---------------- Table Initialization and Resizing -------------- */

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
     * Initializes table, using the size recorded in sizeCtl.
     */
    @SuppressWarnings("unchecked") private final Node<V>[] initTable() {
        Node<V>[] tab; int sc;
        while ((tab = table) == null) {
            if ((sc = sizeCtl) < 0)
                Thread.yield(); // lost initialization race; just spin
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                try {
                    if ((tab = table) == null) {
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        @SuppressWarnings("rawtypes") Node[] tb = new Node[n];
                        table = tab = (Node<V>[])tb;
                        sc = n - (n >>> 2);
                    }
                } finally {
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }

    /**
     * Adds to count, and if table is too small and not already
     * resizing, initiates transfer. If already resizing, helps
     * perform transfer if work is available.  Rechecks occupancy
     * after a transfer to see if another resize is already needed
     * because resizings are lagging additions.
     *
     * @param x the count to add
     * @param check if <0, don't check resize, if <= 1 only check if uncontended
     */
    private final void addCount(long x, int check) {
        Cell[] as; long b, s;
        if ((as = counterCells) != null ||
            !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
            Cell a; long v; int m;
            boolean uncontended = true;
            if (as == null || (m = as.length - 1) < 0 ||
                (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
                !(uncontended =
                  U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
                fullAddCount(x, uncontended);
                return;
            }
            if (check <= 1)
                return;
            s = sumCount();
        }
        if (check >= 0) {
            Node<V>[] tab, nt; int sc;
            while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
                   tab.length < MAXIMUM_CAPACITY) {
                if (sc < 0) {
                    if (sc == -1 || transferIndex <= transferOrigin ||
                        (nt = nextTable) == null)
                        break;
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc - 1))
                        transfer(tab, nt);
                }
                else if (U.compareAndSwapInt(this, SIZECTL, sc, -2))
                    transfer(tab, null);
                s = sumCount();
            }
        }
    }

    /**
     * Tries to presize table to accommodate the given number of elements.
     *
     * @param size number of elements (doesn't need to be perfectly accurate)
     */
    @SuppressWarnings("unchecked") private final void tryPresize(int size) {
        int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY :
            tableSizeFor(size + (size >>> 1) + 1);
        int sc;
        while ((sc = sizeCtl) >= 0) {
            Node<V>[] tab = table; int n;
            if (tab == null || (n = tab.length) == 0) {
                n = (sc > c) ? sc : c;
                if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                    try {
                        if (table == tab) {
                            @SuppressWarnings("rawtypes") Node[] tb = new Node[n];
                            table = (Node<V>[])tb;
                            sc = n - (n >>> 2);
                        }
                    } finally {
                        sizeCtl = sc;
                    }
                }
            }
            else if (c <= sc || n >= MAXIMUM_CAPACITY)
                break;
            else if (tab == table &&
                     U.compareAndSwapInt(this, SIZECTL, sc, -2))
                transfer(tab, null);
        }
    }

    /**
     * Moves and/or copies the nodes in each bin to new table. See
     * above for explanation.
     */
    @SuppressWarnings("unchecked") private final void transfer
        (Node<V>[] tab, Node<V>[] nextTab) {
        int n = tab.length, stride;
        if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
            stride = MIN_TRANSFER_STRIDE; // subdivide range
        if (nextTab == null) {            // initiating
            try {
                @SuppressWarnings("rawtypes") Node[] tb = new Node[n << 1];
                nextTab = (Node<V>[])tb;
            } catch (Throwable ex) {      // try to cope with OOME
                sizeCtl = Integer.MAX_VALUE;
                return;
            }
            nextTable = nextTab;
            transferOrigin = n;
            transferIndex = n;
            Node<V> rev = new Node<V>(MOVED, tab, null, null);
            for (int k = n; k > 0;) {    // progressively reveal ready slots
                int nextk = (k > stride) ? k - stride : 0;
                for (int m = nextk; m < k; ++m)
                    nextTab[m] = rev;
                for (int m = n + nextk; m < n + k; ++m)
                    nextTab[m] = rev;
                U.putOrderedInt(this, TRANSFERORIGIN, k = nextk);
            }
        }
        int nextn = nextTab.length;
        Node<V> fwd = new Node<V>(MOVED, nextTab, null, null);
        boolean advance = true;
        for (int i = 0, bound = 0;;) {
            int nextIndex, nextBound; Node<V> f; Object fk;
            while (advance) {
                if (--i >= bound)
                    advance = false;
                else if ((nextIndex = transferIndex) <= transferOrigin) {
                    i = -1;
                    advance = false;
                }
                else if (U.compareAndSwapInt
                         (this, TRANSFERINDEX, nextIndex,
                          nextBound = (nextIndex > stride ?
                                       nextIndex - stride : 0))) {
                    bound = nextBound;
                    i = nextIndex - 1;
                    advance = false;
                }
            }
            if (i < 0 || i >= n || i + n >= nextn) {
                for (int sc;;) {
                    if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, ++sc)) {
                        if (sc == -1) {
                            nextTable = null;
                            table = nextTab;
                            sizeCtl = (n << 1) - (n >>> 1);
                        }
                        return;
                    }
                }
            }
            else if ((f = tabAt(tab, i)) == null) {
                if (casTabAt(tab, i, null, fwd)) {
                    setTabAt(nextTab, i, null);
                    setTabAt(nextTab, i + n, null);
                    advance = true;
                }
            }
            else if (f.hash >= 0) {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        int runBit = f.hash & n;
                        Node<V> lastRun = f, lo = null, hi = null;
                        for (Node<V> p = f.next; p != null; p = p.next) {
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
                        for (Node<V> p = f; p != lastRun; p = p.next) {
                            int ph = p.hash;
                            Object pk = p.key; V pv = p.val;
                            if ((ph & n) == 0)
                                lo = new Node<V>(ph, pk, pv, lo);
                            else
                                hi = new Node<V>(ph, pk, pv, hi);
                        }
                        setTabAt(nextTab, i, lo);
                        setTabAt(nextTab, i + n, hi);
                        setTabAt(tab, i, fwd);
                        advance = true;
                    }
                }
            }
            else if ((fk = f.key) instanceof TreeBin) {
                TreeBin<V> t = (TreeBin<V>)fk;
                t.acquire(0);
                try {
                    if (tabAt(tab, i) == f) {
                        TreeBin<V> lt = new TreeBin<V>();
                        TreeBin<V> ht = new TreeBin<V>();
                        int lc = 0, hc = 0;
                        for (Node<V> e = t.first; e != null; e = e.next) {
                            int h = e.hash;
                            Object k = e.key; V v = e.val;
                            if ((h & n) == 0) {
                                ++lc;
                                lt.putTreeNode(h, k, v);
                            }
                            else {
                                ++hc;
                                ht.putTreeNode(h, k, v);
                            }
                        }
                        Node<V> ln, hn; // throw away trees if too small
                        if (lc < TREE_THRESHOLD) {
                            ln = null;
                            for (Node<V> p = lt.first; p != null; p = p.next)
                                ln = new Node<V>(p.hash, p.key, p.val, ln);
                        }
                        else
                            ln = new Node<V>(MOVED, lt, null, null);
                        setTabAt(nextTab, i, ln);
                        if (hc < TREE_THRESHOLD) {
                            hn = null;
                            for (Node<V> p = ht.first; p != null; p = p.next)
                                hn = new Node<V>(p.hash, p.key, p.val, hn);
                        }
                        else
                            hn = new Node<V>(MOVED, ht, null, null);
                        setTabAt(nextTab, i + n, hn);
                        setTabAt(tab, i, fwd);
                        advance = true;
                    }
                } finally {
                    t.release(0);
                }
            }
            else
                advance = true; // already processed
        }
    }

    /* ---------------- Counter support -------------- */

    final long sumCount() {
        Cell[] as = counterCells; Cell a;
        long sum = baseCount;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    // See LongAdder version for explanation
    private final void fullAddCount(long x, boolean wasUncontended) {
        int h;
        if ((h = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();      // force initialization
            h = ThreadLocalRandom.getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            Cell[] as; Cell a; int n; long v;
            if ((as = counterCells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {            // Try to attach new Cell
                        Cell r = new Cell(x); // Optimistic create
                        if (cellsBusy == 0 &&
                            U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = counterCells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
                    break;
                else if (counterCells != as || n >= NCPU)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 &&
                         U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                    try {
                        if (counterCells == as) {// Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            counterCells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = ThreadLocalRandom.advanceProbe(h);
            }
            else if (cellsBusy == 0 && counterCells == as &&
                     U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                boolean init = false;
                try {                           // Initialize table
                    if (counterCells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(x);
                        counterCells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x))
                break;                          // Fall back on using base
        }
    }

    /* ----------------Table Traversal -------------- */

    /**
     * Encapsulates traversal for methods such as containsValue; also
     * serves as a base class for other iterators and bulk tasks.
     *
     * At each step, the iterator snapshots the key ("nextKey") and
     * value ("nextVal") of a valid node (i.e., one that, at point of
     * snapshot, has a non-null user value). Because val fields can
     * change (including to null, indicating deletion), field nextVal
     * might not be accurate at point of use, but still maintains the
     * weak consistency property of holding a value that was once
     * valid. To support iterator.remove, the nextKey field is not
     * updated (nulled out) when the iterator cannot advance.
     *
     * Exported iterators must track whether the iterator has advanced
     * (in hasNext vs next) (by setting/checking/nulling field
     * nextVal), and then extract key, value, or key-value pairs as
     * return values of next().
     *
     * Method advance visits once each still-valid node that was
     * reachable upon iterator construction. It might miss some that
     * were added to a bin after the bin was visited, which is OK wrt
     * consistency guarantees. Maintaining this property in the face
     * of possible ongoing resizes requires a fair amount of
     * bookkeeping state that is difficult to optimize away amidst
     * volatile accesses.  Even so, traversal maintains reasonable
     * throughput.
     *
     * Normally, iteration proceeds bin-by-bin traversing lists.
     * However, if the table has been resized, then all future steps
     * must traverse both the bin at the current index as well as at
     * (index + baseSize); and so on for further resizings. To
     * paranoically cope with potential sharing by users of iterators
     * across threads, iteration terminates if a bounds checks fails
     * for a table read.
     *
     * Methods advanceKey and advanceValue are specializations of the
     * common cases of advance, relaying to the full version
     * otherwise. The forEachKey and forEachValue methods further
     * specialize, bypassing all incremental field updates in most cases.
     *
     * This class supports both Spliterator-based traversal and
     * CountedCompleter-based bulk tasks. The same "batch" field is
     * used, but in slightly different ways, in the two cases.  For
     * Spliterators, it is a saturating (at Integer.MAX_VALUE)
     * estimate of element coverage. For CHM tasks, it is a pre-scaled
     * size that halves down to zero for leaf tasks, that is only
     * computed upon execution of the task. (Tasks can be submitted to
     * any pool, of any size, so we don't know scale factors until
     * running.)
     *
     * This class extends CountedCompleter to streamline parallel
     * iteration in bulk operations. This adds only a few fields of
     * space overhead, which is small enough in cases where it is not
     * needed to not worry about it.  Because CountedCompleter is
     * Serializable, but iterators need not be, we need to add warning
     * suppressions.
     */
    @SuppressWarnings("serial") static class Traverser<K,V,R>
        extends CountedCompleter<R> {
        final ConcurrentHashMap<K,V> map;
        Node<V> next;        // the next entry to use
        K nextKey;           // cached key field of next
        V nextVal;           // cached val field of next
        Node<V>[] tab;       // current table; updated if resized
        int index;           // index of bin to use next
        int baseIndex;       // current index of initial table
        int baseLimit;       // index bound for initial table
        final int baseSize;  // initial table size
        int batch;           // split control

        /** Creates iterator for all entries in the table. */
        Traverser(ConcurrentHashMap<K,V> map) {
            this.map = map;
            Node<V>[] t = this.tab = map.table;
            baseLimit = baseSize = (t == null) ? 0 : t.length;
        }

        /** Task constructor */
        Traverser(ConcurrentHashMap<K,V> map, Traverser<K,V,?> it, int batch) {
            super(it);
            this.map = map;
            this.batch = batch; // -1 if unknown
            if (it == null) {
                Node<V>[] t = this.tab = map.table;
                baseLimit = baseSize = (t == null) ? 0 : t.length;
            }
            else { // split parent
                this.tab = it.tab;
                this.baseSize = it.baseSize;
                int hi = this.baseLimit = it.baseLimit;
                it.baseLimit = this.index = this.baseIndex =
                    (hi + it.baseIndex) >>> 1;
            }
        }

        /** Spliterator constructor */
        Traverser(ConcurrentHashMap<K,V> map, Traverser<K,V,?> it) {
            super(it);
            this.map = map;
            if (it == null) {
                Node<V>[] t = this.tab = map.table;
                baseLimit = baseSize = (t == null) ? 0 : t.length;
                long n = map.sumCount();
                batch = ((n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
                         (int)n);
            }
            else {
                this.tab = it.tab;
                this.baseSize = it.baseSize;
                int hi = this.baseLimit = it.baseLimit;
                it.baseLimit = this.index = this.baseIndex =
                    (hi + it.baseIndex) >>> 1;
                this.batch = it.batch >>>= 1;
            }
        }

        /**
         * Advances if possible, returning next valid value, or null if none.
         */
        @SuppressWarnings("unchecked") final V advance() {
            for (Node<V> e = next;;) {
                if (e != null)                  // advance past used/skipped node
                    e = next = e.next;
                while (e == null) {             // get to next non-null bin
                    Node<V>[] t; int i, n;      // must use locals in checks
                    if (baseIndex >= baseLimit || (t = tab) == null ||
                        (n = t.length) <= (i = index) || i < 0)
                        return nextVal = null;
                    if ((e = next = tabAt(t, index)) != null && e.hash < 0) {
                        Object ek;
                        if ((ek = e.key) instanceof TreeBin)
                            e = ((TreeBin<V>)ek).first;
                        else {
                            tab = (Node<V>[])ek;
                            continue;           // restarts due to null val
                        }
                    }
                    if ((index += baseSize) >= n)
                        index = ++baseIndex;    // visit upper slots if present
                }
                nextKey = (K)e.key;
                if ((nextVal = e.val) != null) // skip deleted or special nodes
                    return nextVal;
            }
        }

        /**
         * Common case version for value traversal
         */
        @SuppressWarnings("unchecked") final V advanceValue() {
            outer: for (Node<V> e = next;;) {
                if (e == null || (e = e.next) == null) {
                    Node<V>[] t; int i, len, n; Object ek;
                    if ((t = tab) == null ||
                        baseSize != (len = t.length) ||
                        len < (n = baseLimit) ||
                        baseIndex != (i = index))
                        break;
                    do {
                        if (i < 0 || i >= n) {
                            index = baseIndex = n;
                            next = null;
                            return nextVal = null;
                        }
                        if ((e = tabAt(t, i)) != null && e.hash < 0) {
                            if ((ek = e.key) instanceof TreeBin)
                                e = ((TreeBin<V>)ek).first;
                            else {
                                index = baseIndex = i;
                                next = null;
                                tab = (Node<V>[])ek;
                                break outer;
                            }
                        }
                        ++i;
                    } while (e == null);
                    index = baseIndex = i;
                }
                V v;
                K k = (K)e.key;
                if ((v = e.val) != null) {
                    nextVal = v;
                    nextKey = k;
                    next = e;
                    return v;
                }
            }
            return advance();
        }

        /**
         * Common case version for key traversal
         */
        @SuppressWarnings("unchecked") final K advanceKey() {
            outer: for (Node<V> e = next;;) {
                if (e == null || (e = e.next) == null) {
                    Node<V>[] t; int i, len, n; Object ek;
                    if ((t = tab) == null ||
                        baseSize != (len = t.length) ||
                        len < (n = baseLimit) ||
                        baseIndex != (i = index))
                        break;
                    do {
                        if (i < 0 || i >= n) {
                            index = baseIndex = n;
                            next = null;
                            nextVal = null;
                            return null;
                        }
                        if ((e = tabAt(t, i)) != null && e.hash < 0) {
                            if ((ek = e.key) instanceof TreeBin)
                                e = ((TreeBin<V>)ek).first;
                            else {
                                index = baseIndex = i;
                                next = null;
                                tab = (Node<V>[])ek;
                                break outer;
                            }
                        }
                        ++i;
                    } while (e == null);
                    index = baseIndex = i;
                }
                V v;
                K k = (K)e.key;
                if ((v = e.val) != null) {
                    nextVal = v;
                    nextKey = k;
                    next = e;
                    return k;
                }
            }
            return (advance() == null) ? null : nextKey;
        }

        @SuppressWarnings("unchecked") final void forEachValue(Consumer<? super V> action) {
            if (action == null) throw new NullPointerException();
            Node<V>[] t; int i, len, n;
            if ((t = tab) != null && baseSize == (len = t.length) &&
                len >= (n = baseLimit) && baseIndex == (i = index)) {
                index = baseIndex = n;
                nextVal = null;
                Node<V> e = next;
                next = null;
                if (e != null)
                    e = e.next;
                outer: for (;; e = e.next) {
                    V v; Object ek;
                    for (; e == null; ++i) {
                        if (i < 0 || i >= n)
                            return;
                        if ((e = tabAt(t, i)) != null && e.hash < 0) {
                            if ((ek = e.key) instanceof TreeBin)
                                e = ((TreeBin<V>)ek).first;
                            else {
                                index = baseIndex = i;
                                tab = (Node<V>[])ek;
                                break outer;
                            }
                        }
                    }
                    if ((v = e.val) != null)
                        action.accept(v);
                }
            }
            V v;
            while ((v = advance()) != null)
                action.accept(v);
        }

        @SuppressWarnings("unchecked") final void forEachKey(Consumer<? super K> action) {
            if (action == null) throw new NullPointerException();
            Node<V>[] t; int i, len, n;
            if ((t = tab) != null && baseSize == (len = t.length) &&
                len >= (n = baseLimit) && baseIndex == (i = index)) {
                index = baseIndex = n;
                nextVal = null;
                Node<V> e = next;
                next = null;
                if (e != null)
                    e = e.next;
                outer: for (;; e = e.next) {
                    for (; e == null; ++i) {
                        if (i < 0 || i >= n)
                            return;
                        if ((e = tabAt(t, i)) != null && e.hash < 0) {
                            Object ek;
                            if ((ek = e.key) instanceof TreeBin)
                                e = ((TreeBin<V>)ek).first;
                            else {
                                index = baseIndex = i;
                                tab = (Node<V>[])ek;
                                break outer;
                            }
                        }
                    }
                    Object k = e.key;
                    if (e.val != null)
                        action.accept((K)k);
                }
            }
            while (advance() != null)
                action.accept(nextKey);
        }

        public final void remove() {
            K k = nextKey;
            if (k == null && (advanceValue() == null || (k = nextKey) == null))
                throw new IllegalStateException();
            map.internalReplace(k, null, null);
        }

        public final boolean hasNext() {
            return nextVal != null || advanceValue() != null;
        }

        public final boolean hasMoreElements() { return hasNext(); }

        public void compute() { } // default no-op CountedCompleter body

        public long estimateSize() { return batch; }

        /**
         * Returns a batch value > 0 if this task should (and must) be
         * split, if so, adding to pending count, and in any case
         * updating batch value. The initial batch value is approx
         * exp2 of the number of times (minus one) to split task by
         * two before executing leaf action. This value is faster to
         * compute and more convenient to use as a guide to splitting
         * than is the depth, since it is used while dividing by two
         * anyway.
         */
        final int preSplit() {
            int b;  ForkJoinPool pool;
            if ((b = batch) < 0) { // force initialization
                int sp = (((pool = getPool()) == null) ?
                          ForkJoinPool.getCommonPoolParallelism() :
                          pool.getParallelism()) << 3; // slack of 8
                long n = map.sumCount();
                b = (n <= 0L) ? 0 : (n < (long)sp) ? (int)n : sp;
            }
            b = (b <= 1 || baseIndex >= baseLimit) ? 0 : (b >>> 1);
            if ((batch = b) > 0)
                addToPendingCount(1);
            return b;
        }
    }

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new, empty map with the default initial table size (16).
     */
    public ConcurrentHashMap() {
    }

    /**
     * Creates a new, empty map with an initial table size
     * accommodating the specified number of elements without the need
     * to dynamically resize.
     *
     * @param initialCapacity The implementation performs internal
     * sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative
     */
    public ConcurrentHashMap(int initialCapacity) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException();
        int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ?
                   MAXIMUM_CAPACITY :
                   tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
        this.sizeCtl = cap;
    }

    /**
     * Creates a new map with the same mappings as the given map.
     *
     * @param m the map
     */
    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
        this.sizeCtl = DEFAULT_CAPACITY;
        internalPutAll(m);
    }

    /**
     * Creates a new, empty map with an initial table size based on
     * the given number of elements ({@code initialCapacity}) and
     * initial table density ({@code loadFactor}).
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements,
     * given the specified load factor.
     * @param loadFactor the load factor (table density) for
     * establishing the initial table size
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative or the load factor is nonpositive
     *
     * @since 1.6
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, 1);
    }

    /**
     * Creates a new, empty map with an initial table size based on
     * the given number of elements ({@code initialCapacity}), table
     * density ({@code loadFactor}), and number of concurrently
     * updating threads ({@code concurrencyLevel}).
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements,
     * given the specified load factor.
     * @param loadFactor the load factor (table density) for
     * establishing the initial table size
     * @param concurrencyLevel the estimated number of concurrently
     * updating threads. The implementation may use this value as
     * a sizing hint.
     * @throws IllegalArgumentException if the initial capacity is
     * negative or the load factor or concurrencyLevel are
     * nonpositive
     */
    public ConcurrentHashMap(int initialCapacity,
                               float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();
        if (initialCapacity < concurrencyLevel)   // Use at least as many bins
            initialCapacity = concurrencyLevel;   // as estimated threads
        long size = (long)(1.0 + (long)initialCapacity / loadFactor);
        int cap = (size >= (long)MAXIMUM_CAPACITY) ?
            MAXIMUM_CAPACITY : tableSizeFor((int)size);
        this.sizeCtl = cap;
    }

    /**
     * Creates a new {@link Set} backed by a ConcurrentHashMap
     * from the given type to {@code Boolean.TRUE}.
     *
     * @return the new set
     */
    public static <K> KeySetView<K,Boolean> newKeySet() {
        return new KeySetView<K,Boolean>
            (new ConcurrentHashMap<K,Boolean>(), Boolean.TRUE);
    }

    /**
     * Creates a new {@link Set} backed by a ConcurrentHashMap
     * from the given type to {@code Boolean.TRUE}.
     *
     * @param initialCapacity The implementation performs internal
     * sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative
     * @return the new set
     */
    public static <K> KeySetView<K,Boolean> newKeySet(int initialCapacity) {
        return new KeySetView<K,Boolean>
            (new ConcurrentHashMap<K,Boolean>(initialCapacity), Boolean.TRUE);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return sumCount() <= 0L; // ignore transient negative values
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        long n = sumCount();
        return ((n < 0L) ? 0 :
                (n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
                (int)n);
    }

    /**
     * Returns the number of mappings. This method should be used
     * instead of {@link #size} because a ConcurrentHashMap may
     * contain more mappings than can be represented as an int. The
     * value returned is an estimate; the actual count may differ if
     * there are concurrent insertions or removals.
     *
     * @return the number of mappings
     */
    public long mappingCount() {
        long n = sumCount();
        return (n < 0L) ? 0L : n; // ignore transient negative values
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
    public V get(Object key) {
        return internalGet(key);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or the given defaultValue if this map contains no mapping for the key.
     *
     * @param key the key
     * @param defaultValue the value to return if this map contains
     * no mapping for the given key
     * @return the mapping for the key, if present; else the defaultValue
     * @throws NullPointerException if the specified key is null
     */
    public V getOrDefault(Object key, V defaultValue) {
        V v;
        return (v = internalGet(key)) == null ? defaultValue : v;
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param  key possible key
     * @return {@code true} if and only if the specified object
     *         is a key in this table, as determined by the
     *         {@code equals} method; {@code false} otherwise
     * @throws NullPointerException if the specified key is null
     */
    public boolean containsKey(Object key) {
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
        V v;
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        while ((v = it.advanceValue()) != null) {
            if (v == value || value.equals(v))
                return true;
        }
        return false;
    }

    /**
     * Legacy method testing if some key maps into the specified value
     * in this table.  This method is identical in functionality to
     * {@link #containsValue(Object)}, and exists solely to ensure
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
    @Deprecated public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p>The value can be retrieved by calling the {@code get} method
     * with a key that is equal to the original key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}
     * @throws NullPointerException if the specified key or value is null
     */
    public V put(K key, V value) {
        return internalPut(key, value, false);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or {@code null} if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    public V putIfAbsent(K key, V value) {
        return internalPut(key, value, true);
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        internalPutAll(m);
    }

    /**
     * If the specified key is not already associated with a value (or
     * is mapped to {@code null}), attempts to compute its value using
     * the given mapping function and enters it into this map unless
     * {@code null}. The entire method invocation is performed
     * atomically, so the function is applied at most once per key.
     * Some attempted update operations on this map by other threads
     * may be blocked while computation is in progress, so the
     * computation should be short and simple, and must not attempt to
     * update any other mappings of this Map.
     *
     * @param key key with which the specified value is to be associated
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with
     *         the specified key, or null if the computed value is null
     * @throws NullPointerException if the specified key or mappingFunction
     *         is null
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete
     * @throws RuntimeException or Error if the mappingFunction does so,
     *         in which case the mapping is left unestablished
     */
    public V computeIfAbsent
        (K key, Function<? super K, ? extends V> mappingFunction) {
        return internalComputeIfAbsent(key, mappingFunction);
    }

    /**
     * If the value for the specified key is present and non-null,
     * attempts to compute a new mapping given the key and its current
     * mapped value.  The entire method invocation is performed
     * atomically.  Some attempted update operations on this map by
     * other threads may be blocked while computation is in progress,
     * so the computation should be short and simple, and must not
     * attempt to update any other mappings of this Map.
     *
     * @param key key with which a value may be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key or remappingFunction
     *         is null
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete
     * @throws RuntimeException or Error if the remappingFunction does so,
     *         in which case the mapping is unchanged
     */
    public V computeIfPresent
        (K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return internalCompute(key, true, remappingFunction);
    }

    /**
     * Attempts to compute a mapping for the specified key and its
     * current mapped value (or {@code null} if there is no current
     * mapping). The entire method invocation is performed atomically.
     * Some attempted update operations on this map by other threads
     * may be blocked while computation is in progress, so the
     * computation should be short and simple, and must not attempt to
     * update any other mappings of this Map.
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key or remappingFunction
     *         is null
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete
     * @throws RuntimeException or Error if the remappingFunction does so,
     *         in which case the mapping is unchanged
     */
    public V compute
        (K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return internalCompute(key, false, remappingFunction);
    }

    /**
     * If the specified key is not already associated with a
     * (non-null) value, associates it with the given value.
     * Otherwise, replaces the value with the results of the given
     * remapping function, or removes if {@code null}. The entire
     * method invocation is performed atomically.  Some attempted
     * update operations on this map by other threads may be blocked
     * while computation is in progress, so the computation should be
     * short and simple, and must not attempt to update any other
     * mappings of this Map.
     *
     * @param key key with which the specified value is to be associated
     * @param value the value to use if absent
     * @param remappingFunction the function to recompute a value if present
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key or the
     *         remappingFunction is null
     * @throws RuntimeException or Error if the remappingFunction does so,
     *         in which case the mapping is unchanged
     */
    public V merge
        (K key, V value,
         BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return internalMerge(key, value, remappingFunction);
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
    public V remove(Object key) {
        return internalReplace(key, null, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    public boolean remove(Object key, Object value) {
        if (key == null)
            throw new NullPointerException();
        return value != null && internalReplace(key, null, value) != null;
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
    public V replace(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();
        return internalReplace(key, value, null);
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
     * reflected in the set, and vice-versa.
     *
     * @return the set view
     */
    public KeySetView<K,V> keySet() {
        KeySetView<K,V> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySetView<K,V>(this, null));
    }

    /**
     * Returns a {@link Set} view of the keys in this map, using the
     * given common mapped value for any additions (i.e., {@link
     * Collection#add} and {@link Collection#addAll(Collection)}).
     * This is of course only appropriate if it is acceptable to use
     * the same value for all additions from this view.
     *
     * @param mappedValue the mapped value to use for any additions
     * @return the set view
     * @throws NullPointerException if the mappedValue is null
     */
    public KeySetView<K,V> keySet(V mappedValue) {
        if (mappedValue == null)
            throw new NullPointerException();
        return new KeySetView<K,V>(this, mappedValue);
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.
     *
     * @return the collection view
     */
    public ValuesView<K,V> values() {
        ValuesView<K,V> vs = values;
        return (vs != null) ? vs : (values = new ValuesView<K,V>(this));
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
     *
     * @return the set view
     */
    public Set<Map.Entry<K,V>> entrySet() {
        EntrySetView<K,V> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySetView<K,V>(this));
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
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v;
        while ((v = it.advanceValue()) != null) {
            h += it.nextKey.hashCode() ^ v.hashCode();
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
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        V v;
        if ((v = it.advanceValue()) != null) {
            for (;;) {
                K k = it.nextKey;
                sb.append(k == this ? "(this Map)" : k);
                sb.append('=');
                sb.append(v == this ? "(this Map)" : v);
                if ((v = it.advanceValue()) == null)
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
            Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
            V val;
            while ((val = it.advanceValue()) != null) {
                Object v = m.get(it.nextKey);
                if (v == null || (v != val && !v.equals(val)))
                    return false;
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

    @SuppressWarnings("serial") static final class KeyIterator<K,V>
        extends Traverser<K,V,Object>
        implements Spliterator<K>, Iterator<K>, Enumeration<K> {
        KeyIterator(ConcurrentHashMap<K,V> map) { super(map); }
        KeyIterator(ConcurrentHashMap<K,V> map, Traverser<K,V,Object> it) {
            super(map, it);
        }
        public Spliterator<K> trySplit() {
            return (baseLimit - baseIndex <= 1) ? null :
                new KeyIterator<K,V>(map, this);
        }
        public final K next() {
            K k;
            if ((k = (nextVal == null) ? advanceKey() : nextKey) == null)
                throw new NoSuchElementException();
            nextVal = null;
            return k;
        }

        public final K nextElement() { return next(); }

        public Iterator<K> iterator() { return this; }

        public void forEach(Consumer<? super K> action) {
            forEachKey(action);
        }

        public boolean tryAdvance(Consumer<? super K> block) {
            if (block == null) throw new NullPointerException();
            K k;
            if ((k = advanceKey()) == null)
                return false;
            block.accept(k);
            return true;
        }

        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.CONCURRENT |
                Spliterator.NONNULL;
        }

    }

    @SuppressWarnings("serial") static final class ValueIterator<K,V>
        extends Traverser<K,V,Object>
        implements Spliterator<V>, Iterator<V>, Enumeration<V> {
        ValueIterator(ConcurrentHashMap<K,V> map) { super(map); }
        ValueIterator(ConcurrentHashMap<K,V> map, Traverser<K,V,Object> it) {
            super(map, it);
        }
        public Spliterator<V> trySplit() {
            return (baseLimit - baseIndex <= 1) ? null :
                new ValueIterator<K,V>(map, this);
        }

        public final V next() {
            V v;
            if ((v = nextVal) == null && (v = advanceValue()) == null)
                throw new NoSuchElementException();
            nextVal = null;
            return v;
        }

        public final V nextElement() { return next(); }

        public Iterator<V> iterator() { return this; }

        public void forEach(Consumer<? super V> action) {
            forEachValue(action);
        }

        public boolean tryAdvance(Consumer<? super V> block) {
            V v;
            if (block == null) throw new NullPointerException();
            if ((v = advanceValue()) == null)
                return false;
            block.accept(v);
            return true;
        }

        public int characteristics() {
            return Spliterator.CONCURRENT | Spliterator.NONNULL;
        }
    }

    @SuppressWarnings("serial") static final class EntryIterator<K,V>
        extends Traverser<K,V,Object>
        implements Spliterator<Map.Entry<K,V>>, Iterator<Map.Entry<K,V>> {
        EntryIterator(ConcurrentHashMap<K,V> map) { super(map); }
        EntryIterator(ConcurrentHashMap<K,V> map, Traverser<K,V,Object> it) {
            super(map, it);
        }
        public Spliterator<Map.Entry<K,V>> trySplit() {
            return (baseLimit - baseIndex <= 1) ? null :
                new EntryIterator<K,V>(map, this);
        }

        public final Map.Entry<K,V> next() {
            V v;
            if ((v = nextVal) == null && (v = advanceValue()) == null)
                throw new NoSuchElementException();
            K k = nextKey;
            nextVal = null;
            return new MapEntry<K,V>(k, v, map);
        }

        public Iterator<Map.Entry<K,V>> iterator() { return this; }

        public void forEach(Consumer<? super Map.Entry<K,V>> action) {
            if (action == null) throw new NullPointerException();
            V v;
            while ((v = advanceValue()) != null)
                action.accept(entryFor(nextKey, v));
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> block) {
            V v;
            if (block == null) throw new NullPointerException();
            if ((v = advanceValue()) == null)
                return false;
            block.accept(entryFor(nextKey, v));
            return true;
        }

        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.CONCURRENT |
                Spliterator.NONNULL;
        }
    }

    /**
     * Exported Entry for iterators
     */
    static final class MapEntry<K,V> implements Map.Entry<K,V> {
        final K key; // non-null
        V val;       // non-null
        final ConcurrentHashMap<K,V> map;
        MapEntry(K key, V val, ConcurrentHashMap<K,V> map) {
            this.key = key;
            this.val = val;
            this.map = map;
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
         * value to return is somewhat arbitrary here. Since we do not
         * necessarily track asynchronous changes, the most recent
         * "previous" value could be different from what we return (or
         * could even have been removed in which case the put will
         * re-establish). We do not and cannot guarantee more.
         */
        public final V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = val;
            val = value;
            map.put(key, value);
            return v;
        }
    }

    /**
     * Returns exportable snapshot entry for the given key and value
     * when write-through can't or shouldn't be used.
     */
    static <K,V> AbstractMap.SimpleEntry<K,V> entryFor(K k, V v) {
        return new AbstractMap.SimpleEntry<K,V>(k, v);
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
     * Saves the state of the {@code ConcurrentHashMap} instance to a
     * stream (i.e., serializes it).
     * @param s the stream
     * @serialData
     * the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    @SuppressWarnings("unchecked") private void writeObject
        (java.io.ObjectOutputStream s)
        throws java.io.IOException {
        if (segments == null) { // for serialization compatibility
            segments = (Segment<K,V>[])
                new Segment<?,?>[DEFAULT_CONCURRENCY_LEVEL];
            for (int i = 0; i < segments.length; ++i)
                segments[i] = new Segment<K,V>(LOAD_FACTOR);
        }
        s.defaultWriteObject();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v;
        while ((v = it.advanceValue()) != null) {
            s.writeObject(it.nextKey);
            s.writeObject(v);
        }
        s.writeObject(null);
        s.writeObject(null);
        segments = null; // throw away
    }

    /**
     * Reconstitutes the instance from a stream (that is, deserializes it).
     * @param s the stream
     */
    @SuppressWarnings("unchecked") private void readObject
        (java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        this.segments = null; // unneeded

        // Create all nodes, then place in table once size is known
        long size = 0L;
        Node<V> p = null;
        for (;;) {
            K k = (K) s.readObject();
            V v = (V) s.readObject();
            if (k != null && v != null) {
                int h = spread(k.hashCode());
                p = new Node<V>(h, k, v, p);
                ++size;
            }
            else
                break;
        }
        if (p != null) {
            boolean init = false;
            int n;
            if (size >= (long)(MAXIMUM_CAPACITY >>> 1))
                n = MAXIMUM_CAPACITY;
            else {
                int sz = (int)size;
                n = tableSizeFor(sz + (sz >>> 1) + 1);
            }
            int sc = sizeCtl;
            boolean collide = false;
            if (n > sc &&
                U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                try {
                    if (table == null) {
                        init = true;
                        @SuppressWarnings("rawtypes") Node[] rt = new Node[n];
                        Node<V>[] tab = (Node<V>[])rt;
                        int mask = n - 1;
                        while (p != null) {
                            int j = p.hash & mask;
                            Node<V> next = p.next;
                            Node<V> q = p.next = tabAt(tab, j);
                            setTabAt(tab, j, p);
                            if (!collide && q != null && q.hash == p.hash)
                                collide = true;
                            p = next;
                        }
                        table = tab;
                        addCount(size, -1);
                        sc = n - (n >>> 2);
                    }
                } finally {
                    sizeCtl = sc;
                }
                if (collide) { // rescan and convert to TreeBins
                    Node<V>[] tab = table;
                    for (int i = 0; i < tab.length; ++i) {
                        int c = 0;
                        for (Node<V> e = tabAt(tab, i); e != null; e = e.next) {
                            if (++c > TREE_THRESHOLD &&
                                (e.key instanceof Comparable)) {
                                replaceWithTreeBin(tab, i, e.key);
                                break;
                            }
                        }
                    }
                }
            }
            if (!init) { // Can only happen if unsafely published.
                while (p != null) {
                    internalPut((K)p.key, p.val, false);
                    p = p.next;
                }
            }
        }
    }

    // -------------------------------------------------------

    // Sequential bulk operations

    /**
     * Performs the given action for each (key, value).
     *
     * @param action the action
     */
    public void forEachSequentially
        (BiConsumer<? super K, ? super V> action) {
        if (action == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v;
        while ((v = it.advanceValue()) != null)
            action.accept(it.nextKey, v);
    }

    /**
     * Performs the given action for each non-null transformation
     * of each (key, value).
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachSequentially
        (BiFunction<? super K, ? super V, ? extends U> transformer,
         Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v; U u;
        while ((v = it.advanceValue()) != null) {
            if ((u = transformer.apply(it.nextKey, v)) != null)
                action.accept(u);
        }
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each (key, value), or null if none.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each (key, value), or null if none
     */
    public <U> U searchSequentially
        (BiFunction<? super K, ? super V, ? extends U> searchFunction) {
        if (searchFunction == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v; U u;
        while ((v = it.advanceValue()) != null) {
            if ((u = searchFunction.apply(it.nextKey, v)) != null)
                return u;
        }
        return null;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, or null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public <U> U reduceSequentially
        (BiFunction<? super K, ? super V, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        U r = null, u; V v;
        while ((v = it.advanceValue()) != null) {
            if ((u = transformer.apply(it.nextKey, v)) != null)
                r = (r == null) ? u : reducer.apply(r, u);
        }
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public double reduceToDoubleSequentially
        (ToDoubleBiFunction<? super K, ? super V> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        double r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(it.nextKey, v));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public long reduceToLongSequentially
        (ToLongBiFunction<? super K, ? super V> transformer,
         long basis,
         LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        long r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsLong(r, transformer.applyAsLong(it.nextKey, v));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public int reduceToIntSequentially
        (ToIntBiFunction<? super K, ? super V> transformer,
         int basis,
         IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        int r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsInt(r, transformer.applyAsInt(it.nextKey, v));
        return r;
    }

    /**
     * Performs the given action for each key.
     *
     * @param action the action
     */
    public void forEachKeySequentially
        (Consumer<? super K> action) {
        new Traverser<K,V,Object>(this).forEachKey(action);
    }

    /**
     * Performs the given action for each non-null transformation
     * of each key.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachKeySequentially
        (Function<? super K, ? extends U> transformer,
         Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        K k; U u;
        while ((k = it.advanceKey()) != null) {
            if ((u = transformer.apply(k)) != null)
                action.accept(u);
        }
        ForkJoinTasks.forEachKey
            (this, transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each key, or null if none.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each key, or null if none
     */
    public <U> U searchKeysSequentially
        (Function<? super K, ? extends U> searchFunction) {
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        K k; U u;
        while ((k = it.advanceKey()) != null) {
            if ((u = searchFunction.apply(k)) != null)
                return u;
        }
        return null;
    }

    /**
     * Returns the result of accumulating all keys using the given
     * reducer to combine values, or null if none.
     *
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all keys using the given
     * reducer to combine values, or null if none
     */
    public K reduceKeysSequentially
        (BiFunction<? super K, ? super K, ? extends K> reducer) {
        if (reducer == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        K u, r = null;
        while ((u = it.advanceKey()) != null) {
            r = (r == null) ? u : reducer.apply(r, u);
        }
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, or
     * null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public <U> U reduceKeysSequentially
        (Function<? super K, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        K k; U r = null, u;
        while ((k = it.advanceKey()) != null) {
            if ((u = transformer.apply(k)) != null)
                r = (r == null) ? u : reducer.apply(r, u);
        }
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public double reduceKeysToDoubleSequentially
        (ToDoubleFunction<? super K> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        double r = basis;
        K k;
        while ((k = it.advanceKey()) != null)
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(k));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public long reduceKeysToLongSequentially
        (ToLongFunction<? super K> transformer,
         long basis,
         LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        long r = basis;
        K k;
        while ((k = it.advanceKey()) != null)
            r = reducer.applyAsLong(r, transformer.applyAsLong(k));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public int reduceKeysToIntSequentially
        (ToIntFunction<? super K> transformer,
         int basis,
         IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        int r = basis;
        K k;
        while ((k = it.advanceKey()) != null)
            r = reducer.applyAsInt(r, transformer.applyAsInt(k));
        return r;
    }

    /**
     * Performs the given action for each value.
     *
     * @param action the action
     */
    public void forEachValueSequentially(Consumer<? super V> action) {
        new Traverser<K,V,Object>(this).forEachValue(action);
    }

    /**
     * Performs the given action for each non-null transformation
     * of each value.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachValueSequentially
        (Function<? super V, ? extends U> transformer,
         Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v; U u;
        while ((v = it.advanceValue()) != null) {
            if ((u = transformer.apply(v)) != null)
                action.accept(u);
        }
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each value, or null if none.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each value, or null if none
     */
    public <U> U searchValuesSequentially
        (Function<? super V, ? extends U> searchFunction) {
        if (searchFunction == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v; U u;
        while ((v = it.advanceValue()) != null) {
            if ((u = searchFunction.apply(v)) != null)
                return u;
        }
        return null;
    }

    /**
     * Returns the result of accumulating all values using the
     * given reducer to combine values, or null if none.
     *
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all values
     */
    public V reduceValuesSequentially
        (BiFunction<? super V, ? super V, ? extends V> reducer) {
        if (reducer == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V r = null; V v;
        while ((v = it.advanceValue()) != null)
            r = (r == null) ? v : reducer.apply(r, v);
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values, or
     * null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public <U> U reduceValuesSequentially
        (Function<? super V, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        U r = null, u; V v;
        while ((v = it.advanceValue()) != null) {
            if ((u = transformer.apply(v)) != null)
                r = (r == null) ? u : reducer.apply(r, u);
        }
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public double reduceValuesToDoubleSequentially
        (ToDoubleFunction<? super V> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        double r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(v));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public long reduceValuesToLongSequentially
        (ToLongFunction<? super V> transformer,
         long basis,
         LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        long r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsLong(r, transformer.applyAsLong(v));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public int reduceValuesToIntSequentially
        (ToIntFunction<? super V> transformer,
         int basis,
         IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        int r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsInt(r, transformer.applyAsInt(v));
        return r;
    }

    /**
     * Performs the given action for each entry.
     *
     * @param action the action
     */
    public void forEachEntrySequentially
        (Consumer<? super Map.Entry<K,V>> action) {
        if (action == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v;
        while ((v = it.advanceValue()) != null)
            action.accept(entryFor(it.nextKey, v));
    }

    /**
     * Performs the given action for each non-null transformation
     * of each entry.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachEntrySequentially
        (Function<Map.Entry<K,V>, ? extends U> transformer,
         Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v; U u;
        while ((v = it.advanceValue()) != null) {
            if ((u = transformer.apply(entryFor(it.nextKey, v))) != null)
                action.accept(u);
        }
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each entry, or null if none.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each entry, or null if none
     */
    public <U> U searchEntriesSequentially
        (Function<Map.Entry<K,V>, ? extends U> searchFunction) {
        if (searchFunction == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        V v; U u;
        while ((v = it.advanceValue()) != null) {
            if ((u = searchFunction.apply(entryFor(it.nextKey, v))) != null)
                return u;
        }
        return null;
    }

    /**
     * Returns the result of accumulating all entries using the
     * given reducer to combine values, or null if none.
     *
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all entries
     */
    public Map.Entry<K,V> reduceEntriesSequentially
        (BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer) {
        if (reducer == null) throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        Map.Entry<K,V> r = null; V v;
        while ((v = it.advanceValue()) != null) {
            Map.Entry<K,V> u = entryFor(it.nextKey, v);
            r = (r == null) ? u : reducer.apply(r, u);
        }
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * or null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public <U> U reduceEntriesSequentially
        (Function<Map.Entry<K,V>, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        U r = null, u; V v;
        while ((v = it.advanceValue()) != null) {
            if ((u = transformer.apply(entryFor(it.nextKey, v))) != null)
                r = (r == null) ? u : reducer.apply(r, u);
        }
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public double reduceEntriesToDoubleSequentially
        (ToDoubleFunction<Map.Entry<K,V>> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        double r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(entryFor(it.nextKey, v)));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public long reduceEntriesToLongSequentially
        (ToLongFunction<Map.Entry<K,V>> transformer,
         long basis,
         LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        long r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsLong(r, transformer.applyAsLong(entryFor(it.nextKey, v)));
        return r;
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public int reduceEntriesToIntSequentially
        (ToIntFunction<Map.Entry<K,V>> transformer,
         int basis,
         IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        Traverser<K,V,Object> it = new Traverser<K,V,Object>(this);
        int r = basis; V v;
        while ((v = it.advanceValue()) != null)
            r = reducer.applyAsInt(r, transformer.applyAsInt(entryFor(it.nextKey, v)));
        return r;
    }

    // Parallel bulk operations

    /**
     * Performs the given action for each (key, value).
     *
     * @param action the action
     */
    public void forEachInParallel(BiConsumer<? super K,? super V> action) {
        ForkJoinTasks.forEach
            (this, action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each (key, value).
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachInParallel
        (BiFunction<? super K, ? super V, ? extends U> transformer,
                            Consumer<? super U> action) {
        ForkJoinTasks.forEach
            (this, transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each (key, value), or null if none.  Upon
     * success, further element processing is suppressed and the
     * results of any other parallel invocations of the search
     * function are ignored.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each (key, value), or null if none
     */
    public <U> U searchInParallel
        (BiFunction<? super K, ? super V, ? extends U> searchFunction) {
        return ForkJoinTasks.search
            (this, searchFunction).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, or null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public <U> U reduceInParallel
        (BiFunction<? super K, ? super V, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        return ForkJoinTasks.reduce
            (this, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public double reduceToDoubleInParallel
        (ToDoubleBiFunction<? super K, ? super V> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        return ForkJoinTasks.reduceToDouble
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public long reduceToLongInParallel
        (ToLongBiFunction<? super K, ? super V> transformer,
         long basis,
         LongBinaryOperator reducer) {
        return ForkJoinTasks.reduceToLong
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     */
    public int reduceToIntInParallel
        (ToIntBiFunction<? super K, ? super V> transformer,
         int basis,
         IntBinaryOperator reducer) {
        return ForkJoinTasks.reduceToInt
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Performs the given action for each key.
     *
     * @param action the action
     */
    public void forEachKeyInParallel(Consumer<? super K> action) {
        ForkJoinTasks.forEachKey
            (this, action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each key.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachKeyInParallel
        (Function<? super K, ? extends U> transformer,
         Consumer<? super U> action) {
        ForkJoinTasks.forEachKey
            (this, transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each key, or null if none. Upon success,
     * further element processing is suppressed and the results of
     * any other parallel invocations of the search function are
     * ignored.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each key, or null if none
     */
    public <U> U searchKeysInParallel
        (Function<? super K, ? extends U> searchFunction) {
        return ForkJoinTasks.searchKeys
            (this, searchFunction).invoke();
    }

    /**
     * Returns the result of accumulating all keys using the given
     * reducer to combine values, or null if none.
     *
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all keys using the given
     * reducer to combine values, or null if none
     */
    public K reduceKeysInParallel
        (BiFunction<? super K, ? super K, ? extends K> reducer) {
        return ForkJoinTasks.reduceKeys
            (this, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, or
     * null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public <U> U reduceKeysInParallel
        (Function<? super K, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        return ForkJoinTasks.reduceKeys
            (this, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public double reduceKeysToDoubleInParallel
        (ToDoubleFunction<? super K> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        return ForkJoinTasks.reduceKeysToDouble
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public long reduceKeysToLongInParallel
        (ToLongFunction<? super K> transformer,
         long basis,
         LongBinaryOperator reducer) {
        return ForkJoinTasks.reduceKeysToLong
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     */
    public int reduceKeysToIntInParallel
        (ToIntFunction<? super K> transformer,
         int basis,
         IntBinaryOperator reducer) {
        return ForkJoinTasks.reduceKeysToInt
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Performs the given action for each value.
     *
     * @param action the action
     */
    public void forEachValueInParallel(Consumer<? super V> action) {
        ForkJoinTasks.forEachValue
            (this, action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each value.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachValueInParallel
        (Function<? super V, ? extends U> transformer,
         Consumer<? super U> action) {
        ForkJoinTasks.forEachValue
            (this, transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each value, or null if none.  Upon success,
     * further element processing is suppressed and the results of
     * any other parallel invocations of the search function are
     * ignored.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each value, or null if none
     */
    public <U> U searchValuesInParallel
        (Function<? super V, ? extends U> searchFunction) {
        return ForkJoinTasks.searchValues
            (this, searchFunction).invoke();
    }

    /**
     * Returns the result of accumulating all values using the
     * given reducer to combine values, or null if none.
     *
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all values
     */
    public V reduceValuesInParallel
        (BiFunction<? super V, ? super V, ? extends V> reducer) {
        return ForkJoinTasks.reduceValues
            (this, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values, or
     * null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public <U> U reduceValuesInParallel
        (Function<? super V, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        return ForkJoinTasks.reduceValues
            (this, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public double reduceValuesToDoubleInParallel
        (ToDoubleFunction<? super V> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        return ForkJoinTasks.reduceValuesToDouble
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public long reduceValuesToLongInParallel
        (ToLongFunction<? super V> transformer,
         long basis,
         LongBinaryOperator reducer) {
        return ForkJoinTasks.reduceValuesToLong
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     */
    public int reduceValuesToIntInParallel
        (ToIntFunction<? super V> transformer,
         int basis,
         IntBinaryOperator reducer) {
        return ForkJoinTasks.reduceValuesToInt
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Performs the given action for each entry.
     *
     * @param action the action
     */
    public void forEachEntryInParallel(Consumer<? super Map.Entry<K,V>> action) {
        ForkJoinTasks.forEachEntry
            (this, action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each entry.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     */
    public <U> void forEachEntryInParallel
        (Function<Map.Entry<K,V>, ? extends U> transformer,
         Consumer<? super U> action) {
        ForkJoinTasks.forEachEntry
            (this, transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each entry, or null if none.  Upon success,
     * further element processing is suppressed and the results of
     * any other parallel invocations of the search function are
     * ignored.
     *
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @return a non-null result from applying the given search
     * function on each entry, or null if none
     */
    public <U> U searchEntriesInParallel
        (Function<Map.Entry<K,V>, ? extends U> searchFunction) {
        return ForkJoinTasks.searchEntries
            (this, searchFunction).invoke();
    }

    /**
     * Returns the result of accumulating all entries using the
     * given reducer to combine values, or null if none.
     *
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all entries
     */
    public Map.Entry<K,V> reduceEntriesInParallel
        (BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer) {
        return ForkJoinTasks.reduceEntries
            (this, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * or null if none.
     *
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public <U> U reduceEntriesInParallel
        (Function<Map.Entry<K,V>, ? extends U> transformer,
         BiFunction<? super U, ? super U, ? extends U> reducer) {
        return ForkJoinTasks.reduceEntries
            (this, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public double reduceEntriesToDoubleInParallel
        (ToDoubleFunction<Map.Entry<K,V>> transformer,
         double basis,
         DoubleBinaryOperator reducer) {
        return ForkJoinTasks.reduceEntriesToDouble
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public long reduceEntriesToLongInParallel
        (ToLongFunction<Map.Entry<K,V>> transformer,
         long basis,
         LongBinaryOperator reducer) {
        return ForkJoinTasks.reduceEntriesToLong
            (this, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     */
    public int reduceEntriesToIntInParallel
        (ToIntFunction<Map.Entry<K,V>> transformer,
         int basis,
         IntBinaryOperator reducer) {
        return ForkJoinTasks.reduceEntriesToInt
            (this, transformer, basis, reducer).invoke();
    }


    /* ----------------Views -------------- */

    /**
     * Base class for views.
     */
    abstract static class CHMCollectionView<K,V,E>
            implements Collection<E>, java.io.Serializable {
        private static final long serialVersionUID = 7249069246763182397L;
        final ConcurrentHashMap<K,V> map;
        CHMCollectionView(ConcurrentHashMap<K,V> map)  { this.map = map; }

        /**
         * Returns the map backing this view.
         *
         * @return the map backing this view
         */
        public ConcurrentHashMap<K,V> getMap() { return map; }

        /**
         * Removes all of the elements from this view, by removing all
         * the mappings from the map backing this view.
         */
        public final void clear()      { map.clear(); }
        public final int size()        { return map.size(); }
        public final boolean isEmpty() { return map.isEmpty(); }

        // implementations below rely on concrete classes supplying these
        // abstract methods
        /**
         * Returns a "weakly consistent" iterator that will never
         * throw {@link ConcurrentModificationException}, and
         * guarantees to traverse elements as they existed upon
         * construction of the iterator, and may (but is not
         * guaranteed to) reflect any modifications subsequent to
         * construction.
         */
        public abstract Iterator<E> iterator();
        public abstract boolean contains(Object o);
        public abstract boolean remove(Object o);

        private static final String oomeMsg = "Required array size too large";

        public final Object[] toArray() {
            long sz = map.mappingCount();
            if (sz > MAX_ARRAY_SIZE)
                throw new OutOfMemoryError(oomeMsg);
            int n = (int)sz;
            Object[] r = new Object[n];
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError(oomeMsg);
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
                        n = MAX_ARRAY_SIZE;
                    else
                        n += (n >>> 1) + 1;
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = e;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        @SuppressWarnings("unchecked")
        public final <T> T[] toArray(T[] a) {
            long sz = map.mappingCount();
            if (sz > MAX_ARRAY_SIZE)
                throw new OutOfMemoryError(oomeMsg);
            int m = (int)sz;
            T[] r = (a.length >= m) ? a :
                (T[])java.lang.reflect.Array
                .newInstance(a.getClass().getComponentType(), m);
            int n = r.length;
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError(oomeMsg);
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
                        n = MAX_ARRAY_SIZE;
                    else
                        n += (n >>> 1) + 1;
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = (T)e;
            }
            if (a == r && i < n) {
                r[i] = null; // null-terminate
                return r;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        /**
         * Returns a string representation of this collection.
         * The string representation consists of the string representations
         * of the collection's elements in the order they are returned by
         * its iterator, enclosed in square brackets ({@code "[]"}).
         * Adjacent elements are separated by the characters {@code ", "}
         * (comma and space).  Elements are converted to strings as by
         * {@link String#valueOf(Object)}.
         *
         * @return a string representation of this collection
         */
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            Iterator<E> it = iterator();
            if (it.hasNext()) {
                for (;;) {
                    Object e = it.next();
                    sb.append(e == this ? "(this Collection)" : e);
                    if (!it.hasNext())
                        break;
                    sb.append(',').append(' ');
                }
            }
            return sb.append(']').toString();
        }

        public final boolean containsAll(Collection<?> c) {
            if (c != this) {
                for (Object e : c) {
                    if (e == null || !contains(e))
                        return false;
                }
            }
            return true;
        }

        public final boolean removeAll(Collection<?> c) {
            boolean modified = false;
            for (Iterator<E> it = iterator(); it.hasNext();) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        public final boolean retainAll(Collection<?> c) {
            boolean modified = false;
            for (Iterator<E> it = iterator(); it.hasNext();) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

    }

    abstract static class CHMSetView<K,V,E>
            extends CHMCollectionView<K,V,E>
            implements Set<E>, java.io.Serializable {
        private static final long serialVersionUID = 7249069246763182397L;
        CHMSetView(ConcurrentHashMap<K,V> map) { super(map); }

        // Implement Set API

        /**
         * Implements {@link Set#hashCode()}.
         * @return the hash code value for this set
         */
        public final int hashCode() {
            int h = 0;
            for (E e : this)
                h += e.hashCode();
            return h;
        }

        /**
         * Implements {@link Set#equals(Object)}.
         * @param o object to be compared for equality with this set
         * @return {@code true} if the specified object is equal to this set
        */
        public final boolean equals(Object o) {
            Set<?> c;
            return ((o instanceof Set) &&
                    ((c = (Set<?>)o) == this ||
                     (containsAll(c) && c.containsAll(this))));
        }
    }

    /**
     * A view of a ConcurrentHashMap as a {@link Set} of keys, in
     * which additions may optionally be enabled by mapping to a
     * common value.  This class cannot be directly instantiated.
     * See {@link #keySet() keySet()},
     * {@link #keySet(Object) keySet(V)},
     * {@link #newKeySet() newKeySet()},
     * {@link #newKeySet(int) newKeySet(int)}.
     */
    public static class KeySetView<K,V>
            extends CHMSetView<K,V,K>
            implements Set<K>, java.io.Serializable {
        private static final long serialVersionUID = 7249069246763182397L;
        private final V value;
        KeySetView(ConcurrentHashMap<K,V> map, V value) {  // non-public
            super(map);
            this.value = value;
        }

        /**
         * Returns the default mapped value for additions,
         * or {@code null} if additions are not supported.
         *
         * @return the default mapped value for additions, or {@code null}
         * if not supported
         */
        public V getMappedValue() { return value; }

        /**
         * {@inheritDoc}
         * @throws NullPointerException if the specified key is null
         */
        public boolean contains(Object o) { return map.containsKey(o); }

        /**
         * Removes the key from this map view, by removing the key (and its
         * corresponding value) from the backing map.  This method does
         * nothing if the key is not in the map.
         *
         * @param  o the key to be removed from the backing map
         * @return {@code true} if the backing map contained the specified key
         * @throws NullPointerException if the specified key is null
         */
        public boolean remove(Object o) { return map.remove(o) != null; }

        /**
         * @return an iterator over the keys of the backing map
         */
        public Iterator<K> iterator() { return new KeyIterator<K,V>(map); }

        /**
         * Adds the specified key to this set view by mapping the key to
         * the default mapped value in the backing map, if defined.
         *
         * @param e key to be added
         * @return {@code true} if this set changed as a result of the call
         * @throws NullPointerException if the specified key is null
         * @throws UnsupportedOperationException if no default mapped value
         * for additions was provided
         */
        public boolean add(K e) {
            V v;
            if ((v = value) == null)
                throw new UnsupportedOperationException();
            return map.internalPut(e, v, true) == null;
        }

        /**
         * Adds all of the elements in the specified collection to this set,
         * as if by calling {@link #add} on each one.
         *
         * @param c the elements to be inserted into this set
         * @return {@code true} if this set changed as a result of the call
         * @throws NullPointerException if the collection or any of its
         * elements are {@code null}
         * @throws UnsupportedOperationException if no default mapped value
         * for additions was provided
         */
        public boolean addAll(Collection<? extends K> c) {
            boolean added = false;
            V v;
            if ((v = value) == null)
                throw new UnsupportedOperationException();
            for (K e : c) {
                if (map.internalPut(e, v, true) == null)
                    added = true;
            }
            return added;
        }

        public Spliterator<K> spliterator() {
            return new KeyIterator<>(map, null);
        }

    }

    /**
     * A view of a ConcurrentHashMap as a {@link Collection} of
     * values, in which additions are disabled. This class cannot be
     * directly instantiated. See {@link #values()}.
     *
     * <p>The view's {@code iterator} is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public static final class ValuesView<K,V>
            extends CHMCollectionView<K,V,V>
            implements Collection<V>, java.io.Serializable {
        private static final long serialVersionUID = 2249069246763182397L;
        ValuesView(ConcurrentHashMap<K,V> map) { super(map); }
        public final boolean contains(Object o) {
            return map.containsValue(o);
        }
        public final boolean remove(Object o) {
            if (o != null) {
                for (Iterator<V> it = iterator(); it.hasNext();) {
                    if (o.equals(it.next())) {
                        it.remove();
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * @return an iterator over the values of the backing map
         */
        public final Iterator<V> iterator() {
            return new ValueIterator<K,V>(map);
        }

        /** Always throws {@link UnsupportedOperationException}. */
        public final boolean add(V e) {
            throw new UnsupportedOperationException();
        }
        /** Always throws {@link UnsupportedOperationException}. */
        public final boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        public Spliterator<V> spliterator() {
            return new ValueIterator<K,V>(map, null);
        }

    }

    /**
     * A view of a ConcurrentHashMap as a {@link Set} of (key, value)
     * entries.  This class cannot be directly instantiated. See
     * {@link #entrySet()}.
     */
    public static final class EntrySetView<K,V>
            extends CHMSetView<K,V,Map.Entry<K,V>>
            implements Set<Map.Entry<K,V>>, java.io.Serializable {
        private static final long serialVersionUID = 2249069246763182397L;
        EntrySetView(ConcurrentHashMap<K,V> map) { super(map); }

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

        /**
         * @return an iterator over the entries of the backing map
         */
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator<K,V>(map);
        }

        /**
         * Adds the specified mapping to this view.
         *
         * @param e mapping to be added
         * @return {@code true} if this set changed as a result of the call
         * @throws NullPointerException if the entry, its key, or its
         * value is null
         */
        public final boolean add(Entry<K,V> e) {
            return map.internalPut(e.getKey(), e.getValue(), false) == null;
        }
        /**
         * Adds all of the mappings in the specified collection to this
         * set, as if by calling {@link #add(Map.Entry)} on each one.
         * @param c the mappings to be inserted into this set
         * @return {@code true} if this set changed as a result of the call
         * @throws NullPointerException if the collection or any of its
         * entries, keys, or values are null
         */
        public final boolean addAll(Collection<? extends Entry<K,V>> c) {
            boolean added = false;
            for (Entry<K,V> e : c) {
                if (add(e))
                    added = true;
            }
            return added;
        }

        public Spliterator<Map.Entry<K,V>> spliterator() {
            return new EntryIterator<K,V>(map, null);
        }

    }

    // ---------------------------------------------------------------------

    /**
     * Predefined tasks for performing bulk parallel operations on
     * ConcurrentHashMaps. These tasks follow the forms and rules used
     * for bulk operations. Each method has the same name, but returns
     * a task rather than invoking it. These methods may be useful in
     * custom applications such as submitting a task without waiting
     * for completion, using a custom pool, or combining with other
     * tasks.
     */
    public static class ForkJoinTasks {
        private ForkJoinTasks() {}

        /**
         * Returns a task that when invoked, performs the given
         * action for each (key, value)
         *
         * @param map the map
         * @param action the action
         * @return the task
         */
        public static <K,V> ForkJoinTask<Void> forEach
            (ConcurrentHashMap<K,V> map,
             BiConsumer<? super K, ? super V> action) {
            if (action == null) throw new NullPointerException();
            return new ForEachMappingTask<K,V>(map, null, -1, action);
        }

        /**
         * Returns a task that when invoked, performs the given
         * action for each non-null transformation of each (key, value)
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case the action is not applied)
         * @param action the action
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<Void> forEach
            (ConcurrentHashMap<K,V> map,
             BiFunction<? super K, ? super V, ? extends U> transformer,
             Consumer<? super U> action) {
            if (transformer == null || action == null)
                throw new NullPointerException();
            return new ForEachTransformedMappingTask<K,V,U>
                (map, null, -1, transformer, action);
        }

        /**
         * Returns a task that when invoked, returns a non-null result
         * from applying the given search function on each (key,
         * value), or null if none. Upon success, further element
         * processing is suppressed and the results of any other
         * parallel invocations of the search function are ignored.
         *
         * @param map the map
         * @param searchFunction a function returning a non-null
         * result on success, else null
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> search
            (ConcurrentHashMap<K,V> map,
             BiFunction<? super K, ? super V, ? extends U> searchFunction) {
            if (searchFunction == null) throw new NullPointerException();
            return new SearchMappingsTask<K,V,U>
                (map, null, -1, searchFunction,
                 new AtomicReference<U>());
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all (key, value) pairs
         * using the given reducer to combine values, or null if none.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case it is not combined)
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> reduce
            (ConcurrentHashMap<K,V> map,
             BiFunction<? super K, ? super V, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceMappingsTask<K,V,U>
                (map, null, -1, null, transformer, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all (key, value) pairs
         * using the given reducer to combine values, and the given
         * basis as an identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Double> reduceToDouble
            (ConcurrentHashMap<K,V> map,
             ToDoubleBiFunction<? super K, ? super V> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceMappingsToDoubleTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all (key, value) pairs
         * using the given reducer to combine values, and the given
         * basis as an identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Long> reduceToLong
            (ConcurrentHashMap<K,V> map,
             ToLongBiFunction<? super K, ? super V> transformer,
             long basis,
             LongBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceMappingsToLongTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all (key, value) pairs
         * using the given reducer to combine values, and the given
         * basis as an identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Integer> reduceToInt
            (ConcurrentHashMap<K,V> map,
             ToIntBiFunction<? super K, ? super V> transformer,
             int basis,
             IntBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceMappingsToIntTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, performs the given action
         * for each key.
         *
         * @param map the map
         * @param action the action
         * @return the task
         */
        public static <K,V> ForkJoinTask<Void> forEachKey
            (ConcurrentHashMap<K,V> map,
             Consumer<? super K> action) {
            if (action == null) throw new NullPointerException();
            return new ForEachKeyTask<K,V>(map, null, -1, action);
        }

        /**
         * Returns a task that when invoked, performs the given action
         * for each non-null transformation of each key.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case the action is not applied)
         * @param action the action
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<Void> forEachKey
            (ConcurrentHashMap<K,V> map,
             Function<? super K, ? extends U> transformer,
             Consumer<? super U> action) {
            if (transformer == null || action == null)
                throw new NullPointerException();
            return new ForEachTransformedKeyTask<K,V,U>
                (map, null, -1, transformer, action);
        }

        /**
         * Returns a task that when invoked, returns a non-null result
         * from applying the given search function on each key, or
         * null if none.  Upon success, further element processing is
         * suppressed and the results of any other parallel
         * invocations of the search function are ignored.
         *
         * @param map the map
         * @param searchFunction a function returning a non-null
         * result on success, else null
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> searchKeys
            (ConcurrentHashMap<K,V> map,
             Function<? super K, ? extends U> searchFunction) {
            if (searchFunction == null) throw new NullPointerException();
            return new SearchKeysTask<K,V,U>
                (map, null, -1, searchFunction,
                 new AtomicReference<U>());
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating all keys using the given reducer to combine
         * values, or null if none.
         *
         * @param map the map
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<K> reduceKeys
            (ConcurrentHashMap<K,V> map,
             BiFunction<? super K, ? super K, ? extends K> reducer) {
            if (reducer == null) throw new NullPointerException();
            return new ReduceKeysTask<K,V>
                (map, null, -1, null, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all keys using the given
         * reducer to combine values, or null if none.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case it is not combined)
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> reduceKeys
            (ConcurrentHashMap<K,V> map,
             Function<? super K, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceKeysTask<K,V,U>
                (map, null, -1, null, transformer, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all keys using the given
         * reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Double> reduceKeysToDouble
            (ConcurrentHashMap<K,V> map,
             ToDoubleFunction<? super K> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceKeysToDoubleTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all keys using the given
         * reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Long> reduceKeysToLong
            (ConcurrentHashMap<K,V> map,
             ToLongFunction<? super K> transformer,
             long basis,
             LongBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceKeysToLongTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all keys using the given
         * reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Integer> reduceKeysToInt
            (ConcurrentHashMap<K,V> map,
             ToIntFunction<? super K> transformer,
             int basis,
             IntBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceKeysToIntTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, performs the given action
         * for each value.
         *
         * @param map the map
         * @param action the action
         * @return the task
         */
        public static <K,V> ForkJoinTask<Void> forEachValue
            (ConcurrentHashMap<K,V> map,
             Consumer<? super V> action) {
            if (action == null) throw new NullPointerException();
            return new ForEachValueTask<K,V>(map, null, -1, action);
        }

        /**
         * Returns a task that when invoked, performs the given action
         * for each non-null transformation of each value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case the action is not applied)
         * @param action the action
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<Void> forEachValue
            (ConcurrentHashMap<K,V> map,
             Function<? super V, ? extends U> transformer,
             Consumer<? super U> action) {
            if (transformer == null || action == null)
                throw new NullPointerException();
            return new ForEachTransformedValueTask<K,V,U>
                (map, null, -1, transformer, action);
        }

        /**
         * Returns a task that when invoked, returns a non-null result
         * from applying the given search function on each value, or
         * null if none.  Upon success, further element processing is
         * suppressed and the results of any other parallel
         * invocations of the search function are ignored.
         *
         * @param map the map
         * @param searchFunction a function returning a non-null
         * result on success, else null
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> searchValues
            (ConcurrentHashMap<K,V> map,
             Function<? super V, ? extends U> searchFunction) {
            if (searchFunction == null) throw new NullPointerException();
            return new SearchValuesTask<K,V,U>
                (map, null, -1, searchFunction,
                 new AtomicReference<U>());
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating all values using the given reducer to combine
         * values, or null if none.
         *
         * @param map the map
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<V> reduceValues
            (ConcurrentHashMap<K,V> map,
             BiFunction<? super V, ? super V, ? extends V> reducer) {
            if (reducer == null) throw new NullPointerException();
            return new ReduceValuesTask<K,V>
                (map, null, -1, null, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all values using the
         * given reducer to combine values, or null if none.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case it is not combined)
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> reduceValues
            (ConcurrentHashMap<K,V> map,
             Function<? super V, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceValuesTask<K,V,U>
                (map, null, -1, null, transformer, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all values using the
         * given reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Double> reduceValuesToDouble
            (ConcurrentHashMap<K,V> map,
             ToDoubleFunction<? super V> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceValuesToDoubleTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all values using the
         * given reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Long> reduceValuesToLong
            (ConcurrentHashMap<K,V> map,
             ToLongFunction<? super V> transformer,
             long basis,
             LongBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceValuesToLongTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all values using the
         * given reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Integer> reduceValuesToInt
            (ConcurrentHashMap<K,V> map,
             ToIntFunction<? super V> transformer,
             int basis,
             IntBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceValuesToIntTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, perform the given action
         * for each entry.
         *
         * @param map the map
         * @param action the action
         * @return the task
         */
        public static <K,V> ForkJoinTask<Void> forEachEntry
            (ConcurrentHashMap<K,V> map,
             Consumer<? super Map.Entry<K,V>> action) {
            if (action == null) throw new NullPointerException();
            return new ForEachEntryTask<K,V>(map, null, -1, action);
        }

        /**
         * Returns a task that when invoked, perform the given action
         * for each non-null transformation of each entry.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case the action is not applied)
         * @param action the action
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<Void> forEachEntry
            (ConcurrentHashMap<K,V> map,
             Function<Map.Entry<K,V>, ? extends U> transformer,
             Consumer<? super U> action) {
            if (transformer == null || action == null)
                throw new NullPointerException();
            return new ForEachTransformedEntryTask<K,V,U>
                (map, null, -1, transformer, action);
        }

        /**
         * Returns a task that when invoked, returns a non-null result
         * from applying the given search function on each entry, or
         * null if none.  Upon success, further element processing is
         * suppressed and the results of any other parallel
         * invocations of the search function are ignored.
         *
         * @param map the map
         * @param searchFunction a function returning a non-null
         * result on success, else null
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> searchEntries
            (ConcurrentHashMap<K,V> map,
             Function<Map.Entry<K,V>, ? extends U> searchFunction) {
            if (searchFunction == null) throw new NullPointerException();
            return new SearchEntriesTask<K,V,U>
                (map, null, -1, searchFunction,
                 new AtomicReference<U>());
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating all entries using the given reducer to combine
         * values, or null if none.
         *
         * @param map the map
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Map.Entry<K,V>> reduceEntries
            (ConcurrentHashMap<K,V> map,
             BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer) {
            if (reducer == null) throw new NullPointerException();
            return new ReduceEntriesTask<K,V>
                (map, null, -1, null, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all entries using the
         * given reducer to combine values, or null if none.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element, or null if there is no transformation (in
         * which case it is not combined)
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V,U> ForkJoinTask<U> reduceEntries
            (ConcurrentHashMap<K,V> map,
             Function<Map.Entry<K,V>, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceEntriesTask<K,V,U>
                (map, null, -1, null, transformer, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all entries using the
         * given reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Double> reduceEntriesToDouble
            (ConcurrentHashMap<K,V> map,
             ToDoubleFunction<Map.Entry<K,V>> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceEntriesToDoubleTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all entries using the
         * given reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Long> reduceEntriesToLong
            (ConcurrentHashMap<K,V> map,
             ToLongFunction<Map.Entry<K,V>> transformer,
             long basis,
             LongBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceEntriesToLongTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }

        /**
         * Returns a task that when invoked, returns the result of
         * accumulating the given transformation of all entries using the
         * given reducer to combine values, and the given basis as an
         * identity value.
         *
         * @param map the map
         * @param transformer a function returning the transformation
         * for an element
         * @param basis the identity (initial default value) for the reduction
         * @param reducer a commutative associative combining function
         * @return the task
         */
        public static <K,V> ForkJoinTask<Integer> reduceEntriesToInt
            (ConcurrentHashMap<K,V> map,
             ToIntFunction<Map.Entry<K,V>> transformer,
             int basis,
             IntBinaryOperator reducer) {
            if (transformer == null || reducer == null)
                throw new NullPointerException();
            return new MapReduceEntriesToIntTask<K,V>
                (map, null, -1, null, transformer, basis, reducer);
        }
    }

    // -------------------------------------------------------

    /*
     * Task classes. Coded in a regular but ugly format/style to
     * simplify checks that each variant differs in the right way from
     * others. The null screenings exist because compilers cannot tell
     * that we've already null-checked task arguments, so we force
     * simplest hoisted bypass to help avoid convoluted traps.
     */

    @SuppressWarnings("serial") static final class ForEachKeyTask<K,V>
        extends Traverser<K,V,Void> {
        final Consumer<? super K> action;
        ForEachKeyTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Consumer<? super K> action) {
            super(m, p, b);
            this.action = action;
        }
        public final void compute() {
            final Consumer<? super K> action;
            if ((action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachKeyTask<K,V>(map, this, b, action).fork();
                forEachKey(action);
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class ForEachValueTask<K,V>
        extends Traverser<K,V,Void> {
        final Consumer<? super V> action;
        ForEachValueTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Consumer<? super V> action) {
            super(m, p, b);
            this.action = action;
        }
        public final void compute() {
            final Consumer<? super V> action;
            if ((action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachValueTask<K,V>(map, this, b, action).fork();
                forEachValue(action);
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class ForEachEntryTask<K,V>
        extends Traverser<K,V,Void> {
        final Consumer<? super Entry<K,V>> action;
        ForEachEntryTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Consumer<? super Entry<K,V>> action) {
            super(m, p, b);
            this.action = action;
        }
        public final void compute() {
            final Consumer<? super Entry<K,V>> action;
            if ((action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachEntryTask<K,V>(map, this, b, action).fork();
                V v;
                while ((v = advanceValue()) != null)
                    action.accept(entryFor(nextKey, v));
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class ForEachMappingTask<K,V>
        extends Traverser<K,V,Void> {
        final BiConsumer<? super K, ? super V> action;
        ForEachMappingTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             BiConsumer<? super K,? super V> action) {
            super(m, p, b);
            this.action = action;
        }
        public final void compute() {
            final BiConsumer<? super K, ? super V> action;
            if ((action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachMappingTask<K,V>(map, this, b, action).fork();
                V v;
                while ((v = advanceValue()) != null)
                    action.accept(nextKey, v);
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class ForEachTransformedKeyTask<K,V,U>
        extends Traverser<K,V,Void> {
        final Function<? super K, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedKeyTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Function<? super K, ? extends U> transformer, Consumer<? super U> action) {
            super(m, p, b);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final Function<? super K, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                (action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachTransformedKeyTask<K,V,U>
                        (map, this, b, transformer, action).fork();
                K k; U u;
                while ((k = advanceKey()) != null) {
                    if ((u = transformer.apply(k)) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class ForEachTransformedValueTask<K,V,U>
        extends Traverser<K,V,Void> {
        final Function<? super V, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedValueTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Function<? super V, ? extends U> transformer, Consumer<? super U> action) {
            super(m, p, b);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final Function<? super V, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                (action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachTransformedValueTask<K,V,U>
                        (map, this, b, transformer, action).fork();
                V v; U u;
                while ((v = advanceValue()) != null) {
                    if ((u = transformer.apply(v)) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class ForEachTransformedEntryTask<K,V,U>
        extends Traverser<K,V,Void> {
        final Function<Map.Entry<K,V>, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedEntryTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Function<Map.Entry<K,V>, ? extends U> transformer, Consumer<? super U> action) {
            super(m, p, b);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final Function<Map.Entry<K,V>, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                (action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachTransformedEntryTask<K,V,U>
                        (map, this, b, transformer, action).fork();
                V v; U u;
                while ((v = advanceValue()) != null) {
                    if ((u = transformer.apply(entryFor(nextKey,
                                                        v))) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class ForEachTransformedMappingTask<K,V,U>
        extends Traverser<K,V,Void> {
        final BiFunction<? super K, ? super V, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedMappingTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             BiFunction<? super K, ? super V, ? extends U> transformer,
             Consumer<? super U> action) {
            super(m, p, b);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final BiFunction<? super K, ? super V, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                (action = this.action) != null) {
                for (int b; (b = preSplit()) > 0;)
                    new ForEachTransformedMappingTask<K,V,U>
                        (map, this, b, transformer, action).fork();
                V v; U u;
                while ((v = advanceValue()) != null) {
                    if ((u = transformer.apply(nextKey, v)) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial") static final class SearchKeysTask<K,V,U>
        extends Traverser<K,V,U> {
        final Function<? super K, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchKeysTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Function<? super K, ? extends U> searchFunction,
             AtomicReference<U> result) {
            super(m, p, b);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final Function<? super K, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                (result = this.result) != null) {
                for (int b;;) {
                    if (result.get() != null)
                        return;
                    if ((b = preSplit()) <= 0)
                        break;
                    new SearchKeysTask<K,V,U>
                        (map, this, b, searchFunction, result).fork();
                }
                while (result.get() == null) {
                    K k; U u;
                    if ((k = advanceKey()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(k)) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class SearchValuesTask<K,V,U>
        extends Traverser<K,V,U> {
        final Function<? super V, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchValuesTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Function<? super V, ? extends U> searchFunction,
             AtomicReference<U> result) {
            super(m, p, b);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final Function<? super V, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                (result = this.result) != null) {
                for (int b;;) {
                    if (result.get() != null)
                        return;
                    if ((b = preSplit()) <= 0)
                        break;
                    new SearchValuesTask<K,V,U>
                        (map, this, b, searchFunction, result).fork();
                }
                while (result.get() == null) {
                    V v; U u;
                    if ((v = advanceValue()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(v)) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class SearchEntriesTask<K,V,U>
        extends Traverser<K,V,U> {
        final Function<Entry<K,V>, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchEntriesTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             Function<Entry<K,V>, ? extends U> searchFunction,
             AtomicReference<U> result) {
            super(m, p, b);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final Function<Entry<K,V>, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                (result = this.result) != null) {
                for (int b;;) {
                    if (result.get() != null)
                        return;
                    if ((b = preSplit()) <= 0)
                        break;
                    new SearchEntriesTask<K,V,U>
                        (map, this, b, searchFunction, result).fork();
                }
                while (result.get() == null) {
                    V v; U u;
                    if ((v = advanceValue()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(entryFor(nextKey,
                                                           v))) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        return;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class SearchMappingsTask<K,V,U>
        extends Traverser<K,V,U> {
        final BiFunction<? super K, ? super V, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchMappingsTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             BiFunction<? super K, ? super V, ? extends U> searchFunction,
             AtomicReference<U> result) {
            super(m, p, b);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final BiFunction<? super K, ? super V, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                (result = this.result) != null) {
                for (int b;;) {
                    if (result.get() != null)
                        return;
                    if ((b = preSplit()) <= 0)
                        break;
                    new SearchMappingsTask<K,V,U>
                        (map, this, b, searchFunction, result).fork();
                }
                while (result.get() == null) {
                    V v; U u;
                    if ((v = advanceValue()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(nextKey, v)) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class ReduceKeysTask<K,V>
        extends Traverser<K,V,K> {
        final BiFunction<? super K, ? super K, ? extends K> reducer;
        K result;
        ReduceKeysTask<K,V> rights, nextRight;
        ReduceKeysTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             ReduceKeysTask<K,V> nextRight,
             BiFunction<? super K, ? super K, ? extends K> reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.reducer = reducer;
        }
        public final K getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final BiFunction<? super K, ? super K, ? extends K> reducer;
            if ((reducer = this.reducer) != null) {
                for (int b; (b = preSplit()) > 0;)
                    (rights = new ReduceKeysTask<K,V>
                     (map, this, b, rights, reducer)).fork();
                K u, r = null;
                while ((u = advanceKey()) != null) {
                    r = (r == null) ? u : u == null ? r : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    ReduceKeysTask<K,V>
                        t = (ReduceKeysTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        K tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                        reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class ReduceValuesTask<K,V>
        extends Traverser<K,V,V> {
        final BiFunction<? super V, ? super V, ? extends V> reducer;
        V result;
        ReduceValuesTask<K,V> rights, nextRight;
        ReduceValuesTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             ReduceValuesTask<K,V> nextRight,
             BiFunction<? super V, ? super V, ? extends V> reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.reducer = reducer;
        }
        public final V getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final BiFunction<? super V, ? super V, ? extends V> reducer;
            if ((reducer = this.reducer) != null) {
                for (int b; (b = preSplit()) > 0;)
                    (rights = new ReduceValuesTask<K,V>
                     (map, this, b, rights, reducer)).fork();
                V r = null, v;
                while ((v = advanceValue()) != null)
                    r = (r == null) ? v : reducer.apply(r, v);
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    ReduceValuesTask<K,V>
                        t = (ReduceValuesTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        V tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                        reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class ReduceEntriesTask<K,V>
        extends Traverser<K,V,Map.Entry<K,V>> {
        final BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer;
        Map.Entry<K,V> result;
        ReduceEntriesTask<K,V> rights, nextRight;
        ReduceEntriesTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             ReduceEntriesTask<K,V> nextRight,
             BiFunction<Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.reducer = reducer;
        }
        public final Map.Entry<K,V> getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer;
            if ((reducer = this.reducer) != null) {
                for (int b; (b = preSplit()) > 0;)
                    (rights = new ReduceEntriesTask<K,V>
                     (map, this, b, rights, reducer)).fork();
                Map.Entry<K,V> r = null;
                V v;
                while ((v = advanceValue()) != null) {
                    Map.Entry<K,V> u = entryFor(nextKey, v);
                    r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    ReduceEntriesTask<K,V>
                        t = (ReduceEntriesTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        Map.Entry<K,V> tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                        reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceKeysTask<K,V,U>
        extends Traverser<K,V,U> {
        final Function<? super K, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceKeysTask<K,V,U> rights, nextRight;
        MapReduceKeysTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceKeysTask<K,V,U> nextRight,
             Function<? super K, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final Function<? super K, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceKeysTask<K,V,U>
                     (map, this, b, rights, transformer, reducer)).fork();
                K k; U r = null, u;
                while ((k = advanceKey()) != null) {
                    if ((u = transformer.apply(k)) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceKeysTask<K,V,U>
                        t = (MapReduceKeysTask<K,V,U>)c,
                        s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                        reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceValuesTask<K,V,U>
        extends Traverser<K,V,U> {
        final Function<? super V, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceValuesTask<K,V,U> rights, nextRight;
        MapReduceValuesTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceValuesTask<K,V,U> nextRight,
             Function<? super V, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final Function<? super V, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceValuesTask<K,V,U>
                     (map, this, b, rights, transformer, reducer)).fork();
                U r = null, u;
                V v;
                while ((v = advanceValue()) != null) {
                    if ((u = transformer.apply(v)) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceValuesTask<K,V,U>
                        t = (MapReduceValuesTask<K,V,U>)c,
                        s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                        reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceEntriesTask<K,V,U>
        extends Traverser<K,V,U> {
        final Function<Map.Entry<K,V>, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceEntriesTask<K,V,U> rights, nextRight;
        MapReduceEntriesTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceEntriesTask<K,V,U> nextRight,
             Function<Map.Entry<K,V>, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final Function<Map.Entry<K,V>, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceEntriesTask<K,V,U>
                     (map, this, b, rights, transformer, reducer)).fork();
                U r = null, u;
                V v;
                while ((v = advanceValue()) != null) {
                    if ((u = transformer.apply(entryFor(nextKey,
                                                        v))) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceEntriesTask<K,V,U>
                        t = (MapReduceEntriesTask<K,V,U>)c,
                        s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                        reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceMappingsTask<K,V,U>
        extends Traverser<K,V,U> {
        final BiFunction<? super K, ? super V, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceMappingsTask<K,V,U> rights, nextRight;
        MapReduceMappingsTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceMappingsTask<K,V,U> nextRight,
             BiFunction<? super K, ? super V, ? extends U> transformer,
             BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final BiFunction<? super K, ? super V, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceMappingsTask<K,V,U>
                     (map, this, b, rights, transformer, reducer)).fork();
                U r = null, u;
                V v;
                while ((v = advanceValue()) != null) {
                    if ((u = transformer.apply(nextKey, v)) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceMappingsTask<K,V,U>
                        t = (MapReduceMappingsTask<K,V,U>)c,
                        s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                        reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceKeysToDoubleTask<K,V>
        extends Traverser<K,V,Double> {
        final ToDoubleFunction<? super K> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceKeysToDoubleTask<K,V> rights, nextRight;
        MapReduceKeysToDoubleTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceKeysToDoubleTask<K,V> nextRight,
             ToDoubleFunction<? super K> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToDoubleFunction<? super K> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceKeysToDoubleTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                K k;
                while ((k = advanceKey()) != null)
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(k));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceKeysToDoubleTask<K,V>
                        t = (MapReduceKeysToDoubleTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceValuesToDoubleTask<K,V>
        extends Traverser<K,V,Double> {
        final ToDoubleFunction<? super V> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceValuesToDoubleTask<K,V> rights, nextRight;
        MapReduceValuesToDoubleTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceValuesToDoubleTask<K,V> nextRight,
             ToDoubleFunction<? super V> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToDoubleFunction<? super V> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceValuesToDoubleTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(v));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceValuesToDoubleTask<K,V>
                        t = (MapReduceValuesToDoubleTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceEntriesToDoubleTask<K,V>
        extends Traverser<K,V,Double> {
        final ToDoubleFunction<Map.Entry<K,V>> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceEntriesToDoubleTask<K,V> rights, nextRight;
        MapReduceEntriesToDoubleTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceEntriesToDoubleTask<K,V> nextRight,
             ToDoubleFunction<Map.Entry<K,V>> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToDoubleFunction<Map.Entry<K,V>> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceEntriesToDoubleTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(entryFor(nextKey,
                                                                    v)));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceEntriesToDoubleTask<K,V>
                        t = (MapReduceEntriesToDoubleTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceMappingsToDoubleTask<K,V>
        extends Traverser<K,V,Double> {
        final ToDoubleBiFunction<? super K, ? super V> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceMappingsToDoubleTask<K,V> rights, nextRight;
        MapReduceMappingsToDoubleTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceMappingsToDoubleTask<K,V> nextRight,
             ToDoubleBiFunction<? super K, ? super V> transformer,
             double basis,
             DoubleBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToDoubleBiFunction<? super K, ? super V> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceMappingsToDoubleTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(nextKey, v));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceMappingsToDoubleTask<K,V>
                        t = (MapReduceMappingsToDoubleTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceKeysToLongTask<K,V>
        extends Traverser<K,V,Long> {
        final ToLongFunction<? super K> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceKeysToLongTask<K,V> rights, nextRight;
        MapReduceKeysToLongTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceKeysToLongTask<K,V> nextRight,
             ToLongFunction<? super K> transformer,
             long basis,
             LongBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToLongFunction<? super K> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceKeysToLongTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                K k;
                while ((k = advanceKey()) != null)
                    r = reducer.applyAsLong(r, transformer.applyAsLong(k));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceKeysToLongTask<K,V>
                        t = (MapReduceKeysToLongTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceValuesToLongTask<K,V>
        extends Traverser<K,V,Long> {
        final ToLongFunction<? super V> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceValuesToLongTask<K,V> rights, nextRight;
        MapReduceValuesToLongTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceValuesToLongTask<K,V> nextRight,
             ToLongFunction<? super V> transformer,
             long basis,
             LongBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToLongFunction<? super V> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceValuesToLongTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsLong(r, transformer.applyAsLong(v));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceValuesToLongTask<K,V>
                        t = (MapReduceValuesToLongTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceEntriesToLongTask<K,V>
        extends Traverser<K,V,Long> {
        final ToLongFunction<Map.Entry<K,V>> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceEntriesToLongTask<K,V> rights, nextRight;
        MapReduceEntriesToLongTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceEntriesToLongTask<K,V> nextRight,
             ToLongFunction<Map.Entry<K,V>> transformer,
             long basis,
             LongBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToLongFunction<Map.Entry<K,V>> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceEntriesToLongTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsLong(r, transformer.applyAsLong(entryFor(nextKey, v)));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceEntriesToLongTask<K,V>
                        t = (MapReduceEntriesToLongTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceMappingsToLongTask<K,V>
        extends Traverser<K,V,Long> {
        final ToLongBiFunction<? super K, ? super V> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceMappingsToLongTask<K,V> rights, nextRight;
        MapReduceMappingsToLongTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceMappingsToLongTask<K,V> nextRight,
             ToLongBiFunction<? super K, ? super V> transformer,
             long basis,
             LongBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToLongBiFunction<? super K, ? super V> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceMappingsToLongTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsLong(r, transformer.applyAsLong(nextKey, v));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceMappingsToLongTask<K,V>
                        t = (MapReduceMappingsToLongTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceKeysToIntTask<K,V>
        extends Traverser<K,V,Integer> {
        final ToIntFunction<? super K> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceKeysToIntTask<K,V> rights, nextRight;
        MapReduceKeysToIntTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceKeysToIntTask<K,V> nextRight,
             ToIntFunction<? super K> transformer,
             int basis,
             IntBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToIntFunction<? super K> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceKeysToIntTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                K k;
                while ((k = advanceKey()) != null)
                    r = reducer.applyAsInt(r, transformer.applyAsInt(k));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceKeysToIntTask<K,V>
                        t = (MapReduceKeysToIntTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceValuesToIntTask<K,V>
        extends Traverser<K,V,Integer> {
        final ToIntFunction<? super V> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceValuesToIntTask<K,V> rights, nextRight;
        MapReduceValuesToIntTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceValuesToIntTask<K,V> nextRight,
             ToIntFunction<? super V> transformer,
             int basis,
             IntBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToIntFunction<? super V> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceValuesToIntTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsInt(r, transformer.applyAsInt(v));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceValuesToIntTask<K,V>
                        t = (MapReduceValuesToIntTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceEntriesToIntTask<K,V>
        extends Traverser<K,V,Integer> {
        final ToIntFunction<Map.Entry<K,V>> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceEntriesToIntTask<K,V> rights, nextRight;
        MapReduceEntriesToIntTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceEntriesToIntTask<K,V> nextRight,
             ToIntFunction<Map.Entry<K,V>> transformer,
             int basis,
             IntBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToIntFunction<Map.Entry<K,V>> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceEntriesToIntTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsInt(r, transformer.applyAsInt(entryFor(nextKey,
                                                                    v)));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceEntriesToIntTask<K,V>
                        t = (MapReduceEntriesToIntTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial") static final class MapReduceMappingsToIntTask<K,V>
        extends Traverser<K,V,Integer> {
        final ToIntBiFunction<? super K, ? super V> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceMappingsToIntTask<K,V> rights, nextRight;
        MapReduceMappingsToIntTask
            (ConcurrentHashMap<K,V> m, Traverser<K,V,?> p, int b,
             MapReduceMappingsToIntTask<K,V> nextRight,
             ToIntBiFunction<? super K, ? super V> transformer,
             int basis,
             IntBinaryOperator reducer) {
            super(m, p, b); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        @SuppressWarnings("unchecked") public final void compute() {
            final ToIntBiFunction<? super K, ? super V> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int b; (b = preSplit()) > 0;)
                    (rights = new MapReduceMappingsToIntTask<K,V>
                     (map, this, b, rights, transformer, r, reducer)).fork();
                V v;
                while ((v = advanceValue()) != null)
                    r = reducer.applyAsInt(r, transformer.applyAsInt(nextKey, v));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    MapReduceMappingsToIntTask<K,V>
                        t = (MapReduceMappingsToIntTask<K,V>)c,
                        s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long SIZECTL;
    private static final long TRANSFERINDEX;
    private static final long TRANSFERORIGIN;
    private static final long BASECOUNT;
    private static final long CELLSBUSY;
    private static final long CELLVALUE;
    private static final long ABASE;
    private static final int ASHIFT;

    static {
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = ConcurrentHashMap.class;
            SIZECTL = U.objectFieldOffset
                (k.getDeclaredField("sizeCtl"));
            TRANSFERINDEX = U.objectFieldOffset
                (k.getDeclaredField("transferIndex"));
            TRANSFERORIGIN = U.objectFieldOffset
                (k.getDeclaredField("transferOrigin"));
            BASECOUNT = U.objectFieldOffset
                (k.getDeclaredField("baseCount"));
            CELLSBUSY = U.objectFieldOffset
                (k.getDeclaredField("cellsBusy"));
            Class<?> ck = Cell.class;
            CELLVALUE = U.objectFieldOffset
                (ck.getDeclaredField("value"));
            Class<?> sc = Node[].class;
            ABASE = U.arrayBaseOffset(sc);
            int scale = U.arrayIndexScale(sc);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
