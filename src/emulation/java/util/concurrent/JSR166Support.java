package java.util.concurrent;
import java.util.concurrent.locks.*;

/**
 * Package-private native methods for classes introduced in JSR166
 */
public final class JSR166Support {
    /**
     *  implementation of trylock entry in Locks.attempt.
     */
    public static boolean tryLockEnter(Object o) {
        throw new UnsupportedOperationException();
    }

    /**
     *  implementation of trylock exit in Locks.attempt.
     */
    public static void    tryLockExit(Object o) {
        throw new UnsupportedOperationException();
    }
    
    /**
     *  implementation of Locks.newConditionFor(obj).wait()
     */
    public static void conditionWait(Object obj, 
                              Object cond
                              ) throws InterruptedException {
        throw new UnsupportedOperationException();
    }
    

    /**
     * implementation of Locks.newConditionFor(obj).waitNanos()
     */
    public static long conditionRelWait(Object obj, 
                                 Object cond, 
                                 long nanos) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    /**
     * implementation of Locks.newConditionFor(obj).waitUntil()
     */
    public static boolean conditionAbsWait(Object obj, 
                                    Object cond, 
                                    long deadline) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

  
    /**
     * implementation of Locks.newConditionFor(obj).signal()
     */
    public static void conditionNotify(Object base, Object cond) {
        throw new UnsupportedOperationException();
    }

    /**
     * implementation of Locks.newConditionFor(obj).signalAll()
     */
    public static void conditionNotifyAll(Object base, Object cond) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * implementation of TimeUnit.highResolutionTime
     */
    public static long currentTimeNanos() {
        return System.currentTimeMillis() * 1000000;
    }

}
