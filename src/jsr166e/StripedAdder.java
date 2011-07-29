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
 * A set of variables that together maintain a sum.  When updates
 * (method {@link #add}) are contended across threads, this set of
 * adder variables may grow dynamically to reduce contention. Method
 * {@link #sum} returns the current combined total across these
 * adders. This value is <em>NOT</em> an atomic snapshot (concurrent
 * updates may occur while the sum is being calculated), and so cannot
 * be used alone for fine-grained synchronization control.
 *
 * <p> This class may be applicable when many threads frequently
 * update a common sum that is used for purposes such as collecting
 * statistics. In this case, performance may be significantly faster
 * than using a shared {@link AtomicLong}, at the expense of using
 * more space.  On the other hand, if it is known that only one thread
 * can ever update the sum, performance may be significantly slower
 * than just updating a local variable.
 *
 * <p>A StripedAdder may optionally be constructed with a given
 * expected contention level; i.e., the number of threads that are
 * expected to concurrently update the sum. Supplying an accurate
 * value may improve performance by reducing the need for dynamic
 * adjustment.
 *
 * @author Doug Lea
 */
public class StripedAdder implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    /*
     * A StripedAdder maintains a table of Atomic long variables. The
     * table is indexed by per-thread hash codes.
     *
     * Table entries are of class Adder; a variant of AtomicLong
     * padded to reduce cache contention on most processors. Padding
     * is overkill for most Atomics because they are usually
     * irregularly scattered in memory and thus don't interfere much
     * with each other. But Atomic objects residing in arrays will
     * tend to be placed adjacent to each other, and so will most
     * often share cache lines (with a huge negative performance
     * impact) without this precaution.
     *
     * Because Adders are relatively large, we avoid creating them
     * until they are needed. On the other hand, we try to create them
     * on any sign of contention.
     *
     * Per-thread hash codes are initialized to random values.
     * Collisions are indicated by failed CASes when performing an add
     * operation (see method retryAdd). Upon a collision, if the table
     * size is less than the capacity, it is doubled in size unless
     * some other thread holds lock. If a hashed slot is empty, and
     * lock is available, a new Adder is created. Otherwise, if the
     * slot exists, a CAS is tried.  Retries proceed by "double
     * hashing", using a secondary hash (Marsaglia XorShift) to try to
     * find a free slot.
     *
     * By default, the table is lazily initialized.  Upon first use,
     * the table is set to size 1, and contains a single Adder. The
     * maximum table size is bounded by nearest power of two >= the
     * number of CPUS.  The table size is capped because, when there
     * are more threads than CPUs, supposing that each thread were
     * bound to a CPU, there would exist a perfect hash function
     * mapping threads to slots that eliminates collisions. When we
     * reach capacity, we search for this mapping by randomly varying
     * the hash codes of colliding threads.  Because search is random,
     * and failures only become known via CAS failures, convergence
     * will be slow, and because threads are typically not bound to
     * CPUS forever, may not occur at all. However, despite these
     * limitations, observed contention is typically low in these
     * cases.
     *
     * A single spinlock is used for resizing the table as well as
     * populating slots with new Adders. After initialization, there
     * is no need for a blocking lock: Upon lock contention, threads
     * try other slots rather than blocking. After initialization, at
     * least one slot exists, so retries will eventually find a
     * candidate Adder.  During these retries, there is increased
     * contention and reduced locality, which is still better than
     * alternatives.
     */

    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Padded variant of AtomicLong.  The value field is placed
     * between pads, hoping that the JVM doesn't reorder them.
     * Updates are via inlined CAS in methods add and retryAdd.
     */
    static final class Adder {
        volatile long p0, p1, p2, p3, p4, p5, p6;
        volatile long value;
        volatile long q0, q1, q2, q3, q4, q5, q6;
        Adder(long x) { value = x; }
    }

    /**
     * Holder for the thread-local hash code. The code is initially
     * random, but may be set to a different value upon collisions.
     */
    static final class HashCode {
        static final Random rng = new Random();
        int code;
        HashCode() {
            int h = rng.nextInt(); // Avoid zero, because of xorShift rehash
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
     * Static per-thread hash codes. Shared across all StripedAdders
     * to reduce ThreadLocal pollution and because adjustments due to
     * collisions in one table are likely to be appropriate for
     * others.
     */
    static final ThreadHashCode threadHashCode = new ThreadHashCode();

    /**
     * Table of adders. When non-null, size is a power of 2.
     */
    private transient volatile Adder[] adders;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Adders.
     */
    private volatile int busy;

    /**
     * Creates a new adder with zero sum.
     */
    public StripedAdder() {
    }

    /**
     * Creates a new adder with zero sum, and with stripes presized
     * for the given expected contention level.
     *
     * @param expectedContention the expected number of threads that
     * will concurrently update the sum.
     */
    public StripedAdder(int expectedContention) {
        int cap = (expectedContention < NCPU) ? expectedContention : NCPU;
        int size = 1;
        while (size < cap)
            size <<= 1;
        Adder[] as = new Adder[size];
        for (int i = 0; i < size; ++i)
            as[i] = new Adder(0);
        this.adders = as;
    }

    /**
     * Adds the given value.
     *
     * @param x the value to add
     */
    public void add(long x) {
        Adder[] as; Adder a; int n;  // locals to hold volatile reads
        HashCode hc = threadHashCode.get();
        int h = hc.code;
        boolean contended;
        if ((as = adders) != null && (n = as.length) > 0 &&
            (a = as[(n - 1) & h]) != null) {
            long v = a.value;
            if (UNSAFE.compareAndSwapLong(a, valueOffset, v, v + x))
                return;
            contended = true;
        }
        else
            contended = false;
        retryAdd(x, hc, contended);
    }

    /**
     * Handle cases of add involving initialization, resizing,
     * creating new Adders, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *
     * @param x the value to add
     * @param hc the hash code holder
     * @param precontended true if CAS failed before call
     */
    private void retryAdd(long x, HashCode hc, boolean precontended) {
        int h = hc.code;
        boolean collide = false; // true if last slot nonempty
        for (;;) {
            Adder[] as; Adder a; int n;
            if ((as = adders) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (busy == 0) {            // Try to attach new Adder
                        Adder r = new Adder(x); // Optimistically create
                        if (busy == 0 &&
                            UNSAFE.compareAndSwapInt(this, busyOffset, 0, 1)) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Adder[] rs; int m, j;
                                if ((rs = adders) != null &&
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
                else if (precontended)          // CAS already known to fail
                    precontended = false;       // Continue after rehash
                else {
                    long v = a.value;
                    if (UNSAFE.compareAndSwapLong(a, valueOffset, v, v + x))
                        break;
                    if (!collide)
                        collide = true;
                    else if (n >= NCPU || adders != as)
                        collide = false;        // Can't expand
                    else if (busy == 0 &&
                             UNSAFE.compareAndSwapInt(this, busyOffset, 0, 1)) {
                        collide = false;
                        try {
                            if (adders == as) { // Expand table
                                Adder[] rs = new Adder[n << 1];
                                for (int i = 0; i < n; ++i)
                                    rs[i] = as[i];
                                adders = rs;
                            }
                        } finally {
                            busy = 0;
                        }
                        continue;
                    }
                }
                h ^= h << 13;                   // Rehash
                h ^= h >>> 17;
                h ^= h << 5;
            }
            else if (adders == as) {            // Try to default-initialize
                Adder[] rs = new Adder[1];
                rs[0] = new Adder(x);
                boolean init = false;
                while (adders == as) {
                    if (UNSAFE.compareAndSwapInt(this, busyOffset, 0, 1)) {
                        try {
                            if (adders == as) {
                                adders = rs;
                                init = true;
                            }
                        } finally {
                            busy = 0;
                        }
                        break;
                    }
                    if (adders != as)
                        break;
                    Thread.yield();              // Back off
                }
                if (init)
                    break;
            }
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
     * Returns an estimate of the current sum.  The result is
     * calculated by summing multiple variables, so may not be
     * accurate if updates occur concurrently with this method.
     *
     * @return the estimated sum
     */
    public long sum() {
        long sum = 0L;
        Adder[] as = adders;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Adder a = as[i];
                if (a != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    /**
     * Resets each of the variables to zero, returning the estimated
     * previous sum. This is effective in fully resetting the sum only
     * if there are no concurrent updates.
     *
     * @return the estimated previous sum
     */
    public long reset() {
        long sum = 0L;
        Adder[] as = adders;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Adder a = as[i];
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
        add(s.readLong());
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long busyOffset;
    private static final long valueOffset;
    static {
        try {
            UNSAFE = getUnsafe();
            Class<?> sk = StripedAdder.class;
            busyOffset = UNSAFE.objectFieldOffset
                (sk.getDeclaredField("busy"));
            Class<?> ak = Adder.class;
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
