/*
 * Written by Doug Lea, Bill Scherer, and Michael Scott with
 * assistance from members of JCP JSR-166 Expert Group and released to
 * the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent;
import java.util.concurrent.*; // for javadoc (till 6280605 is fixed)
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.util.Random;

/**
 * A synchronization point at which threads can pair and swap elements
 * within pairs.  Each thread presents some object on entry to the
 * {@link #exchange exchange} method, matches with a partner thread,
 * and receives its partner's object on return.
 *
 * <p><b>Sample Usage:</b>
 * Here are the highlights of a class that uses an {@code Exchanger}
 * to swap buffers between threads so that the thread filling the
 * buffer gets a freshly emptied one when it needs it, handing off the
 * filled one to the thread emptying the buffer.
 * <pre>{@code
 * class FillAndEmpty {
 *   Exchanger<DataBuffer> exchanger = new Exchanger<DataBuffer>();
 *   DataBuffer initialEmptyBuffer = ... a made-up type
 *   DataBuffer initialFullBuffer = ...
 *
 *   class FillingLoop implements Runnable {
 *     public void run() {
 *       DataBuffer currentBuffer = initialEmptyBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           addToBuffer(currentBuffer);
 *           if (currentBuffer.isFull())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       } catch (InterruptedException ex) { ... handle ... }
 *     }
 *   }
 *
 *   class EmptyingLoop implements Runnable {
 *     public void run() {
 *       DataBuffer currentBuffer = initialFullBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           takeFromBuffer(currentBuffer);
 *           if (currentBuffer.isEmpty())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       } catch (InterruptedException ex) { ... handle ...}
 *     }
 *   }
 *
 *   void start() {
 *     new Thread(new FillingLoop()).start();
 *     new Thread(new EmptyingLoop()).start();
 *   }
 * }
 * }</pre>
 *
 * <p>Memory consistency effects: For each pair of threads that
 * successfully exchange objects via an {@code Exchanger}, actions
 * prior to the {@code exchange()} in each thread
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * those subsequent to a return from the corresponding {@code exchange()}
 * in the other thread.
 *
 * @since 1.5
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @param <V> The type of objects that may be exchanged
 */
public class Exchanger<V> {
    /*
     * The underlying idea is to use a stack to hold nodes containing
     * pairs of items to be exchanged. Except that:
     *
     *  *  Only one element of the pair is known on creation by a
     *     first-arriving thread; the other is a "hole" waiting to be
     *     filled in. This is a degenerate form of the dual stacks
     *     described in "Nonblocking Concurrent Objects with Condition
     *     Synchronization",  by W. N. Scherer III and M. L. Scott.
     *     18th Annual Conf. on  Distributed Computing, Oct. 2004.
     *     It is "degenerate" in that both the items and the holes
     *     are shared in the same nodes.
     *
     *  *  There isn't really a stack here! There can't be -- if two
     *     nodes were both in the stack, they should cancel themselves
     *     out by combining. So that's what we do. The 0th element of
     *     the "arena" array serves only as the top of stack.  The
     *     remainder of the array is a form of the elimination backoff
     *     collision array described in "A Scalable Lock-free Stack
     *     Algorithm", by D. Hendler, N. Shavit, and L. Yerushalmi.
     *     16th ACM Symposium on Parallelism in Algorithms and
     *     Architectures, June 2004. Here, threads spin (using short
     *     timed waits with exponential backoff) looking for each
     *     other.  If they fail to find others waiting, they try the
     *     top spot again.  As shown in that paper, this always
     *     converges.
     *
     * The backoff elimination mechanics never come into play in
     * common usages where only two threads ever meet to exchange
     * items, but they prevent contention bottlenecks when an
     * exchanger is used by a large number of threads.
     *
     * For more details, see the paper "A Scalable Elimination-based
     * Exchange Channel" by William Scherer, Doug Lea, and Michael
     * Scott in Proceedings of SCOOL05 workshop. Available at:
     * http://hdl.handle.net/1802/2104
     */

