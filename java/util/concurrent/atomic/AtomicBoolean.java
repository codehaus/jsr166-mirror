package java.util.concurrent.atomic;

/**
 * An AtomicBoolean maintains a boolean value that is updated atomically.
 **/
public final class AtomicBoolean implements java.io.Serializable { 

    /**
     * Create a new AtomicRef with the given initial value;
     **/
    public AtomicBoolean(boolean initialValue) {
        // for now;
    }
  
    /**
     * Get the current value
     **/
    public boolean get() {
        return false; // for now
    }
  
    /**
     * Set to the given value
     **/
    public void set(boolean newValue) {
        // for now
    }

    /**
     * Set to the give value and return the old value
     **/
    public boolean getAndSet(boolean newValue) {
        return false; // for now
    }
  
    /**
     * Atomically set the value to the given updated value
     * if the current value is equal to the expected value.
     * @return true if successful.
     **/
    public boolean attemptUpdate(boolean expect, boolean update) {
        return false; // for now
    }
  
}
