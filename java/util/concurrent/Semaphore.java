package java.util.concurrent;

/**
 * A counting semaphore.  Conceptually, a semaphore maintains a set of
 * permits.  Each {@link #acquire} blocks if necessary until a permit is
 * available, and then takes it.  Each {@link #release} adds a permit,
 * potentially releasing a blocking acquirer.
 * However, no actual permit objects are used; the <tt>Semaphore</tt> just
 * keeps a count of the number available and acts accordingly.
 *
 * <p>Semaphores are used to restrict the number of threads than can
 * access some (physical or logical) resource. For example, here is
 * a class that uses a semaphore to control access to a pool of items:
 * <pre>
 * class Pool {
 *   private static final MAX_AVAILABLE = 100;
 *   private final Semaphore available = new Semaphore(MAX_AVAILABLE);
 *
 *   public Object getItem() throws InterruptedException {
 *     available.acquire();
 *     return getNextAvailableItem();
 *   }
 *
 *   public void putItem(Object x) {
 *     if (markAsUnused(x))
 *       available.release();
 *   }
 *
 *   // Not a particularly efficient data structure; just for demo
 *
 *   protected Object[] items = ... whatever kinds of items being managed
 *   protected boolean[] used = new boolean[MAX_AVAILABLE];
 *
 *   protected synchronized Object getNextAvailableItem() {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (!used[i]) {
 *          used[i] = true;
 *          return items[i];
 *       }
 *     }
 *     return null; // not reached
 *   }
 *
 *   protected synchronized boolean markAsUnused(Object item) {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (item == items[i]) {
 *          if (used[i]) {
 *            used[i] = false;
 *            return true;
 *          }
 *          else
 *            return false;
 *       }
 *     }
 *     return false;
 *   }
 *
 * }
 * </pre>
 * <p>Before obtaining an item each thread must acquire a permit from the
 * semaphore, guaranteeing that an item is available for use. When the
 * thread has finished with the item it is returned back to the pool and
 * a permit is returned to the semaphore, allowing another thread to
 * acquire that item.
 * Note that no synchronization lock is held when {@link #acquire} is
 * called as that would prevent an item from being returned to the pool.
 * A semaphore encapsulates the synchronization needed to restrict access to
 * the pool, separately from any synchronization needed to maintain the
 * consistency of the pool itself.
 *
 * <p>A semaphore initialized to one, and which is used such that it only
 * has at most one permit available, can serve as a mutual exclusion lock.
 * This is more
 * commonly known as a <em>binary semaphore</em>, because it only has two
 * states: one permit available, or zero permits available.
 * When used in this way, the binary semaphore has the property (unlike many
 * {@link Lock} implementations, that the &quot;lock&quot; can be released by
 * a thread other than the owner (as semaphores have no notion of ownership).
 * This can be useful in some specialised contexts, such as deadlock recovery.
 *
 * <p>This class makes no guarantees about the order in which waiting threads
 * acquire permits. If you need deterministic guarantees, consider
 * using {@link FifoSemaphore}.
 *
 * @fixme Barging needs to be discussed. Need to see implementation.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/22 01:01:43 $
 * @editor $Author: dholmes $
 *
 */
public class Semaphore {

    private long permits = 0;

    /**
     * Construct a <tt>Semaphore</tt> with the given number of
     * permits.
     * @param permits the initial number of permits available
     */
    public Semaphore(long permits) {
        this.permits = permits;
    }

    /**
     * Acquire a permit from this semaphore, blocking until one is
     * available, or the thread is {@link Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread happens to be chosen as the
     * thread to receive the permit; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     */
    public void acquire() throws InterruptedException {}

    /**
     * Acquires a permit only if one is available at the time of invocation.
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value <tt>true</tt>,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then this method will return
     * immediately with the value <tt>false</tt>.
     *
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * otherwise.
     */
    public boolean tryAcquire() {
        return false;
    }

    /**
     * Acquires a permit if one becomes available  within the given waiting
     * time and the
     * current thread has not been {@link Thread#interrupt interrupted}.
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value <tt>true</tt>,
     * reducing the number of available permits by one.
     * <p>If no permit is available then
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread happens to be chosen as the
     * thread to receive the permit; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If a permit is acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire
     * a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * The given waiting time is a best-effort lower bound. If the time is
     * less than or equal to zero, the method will not wait at all.
     *
     * @param timeout the maximum time to wait for a permit
     * @param granularity the time unit of the <tt>timeout</tt> argument.
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * if the waiting time elapsed before a permit was acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(long timeout, TimeUnit granularity)
        throws InterruptedException {
        return false;
    }

    /**
     * Releases a permit, returning it to the semaphore.
     * <p>Releases a permit, increasing the number of available permits
     * by one.
     * If any threads are blocking trying to acquire a permit, then one
     * is selected and given the permit that was just released.
     * That thread is re-enabled for thread scheduling purposes.
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link #acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     */
    public void release() {}

    /**
     * Return the current number of permits available in this semaphore.
     * <p>This method is typically used for debugging and testing purposes.
     * @return the number of permits available in this semaphore.
     */
    public long availablePermits() {
        return permits;
    }
}