    /** The number of CPUs, for sizing and spin control */
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * Size of collision space. Using a size of half the number of
     * CPUs provides enough space for threads to find each other but
     * not so much that it would always require one or more to time
     * out to become unstuck.  Note that the arena array holds SIZE+1
     * elements, to include the top-of-stack slot.  Imposing a ceiling
     * is suboptimal for huge machines, but bounds backoff times to
     * acceptable values. To ensure max times less than 2.4 seconds,
     * the ceiling value plus the shift value of backoff base (below)
     * should be less than or equal to 31.
     */
    private static final int SIZE = Math.min(25, (NCPUS + 1) / 2);

    /**
     * Base unit in nanoseconds for backoffs.  Must be a power of two.
     * Should be small because backoffs exponentially increase from base.
     * The value should be close to the round-trip time of a call to
     * LockSupport.park in the case where some other thread has already
     * called unpark.  On multiprocessors, timed waits less than this value
     * are implemented by spinning.
     */
    static final long BACKOFF_BASE = (1L << 6);

    /**
     * The number of nanoseconds for which it is faster to spin rather
     * than to use timed park. Should normally be zero on
     * uniprocessors and BACKOFF_BASE on multiprocessors.
     */
    static final long spinForTimeoutThreshold = (NCPUS < 2) ? 0 : BACKOFF_BASE;

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes.  Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     */
    static final int maxTimedSpins = (NCPUS < 2) ? 0 : 16;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     */
    static final int maxUntimedSpins = maxTimedSpins * 32;

    /**
     * Sentinel item representing cancellation.  This value is placed
     * in holes on cancellation, and used as a return value from Node
     * methods to indicate failure to set or get hole.
     */
    static final Object FAIL = new Object();

    /**
     * The collision arena. arena[0] is used as the top of the stack.
     * The remainder is used as the collision elimination space.
     */
    private final AtomicReference<Node>[] arena;

    /**
     * Per-thread random number generator.  Because random numbers
     * are used to choose slots and delays to reduce contention, the
     * random number generator itself cannot introduce contention.
     * And the statistical quality of the generator is not too
     * important.  So we use a custom cheap generator, and maintain
     * it as a thread local.
     */
    private static final ThreadLocal<RNG> random = new ThreadLocal<RNG>() {
        public RNG initialValue() { return new RNG(); } };

    /**
     * Creates a new Exchanger.
     */
    public Exchanger() {
	arena = (AtomicReference<Node>[]) new AtomicReference[SIZE + 1];
        for (int i = 0; i < arena.length; ++i)
            arena[i] = new AtomicReference<Node>();
    }

    /**
     * Main exchange function, handling the different policy variants.
     * Uses Object, not "V" as argument and return value to simplify
     * handling of internal sentinel values. Callers from public
     * methods cast accordingly.
     *
     * @param item the item to exchange
     * @param timed true if the wait is timed
     * @param nanos if timed, the maximum wait time
     * @return the other thread's item
     */
    private Object doExchange(Object item, boolean timed, long nanos)
	throws InterruptedException, TimeoutException {
	long lastTime = timed ? System.nanoTime() : 0;
        int idx = 0;     // start out at slot representing top
	int backoff = 0; // increases on failure to occupy a slot
	Node me = new Node(item);

	for (;;) {
            AtomicReference<Node> slot = arena[idx];
            Node you = slot.get();

            // Try to occupy this slot
            if (you == null && slot.compareAndSet(null, me)) {
                // If this is top slot, use regular wait, else backoff-wait
                Object v = ((idx == 0)?
                            me.waitForHole(timed, nanos) :
                            me.waitForHole(true, randomDelay(backoff)));
                if (slot.get() == me)
                    slot.compareAndSet(me, null);
                if (v != FAIL)
                    return v;
                if (Thread.interrupted())
                    throw new InterruptedException();
                if (timed) {
                    long now = System.nanoTime();
                    nanos -= now - lastTime;
                    lastTime = now;
                    if (nanos <= 0)
                        throw new TimeoutException();
                }

                me = new Node(me.item);   // Throw away nodes on failure
                if (backoff < SIZE - 1)   // Increase or stay saturated
                    ++backoff;
                idx = 0;                  // Restart at top
                continue;
            }

            // Try to release waiter from apparently non-empty slot
            if (you != null || (you = slot.get()) != null) {
                boolean success = (you.get() == null &&
                                   you.compareAndSet(null, me.item));
                if (slot.get() == you)
                    slot.compareAndSet(you, null);
                if (success) {
                    you.signal();
                    return you.item;
                }
            }

            // Retry with a random non-top slot <= backoff
            idx = backoff == 0 ? 1 : 1 + random.get().next() % (backoff + 1);
        }
    }

