/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicLong maintains a <tt>long</tt> value that is updated atomically.
 * @since 1.5
 * @author Doug Lea
 */
public final class AtomicLong implements java.io.Serializable { 
    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
      try {
        valueOffset = unsafe.objectFieldOffset
            (AtomicLong.class.getDeclaredField("value"));
      } catch(Exception ex) { throw new Error(ex); }
    }

    private volatile long value;

    /**
     * Create a new AtomicLong with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicLong(long initialValue) {
        value = initialValue;
    }

    /**
     * Create a new AtomicLong with initial value <tt>0</tt>.
     */
    public AtomicLong() {
    }
  
    /**
     * Get the current value.
     *
     * @return the current value
     */
    public long get() {
        return value;
    }
 
    /**
     * Set to the given value.
     *
     * @param newValue the new value
     */
    public void set(long newValue) {
        value = newValue;
    }
  
    /**
     * Set to the give value and return the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public long getAndSet(long newValue) {
        while (true) {
            long current = get();
            if (compareAndSet(current, newValue))
                return current;
        }
    }
  
    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public boolean compareAndSet(long expect, long update) {
      return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public boolean weakCompareAndSet(long expect, long update) {
      return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }
  
    /**
     * Atomically increment by one the current value.
     * @return the previous value
     */
    public long getAndIncrement() {
        while (true) {
            long current = get();
            long next = current + 1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically decrement by one the current value.
     * @return the previous value
     */
    public long getAndDecrement() {
        while (true) {
            long current = get();
            long next = current - 1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @param delta the value to add
     * @return the previous value
     */
    public long getAndAdd(long delta) {
        while (true) {
            long current = get();
            long next = current + delta;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
    /**
     * Atomically increment by one the current value.
     * @return the updated value
     */
    public long incrementAndGet() {
        for (;;) {
            long current = get();
            long next = current + 1;
            if (compareAndSet(current, next))
                return next;
        }
    }
    
    /**
     * Atomically decrement by one the current value.
     * @return the updated value
     */
    public long decrementAndGet() {
        for (;;) {
            long current = get();
            long next = current - 1;
            if (compareAndSet(current, next))
                return next;
        }
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @param delta the value to add
     * @return the updated value
     */
    public long addAndGet(long delta) {
        for (;;) {
            long current = get();
            long next = current + delta;
            if (compareAndSet(current, next))
                return next;
        }
    }
  
  
}
