/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicReferenceArray maintains an array of object references in
 * which elements may be updated atomically, and are accessed using
 * <tt>volatile</tt> semantics.
 * @since 1.5
 * @author Doug Lea
 */
public final class AtomicReferenceArray<V> implements java.io.Serializable { 
    private static final long serialVersionUID = -6209656149925076980L;

    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final int base = unsafe.arrayBaseOffset(Object[].class);
    private static final int scale = unsafe.arrayIndexScale(Object[].class);
    private final Object[] array;

    private final long rawIndex(int i) {
        if (i < 0 || i >= array.length)
            throw new IndexOutOfBoundsException("index " + i);
        return base + i * scale;
    }

    /**
     * Create a new AtomicReferenceArray of given length.
     * @param length the length of the array
     */
    public AtomicReferenceArray(int length) {
        array = new Object[length];
        for (int i = 0; i < length; ++i)
            unsafe.putObjectVolatile(array, rawIndex(i), null);
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
    public V get(int i) {
        return (V) unsafe.getObjectVolatile(array, rawIndex(i));
    }
 
    /**
     * Set the element at position <tt>i</tt> to the given value.
     *
     * @param i the index
     * @param newValue the new value
     */
    public void set(int i, V newValue) {
        unsafe.putObjectVolatile(array, rawIndex(i), newValue);
    }
  
    /**
     * Set the element at position <tt>i</tt> to the given value and return the
     * old value.
     *
     * @param i the index
     * @param newValue the new value
     * @return the previous value
     */
    public V getAndSet(int i, V newValue) {
        while (true) {
            V current = get(i);
            if (compareAndSet(i, current, newValue))
                return current;
        }
    }
  
    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @param i the index
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public boolean compareAndSet(int i, V expect, V update) {
        return unsafe.compareAndSwapObject(array, rawIndex(i), 
                                         expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @param i the index
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public boolean weakCompareAndSet(int i, V expect, V update) {
        return compareAndSet(i, expect, update);
    }
}
