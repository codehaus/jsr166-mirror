/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * Tempoarary class for preliminary release only; not part of JSR-166.
 * Contains native methods for some classes introduced in JSR166
 * @since 1.5
 * @author Doug Lea
 */
public final class JSR166Support {
    private static native void registerNatives();
    static {
        registerNatives();
    }
    
    /**
     * Native implementation of trylock entry in Locks.attempt.
     */
    public static native boolean tryLockEnter(Object o);

    /**
     * Native implementation of trylock exit in Locks.attempt.
     */
    public static native void    tryLockExit(Object o);
    
    /**
     * Native implementation of Locks.newConditionFor(obj).wait()
     */
    public static native void conditionWait(Object obj, 
                                     Object cond
                                     ) throws InterruptedException;
    

    /**
     * Native implementation of Locks.newConditionFor(obj).waitNanos()
     */
    public static native long conditionRelWait(Object obj, 
                                        Object cond, 
                                        long nanos) throws InterruptedException;

    /**
     * Native implementation of Locks.newConditionFor(obj).waitUntil()
     */
    public static native boolean conditionAbsWait(Object obj, 
                                           Object cond, 
                                           long deadline) throws InterruptedException;
  
    /**
     * Native implementation of Locks.newConditionFor(obj).signal()
     */
    public static native void conditionNotify(Object base, Object cond);

    /**
     * Native implementation of Locks.newConditionFor(obj).signalAll()
     */
    public static native void conditionNotifyAll(Object base, Object cond);
    
    /**
     * Native implementation of TimeUnit.nanoTime
     */
    public static native long currentTimeNanos();
    
    /**
     * Native implementation of LockSupport.park
     */
    public static native void park(boolean isAbsolute, long time);

    /**
     * Native implementation of LockSupport.unpark
     */
    public static native void unpark(Object thread);

}
