package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicInteger maintains a int value that is updated atomically.
 **/
public class AtomicInteger implements java.io.Serializable {
    private volatile int value;

    /**
     * Create a new AtomicInteger with the given initial value;
     **/
    public AtomicInteger(int initialValue) {
        value = initialValue;
    }

    /**
     * Create a new AtomicInteger with null initial value;
     **/
    public AtomicInteger() {
    }

    /**
     * Get the current value
     **/
    public synchronized final int get() {
        return value;
    }

    /**
     * Set to the given value
     **/
    public synchronized final void set(int newValue) {
        value = newValue;
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     **/
    public synchronized final boolean compareAndSet(int expect, int update) {
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
    public final boolean weakCompareAndSet(int expect, int update) {
        return compareAndSet(expect, update);
    }

    /**
     * Set to the given value and return the old value
     **/
    public final int getAndSet(int newValue) {
        while (true) {
            int x = get();
            if (compareAndSet(x, newValue))
                return x;
        }
    }


}
