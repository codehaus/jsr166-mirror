/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An <tt>AtomicBoolean</tt> maintains a <tt>boolean</tt> value that
 * is updated atomically. See the package specification for
 * description of the general properties it shares with other atomics.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/06/24 14:34:49 $
 * @editor $Author: dl $
 * @author Doug Lea
 */
public class AtomicBoolean implements java.io.Serializable {
    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
      try {
        valueOffset = unsafe.objectFieldOffset
            (AtomicBoolean.class.getDeclaredField("value"));
      }
      catch (Exception ex) { throw new Error(ex); }
    }

    private volatile int value;

    /**
     * Creates a new <tt>AtomicBoolean</tt> with the given initial value.
     *
     * @param initialValue the intial value
     */
    public AtomicBoolean(boolean initialValue) {
        value = initialValue ? 1 : 0;
    }

    /**
     * Returns the current value.
     *
     * @return the current value
     */
    public final boolean get() {
        return value != 0;
    }

    /**
     * Atomically sets the value to the given update value if the
     * current value is equal to the expected value.  Any given
     * invocation of this operation may fail (return
     * <code>false</code>) spuriously, but repeated invocation when
     * the current value holds the expected value and no other thread
     * is also attempting to set the value will eventually succeed.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful
     */
    public final boolean compareAndSet(boolean expect, boolean update) {
        int e = expect ? 1 : 0;
        int u = update ? 1 : 0;
        return unsafe.compareAndSwapInt(this, valueOffset, e, u);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public boolean weakCompareAndSet(boolean expect, boolean update) {
        int e = expect ? 1 : 0;
        int u = update ? 1 : 0;
        return unsafe.compareAndSwapInt(this, valueOffset, e, u);
    }

    /**
     * Unconditionally sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(boolean newValue) {
        value = newValue ? 1 : 0;
    }

    /**
     * Sets to the given value and returns the previous value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final boolean getAndSet(boolean newValue) {
        for (;;) {
            boolean current = get();
            if (compareAndSet(current, newValue))
                return current;
        }
    }
}
