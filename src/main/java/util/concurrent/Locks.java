/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import sun.misc.Unsafe;
import java.util.Date;

/**
 * Utility operations for built-in synchronization and {@link Lock} classes.
 * <p>The <tt>Locks</tt> class defines utility methods that enhance the
 * use of <tt>synchronized</tt> methods and statements by:
 * <ul>
 * <li>allowing for an attempt to acquire a monitor lock only if it is free, 
 * or if it becomes free within a specified time;
 * <li>allowing an attempt to acquire a monitor lock to be interruptible; and
 * <li>providing a method to acquire multiple monitor locks only if
 * they are all available.
 * </ul>
 * <p>An additional convenience method is provided to acquire multiple
 * {@link Lock} instances only if they are all available.
 * <p>To preserve the block-structured locking that is required for the
 * built-in monitor locks, each method takes a {@link Runnable} action as 
 * an argument and executes its {@link Runnable#run run} method with the 
 * appropriate locks held. 
 * When the {@link Runnable#run run} method completes all locks
 * are released.
 *
 * <p><b>Note:</b> All methods that take {@link Object} parameters treat
 * those parameters as {@link Object Objects}, even if they happen to be
 * {@link Lock} instances. These methods will always acquire the monitor
 * lock of the given object - they will not perform a {@link Lock#lock}
 * invocation.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter
 * will result in a {@link NullPointerException} being thrown.
 *
 * <h3>Memory Synchronization</h3>
 * <p>When a {@link Runnable} object's {@link Runnable#run run} method is 
 * executed it means that the appropriate 
 * lock (or locks) has been acquired, and so the memory synchronization 
 * effects of acquiring that lock will have taken place. Similarly, when
 * the {@link Runnable} object's {@link Runnable#run run} method completes,
 * the lock (or locks) is released and the associated memory synchronization
 * effects will take place. Exactly what those memory synchronization
 * effects are will depend on the nature of the lock and the type of 
 * acquisition/release - for example, reentrantly acquiring a monitor lock
 * has no associated memory synchronization effects.
 * <p>When mutliple locks are involved it may be that some of the locks are
 * acquired and subsequently released, before an unavailable lock is found.
 * In that case the memory synchronization effects will be those of the locks
 * that were actually acquired and actually released.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/05/27 18:14:40 $
 * @editor $Author: dl $
 *
 * @fixme add implementation notes for any performance issues related to
 * timeouts or interrupts
 **/
public class Locks {
    private Locks() {} // uninstantiable.

    /**
     * Performs the given action holding the monitor lock of
     * the given object only if that lock is currently free.
     *
     * <p>If the monitor lock of the given object is immediately available
     * to the current thread then it is acquired. 
     * The action is then executed and finally the monitor lock is released
     * and the method returns with the value <tt>true</tt>.
     * <p>If the monitor lock is not available then the method returns 
     * immediately with the value <tt>false</tt>.
     * <p>If the action completes abruptly due to an {@link Error} or
     * {@link RuntimeException}, then the method completes abruptly
     * for the same reason, after the lock has been released.
     *
     * @param lock the object whose monitor lock must be acquired
     * @param action the code to run while holding the monitor lock
     * @return <tt>true</tt> if the action was executed, and <tt>false</tt>
     * otherwise.
     **/
    public static boolean attempt(Object lock, Runnable action) {
        if (lock == null || action == null)
            throw new NullPointerException();

        if (!JSR166Support.tryLockEnter(lock))
            return false;
        
        try {
            action.run();
            return true;
        }
        finally {
            JSR166Support.tryLockExit(lock);
        }
    }

