package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicLong maintains a long value that is updated atomically.
 **/
public class AtomicLong implements java.io.Serializable {
    private volatile long value;

    /**
     * Create a new AtomicLong with the given initial value;
     **/
    public AtomicLong(long initialValue) {
        value = initialValue;
    }

    /**
     * Create a new AtomicLong with null initial value;
     **/
    public AtomicLong() {
    }

    /**
     * Get the current value
     **/
    public synchronized final long get() {
        return value;
    }

    /**
     * Set to the given value
     **/
    public synchronized final void set(long newValue) {
        value = newValue;
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     **/
    public synchronized final boolean compareAndSet(long expect, long update) {
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
    public final boolean weakCompareAndSet(long expect, long update) {
        return compareAndSet(expect, update);
    }

    /**
     * Set to the given value and return the old value
     **/
    public final long getAndSet(long newValue) {
        while (true) {
            long x = get();
            if (compareAndSet(x, newValue))
                return x;
        }
    }


}
