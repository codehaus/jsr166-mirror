/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicInteger maintains an <tt>int</tt> value that is updated
 * atomically.
 **/
public final class AtomicInteger implements java.io.Serializable { 
    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
      try {
        valueOffset = 
          unsafe.objectFieldOffset(AtomicInteger.class.getDeclaredField("value"));
      }
      catch(Exception ex) { throw new Error(ex); }
    }

    private volatile int value;

    /**
     * Create a new AtomicInteger with the given initial value;
     **/
    public AtomicInteger(int initialValue) {
        value = initialValue;
    }

    /**
     * Get the current value
     **/
    public int get() {
        return value;
    }
  
    /**
     * Set to the given value
     **/
    public void set(int newValue) {
        value = newValue;
    }

    /**
     * Set to the give value and return the old value
     **/
    public int getAndSet(int newValue) {
        for (;;) {
            int current = get();
            if (compareAndSet(current, newValue))
                return current;
        }
    }

    /**
     * Atomically increment the current value.
     * @return the previous value;
     **/
    public int getAndIncrement() {
        for (;;) {
            int current = get();
            int next = current+1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically decrement the current value.
     * @return the previous value;
     **/
    public int getAndDecrement() {
        for (;;) {
            int current = get();
            int next = current-1;
            if (compareAndSet(current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @return the previous value;
     **/
    public int getAndAdd(int y) {
        for (;;) {
            int current = get();
            int next = current+y;
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
    public boolean compareAndSet(int expect, int update) {
      return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @return true if successful.
     **/
    public boolean weakCompareAndSet(int expect, int update) {
      return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
    }
  
}
