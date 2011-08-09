/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e.extra;
import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Double.longBitsToDouble;

/**
 * A {@code double} value that may be updated atomically.  See the
 * {@link java.util.concurrent.atomic} package specification for
 * description of the properties of atomic variables.  An {@code
 * AtomicDouble} is used in applications such as atomic accumulation,
 * and cannot be used as a replacement for a {@link Double}.  However,
 * this class does extend {@code Number} to allow uniform access by
 * tools and utilities that deal with numerically-based classes.
 *
 * <p>This class differs from the primitive double {@code ==} operator
 * and from {@link Double#equals} in that it uses purely bitwise
 * equality in methods such as {@link #compareAndSet}, as if
 * implemented by:
 *  <pre> {@code
 * boolean bitEquals(double x, double y) {
 *   long xBits = Double.doubleToRawLongBits(x);
 *   long yBits = Double.doubleToRawLongBits(y);
 *   return xBits == yBits;
 * }}</pre>
 *
 * @author Doug Lea
 * @author Martin Buchholz
 */
public class AtomicDouble extends Number implements java.io.Serializable {
    static final long serialVersionUID = -8405198993435143622L;

    // setup to use Unsafe.compareAndSwapLong for updates
    private static final sun.misc.Unsafe unsafe = getUnsafe();
    private static final long valueOffset;

    /**
     * Records whether the underlying JVM supports lockless
     * compareAndSwap for longs. While the Unsafe.compareAndSwapLong
     * method works in either case, some constructions should be
     * handled at Java level to avoid locking user-visible locks.
     */
    static final boolean VM_SUPPORTS_LONG_CAS = VMSupportsCS8();

    /**
     * Returns whether underlying JVM supports lockless CompareAndSet
     * for longs. Called only once and cached in VM_SUPPORTS_LONG_CAS.
     */
    private static boolean VMSupportsCS8() {
        final Class<?> klazz = java.util.concurrent.atomic.AtomicLong.class;
        return java.security.AccessController.doPrivileged
            (new java.security.PrivilegedAction<Boolean>() {
                public Boolean run() {
                    try {
                        java.lang.reflect.Method m =
                            klazz.getDeclaredMethod("VMSupportsCS8", new Class<?>[0]);
                        m.setAccessible(true);
                        return (Boolean) m.invoke(new Class<?>[0]);
                    } catch (Throwable t) { throw new Error(t); }
                }});
    }

    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                (AtomicDouble.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    private volatile long value;

    /**
     * Creates a new AtomicDouble with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicDouble(double initialValue) {
        value = doubleToRawLongBits(initialValue);
    }

    /**
     * Creates a new AtomicDouble with initial value {@code 0.0}.
     */
    public AtomicDouble() { this(0.0); }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public final double get() {
        return longBitsToDouble(value);
    }

    /**
     * Sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(double newValue) {
        value = doubleToRawLongBits(newValue);
    }

    /**
     * Eventually sets to the given value.
     *
     * @param newValue the new value
     */
    public final void lazySet(double newValue) {
        unsafe.putOrderedLong(this, valueOffset, doubleToRawLongBits(newValue));
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final double getAndSet(double newValue) {
        long newBits = doubleToRawLongBits(newValue);
        while (true) {
            long currentBits = value;
            if (unsafe.compareAndSwapLong(this, valueOffset,
                                          currentBits, newBits))
                return longBitsToDouble(currentBits);
        }
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(double expect, double update) {
        return unsafe.compareAndSwapLong(this, valueOffset,
                                         doubleToRawLongBits(expect),
                                         doubleToRawLongBits(update));
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * <p>May <a href="package-summary.html#Spurious">fail spuriously</a>
     * and does not provide ordering guarantees, so is only rarely an
     * appropriate alternative to {@code compareAndSet}.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public final boolean weakCompareAndSet(double expect, double update) {
        return compareAndSet(expect, update);
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final double getAndAdd(double delta) {
        while (true) {
            long currentBits = value;
            long nextBits = doubleToRawLongBits(longBitsToDouble(currentBits) + delta);
            if (unsafe.compareAndSwapLong(this, valueOffset,
                                          currentBits, nextBits))
                return longBitsToDouble(currentBits);
        }
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final double addAndGet(double delta) {
        for (;;) {
            double current = get();
            double next = current + delta;
            if (compareAndSet(current, next))
                return next;
        }
    }

    /**
     * Returns the String representation of the current value.
     * @return the String representation of the current value.
     */
    public String toString() {
        return Double.toString(get());
    }


    /**
     * Returns the value of this {@code AtomicDouble} as an {@code int}
     * after a narrowing primitive conversion.
     */
    public int intValue() {
        return (int)get();
    }

    /**
     * Returns the value of this {@code AtomicDouble} as a {@code long}.
     */
    public long longValue() {
        return (long)get();
    }

    /**
     * Returns the value of this {@code AtomicDouble} as a {@code float}
     * after a widening primitive conversion.
     */
    public float floatValue() {
        return (float)get();
    }

    /**
     * Returns the value of this {@code AtomicDouble} as a {@code double}
     * after a widening primitive conversion.
     */
    public double doubleValue() {
        return get();
    }

    /**
     * Returns a sun.misc.Unsafe.  Suitable for use in a 3rd party package.
     * Replace with a simple call to Unsafe.getUnsafe when integrating
     * into a jdk.
     *
     * @return a sun.misc.Unsafe
     */
    private static sun.misc.Unsafe getUnsafe() {
        try {
            return sun.misc.Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                return java.security.AccessController.doPrivileged
                    (new java.security
                     .PrivilegedExceptionAction<sun.misc.Unsafe>() {
                        public sun.misc.Unsafe run() throws Exception {
                            java.lang.reflect.Field f = sun.misc
                                .Unsafe.class.getDeclaredField("theUnsafe");
                            f.setAccessible(true);
                            return (sun.misc.Unsafe) f.get(null);
                        }});
            } catch (java.security.PrivilegedActionException e) {
                throw new RuntimeException("Could not initialize intrinsics",
                                           e.getCause());
            }
        }
    }

}
