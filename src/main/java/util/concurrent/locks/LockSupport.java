/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import sun.misc.Unsafe;


/**
 * Basic thread blocking primitives useful for creating locks and
 * other synchronization classes. 
 * <p>This class associates with each thread that uses it, a permit
 * (in the sense of the {@link java.util.concurrent.Semaphore Semaphore}
 * class). A call to <tt>park</tt> will return immediately if the permit
 * is available - consuming it in the process - otherwise it may block.
 * A call to <tt>unpark</tt> makes the permit available, if it was not already
 * available.
 * <p>The <tt>park</tt> and <tt>unpark</tt>
 * methods provide a means of blocking and unblocking threads that
 * eliminate the main problem that cause the deprecated methods
 * <tt>Thread.suspend</tt> and <tt>Thread.resume</tt> to be unusable
 * for such purposes: races between one thread invoking
 * <tt>park</tt> and another thread trying to <tt>unpark</tt> it
 * preserve liveness, due to the permit.  
 * Even so, these methods are designed to be used
 * as tools for creating higher-level synchronization utilities, and
 * are not in themselves useful for most concurrency control
 * applications.
 *
 * <p><b>Sample Usage.</b> Here is a sketch of a First-in-first-out
 * non-reentrant lock class that should be made more efficient
 * and complete to be useful in practice, but illustrates basic usage.
 * <pre>
 * class FIFOMutex {
 *   private AtomicBoolean locked = new AtomicBoolean(false);
 *   private Queue<Thread> waiters = new ConcurrentLinkedQueue<Thread>();
 *
 *   public void lock() { 
 *     boolean wasInterrupted = false;
 *     Thread current = Thread.currentThread();
 *     waiters.add(current);
 *
 *     while (waiters.peek() != current || 
 *            !locked.compareAndSet(false, true)) { 
 *        LockSupport.park();
 *        if (Thread.interrupted()) // ignore interrupts while waiting
 *          wasInterrupted = true;
 *     }
 *     waiters.poll();
 *     if (wasInterrupted)          // reassert interrupt status on exit
 *        current.interrupt();
 *   }
 *
 *   public void unlock() {
 *     locked.set(false);
 *     LockSupport.unpark(waiters.peek());
 *   } 
 * }
 * </pre>
 */

public class LockSupport {
    private LockSupport() {} // Cannot be instantiated.

    private static final Unsafe unsafe =  Unsafe.getUnsafe();

    /**
     * Make available the permit for the given thread, if it was not already
     * available.
     * If the thread was blocked on <tt>park</tt> then it will unblock. 
     * Otherwise, it's next call to <tt>park</tt> is guaranteed not to block.
     * @param thread the thread to unpark, or <tt>null</tt>, in which case
     * this operation has no effect.
     */
    public static void unpark(Thread thread) {
        if (thread != null)
            unsafe.unpark(thread);
    }

    /**
     * Disables the current thread for thread scheduling purposes unless the
     * permit is available.
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise 
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes <tt>unpark</tt> with the current thread 
     * as the target; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     * <p>This method does <em>not</em> report which of these caused the 
     * method to return. Callers should re-check the conditions which caused 
     * the thread to park in the first place. Callers may also determine, 
     * for example, the interrupt status of the thread upon return.
     */
    public static void park() {
        unsafe.park(false, 0);
    }

    /**
     * Disables the current thread for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise 
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of four things happens:
     * <ul>
     * <li>Some other thread invokes <tt>unpark</tt> with the current thread 
     * as the target; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses; or
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     * <p>This method does <em>not</em> report which of these caused the 
     * method to return. Callers should re-check the conditions which caused 
     * the thread to park in the first place. Callers may also determine, 
     * for example, the interrupt status of the thread, or the elapsed time
     * upon return.
     *
     * @param nanos the maximum number of nanoseconds to wait
     */
    public static void parkNanos(long nanos) {
        unsafe.park(false, nanos);   
    }

    /**
     * Disables the current thread for thread scheduling purposes, until
     * the specified deadline, unless the permit is available.
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise 
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of four things happens:
     * <ul>
     * <li>Some other thread invokes <tt>unpark</tt> with the current thread 
     * as the target; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified deadline passes; or
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     * <p>This method does <em>not</em> report which of these caused the 
     * method to return. Callers should re-check the conditions which caused 
     * the thread to park in the first place. Callers may also determine, 
     * for example, the interrupt status of the thread, or the current time
     * upon return.
     *
     * @param deadline the absolute time, in milliseconds from the Epoch, to
     * wait until
     */
    public static void parkUntil(long deadline) {
        unsafe.park(true, deadline);   
    }

}


