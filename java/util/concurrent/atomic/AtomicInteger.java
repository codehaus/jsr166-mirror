package java.util.concurrent.atomic;

/**
 * An AtomicInteger maintains an <tt>int</tt> value that is updated
 * atomically. See the package specification for description of the
 * general properties it shares with other atomics.
 **/
public class AtomicInteger implements java.io.Serializable { 

    /**
     * Create a new AtomicInteger with the given initial value.
     * @param initialValue the intial value
     **/
    public AtomicInteger(int initialValue) {
        // for now
    }

    /**
     * Get the current value.
     * @return the current value.
     **/
    public final int get() {
        return 0; // for now
    }
  
    /**
     * Atomically set the value to the given update value if the
     * current value is equal to the expected value.  Any given
     * invocation of this operation may fail (return
     * <code>false</code>) spuriously, but repeated invocation when
     * the current value holds the expected value and no other thread
     * is also attempting to set the value will eventually succeed.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful
     **/
    public final boolean attemptUpdate(int expect, int update) {
        return false; // for now
    }

    /**
     * Unconditionally set to the given value.
     * @param newValue the new value.
     **/
    public final void set(int newValue) {
        // for now
    }

    /**
     * Set to the given value and return the previous value
     * @param newValue the new value.
     * @return the previous value
     **/
    public final int getAndSet(int newValue) {
        return 0; // for now
    }

    /**
     * Atomically increment the current value -- the atomic version
     * of postincrement <code>v++</code>.
     * @return the previous value
     **/
    public final int getAndIncrement() {
        return 0; // for now
    }
  
  
    /**
     * Atomically decrement the current value -- the atomic version
     * of postdecrement <code>v--</code>.
     * @return the previous value
     **/
    public final int getAndDecrement() {
        return 0; // for now
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @param x the value to be added.
     * @return the previous value
     **/
    public final int getAndAdd(int x) {
        return 0; // for now
    }
  
  
}
