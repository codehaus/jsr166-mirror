package java.util.concurrent;

/**
 * A <tt>Lock</tt> provides more flexible locking operations than
 * can be obtained using <tt>synchronized</tt> methods and statements.
 *
 * <p>A <tt>Lock</tt> is a tool for controlling access to a shared
 * resource by multiple threads. Commonly, a lock provides exclusive access
 * to a shared resource: only one thread at a time can acquire the
 * lock and all access to the shared resource requires that the lock be
 * acquired first. However, some locks may allow concurrent access to a shared
 * resource, such as the read lock of a {@link ReadWriteLock}.
 *
 * <p>The use of <tt>synchronized</tt> methods or statements provides 
 * access to the implicit monitor lock associated with every object, but
 * forces all lock acquisition and release to occur in a block-structured way:
 * when multiple locks are acquired they must be released in the opposite
 * order, and all locks must be released in the same lexical scope in which
 * they were acquired.
 *
 * <p>A <tt>Lock</tt> can be acquired and released in different scopes, and
 * multiple locks can be acquired and released in any order. This flexibility
 * is essential for implementing efficient algorithms for the concurrent
 * access of some data structures. But with this increased flexibilty comes
 * additional responsibility as the absence of block-structured locking
 * removes the automatic release of locks that occurs with 
 * <tt>synchronized</tt> methods and statements. For the simplest usage
 * the following idiom should be used:
 * <pre><tt>     Lock l = ...; 
 *     l.lock();
 *     try {
 *         // access the resource protected by this lock
 *     }
 *     finally {
 *         l.unlock();
 *     }
 * </tt></pre>
 * <p>A <tt>Lock</tt> also provides additional functionality over the use
 * of <tt>synchronized</tt> methods and blocks by providing a non-blocking
 * attempt to acquire a lock ({@link #tryLock()}), an attempt to acquire the
 * lock that can be interrupted ({@link #lockInterruptibly}, and an attempt
 * to acquire the lock that can timeout ({@link #tryLock(long, Clock)}).
 * A <tt>Lock</tt> can also provide behaviour and semantics that is quite
 * different to that of the implicit monitor lock.
 *
 * @see ReentrantLock
 * @see Condition
 * @see ReadWriteLock
 * @see Locks
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2002/12/05 00:49:20 $
 * @editor $Author: dholmes $
 **/
public interface Lock {

    /**
     * Acquire the lock. If the lock is not available then
     * the current thread will block until the lock has been acquired.
     * <p>A concrete <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the
     * lock, such as an invocation that would cause deadlock, and may throw 
     * an exception in such circumstances. The circumstances and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     *
     **/
    public void lock();

    /**
     * Acquire the lock only if the current thread is not interrupted.
     * <p>If the lock is available then this method will return immediately.
     * <p>If the lock is not available then the current thread will block until
     * one of two things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     * <p>If the current thread is {@link Thread#interrupt interrupted} 
     * while waiting to acquire the lock then {@link InterruptedException}
     * is thrown and the current thread's <em>interrupted status</em> 
     * is cleared.
     *
     * <p>A concrete <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the
     * lock, such as an invocation that would cause deadlock, and may throw 
     * an exception in such circumstances. The circumstances and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     *
     * @throws InterruptedException if the current thread is interrupted
     * while trying to acquire the lock.
     *
     * @see Thread#interrupt
     *
     * @fixme This allows for interruption only if waiting actually occurs.
     * Is this correct? Is it too strict?
     **/
    public void lockInterruptibly() throws InterruptedException;


    /**
     * Acquire the lock only if it is free at the time of invocation.
     * <p>If the lock is available then this method will return immediately
     * with the value <tt>true</tt>.
     * <p>If the lock is not available then this method will return 
     * immediately with the value <tt>false</tt>.
     * <p>A typical usage idiom for this method would be:
     * <pre>
     *      Lock lock = ...;
     *      if (lock.tryLock()) {
     *          try {
     *              // manipulate protected state
     *          finally {
     *              lock.unlock();
     *          }
     *      }
     *      else {
     *          // perform alternative actions
     *      }
     * </pre>
     * This usage ensures that the lock is unlocked if it was acquired, and
     * doesn't try to unlock if the lock was not acquired.
     *
     * @return <tt>true</tt> if the lock was acquired and <tt>false</tt>
     * otherwise.
     **/
    public boolean tryLock();

    /**
     * Acquire the lock if it is free within the given wait time and the
     * current thread has not been interrupted. 
     * <p>If the lock is available then this method will return immediately
     * with the value <tt>true</tt>.
     * <p>If the lock is not available then the current thread will block until
     * one of three things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li> The specified wait time elapses
     * </ul>
     * <p>If the lock is acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread is {@link Thread#interrupt interrupted} 
     * while waiting to acquire the lock then {@link InterruptedException}
     * is thrown and the current thread's <em>interrupted status</em> 
     * is cleared.
     * <p>If the specified wait time elapses then the value <tt>false</tt>
     * is returned.
     * <p>The given wait time is a best-effort lower bound. If the time is 
     * less than or equal to zero, the method will not wait at all, but may 
     * still throw an <tt>InterruptedException</tt> if the thread is 
     * {@link Thread#interrupt interrupted}.
     *
     * <p>A concrete <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the
     * lock, such as an invocation that would cause deadlock, and may throw 
     * an exception in such circumstances. The circumstances and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     *
     * @param time the maximum time to wait for the lock
     * @param granularity the time unit of the <tt>time</tt> argument.
     * @return <tt>true</tt> if the lock was acquired and <tt>false</tt>
     * if the wait time elapsed before the lock was acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * while trying to acquire the lock.
     *
     * @see Thread#interrupt
     *
     * @fixme We have inconsistent interrupt semantics if the thread is
     * interrupted before calling this method: if the lock is available
     * we won't throw IE, if the lock is not available and the timeout is <=0
     * then we may throw IE. Need to resolve this.
     *
     * @fixme (2) Do we throw NPE if Clock is null?
     *
     **/
    public boolean tryLock(long time, Clock granularity) throws InterruptedException;

    /**
     * Release the lock.
     * <p>A concrete <tt>Lock</tt> implementation will usually impose
     * restrictions on which thread can release a lock (typically only the
     * holder of the lock can release it) and may throw
     * an exception if the restriction is violated. 
     * Any restrictions and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     **/
    public void unlock();

    /**
     * Construct a new {@link Condition} for this <tt>Lock</tt>.
     * <p>The exact operation of the {@link Condition} depends on the concrete
     * <tt>Lock</tt> implementation and must be documented by that
     * implementation.
     * 
     * @return A {@link Condition} object for this <tt>Lock</tt>, or
     * <tt>null</tt> if this <tt>Lock</tt> type does not support conditions.
     *
     * @fixme Should this always return a new condition (which could be a
     * "recycled" instance from a pool), or could a lock enforce, for example,
     * a singleton condition? I can't think of an example where this might
     * make sense but what should we say about the returned instance here?
     * Anything? Nothing?
     **/
    public Condition newCondition();

}

