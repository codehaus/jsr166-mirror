package java.util.concurrent;
import java.util.concurrent.locks.*;

/**
 * Package-private native methods for classes introduced in JSR166
 */
public final class JSR166Support {
    /**
     *  implementation of trylock entry in Locks.attempt.
     */
    static boolean tryLockEnter(Object o) {
        return false;
    }

    /**
     *  implementation of trylock exit in Locks.attempt.
     */
    static void    tryLockExit(Object o) {
    }
    
    /**
     *  implementation of Locks.newConditionFor(obj).wait()
     */
    static void conditionWait(Object obj, 
                              Object cond
                              ) throws InterruptedException {
    }
    

    /**
     * implementation of Locks.newConditionFor(obj).waitNanos()
     */
    static long conditionRelWait(Object obj, 
                                 Object cond, 
                                 long nanos) throws InterruptedException {
        return -1;
    }

    /**
     * implementation of Locks.newConditionFor(obj).waitUntil()
     */
    static boolean conditionAbsWait(Object obj, 
                                    Object cond, 
                                    long deadline) throws InterruptedException {
        return false;
    }

  
    /**
     * implementation of Locks.newConditionFor(obj).signal()
     */
    static void conditionNotify(Object base, Object cond) {
    }

    /**
     * implementation of Locks.newConditionFor(obj).signalAll()
     */
    static void conditionNotifyAll(Object base, Object cond) {
    }
    
    /**
     * implementation of TimeUnit.highResolutionTime
     */
    static long currentTimeNanos() {
        return System.currentTimeMillis() * 1000000;
    }
    
    /**
     * implementation of thread-blocking primitive used in
     * ReentrantLock (and possibly elsewhere). Block current thread
     * until a balancing unpark occurs, or the thread is interrupted,
     * or if isAbsolute is false, the relative time in nanoseconds
     * elapses, or if true, the time of day in millisecs since epoch
     * elapses, or if a balancing unpark has already been
     * issued, or just spuriously.
     * @param isAbsolute true if time represents a deadline, false if a timeout.
     * @param time the deadline or timeout. If zero and isAbsolute is
     * false, means to wait forever.
     */
    public static void park(ReentrantLock.ReentrantLockQueueNode node, boolean isAbsolute, long time) {
        try {
            synchronized(node) {
                int s = node.parkSemaphore;
                if (s > 0) {
                    node.parkSemaphore = 0;
                    return;
                }
                if (time == 0) 
                    node.wait();
                else if (!isAbsolute) {
                    long t = time / 1000000;
                    if (t == 0) t = 1;
                    node.wait(t);
                }
                else {
                    long t = time - System.currentTimeMillis();
                    if (t <= 0)
                        return;
                    node.wait(t);
                }
            }
        }
        catch(InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * implementation of thread-unblocking primitive used in
     * ReentrantLock (and possibly elsewhere). Unblock the given
     * thread blocked on park, or, if it is not blocked, cause the
     * subsequent call to park not to block. 
     * @param thread the thread to unpark (no-op if null).
     */
    public static void unpark(ReentrantLock.ReentrantLockQueueNode node, Thread thread) {
        if (node == null)
            return;
        synchronized(node) {
            int s = node.parkSemaphore;
            node.parkSemaphore = 1;
            if (s < 1)
                node.notify();
        }
    }


    /**
     * Implementation of Locks.mightBeLocked.
     */
    static boolean mightBeLocked(Object x) {
        return true;
    }

}
