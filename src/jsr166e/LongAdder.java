/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * One or more variables that together maintain an initially zero sum.
 * When updates (method {@link #add}) are contended across threads,
 * the set of variables may grow dynamically to reduce contention.
 *
 * <p> This class is usually preferable to {@link AtomicLong} when
 * multiple threads update a common sum that is used for purposes such
 * as collecting statistics, not for fine-grained synchronization
 * control.  Under low update contention, the two classes have similar
 * characteristics. But under high contention, expected throughput of
 * this class is significantly higher, at the expense of higher space
 * consumption.
 *
 * <p> Method {@link #sum} returns the current combined total across
 * the variables maintaining the sum.  This value is <em>NOT</em> an
 * atomic snapshot: Invocation of {@code sum} in the absence of
 * concurrent updates returns an accurate result, but concurrent
 * updates that occur while the sum is being calculated might not be
 * incorporated.  The sum may also be {@code reset} to zero, as
 * an alternative to creating a new adder.  However, method {@link
 * #reset} is intrinsically racy, so should only be used when it is
 * known that no threads are concurrently updating the sum.
 *
 * <p><em>jsr166e note: This class is targeted to be placed in
 * java.util.concurrent.atomic<em>
 *
 * @author Doug Lea
 */
public class LongAdder implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    /*
     * A LongAdder maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field. The table size
     * is a power of two. Indexing uses masked per-thread hash codes
     *
     * Table entries are of class Cell; a variant of AtomicLong padded
     * to reduce cache contention on most processors. Padding is
     * overkill for most Atomics because they are usually irregularly
     * scattered in memory and thus don't interfere much with each
     * other. But Atomic objects residing in arrays will tend to be
     * placed adjacent to each other, and so will most often share
     * cache lines (with a huge negative performance impact) without
     * this precaution.
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.  When there is no contention, all
     * updates are made to the base field.  Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS.
     *
     * Per-thread hash codes are initialized to random values.
     * Contention and/or table collisions are indicated by failed
     * CASes when performing an add operation (see method
     * retryAdd). Upon a collision, if the table size is less than the
     * capacity, it is doubled in size unless some other thread holds
     * the lock. If a hashed slot is empty, and lock is available, a
     * new Cell is created. Otherwise, if the slot exists, a CAS is
     * tried.  Retries proceed by "double hashing", using a secondary
     * hash (Marsaglia XorShift) to try to find a free slot.
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions. When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.  Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all. However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *
     * A single spinlock is used for initializing and resizing the
     * table, as well as populating slots with new Cells.  There is no
     * need for a blocking lock: Upon lock contention, threads try
     * other slots (or the base) rather than blocking.  During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.  We do not try to detect or remove such cells,
     * under the assumption that for long-running adders, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     *
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     */

    /**
     * Padded variant of AtomicLong.  The value field is placed
     * between pads, hoping that the JVM doesn't reorder them.
     * Updates are via inlined CAS in methods add and retryAdd.
     */
    static final class Cell {
        volatile long p0, p1, p2, p3, p4, p5, p6;
        volatile long value;
        volatile long q0, q1, q2, q3, q4, q5, q6;
        Cell(long x) { value = x; }
    }

    /**
     * Holder for the thread-local hash code. The code is initially
     * random, but may be set to a different value upon collisions.
     */
    static final class HashCode {
        static final Random rng = new Random();
        int code;
        HashCode() {
            int h = rng.nextInt(); // Avoid zero to allow xorShift rehash
            code = (h == 0) ? 1 : h;
        }
    }

    /**
     * The corresponding ThreadLocal class
     */
    static final class ThreadHashCode extends ThreadLocal<HashCode> {
        public HashCode initialValue() { return new HashCode(); }
    }

    /**
     * Static per-thread hash codes. Shared across all LongAdders
     * to reduce ThreadLocal pollution and because adjustments due to
     * collisions in one table are likely to be appropriate for
     * others.
     */
    static final ThreadHashCode threadHashCode = new ThreadHashCode();

    /** Number of CPUS, to place bound on table size */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     */
    private transient volatile Cell[] cells;

    /**
     * Base sum, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     */
    private transient volatile long base;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     */
    private transient volatile int busy;

    /**
     * Creates a new adder with initial sum of zero.
     */
    public LongAdder() {
    }

    /**
     * Adds the given value.
     *
     * @param x the value to add
     */
    public void add(long x) {
        Cell[] as; long v; HashCode hc; Cell a; int n;
        if ((as = cells) != null ||
            !UNSAFE.compareAndSwapLong(this, baseOffset, v = base, v + x)) {
            boolean uncontended = true;
            int h = (hc = threadHashCode.get()).code;
            if (as == null || (n = as.length) < 1 ||
                (a = as[(n - 1) & h]) == null ||
                !(uncontended = UNSAFE.compareAndSwapLong(a, valueOffset,
                                                          v = a.value, v + x)))
                retryAdd(x, hc, uncontended);
        }
    }

    /**
     * Handle cases of add involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *
     * @param x the value to add
     * @param hc the hash code holder
     * @param wasUncontended false if CAS failed before call
     */
    private void retryAdd(long x, HashCode hc, boolean wasUncontended) {
        int h = hc.code;
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            Cell[] as; Cell a; int n; long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (busy == 0) {            // Try to attach new Cell
                        Cell r = new Cell(x);   // Optimistically create
                        if (busy == 0 &&
                            UNSAFE.compareAndSwapInt(this, busyOffset, 0, 1)) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                busy = 0;
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
                else if (UNSAFE.compareAndSwapLong(a, valueOffset,
                                                   v = a.value, v + x))
                    break;
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (busy == 0 &&           // Try to expand table
                         UNSAFE.compareAndSwapInt(this, busyOffset, 0, 1)) {
                    try {
                        if (cells == as) {
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        busy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h ^= h << 13;                   // Rehash
                h ^= h >>> 17;
                h ^= h << 5;
            }
            else if (busy == 0 && cells == as &&
                     UNSAFE.compareAndSwapInt(this, busyOffset, 0, 1)) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        init = true;
                    }
                } finally {
                    busy = 0;
                }
                if (init)
                    break;
            }
            else if (UNSAFE.compareAndSwapLong(this, baseOffset,
                                               v = base, v + x))
                break;                          // Fall back on using base
        }
        hc.code = h;                            // Record index for next time
    }

    /**
     * Equivalent to {@code add(1)}.
     */
    public void increment() {
        add(1L);
    }

    /**
     * Equivalent to {@code add(-1)}.
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * Returns the current sum.  The result is only guaranteed to be
     * accurate in the absence of concurrent updates. Otherwise, it
     * may fail to reflect one or more updates occuring while
     * calculating the result.
     *
     * @return the sum
     */
    public long sum() {
        Cell[] as = cells;
        long sum = base;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the sum to zero.  This is
     * effective in setting the sum to zero only if there are no
     * concurrent updates.
     */
    public void reset() {
        Cell[] as = cells;
        base = 0L;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null)
                    a.value = 0L;
            }
        }
    }

    /**
     * Equivalent in effect to {@link #sum} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final sum occurring before
     * the reset.
     *
     * @return the sum
     */
    public long sumThenReset() {
        Cell[] as = cells;
        long sum = base;
        base = 0L;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        s.defaultWriteObject();
        s.writeLong(sum());
    }

    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        busy = 0;
        cells = null;
        base = s.readLong();
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long baseOffset;
    private static final long busyOffset;
    private static final long valueOffset;
    static {
        try {
            UNSAFE = getUnsafe();
            Class<?> sk = LongAdder.class;
            baseOffset = UNSAFE.objectFieldOffset
                (sk.getDeclaredField("base"));
            busyOffset = UNSAFE.objectFieldOffset
                (sk.getDeclaredField("busy"));
            Class<?> ak = Cell.class;
            valueOffset = UNSAFE.objectFieldOffset
                (ak.getDeclaredField("value"));
        } catch (Exception e) {
            throw new Error(e);
        }
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