    /**
     * Performs the given action holding the monitor locks of
     * the given objects only if those locks are currently free.
     *
     * <p>If the monitor locks of each object in the array are immediately 
     * available to the current thread then they are acquired. 
     * The action is then executed and finally the monitor locks are released
     * and the method returns with the value <tt>true</tt>.
     * <p>If any of the monitor locks is not available then 
     * all previously acquired monitor locks are released and the method 
     * returns with the value <tt>false</tt>.
     * <p>If the action completes abruptly due to an {@link Error} or
     * {@link RuntimeException}, then the method completes abruptly
     * for the same reason, after all the locks have been released.
     *
     * @param locks the objects whose monitor locks must be acquired
     * @param action the code to run while holding the monitor locks
     * @return <tt>true</tt> if the action was executed, and <tt>false</tt>
     * otherwise.
     *
     * @throws NullPointerException if an attempt is made to acquire the
     * monitor lock of a <tt>null</tt> element in the <tt>locks</tt> array.
     **/
    public static boolean attempt(Object[] locks, Runnable action) {
        // This code is a little mysterious looking unless you remember
        // that finally clauses execute before return or throw.
        int lastlocked = -1;
        try {
            boolean ok = true;
            for (int i = 0; i < locks.length; ++i) {
                Object l = locks[i];
                if (l == null) 
                    throw new NullPointerException(); 
                if (!JSR166Support.tryLockEnter(l))
                    return false;
                lastlocked = i;
            }
            action.run();
            return true;
        }
        finally {
            for (int i = lastlocked; i >= 0; --i)
                JSR166Support.tryLockExit(locks[i]);
        }
    }

    /**
     * Performs the given action holding the given {@link Lock} instances, only
     * if those {@link Lock} instances are currently free.
     *
     * <p>If each of the locks in the array are immediately 
     * available to the current thread then they are acquired. 
     * The action is then executed and finally the locks are 
     * released and the method returns with the value <tt>true</tt>.
     * <p>If any of the locks are not available then 
     * all previously acquired locks are released and the 
     * method returns immediately with the value <tt>false</tt>.
     * <p>If the action completes abruptly due to an {@link Error} or
     * {@link RuntimeException}, then the method completes abruptly
     * for the same reason, after all the locks have been 
     * released.
     *
     * @param locks the {@link Lock} instances that must be acquired
     * @param action the code to run while holding the given locks
     * @return <tt>true</tt> if the action was executed, and <tt>false</tt>
     * otherwise.
     *
     * @throws NullPointerException if an attempt is made to acquire the
     * lock of a <tt>null</tt> element in the <tt>locks</tt> array.
     **/
    public static boolean attempt(Lock[] locks, Runnable action) {
        // This code is a little mysterious looking unless you remember
        // that finally clauses execute before return or throw.
        int lastlocked = -1;
        try {
            for (int i = 0; i < locks.length; ++i) {
                Lock l = locks[i];
                if (l == null) 
                    throw new NullPointerException(); 
                if (!l.tryLock()) 
                    return false;
                lastlocked = i;
            }

            action.run();
            return true;
        }
        finally {
            for (int i = lastlocked; i >= 0; --i)
                locks[i].unlock();
        }
    }

    /**
     * Returns a conservative indicator of whether the given lock is
     * held by any thread. This method always returns true if
     * the lock is held, but may return true even if not held.
     *
     * @return true if lock is held, and either true or false if not held.
     */
    public static boolean mightBeLocked(Object lock) {
        return JSR166Support.mightBeLocked(lock);
    }

