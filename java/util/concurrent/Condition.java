package java.util.concurrent;

/**
 * <tt>Conditions</tt> abstract out the <tt>Object</tt> monitor 
 * methods ({@link Object#wait() wait}, {@link Object#notify notify} and
 * {@link Object#notifyAll notifyAll}) into distinct objects that can be bound 
 * to other objects to give the effect of having multiple wait-sets per
 * object monitor. They also generalise the monitor methods to allow them to 
 * be used with arbitrary {@link Lock} implementations when needed.
 *
 * <p>Conditions (also known as condition queues or condition variables) 
 * provide
 * a means for one thread to suspend execution (to &quot;wait&quot;) until
 * notified by another thread that some state-condition the first thread is 
 * waiting for may now be true. Because access to this shared state information
 * occurs in different threads, it must be protected and invariably
 * a lock, of some form, is always associated with the condition. The key
 * property that waiting for a condition provides is that it 
 * <em>atomically</em> releases the associated lock and suspends the current
 * thread. 
 *
 * <p>A <tt>Condition</tt> is intrinsically bound to a lock: either a
 * the in-built monitor lock of an object, or a {@link Lock} instance.
 * To obtain a <tt>Condition</tt> for a particular object's monitor lock
 * use the {@link Locks#newConditionFor(Object)} method.
 * To obtain a <tt>Condition</tt> for a particular {@link Lock} use its
 * {@link Lock#newCondition} method.
 *
 * <p>When waiting upon a <tt>Condition</tt> a &quot;<em>spurious 
 * wakeup</em>&quot; is permitted to occur, in 
 * general, as a concession to the underlying platform semantics.
 * This has little practical impact on most application programs as a
 * <tt>Condition</tt> should always be waited upon in a loop, testing
 * the state predicate that is being waited for. An implementation is
 * free to remove the possibility of spurious wakeups but it is 
 * recommended that applications programmers always assume that they can
 * occur and so always wait in a loop.
 *
 * <p>A <tt>Condition</tt> can provide behaviour and semantics that is 
 * different to that of the <tt>Object</tt> monitor methods, such as 
 * guaranteed ordering for notifications, or not requiring a lock to be held 
 * when performing notifications.
 * If an implementation provides such specialised semantics then the 
 * implementation must document those semantics.
 *
 * <p>It is recognised that the three forms of condition waiting 
 * (interruptible, non-interruptible, and timed) may differ in their ease of 
 * implementation on some platforms and in their performance characteristics.
 * In particular, it may be difficult to provide these features and maintain 
 * specific semantics such as ordering guarantees. Consequently an 
 * implementation is not required to define exactly the same guarantees or
 * semantics for all three forms of waiting; but it is required to
 * clearly document the semantics and guarantees provided by each of them.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter 
 * will result in a {@link NullPointerException} being thrown.
 *
 * @fixme Need to clarify interrupt semantics of wait and be careful of 
 * wording. Current semantics are that if you have interrupted state set on
 * calling await then you throw IE. That sounds reasonable. One issue is 
 * whether you do this before or after releasing the lock. The wording is
 * easiest if you write it as if this is checked after releasing the lock, 
 * but an implementation need not do that, but the difference in behaviour
 * could be observable.
 *
 * @fixme (2) How on earth would you get an interruptible wait if you wanted
 * to map straight onto a pthread_cond_t ? I don't think it is possible.
 * You would have to add a wrapper to allow checking of an interrupt flag.
 *
 * @fixme (3) Need a usage example
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2002/12/09 06:22:56 $
 * @editor $Author: dholmes $
 **/
public interface Condition {

    /**
     * Causes the current thread to wait until it is signalled or interrupted.
     *
     * <p>The lock associated with this <tt>Condition</tt> is atomically 
     * released and the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until <em>one</em> of four things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #signal} method for this 
     * <tt>Condition</tt> and the current thread happens to be chosen as the 
     * thread to be awakened; or
     * <li>Some other thread invokes the {@link #signalAll} method for this 
     * <tt>Condition</tt>; or
     * <li>The <em>interrupted status</em> of the current thread is set, either
     * because it entered this method with its interrupted status set, or
     * some other thread {@link Thread#interrupt interrupted} it while it was
     * waiting; or
     * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
     * </ul>
     *
     * <p>In all cases, before this method can return the current thread must
     * re-acquire the lock associated with this <tt>Condition</tt>. When the
     * thread returns it is <em>guaranteed</em> to hold this lock.
     *
     * <p>If the current thread's interrupted status is set
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared.
     * 
     * <p>The conditions under which the wait completes are mutually 
     * exclusive. For example, if the thread is signalled then it will
     * never return by throwing {@link InterruptedException}; conversely
     * a thread that is interrupted and throws {@link InterruptedException}
     * will never consume a {@link #signal}.
     *
     * <p>The current thread is assumed to hold the lock associated with this
     * condition when this method is called.
     * It is up to the implementation to determine if this is
     * the case and if not, how to respond. Typically, an exception will be 
     * thrown (such as {@link IllegalMonitorStateException}) and the
     * implementation document that fact.
     *
     * @throws InterruptedException if the current thread had its interrupted
     * status set on entry to this method, or while waiting.
     **/
    public void await() throws InterruptedException;

