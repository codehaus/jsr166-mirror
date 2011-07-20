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
 * (method {@link #add}) are contended across threads, the set of
 * adders may grow to reduce contention. Method {@link #sum} returns
 * the current combined total across these adders. This value is
 * <em>NOT</em> an atomic snapshot (concurrent updates may occur while
 * the sum is being calculated), and so cannot be used alone for
 * fine-grained synchronization control.
 * 
 * <p> This class may be applicable when many threads frequently
 * update a common sum that is used for purposes such as collecting
 * statistics. In this case, performance may be significantly faster
 * than using a shared {@link AtomicLong}, at the expense of using
 * significantly more space.  On the other hand, if it is known that
 * only one thread can ever update the sum, performance may be
 * significantly slower than just updating a local variable.
 *
 * @author Doug Lea 
 */
public class StripedAdder implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    /*
     * Overview: We maintain a table of AtomicLongs (padded to reduce
     * false sharing). The table is indexed by per-thread hash codes
     * that are initialized as random values.  The table doubles in
     * size upon contention (as indicated by failed CASes when
     * performing add()), but is capped at the nearest power of two >=
     * #cpus: At that point, contention should be infrequent if each
     * thread has a unique index; so we instead adjust hash codes to
     * new random values upon contention rather than expanding. A
     * single spinlock is used for resizing the table as well as
     * populating slots with new Adders. Upon lock contention, threads
     * just try other slots rather than blocking. We guarantee that at
     * least one slot exists, so retries will eventually find a
     * candidate Adder.
     */

    /** 
     * Number of processors, to place a cap on table growth.
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Version of AtomicLong padded to avoid sharing cache
     * lines on most processors
     */
    static final class Adder extends AtomicLong {
        long p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd;
        Adder(long x) { super(x); }
    }

    /** 
     * Holder for the thread-local hash code.
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
     * Table of adders. Initially of size 2; grows to be at most NCPU.
     */
    private transient volatile Adder[] adders;

    /**
     * Serves as a lock when resizing and/or creating Adders.  There
     * is no need for a blocking lock: When busy, other threads try
     * other slots.
     */
    private final AtomicInteger mutex;

    /**
     * Marsaglia XorShift for rehashing on collisions
     */
    private static int xorShift(int r) { 
        r ^= r << 13;
        r ^= r >>> 17;
        return r ^ (r << 5);
    }

    /**
     * Creates a new adder with initially zero sum.
     */
    public StripedAdder() {
        Adder[] as = new Adder[2];
        as[0] = new Adder(0); // ensure at least one available adder
        this.adders = as;
        this.mutex = new AtomicInteger();
    }

    /**
     * Adds the given value.
     *
     * @param x the value to add
     */
    public void add(long x) {
        HashCode hc = threadHashCode.get();
        for (int h = hc.code;;) {
            Adder[] as = adders;
            int n = as.length;
            Adder a = as[h & (n - 1)];
            if (a != null) {
                long v = a.get();
                if (a.compareAndSet(v, v + x))
                    break;
                if (n >= NCPU) {                 // Collision when table at max
                    h = hc.code = xorShift(h);   // change code
                    continue;
                }
            }
            final AtomicInteger mutex = this.mutex;
            if (mutex.get() != 0)
                h = xorShift(h);                 // Try elsewhere
            else if (mutex.compareAndSet(0, 1)) {
                boolean created = false;
                try {
                    Adder[] rs = adders;
                    if (a != null && rs == as)   // Resize table
                        rs = adders = Arrays.copyOf(as, as.length << 1);
                    int j = h & (rs.length - 1);
                    if (rs[j] == null) {         // Create adder
                        rs[j] = new Adder(x);
                        created = true;
                    }
                } finally {
                    mutex.set(0);
                }
                if (created) {
                    hc.code = h;                // Use this adder next time
                    break;
                }
            }
        }
    }

    /**
     * Returns an estimate of the current sum.  The result is
     * calculated by summing multiple variables, so may not be
     * accurate if updates occur concurrently with this method.
     * 
     * @return the estimated sum
     */
    public long sum() {
        long sum = 0;
        Adder[] as = adders;
        int n = as.length;
        for (int i = 0; i < n; ++i) {
            Adder a = as[i];
            if (a != null)
                sum += a.get();
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
        int n = as.length;
        for (int i = 0; i < n; ++i) {
            Adder a = as[i];
            if (a != null)
                a.set(0L);
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
        long sum = 0;
        Adder[] as = adders;
        int n = as.length;
        for (int i = 0; i < n; ++i) {
            Adder a = as[i];
            if (a != null) {
                sum += a.get();
                a.set(0L);
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
        long c = s.readLong();
        Adder[] as = new Adder[2];
        as[0] = new Adder(c);
        this.adders = as;
        mutex.set(0);
    }

}


