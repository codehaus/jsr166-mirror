/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicLong maintains a <tt>long</tt> value that is updated atomically.
 **/
public final class AtomicLong implements java.io.Serializable { 
    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
      try {
        valueOffset = 
          unsafe.objectFieldOffset(AtomicLong.class.getDeclaredField("value"));
      }
      catch(Exception ex) { throw new Error(ex); }
    }

    private volatile long value;

    /**
     * Create a new AtomicLong with the given initial value;
     **/
    public AtomicLong(long initialValue) {
        value = initialValue;
    }
  
    /**
     * Get the current value
     **/
    public long get() {
        return value;
    }
 
    /**
     * Set to the given value
     **/
    public void set(long newValue) {
        // We must use CAS here for longs in case we are
        // dealing with machine without native wide CAS, in which case
        // it is simulated, but cannot deal with unsynchronized stores.
        while (!compareAndSet(get(), newValue)) 
            ;
    }
  
    /**
     * Set to the give value and return the old value
     **/
    public long getAndSet(long newValue) {
        while (true) {
            long current = get();
            if (compareAndSet(current, newValue))
                return current;
        }
    }
  
  
    /**
     * Atomically increment the current value.
     * @return the previous value;
     **/
    public long getAndIncrement() {
        while (true) {
            long current = get();
            long next = current+1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically decrement the current value.
     * @return the previous value;
     **/
    public long getAndDecrement() {
        while (true) {
            long current = get();
            long next = current-1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @return the previous value;
     **/
    public long getAndAdd(long y) {
        while (true) {
            long current = get();
            long next = current+y;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     **/
    public boolean compareAndSet(long expect, long update) {
      return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @return true if successful.
     **/
    public boolean weakCompareAndSet(long expect, long update) {
      return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }
  
  
}
