/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * <tt>Lock</tt> implementations provide more flexible locking operations than
 * can be obtained using <tt>synchronized</tt> methods and statements.
 *
 * <p>A lock is a tool for controlling access to a shared
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
 * <p>While the scoping mechanism for <tt>synchronized</tt> methods and 
 * statements makes it much easier to program with monitor locks,
 * and helps avoid many common programming errors involving locks, there are
 * rare occasions where you need to work with locks in a more flexible way. For
 * example, some advanced algorithms for traversing concurrently accessed data
 * structures require the use of what is called &quot;hand-over-hand&quot; or 
 * &quot;chain locking&quot;: you acquire the lock of node A, then node B, 
 * then release A and acquire C, then release B and acquire D and so on. 
 * Implementations of the <tt>Lock</tt> interface facilitate the use of such 
 * advanced algorithms by allowing a lock to be acquired and released in 
 * different scopes, and allowing multiple locks to be acquired and released 
 * in any order. 
 *
 * <p>With this increased flexibilty comes
 * additional responsibility as the absence of block-structured locking
 * removes the automatic release of locks that occurs with 
 * <tt>synchronized</tt> methods and statements. For the simplest usage
 * the following idiom should be used:
 * <pre><tt>     Lock l = ...; 
 *     l.lock();
 *     try {
 *         // access the resource protected by this lock
 *     } finally {
 *         l.unlock();
 *     }
 * </tt></pre>
 *
 * <p><tt>Lock</tt> implementations provide additional functionality over the 
 * use
 * of <tt>synchronized</tt> methods and statements by providing a non-blocking
 * attempt to acquire a lock ({@link #tryLock()}), an attempt to acquire the
 * lock that can be interrupted ({@link #lockInterruptibly}, and an attempt
 * to acquire the lock that can timeout ({@link #tryLock(long, TimeUnit)}).
 * This additional functionality is also extended to built-in monitor
 * locks through the methods of the {@link Locks} utility class.
 *
 * <p>A <tt>Lock</tt> class can also provide behavior and semantics that is 
 * quite different from that of the implicit monitor lock, such as guaranteed 
 * ordering,
 * non-reentrant usage, or deadlock detection. If an implementation provides
 * such specialised semantics then the implementation must document those
 * semantics.
 *
 * <p>Note that <tt>Lock</tt> instances are just normal objects and can 
 * themselves be used as the target in a <tt>synchronized</tt> statement.
 * Acquiring the
 * monitor lock of a <tt>Lock</tt> instance has no specified relationship
 * with invoking any of the {@link #lock} methods of that instance. 
 * It is recommended that to avoid confusion you never use <tt>Lock</tt>
 * instances in this way, except within their own implementation.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter 
 * will result in a {@link NullPointerException} being thrown.
 *
 * <h3>Memory Synchronization</h3>
 * <p>All <tt>Lock</tt> implementations <em>must</em> enforce the same
 * memory synchronization semantics as provided by the built-in monitor lock:
 * <ul>
 * <li>A successful lock operation acts like a successful 
 * <tt>monitorEnter</tt> action
 * <li>A successful <tt>unlock</tt> operation acts like a successful
 * <tt>monitorExit</tt> action
 * </ul>
 * Note that unsuccessful locking and unlocking operations, and reentrant
 * locking/unlocking operations, do not require any memory synchronization
 * effects.
 *
 * <h3>Implementation Considerations</h3>
 * <p>It is recognised that the three forms of lock acquisition (interruptible,
 * non-interruptible, and timed) may differ in their ease of implementation
 * on some platforms and in their performance characteristics.
 * In particular, it may be difficult to provide these features and maintain 
 * specific semantics such as ordering guarantees. 
 * Further, the ability to interrupt the acquisition of a lock may not always
 * be feasible to implement on all platforms.
 * <p>Consequently, an implementation is not required to define exactly the 
 * same 
 * guarantees or semantics for all three forms of lock acquistion, nor is it 
 * required to support interruption of the actual lock acquisition.
 * An implementation is required to clearly
 * document the semantics and guarantees provided by each of the locking 
 * methods. It must also obey the interruption semantics as defined in this
 * interface, to the extent that interruption of lock acquisition is 
 * supported: which is either totally, or only on method entry.
 *
 *
 * @see ReentrantLock
 * @see Condition
 * @see ReadWriteLock
 * @see Locks
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/06/09 23:51:33 $
 * @editor $Author: dholmes $
 *
 **/
public interface Lock {

    /**
     * Acquires the lock. 
     * <p>Acquires the lock if it is available and returns immediately.
     * <p>If the lock is not available then
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until the lock has been acquired.
     * <p><b>Implementation Considerations</b>
     * <p>A <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the lock, such as an invocation that would cause 
     * deadlock, and may throw an (unchecked) exception in such circumstances. 
     * The circumstances and the exception type must be documented by that 
     * <tt>Lock</tt> implementation.
     *
     **/
    public void lock();

    /**
     * Acquires the lock unless the current thread is  
     * {@link Thread#interrupt interrupted}. 
     * <p>Acquires the lock if it is available and returns immediately.
     * <p>If the lock is not available then
     * the current thread thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of two things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread, and interruption of lock acquisition is supported.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire 
     * the lock, and interruption of lock acquisition is supported, 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     *
     * <p><b>Implementation Considerations</b>
     * <p>The ability to interrupt a lock acquisition in some implementations
     * may not be possible, and if possible could reasonably be foreseen to 
     * be an expensive operation. 
     * The programmer should be aware that this may be the case. An
     * implementation should document when this is the case.
     *
     * <p>A <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the lock, such as an invocation that would cause 
     * deadlock, and may throw an (unchecked) exception in such circumstances. 
     * The circumstances and the exception type must be documented by that
     * <tt>Lock</tt> implementation.
     *
     * @throws InterruptedException if the current thread is interrupted
     * (and interruption of lock acquisition is supported).
     *
     * @see Thread#interrupt
     *
     **/
    public void lockInterruptibly() throws InterruptedException;


    /**
     * Acquires the lock only if it is free at the time of invocation.
     * <p>Acquires the lock if it is available and returns immediately
     * with the value <tt>true</tt>.
     * <p>If the lock is not available then this method will return 
     * immediately with the value <tt>false</tt>.
     * <p>A typical usage idiom for this method would be:
     * <pre>
     *      Lock lock = ...;
     *      if (lock.tryLock()) {
     *          try {
     *              // manipulate protected state
     *          } finally {
     *              lock.unlock();
     *          }
     *      } else {
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
     * Acquires the lock if it is free within the given waiting time and the
     * current thread has not been {@link Thread#interrupt interrupted}.
     * <p>Acquires the lock if it is available and returns immediately
     * with the value <tt>true</tt>.
     * <p>If the lock is not available then
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread, and interruption of lock acquisition is supported; or
     * <li> The specified waiting time elapses
     * </ul>
     * <p>If the lock is acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire 
     * the lock, and interruption of lock acquisition is supported, 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * The given waiting time is a best-effort lower bound. If the time is 
     * less than or equal to zero, the method will not wait at all.
     *
     * <p><b>Implementation Considerations</b>
     * <p>The ability to interrupt a lock acquisition in some implementations
     * may not be possible, and if possible could reasonably be foreseen to 
     * be an expensive operation. 
     * The programmer should be aware that this may be the case. An
     * implementation should document when this is the case.
     *
     * <p>A <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the lock, such as an invocation that would cause 
     * deadlock, and may throw an (unchecked) exception in such circumstances. 
     * The circumstances and the exception type must be documented by that 
     * <tt>Lock</tt> implementation.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the <tt>time</tt> argument.
     * @return <tt>true</tt> if the lock was acquired and <tt>false</tt>
     * if the waiting time elapsed before the lock was acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * while trying to acquire the lock (and interruption of lock
     * acquisition is supported).
     *
     * @see Thread#interrupt
     *
     **/
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * Releases the lock.
     * <p><b>Implementation Considerations</b>
     * <p>A <tt>Lock</tt> implementation will usually impose
     * restrictions on which thread can release a lock (typically only the
     * holder of the lock can release it) and may throw
     * an (unchecked) exception if the restriction is violated.
     * Any restrictions and the exception
     * type must be documented by that <tt>Lock</tt> implementation.
     **/
    public void unlock();

    /**
     * Returns a {@link Condition} instance that is bound to this <tt>Lock</tt>
     * instance.
     * <p>Conditions are primarily used with the built-in locking provided by
     * <tt>synchronized</tt> methods and statements 
     * (see {@link Locks#newConditionFor}), but in some rare circumstances it 
     * can be useful to wait for a condition when working with a data 
     * structure that is accessed using a stand-alone <tt>Lock</tt> instance 
     * (see {@link ReentrantLock}). 
     * <p>Before waiting on the condition the lock must be held by the 
     * current thread. 
     * A call to {@link Condition#await()} will atomically release the lock 
     * before waiting and re-acquire the lock before the wait returns.
     * <p><b>Implementation Considerations</b>
     * <p>The exact operation of the {@link Condition} instance depends on the
     * <tt>Lock</tt> implementation and must be documented by that
     * implementation.
     * 
     * @return A {@link Condition} instance for this <tt>Lock</tt> instance.
     * @throws UnsupportedOperationException if this <tt>Lock</tt> 
     * implementation does not support conditions.
     **/
    public Condition newCondition();

}












