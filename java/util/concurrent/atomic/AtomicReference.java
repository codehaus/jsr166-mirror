package java.util.concurrent.atomic;

/**
 * An AtomicReference maintains an object reference that is updated
 * atomically.  See the package specification for description of the
 * general properties it shares with other atomics. **/
public class AtomicReference<V> implements java.io.Serializable {

    private volatile V value;

    /**
     * Create a new AtomicReference with the given initial value;
     * @param initialValue the intial value
     **/
    public AtomicReference(V initialValue) {
        value = initialValue;
    }

    /**
     * Get the current value
     * @return the current value.
     **/
    public final V get() {
        return value;
    }

    /**
     * Atomically set the value to the given update value if the
     * current value is <code>==</code> to the expected value.  Any given
     * invocation of this operation may fail (return
     * <code>false</code>) spuriously, but repeated invocation when
     * the current value holds the expected value and no other thread
     * is also attempting to set the value will eventually succeed.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful
     **/
    public final boolean attemptUpdate(V expect, V update) {
        return false;
    }

    /**
     * Unconditionally set to the given value.
     * @param newValue the new value.
     **/
    public final void set(V newValue) {
        value = newValue;
    }

    /**
     * Set to the given value and return the previous value
     * @param newValue the new value.
     * @return the previous value
     **/
    public final V getAndSet(V newValue) {
        while (true) {
            V x = get();
            if (attemptUpdate(x, newValue))
                return x;
        }
    }

}