    /**
     * Returns a random delay less than (base times (2 raised to backoff)).
     */
    private long randomDelay(int backoff) {
        return ((BACKOFF_BASE << backoff) - 1) & random.get().next();
    }

    /**
     * Nodes hold partially exchanged data. This class
     * opportunistically subclasses AtomicReference to represent the
     * hole. So get() returns hole, and compareAndSet CAS'es value
     * into hole.  Note that this class cannot be parameterized as V
     * because the sentinel value FAIL is only of type Object.
     */
    static final class Node extends AtomicReference<Object> {
        private static final long serialVersionUID = -3221313401284163686L;

        /** The element offered by the Thread creating this node. */
	final Object item;

        /** The Thread waiting to be signalled; null until waiting. */
        volatile Thread waiter;

        /**
         * Creates node with given item and empty hole.
         *
         * @param item the item
         */
	Node(Object item) {
            this.item = item;
        }

        /**
         * Unparks thread if it is waiting.
         */
        void signal() {
            LockSupport.unpark(waiter);
        }

        /**
         * Waits for and gets the hole filled in by another thread.
         * Fails if timed out or interrupted before hole filled.
         *
         * @param timed true if the wait is timed
         * @param nanos if timed, the maximum wait time
         * @return on success, the hole; on failure, FAIL
         */
        Object waitForHole(boolean timed, long nanos) {
            long lastTime = timed ? System.nanoTime() : 0;
            int spins = timed ? maxTimedSpins : maxUntimedSpins;
            Thread w = Thread.currentThread();
            for (;;) {
                if (w.isInterrupted())
                    compareAndSet(null, FAIL);
                Object h = get();
                if (h != null)
                    return h;
                if (timed) {
                    long now = System.nanoTime();
                    nanos -= now - lastTime;
                    lastTime = now;
                    if (nanos <= 0) {
                        compareAndSet(null, FAIL);
                        continue;
                    }
                }
                if (spins > 0)
                    --spins;
                else if (waiter == null)
                    waiter = w;
                else if (!timed)
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanos);
            }
        }
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@link Thread#interrupt interrupted}),
     * and then transfers the given object to it, receiving its object
     * in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread. The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of two things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @param x the object to exchange
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     */
    public V exchange(V x) throws InterruptedException {
        try {
            return (V)doExchange(x, false, 0);
        } catch (TimeoutException cannotHappen) {
            throw new Error(cannotHappen);
        }
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@link Thread#interrupt interrupted} or
     * the specified waiting time elapses), and then transfers the given
     * object to it, receiving its object in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread. The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of three things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link TimeoutException}
     * is thrown.
     * If the time is
     * less than or equal to zero, the method will not wait at all.
     *
     * @param x the object to exchange
     * @param timeout the maximum time to wait
     * @param unit the time unit of the <tt>timeout</tt> argument
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     * @throws TimeoutException if the specified waiting time elapses
     *         before another thread enters the exchange
     */
    public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        return (V)doExchange(x, true, unit.toNanos(timeout));
    }

    /**
     * Cheap XorShift random number generator used for determining
     * elimination array slots and backoff delays.  This uses the
     * simplest of the generators described in George Marsaglia's
     * "Xorshift RNGs" paper.  This is not a high-quality generator
     * but is acceptable here.
     */
    static final class RNG {
        /** Use java.util.Random as seed generator for new RNGs. */
        private static final Random seedGenerator = new Random();
        private int seed = seedGenerator.nextInt() | 1;

        /**
         * Returns random nonnegative integer.
         */
        int next() {
            int x = seed;
            x ^= x << 6;
            x ^= x >>> 21;
            seed = x ^= x << 7;
            return x & 0x7FFFFFFF;
        }
    }

}
