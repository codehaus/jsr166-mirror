/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e;
import java.util.Arrays;
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
 * much more space.  On the other hand, if it is known that only one
 * thread can ever update the sum, performance may be significantly
 * slower than just updating a local variable.
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
     * table is indexed by per-thread hash codes that are initialized
     * to random values.
     *
     * The table doubles in size upon contention (as indicated by
     * failed CASes when performing add()), but is capped at the
     * nearest power of two >= #CPUS. This reflects the idea that,
     * when there are more threads than CPUs, then if each thread were
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
     * Table entries are of class Adder; a form of AtomicLong padded
     * to reduce cache contention on most processors. Padding is
     * overkill for most Atomics because they are most often
     * irregularly scattered in memory and thus don't interfere much
     * with each other. But Atomic objects residing in arrays will
     * tend to be placed adjacent to each other, and so will most
     * often share cache lines without this precaution.  Adders are
     * constructed upon first use, which further improves per-thread
     * locality and helps reduce (an already large) footprint.
     *
     * A single spinlock is used for resizing the table as well as
     * populating slots with new Adders. Upon lock contention, threads
     * try other slots rather than blocking. After initialization, at
     * least one slot exists, so retries will eventually find a
     * candidate Adder. During these retries, there is increased
     * contention and reduced locality, which is still better than
     * alternatives.
     */

    /**
     * Number of processors, to place a cap on table growth.
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * The table size set upon first use when default-constructed
     */
    private static final int DEFAULT_ARRAY_SIZE = 8;

    /**
     * Padded version of AtomicLong
     */
    static final class Adder extends AtomicLong {
        long p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd;
        Adder(long x) { super(x); }
    }

    /**
     * Holder for the thread-local hash code. The code starts off with
     * a given random value, but may be set to a different value upon
     * collisions in retryAdd.
     */
    static final class HashCode {
        int code;
        HashCode(int h) { code = h; }
    }

    /**
     * The corresponding ThreadLocal class
     */
    static final class ThreadHashCode extends ThreadLocal<HashCode> {
        static final Random rng = new Random();
        public HashCode initialValue() {
            int h = rng.nextInt();
            return new HashCode((h == 0) ? 1 : h); // ensure nonzero
        }
    }

    /**
     * Static per-thread hash codes. Shared across all StripedAdders
     * because adjustments due to collisions in one table are likely
     * to be appropriate for others.
     */
    static final ThreadHashCode threadHashCode = new ThreadHashCode();

    /**
     * Table of adders. Size is power of two, grows to be at most NCPU.
     */
    private transient volatile Adder[] adders;

    /**
     * Serves as a lock when resizing and/or creating Adders.  There
     * is no need for a blocking lock: Except during initialization
     * races, when busy, other threads try other slots.
     */
    private final AtomicInteger mutex;

    /**
     * Creates a new adder with zero sum.
     */
    public StripedAdder() {
        this.mutex = new AtomicInteger();
        // remaining initialization on first call to add.
    }

    /**
     * Creates a new adder with zero sum, and with stripes presized
     * for the given expected contention level.
     *
     * @param expectedContention the expected number of threads that
     * will concurrently update the sum.
     */
    public StripedAdder(int expectedContention) {
        int size;
        if (expectedContention > 0) {
            int cap = (expectedContention < NCPU) ? expectedContention : NCPU;
            size = 1;
            while (size < cap)
                size <<= 1;
        }
        else
            size = 0;
        Adder[] as = new Adder[size];
        for (int i = 0; i < size; ++i)
            as[i] = new Adder(0);
        this.adders = as;
        this.mutex = new AtomicInteger();
    }

    /**
     * Adds the given value.
     *
     * @param x the value to add
     */
    public void add(long x) {
        Adder[] as; Adder a; int n; long v; // locals to hold volatile reads
        HashCode hc = threadHashCode.get();
        if ((as = adders) == null || (n = as.length) < 1 ||
            (a = as[hc.code & (n - 1)]) == null ||
            !a.compareAndSet(v = a.get(), v + x))
            retryAdd(x, hc);
    }

    /**
     * Handle cases of add involving initialization, resizing,
     * creating new Adders, and/or contention.
     */
    private void retryAdd(long x, HashCode hc) {
        int h = hc.code;
        final AtomicInteger mutex = this.mutex;
        for (boolean retried = false; ; retried = true) {
            Adder[] as; Adder a; long v; int n, k; // Locals for volatiles
            if ((as = adders) == null || (n = as.length) < 1) {
                if (mutex.get() == 0 && mutex.compareAndSet(0, 1)) {
                    try {
                        if (adders == null)        // Default-initialize
                            adders = new Adder[DEFAULT_ARRAY_SIZE];
                    } finally {
                        mutex.set(0);
                    }
                }
                else
                    Thread.yield();               // initialization race
            }
            else if ((a = as[k = h & (n - 1)]) != null &&
                     retried && a.compareAndSet(v = a.get(), v + x))
                break;
            else if ((a == null || n < NCPU) &&
                     mutex.get() == 0 && mutex.compareAndSet(0, 1)) {
                boolean created = false;
                try {
                    if (adders == as) {
                        if (as[k] == null) {
                            as[k] = new Adder(x);
                            created = true;
                        }
                        else {                   // Expand table
                            Adder[] rs = new Adder[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            adders = rs;
                        }
                    }
                } finally {
                    mutex.set(0);
                }
                if (created)
                    break;
            }
            else {                                // Try elsewhere
                h ^= h << 13;
                h ^= h >>> 17;                    // Marsaglia XorShift
                h ^= h << 5;
            }
        }
        hc.code = h;
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
                    sum += a.get();
            }
        }
        return sum;
    }

    /**
     * Resets each of the variables to zero. This is effective in
     * fully resetting the sum only if there are no concurrent
     * updates.
     */
    public void reset() {
        Adder[] as = adders;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Adder a = as[i];
                if (a != null)
                    a.set(0L);
            }
        }
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
     * Equivalent to {@link #sum} followed by {@link #reset}.
     *
     * @return the estimated sum
     */
    public long sumAndReset() {
        long sum = 0L;
        Adder[] as = adders;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Adder a = as[i];
                if (a != null) {
                    sum += a.get();
                    a.set(0L);
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
        mutex.set(0);
        add(s.readLong());
    }

}