    /**
     * Causes the current thread to wait until it is signalled.
     *
     * <p>The lock associated with this <tt>Condition</tt> is atomically 
     * released and the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until <em>one</em> of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #signal} method for this 
     * <tt>Condition</tt> and the current thread happens to be chosen as the 
     * thread to be awakened; or
     * <li>Some other thread invokes the {@link #signalAll} method for this 
     * <tt>Condition</tt>; or
     * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
     * </ul>
     *
     * <p>In all cases, before this method can return the current thread must
     * re-acquire the lock associated with this <tt>Condition</tt>. When the
     * thread returns it is <em>guaranteed</em> to hold this lock.
     *
     * <p>If the current thread's interrupt status is set when it enters
     * this method, or it is {@link Thread#interrupt interrupted} 
     * while waiting, it will continue to wait until signalled. When it finally
     * returns from this method it's <em>interrupted status</em> will still
     * be set.
     * 
     * <p>The current thread is assumed to hold the lock associated with this
     * condition when this method is called.
     * It is up to the implementation to determine if this is
     * the case and if not, how to respond. Typically, an exception will be 
     * thrown (such as {@link IllegalMonitorStateException}) and the
     * implementation document that fact.
     *
     **/
    public void awaitUninterruptibly();

    /**
     * Causes the current thread to wait until it is signalled or interrupted,
     * or the specified waiting time elapses.
     *
     * <p>The lock associated with this <tt>Condition</tt> is atomically 
     * released and the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until <em>one</em> of five things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #signal} method for this 
     * <tt>Condition</tt> and the current thread happens to be chosen as the 
     * thread to be awakened; or 
     * <li>Some other thread invokes the {@link #signalAll} method for this 
     * <tt>Condition</tt>; or
     * <li>The <em>interrupted status</em> of the current thread is set, either
     * because it entered this method with its interrupted status set, or
     * some other thread {@link Thread#interrupt interrupted} it while it was
     * waiting; or
     * <li>The specified waiting time elapses; or
     * <li>A &quot;<em>spurious wakeup</em>&quot; occurs.
     * </ul>
     *
     * <p>In all cases, before this method can return the current thread must
     * re-acquire the lock associated with this <tt>Condition</tt>. When the
     * thread returns it is <em>guaranteed</em> to hold this lock.
     *
     * <p>If the thread is signalled while waiting then the value <tt>true</tt>
     * is returned.
     *
     * <p>If the current thread's interrupted status is set
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * 
     * <p>The conditions under which the wait completes are mutually 
     * exclusive. For example, if the thread is signalled then it will
     * never return by throwing {@link InterruptedException}; conversely
     * a thread that is interrupted and throws {@link InterruptedException}
     * will never consume a {@link #signal}.
     *
     * <p>The current thread is assumed to hold the lock associated with this
     * condition when this method is called.
     * It is up to the implementation to determine if this is
     * the case and if not, how to respond. Typically, an exception will be 
     * thrown (such as {@link IllegalMonitorStateException}) and the
     * implementation document that fact.
     *
     * @throws InterruptedException if the current thread had its interrupted
     * status set on entry to this method, or while waiting.
     *
     * @param time the maximum time to wait
     * @param granularity the time unit of the <tt>time</tt> argument.
     * @return <tt>>true</t> if the current thread was signalled while
     * waiting, and <tt>false</tt> if the waiting time elapsed before
     * a signal occurred.
     *
     */
    public boolean await(long time, Clock granularity) throws InterruptedException;

    /**
     * Wakes up one waiting thread.
     *
     * <p>If any threads are waiting on this <tt>Condition</tt> then one
     * is selected for waking up. That thread must then re-acquire the
     * lock before returning from <tt>await</tt>.
     **/
    public void signal();

    /**
     * Wake up all waiting threads.
     *
     * <p>If any threads are waiting on this <tt>Condition</tt> then they are
     * all woken up. Each thread must re-acquire the lock before it can
     * return from <tt>await</tt>.
     **/
    public void signalAll();

}




