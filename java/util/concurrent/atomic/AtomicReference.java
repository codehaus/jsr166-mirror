/*
 * @(#)AtomicReference.java
 */

package java.util.concurrent.atomic;

/**
 * An <tt>AtomicReference</tt> maintains an object reference that is updated
 * atomically.  See the package specification for description of the
 * general properties it shares with other atomics.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/02/19 08:30:13 $
 * @editor $Author: jozart $
 */
public class AtomicReference<V> implements java.io.Serializable {

    private volatile V value;

    /**
     * Creates a new <tt>AtomicReference</tt> with the given initial value.
     *
     * @param initialValue the intial value
     */
    public AtomicReference(V initialValue) {
        value = initialValue;
    }

    /**
     * Returns the current value.
     *
     * @return the current value
     */
    public final V get() {
        return value;
    }

    /**
     * Atomically sets the value to the given update value if the
     * current value is <code>==</code> to the expected value.  Any given
     * invocation of this operation may fail (return
     * <code>false</code>) spuriously, but repeated invocation when
     * the current value holds the expected value and no other thread
     * is also attempting to set the value will eventually succeed.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful
     */
    public final boolean attemptUpdate(V expect, V update) {
        return false;
    }

    /**
     * Unconditionally sets to the given value.
     *
     * @param newValue the new value.
     */
    public final void set(V newValue) {
        value = newValue;
    }

    /**
     * Sets to the given value and return the previous value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final V getAndSet(V newValue) {
        while (true) {
            V x = get();
            if (attemptUpdate(x, newValue))
                return x;
        }
    }
}
