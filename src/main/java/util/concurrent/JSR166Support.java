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
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static int addressSize = unsafe.addressSize();
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
     * Native implementation of thread-blocking primitive used in
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
    static native void park(boolean isAbsolute, long time);

    /**
     * Temporary version of park to allow emulation.
     * Native implementation of thread-blocking primitive used in
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
        park(isAbsolute, time);
    }

    /**
     * Native implementation of thread-unblocking primitive used in
     * ReentrantLock (and possibly elsewhere). Unblock the given
     * thread blocked on park, or, if it is not blocked, cause the
     * subsequent call to park not to block. 
     * @param thread the thread to unpark (no-op if null).
     */
    static native void unpark(Object thread);

    /**
     * Temporary version of unpark to allow emulation.
     * Native implementation of thread-unblocking primitive used in
     * ReentrantLock (and possibly elsewhere). Unblock the given
     * thread blocked on park, or, if it is not blocked, cause the
     * subsequent call to park not to block. 
     * @param thread the thread to unpark (no-op if null).
     */ 
    public static void unpark(ReentrantLock.ReentrantLockQueueNode node, Thread thread) {
        unpark(thread);
    }

    /**
     * Implementation of Locks.mightBeLocked.
     */
    static boolean mightBeLocked(Object x) {
        // This is actually done non-natively via unsafe, but is
        // highly dependent on JVM object layout details.
        boolean l = (addressSize == 4)? 
            ((unsafe.getInt(x, 0L) & 3) == 1) :
            ((unsafe.getLong(x, 0L) & 3) == 1);
        unsafe.loadLoadBarrier();
        return l;
    }
}
