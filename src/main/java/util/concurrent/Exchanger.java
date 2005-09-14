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
 * Here are the highlights of a class that uses an <tt>Exchanger</tt> to
 * swap buffers between threads so that the thread filling the
 * buffer gets a freshly
 * emptied one when it needs it, handing off the filled one to
 * the thread emptying the buffer.
 * <pre>
 * class FillAndEmpty {
 *   Exchanger&lt;DataBuffer&gt; exchanger = new Exchanger();
 *   DataBuffer initialEmptyBuffer = ... a made-up type
 *   DataBuffer initialFullBuffer = ...
 *
 *   class FillingLoop implements Runnable {
 *     public void run() {
 *       DataBuffer currentBuffer = initialEmptyBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           addToBuffer(currentBuffer);
 *           if (currentBuffer.full())
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
 *           if (currentBuffer.empty())
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
 * </pre>
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
     */

    /**
     * Size of collision space. Using a size of half the number of
     * CPUs provides enough space for threads to find each other but
     * not so much that it would always require one or more to time
     * out to become unstuck. Note that the arena array holds SIZE+1
     * elements, to include the top-of-stack slot.
     */
    private static final int SIZE =
        (Runtime.getRuntime().availableProcessors() + 1) / 2;

    /**
     * Base unit in nanoseconds for backoffs. Must be a power of two.
     * Should be small because backoffs exponentially increase from
     * base.
     */
    private static final long BACKOFF_BASE = 128L;

    /**
     * Sentinel item representing cancellation.  This value is placed
     * in holes on cancellation, and used as a return value from Node
     * methods to indicate failure to set or get hole.
     */
    static final Object FAIL = new Object();

    /**
     * The collision arena. arena[0] is used as the top of the stack.
     * The remainder is used as the collision elimination space.
     * Each slot holds an AtomicReference<Node>, but this cannot be
     * expressed for arrays, so elements are casted on each use.
     */
    private final AtomicReference[] arena;

    /** Generator for random backoffs and delays. */
    private final Random random = new Random();

    /**
     * Creates a new Exchanger.
     */
    public Exchanger() {
	arena = new AtomicReference[SIZE + 1];
        for (int i = 0; i < arena.length; ++i)
            arena[i] = new AtomicReference();
    }

    /**
     * Main exchange function, handling the different policy variants.
     * Uses Object, not "V" as argument and return value to simplify
     * handling of internal sentinel values. Callers from public
     * methods cast accordingly.
     * @param item the item to exchange.
     * @param timed true if the wait is timed.
     * @param nanos if timed, the maximum wait time.
     * @return the other thread's item.
     */
    private Object doExchange(Object item, boolean timed, long nanos)
	throws InterruptedException, TimeoutException {
	Node me = new Node(item);
	long lastTime = (timed)? System.nanoTime() : 0;
        int idx = 0;     // start out at slot representing top
	int backoff = 0; // increases on failure to occupy a slot

	for (;;) {
            AtomicReference<Node> slot = (AtomicReference<Node>)arena[idx];

            // If this slot is already occupied, there is a waiting item...
            Node you = slot.get();
            if (you != null) {
                Object v = you.fillHole(item);
                slot.compareAndSet(you, null);
                if (v != FAIL)       // ... unless it was cancelled
                    return v;
            }

            // Try to occupy this slot
            if (slot.compareAndSet(null, me)) {
                // If this is top slot, use regular wait, else backoff-wait
                Object v = ((idx == 0)?
                            me.waitForHole(timed, nanos) :
                            me.waitForHole(true, randomDelay(backoff)));
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

                me = new Node(item);      // Throw away nodes on failure
                if (backoff < SIZE - 1)   // Increase or stay saturated
                    ++backoff;
                idx = 0;                  // Restart at top
            }

            else // Retry with a random non-top slot <= backoff
                idx = 1 + random.nextInt(backoff + 1);

        }
    }

    /**
     * Returns a random delay less than (base times (2 raised to backoff))
     */
    private long randomDelay(int backoff) {
        return ((BACKOFF_BASE << backoff) - 1) & random.nextInt();
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
        /** The Thread creating this node. */
        final Thread waiter;

        /**
         * Creates node with given item and empty hole.
         * @param item the item.
         */
	Node(Object item) {
            this.item = item;
            waiter = Thread.currentThread();
        }

        /**
         * Tries to fill in hole. On success, wakes up the waiter.
         * @param val the value to place in hole.
         * @return on success, the item; on failure, FAIL.
         */
	Object fillHole(Object val) {
            if (compareAndSet(null, val)) {
                LockSupport.unpark(waiter);
                return item;
            }
            return FAIL;
	}

        /**
         * Waits for and gets the hole filled in by another thread.
         * Fails if timed out or interrupted before hole filled.
         * @param timed true if the wait is timed.
         * @param nanos if timed, the maximum wait time.
         * @return on success, the hole; on failure, FAIL.
         */
        Object waitForHole(boolean timed, long nanos) {
            long lastTime = (timed)? System.nanoTime() : 0;
            Object h;
            while ((h = get()) == null) {
                // If interrupted or timed out, try to cancel by
                // CASing FAIL as hole value.
                if (Thread.currentThread().isInterrupted() ||
                    (timed && nanos <= 0))
                    compareAndSet(null, FAIL);
                else if (!timed)
                    LockSupport.park();
                else {
                    LockSupport.parkNanos(nanos);
                    long now = System.nanoTime();
                    nanos -= now - lastTime;
                    lastTime = now;
                }
            }
            return h;
        }
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * it is {@link Thread#interrupt interrupted}),
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
     * @return the object provided by the other thread.
     * @throws InterruptedException if current thread was interrupted
     * while waiting
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
     * it is {@link Thread#interrupt interrupted}, or the specified waiting
     * time elapses),
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
     * @param unit the time unit of the <tt>timeout</tt> argument.
     * @return the object provided by the other thread.
     * @throws InterruptedException if current thread was interrupted
     * while waiting
     * @throws TimeoutException if the specified waiting time elapses before
     * another thread enters the exchange.
     */
    public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        return (V)doExchange(x, true, unit.toNanos(timeout));
    }
}
