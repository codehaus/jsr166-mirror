package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicBoolean maintains a boolean value that is updated atomically.
 **/
public class AtomicBoolean implements java.io.Serializable {
    private volatile boolean value;

    /**
     * Create a new AtomicBoolean with the given initial value;
     **/
    public AtomicBoolean(boolean initialValue) {
        value = initialValue;
    }

    /**
     * Create a new AtomicBoolean with null initial value;
     **/
    public AtomicBoolean() {
    }

    /**
     * Get the current value
     **/
    public synchronized final boolean get() {
        return value;
    }

    /**
     * Set to the given value
     **/
    public synchronized final void set(boolean newValue) {
        value = newValue;
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     **/
    public synchronized final boolean compareAndSet(boolean expect, boolean update) {
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
    public final boolean weakCompareAndSet(boolean expect, boolean update) {
        return compareAndSet(expect, update);
    }

    /**
     * Set to the given value and return the old value
     **/
    public final boolean getAndSet(boolean newValue) {
        while (true) {
            boolean x = get();
            if (compareAndSet(x, newValue))
                return x;
        }
    }


}