    /**
     * Returns a {@link Condition} instance for use with the given object.
     * <p>The returned {@link Condition} instance has analagous behavior 
     * to the use of the monitor methods on the given object. Given
     * <pre>    Condition c = Locks.newConditionFor(o);
     * </pre>
     * then:
     * <ul>
     * <li><tt>c.await()</tt> is analogous to <tt>o.wait()</tt>
     * <li><tt>c.signal()</tt> is analogous to <tt>o.notify()</tt>; and
     * <li><tt>c.signalAll()</tt> is analogous to <tt>o.notifyAll()</tt>
     * </ul>
     * in that:
     * <ul>
     * <li>If the monitor lock of <tt>o</tt> is not held when any of the 
     * {@link Condition}
     * {@link Condition#await() waiting} or {@link Condition#signal signalling}
     * methods are called, then an {@link IllegalMonitorStateException} is
     * thrown.
     * <li>When the condition {@link Condition#await() waiting} methods are
     * called the monitor lock of <tt>o</tt> is released and before they 
     * return the monitor lock is
     * reacquired and the lock count restored to what it was when the
     * method was called.
     * <li>If a thread is {@link Thread#interrupt interrupted} while waiting
     * then the wait will terminate, an {@link InterruptedException} will be
     * thrown, and the thread's interrupted status will be cleared.
     * <li>The order in which waiting threads are signalled is not specified.
     * <li>The order in which threads returning from await, and threads trying
     * to acquire the monitor lock, are granted the lock, is not specified.
     * </ul>
     * <p>A {@link Condition} instance obtained in this way can be used to 
     * create the
     * affect of having additional monitor wait-sets for the given object.
     * For example, suppose we have a bounded buffer which supports methods
     * to <tt>put</tt> and <tt>take</tt> items in/from the buffer. If a 
     * <tt>take</tt> is attempted on an empty buffer then the thread will block
     * until an item becomes available; if a <tt>put</tt> is attempted on a
     * full buffer, then the thread will block until a space becomes available.
     * We would like to keep waiting <tt>put</tt> threads and <tt>take</tt>
     * threads in separate wait-sets so that we can use the optimisation of
     * only notifying a single thread at a time when items, or spaces, become
     * available in the buffer. This can be achieved using either two 
     * {@link Condition} instances, or one {@link Condition} instance and the 
     * actual
     * monitor wait-set. For clarity we'll use two {@link Condition} instances.
     * <pre><code>
     * class BoundedBuffer {
     *   <b>final Condition notFull  = Locks.newConditionFor(this); 
     *   final Condition notEmpty = Locks.newConditionFor(this); </b>
     *
     *   Object[] items = new Object[100];
     *   int putptr, takeptr, count;
     *
     *   public <b>synchronized</b> void put(Object x) 
     *                              throws InterruptedException {
     *     while (count == items.length) 
     *       <b>notFull.await();</b>
     *     items[putptr] = x; 
     *     if (++putptr == items.length) putptr = 0;
     *     ++count;
     *     <b>notEmpty.signal();</b>
     *   }
     *
     *   public <b>synchronized</b> Object take() throws InterruptedException {
     *     while (count == 0) 
     *       <b>notEmpty.await();</b>
     *     Object x = items[takeptr]; 
     *     if (++takeptr == items.length) takeptr = 0;
     *     --count;
     *     <b>notFull.signal();</b>
     *     return x;
     *   } 
     * }
     * </code></pre>
     *
     * @param lock the object that will be used for its monitor lock and to
     * which the returned condition should be bound.
     * @return a {@link Condition} instance bound to the given object
     **/
    public static Condition newConditionFor(Object lock) {
        return new ConditionObject(lock);
    }

    static final private class ConditionObject implements Condition {
        private final Object lock;
        private final Object cond = new Object();

        ConditionObject(Object lock) { this.lock = lock; }

        public void await() throws InterruptedException {
            JSR166Support.conditionWait(lock, cond);
        }

        public void awaitUninterruptibly() {
            boolean wasInterrupted = Thread.interrupted(); // record and clear
            for (;;) {
                try {
                    JSR166Support.conditionWait(lock, cond);
                    break;
                }
                catch (InterruptedException ex) { // re-interrupted; try again
                    wasInterrupted = true;
                }
            }
            if (wasInterrupted) { // re-establish interrupted state
                Thread.currentThread().interrupt();
            }
        }

        public long awaitNanos(long nanos) throws InterruptedException {
            return JSR166Support.conditionRelWait(lock, cond, nanos);
        }

        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return awaitNanos(unit.toNanos(time)) > 0;
        }

        public boolean awaitUntil(Date deadline) throws InterruptedException {
            return JSR166Support.conditionAbsWait(lock, cond, 
                                                  deadline.getTime());
        }
        public void signal() {
            JSR166Support.conditionNotify(lock, cond);
        }

        public void signalAll() {
            JSR166Support.conditionNotifyAll(lock, cond);
        }
    }

}
