package java.util.concurrent.atomic;

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
     * @return true if successful.
     **/
    public final boolean attemptUpdate(V expect, V update) {
        return false;
    }

    /**
     * Set to the given value and return the old value
     **/
    public final V getAndSet(V newValue) {
        while (true) {
            V x = get();
            if (attemptUpdate(x, newValue))
                return x;
        }
    }

}
