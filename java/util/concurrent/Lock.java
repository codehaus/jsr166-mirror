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
 * <pre><tt>    Lock l = ...; 
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
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2002/12/04 04:34:34 $
 * @editor $Author: dholmes $
 **/
public interface Lock {

    /**
     * Acquire the lock.
     * The current thread will block until the lock has been acquired.
     * 
     * @fixme Is a concrete lock allowed to throw an exception
     * if it detects deadlock? If so, how should that be specified here?
     **/
    public void lock();

    /**
     * Acquire the lock only if the current thread is not interrupted.
     * The current thread will block until the lock has been acquired, or
     * the current thread is {@link Thread#interrupt interrupted}.
     *
     * @fixme do we have specific interruption semantics ie.
     * is <pre><tt>
     * Thread.currentThread().interrupt(); l.lockInterruptibly();
     * </tt></pre>
     * guaranteed to throw the IE?
     *
     * @see Thread#interrupt
     * @throws InterruptedException if the current thread is interrupted
     * while trying to acquire the lock. If this exception is thrown then
     * the lock was not acquired.
     **/
    public void lockInterruptibly() throws InterruptedException;


    /**
     * Acquire the lock only if it is free at the time of invocation.
     * The current thread is not blocked if the lock is unavailable, but
     * instead the method returns immediately with the value 
     * <tt>false</tt>.
     * @return <tt>true</tt> if the lock was acquired and <tt>false</tt>
     * otherwise.
     **/
    public boolean tryLock();

    /**
     * Acquire the lock if it is free within the given wait time and the
     * current thread has not been interrupted. 
     * The current thread will block until either the lock is acquired, the
     * specified waiting time elapses, or the thread is interrupted.
     * <p>The given wait time is a best-effort lower bound. If the time is 
     * less than or equal to zero, the method will not wait at all, but may 
     * still throw an <tt>InterruptedException</tt> if the thread is 
     * interrupted.
     *
     * @param time the maximum time to wait for the lock
     * @param granularity the time unit of the <tt>time</tt> argument.
     * @return <tt>true</tt> if the lock was acquired and <tt>false</tt>
     * if the wait time elapsed before the lock was acquired.
     * @throws InterruptedException if the current thread is interrupted
     * while trying to acquire the lock. If this exception is thrown then
     * the lock was not acquired.
     *
     * @fixme Do we throw NPE if Clock is null?
     *
     * @see Thread#interrupt
     **/
    public boolean tryLock(long time, Clock granularity) throws InterruptedException;

    /**
     * Release the lock.
     * <p>Whether or not it is an error for thread to release a lock that
     * it has not acquired is dependent on the concrete lock type.
     **/
    public void unlock();

    /**
     * Construct a new {@link Condition} for this <tt>Lock</tt>.
     * <p>The exact operation of the condition depends on the concrete lock
     * type.
     * @return a {@link Condition} object for this <tt>Lock</tt>, or
     * <tt>null</tt> if this <tt>Lock</tt> type does not support conditions.
     **/
    public Condition newCondition();

}

