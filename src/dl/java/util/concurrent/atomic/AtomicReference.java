package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicReference maintains an object reference that is updated atomically.
 **/
public class AtomicReference<V> implements java.io.Serializable {
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
      try {
        valueOffset =
          unsafe.objectFieldOffset(AtomicReference.class.getDeclaredField("value"));
      }
      catch(Exception ex) { throw new Error(ex); }
    }

    private volatile V value;

    /**
     * Create a new AtomicReference with the given initial value;
     **/
    public AtomicReference(V initialValue) {
        value = initialValue;
    }

    /**
     * Create a new AtomicReference with null initial value;
     **/
    public AtomicReference() {
    }

    /**
     * Get the current value
     **/
    public final V get() {
        return value;
    }

    /**
     * Set to the given value
     **/
    public final void set(V newValue) {
        value = newValue;
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     **/
    public final boolean compareAndSet(V expect, V update) {
      return unsafe.compareAndSwapObject(this, valueOffset, expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @return true if successful.
     **/
    public final boolean weakCompareAndSet(V expect, V update) {
      return unsafe.compareAndSwapObject(this, valueOffset, expect, update);
    }

    /**
     * Set to the given value and return the old value
     **/
    public final V getAndSet(V newValue) {
        while (true) {
            V x = get();
            if (compareAndSet(x, newValue))
                return x;
        }
    }

    /**
     * Set the value. This operation is guaranteed to act as a
     * volatile store with respect to subsequent invocations of
     * <tt>compareAndSet</tt>, but only as a non-volatile store
     * otherwise.
     */
    public final void setForUpdate(V newValue) {
        // We do not need the full StoreLoad barrier semantics of a
        // volatile store.  But we do need to preserve ordering with
        // respect to other stores by surrounding with storeStore
        // barriers.
        unsafe.storeStoreBarrier();
        // Unsafe store call bypasses barriers used in volatile assignment
        unsafe.putObject(this, valueOffset, newValue);
        unsafe.storeStoreBarrier();
    }

}
