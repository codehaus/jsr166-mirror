package java.util.concurrent;


/**
 * Package-private native methods for classes introduced in JSR166
 */
final class JSR166Support {
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
    
    private static ThreadLocal threadParker = new ThreadLocal() {
            public Object initialValue() { return new Object(); }
        };

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
    static void park(boolean isAbsolute, long time) {
        Object mon = threadParker.get();
        try {
            synchronized(mon) {
                if (time == 0) 
                    mon.wait();
                else if (!isAbsolute) {
                    long t = time / 1000000;
                    if (t == 0) t = 1;
                    mon.wait(t);
                }
                else {
                    long t = time - System.currentTimeMillis();
                    if (t <= 0)
                        return;
                    mon.wait(t);
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
    static void unpark(Object thread) {
        Object mon = threadParker.get();
        synchronized(mon) {
            mon.notifyAll();
        }
    }


    /**
     * Implementation of Locks.mightBeLocked.
     */
    static boolean mightBeLocked(Object x) {
        return true;
    }

}
