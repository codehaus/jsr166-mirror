/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An <tt>int</tt> array in which elements may be updated atomically.
 * See the package
 * specification for description of the properties of atomic
 * variables.
 * @since 1.5
 * @author Doug Lea
 */
public final class AtomicIntegerArray implements java.io.Serializable { 
    private static final long serialVersionUID = 2862133569453604235L;

   // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final int base = unsafe.arrayBaseOffset(int[].class);
    private static final int scale = unsafe.arrayIndexScale(int[].class);
    private final int[] array;

    private final long rawIndex(int i) {
        if (i < 0 || i >= array.length)
            throw new IndexOutOfBoundsException("index " + i);
        return base + i * scale;
    }

    /**
     * Create a new AtomicIntegerArray of given length.
     *
     * @param length the length of the array
     */
    public AtomicIntegerArray(int length) {
        array = new int[length];
        // must perform at least one volatile write to conform to JMM
        if (length > 0)
            unsafe.putIntVolatile(array, rawIndex(0), 0);

    }

    /**
     * Return the length of the array.
     */
    public int length() {
        return array.length;
    }

    /**
     * Get the current value at position <tt>i</tt>.
     *
     * @param i the index
     * @return the current value
     */
    public int get(int i) {
        return unsafe.getIntVolatile(array, rawIndex(i));
    }
 
    /**
     * Set the element at position <tt>i</tt> to the given value.
     *
     * @param i the index
     * @param newValue the new value
     */
    public void set(int i, int newValue) {
        unsafe.putIntVolatile(array, rawIndex(i), newValue);
    }
  
    /**
     * Set the element at position <tt>i</tt> to the given value and return the
     * old value.
     *
     * @param i the index
     * @param newValue the new value
     * @return the previous value
     */
    public int getAndSet(int i, int newValue) {
        while (true) {
            int current = get(i);
            if (compareAndSet(i, current, newValue))
                return current;
        }
    }
  
    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     *
     * @param i the index
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public boolean compareAndSet(int i, int expect, int update) {
        return unsafe.compareAndSwapInt(array, rawIndex(i), 
                                        expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     *
     * @param i the index
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public boolean weakCompareAndSet(int i, int expect, int update) {
        return compareAndSet(i, expect, update);
    }

    /**
     * Atomically increment by one the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the previous value;
     */
    public int getAndIncrement(int i) {
        while (true) {
            int current = get(i);
            int next = current + 1;
            if (compareAndSet(i, current, next))
                return current;
        }
    }
  
    /**
     * Atomically decrement by one the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the previous value;
     */
    public int getAndDecrement(int i) {
        while (true) {
            int current = get(i);
            int next = current - 1;
            if (compareAndSet(i, current, next))
                return current;
        }
    }
  
    /**
     * Atomically add the given value to element at index <tt>i</tt>.
     *
     * @param i the index
     * @param delta the value to add
     * @return the previous value;
     */
    public int getAndAdd(int i, int delta) {
        while (true) {
            int current = get(i);
            int next = current + delta;
            if (compareAndSet(i, current, next))
                return current;
        }
    }
  

    /**
     * Atomically increment by one the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the updated value;
     */
    public int incrementAndGet(int i) {
        while (true) {
            int current = get(i);
            int next = current + 1;
            if (compareAndSet(i, current, next))
                return next;
        }
    }
  
    /**
     * Atomically decrement by one the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the updated value;
     */
    public int decrementAndGet(int i) {
        while (true) {
            int current = get(i);
            int next = current - 1;
            if (compareAndSet(i, current, next))
                return next;
        }
    }
  
    /**
     * Atomically add the given value to element at index <tt>i</tt>.
     *
     * @param i the index
     * @param delta the value to add
     * @return the updated value;
     */
    public int addAndGet(int i, int delta) {
        while (true) {
            int current = get(i);
            int next = current + delta;
            if (compareAndSet(i, current, next))
                return next;
        }
    }
  


}
