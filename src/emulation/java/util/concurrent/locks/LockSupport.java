/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;

/**
 * Basic thread blocking primitives useful for creating lock and
 * synchronization classes. The <tt>park</tt> and <tt>unpark</tt>
 * methods provide a means of blocking and unblocking threads that
 * eliminate the main problem that make the deprecated methods
 * <tt>Thread.suspend</tt> and <tt>Thread.resume</tt> unusable for
 * such purposes: Lost races between one thread invoking <tt>park</tt>
 * and another thread trying to <tt>unpark</tt> it preserve liveness.
 * Even so, these methods are designed to be used as tools for
 * creating higher-level synchronization utilities, and are not in
 * themselves useful for most concurrency control applications.
 */

public class LockSupport {

    /**
     * Unblock the given thread blocked on <tt>park</tt>, or, if it is not
     * blocked, cause the subsequent call to <tt>park</tt> not to block.
     * @param thread the thread to unpark.
     * @throws NullPointerException if thread is null
     */
    public static void unpark(Thread thread) {
        throw new UnsupportedOperationException();
    }

    /**
     * Block current thread, returning when a balancing
     * <tt>unpark</tt> occurs, or a balancing <tt>unpark</tt> has
     * already occurred, or the thread is interrupted, or spuriously
     * (i.e., returning for no "reason"). This method does
     * <em>not</em> report which of these caused the method to
     * return. Callers may determine, for example, the interrupt
     * status of the thread upon return.
     */
    public static void park() {
        throw new UnsupportedOperationException();
    }

    /**
     * Block current thread, returning when a balancing
     * <tt>unpark</tt> occurs, or a balancing <tt>unpark</tt> has
     * already occurred, or the thread is interrupted, or the given
     * nanoseconds have elapsed, or spuriously (i.e., returning for no
     * "reason"). This method does <em>not</em> report which of these
     * caused the method to return. Callers may determine, for
     * example, the interrupt status or elapsed time upon return.
     * @param nanos the maximum number of nanoseconds to block
     */
    public static void parkNanos(long nanos) {
        throw new UnsupportedOperationException();
    }

    /**
     * Block current thread, returning when a balancing
     * <tt>unpark</tt> occurs, or a balancing <tt>unpark</tt> has
     * already occurred, or the thread is interrupted, or the given
     * deadline has elapsed, or spuriously (i.e., returning for no
     * "reason"). This method does <em>not</em> report which of these
     * caused the method to return. Callers may determine, for
     * example, the interrupt status or current time upon return.
     * @param deadline the maximum time, in milliseconds from the Epoch
     */
    public static void parkUntil(long deadline) {
        throw new UnsupportedOperationException();
    }


    // Temporary versions for preliminary release to allow emulation

    static void park(ReentrantLock.ReentrantLockQueueNode node) {
        try {
            synchronized(node) {
                int s = node.parkSemaphore;
                if (s > 0) {
                    node.parkSemaphore = 0;
                    return;
                }
                node.wait();
            }
        }
        catch(InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

    }
    
    static void parkNanos(ReentrantLock.ReentrantLockQueueNode node, long nanos) {
        try {
            synchronized(node) {
                int s = node.parkSemaphore;
                if (s > 0) {
                    node.parkSemaphore = 0;
                    return;
                }
                long t = nanos / 1000000;
                if (t == 0) t = 1;
                node.wait(t);
            }
        }
        catch(InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    static void parkUntil(ReentrantLock.ReentrantLockQueueNode node, long deadline) {
        try {
            synchronized(node) {
                int s = node.parkSemaphore;
                if (s > 0) {
                    node.parkSemaphore = 0;
                    return;
                }
                long t = deadline - System.currentTimeMillis();
                if (t <= 0)
                    return;
                node.wait(t);
            }
        }
        catch(InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
    
    static void unpark(ReentrantLock.ReentrantLockQueueNode node, Thread thread) {
        synchronized(node) {
            int s = node.parkSemaphore;
            node.parkSemaphore = 1;
            if (s < 1)
                node.notify();
        }
    }

}
