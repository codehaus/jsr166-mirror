package java.util.concurrent.atomic;

/**
 * An AtomicDouble maintains a <tt>double</tt> value that is updated
 * atomically.
 **/
public final class AtomicDouble implements java.io.Serializable { 

    /**
     * Create a new AtomicDouble with the given initial value;
     **/
    public AtomicDouble(double initialValue) {
        // for now;
    }
  
    /**
     * Get the current value
     **/
    public double get() {
        return 0.0; // for now
    }
  
    /**
     * Set to the given value
     **/
    public void set(double newValue) {
        // for now
    }
  
    /**
     * Set to the give value and return the old value
     **/
    public double getAndSet(double newValue) {
        return 0.0; // for now
    }
 
  
    /**
     * Atomically set the value to the given updated value
     * if the current value is equal to the expected value.
     * @return true if successful.
     **/
    public boolean attemptUpdate(double expect, double update) {
        return false; // for now
    }
  
}
