package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicReference maintains an object reference that is updated atomically.
 **/
public class AtomicReference<V> implements java.io.Serializable { 
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
    public synchronized final V get() {
        return value;
    }
  
    /**
     * Set to the given value
     **/
    public synchronized final void set(V newValue) {
        value = newValue;
    }
  
    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     **/
    public synchronized final boolean compareAndSet(V expect, V update) {
        if (value == expect) {
            value = update;
            return true;
        }
        else
            return false;
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @return true if successful.
     **/
    public final boolean weakCompareAndSet(V expect, V update) {
        return compareAndSet(expect, update);
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

  
}
