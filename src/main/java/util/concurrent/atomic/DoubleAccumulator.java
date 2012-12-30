/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;
import java.io.IOException;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.function.DoubleBinaryOperator;

/**
 * One or more variables that together maintain a running {@code double}
 * value updated using a supplied function.  When updates (method
 * {@link #accumulate}) are contended across threads, the set of variables
 * may grow dynamically to reduce contention.  Method {@link #get}
 * (or, equivalently, {@link #doubleValue}) returns the current value
 * across the variables maintaining updates.
 *
 * <p>The supplied accumulator function must be side-effect-free.  It
 * may be re-applied when attempted updates fail due to contention
 * among threads. The function is applied with the current value as
 * its first argument, and the given update as the second argument.
 * For example, to maintain a running maximum value, you could supply
 * {@code (x, y) -> (y > x) ? y : x} along with {@code
 * Double.MINIMUM_VALUE} as the identity.  (Class {@link DoubleAdder}
 * provides analogs of the functionality of this class for the common
 * special case of maintaining sums.)
 *
 * <p>This class extends {@link Number}, but does <em>not</em> define
 * methods such as {@code hashCode} and {@code compareTo} because
 * instances are expected to be mutated, and so are not useful as
 * collection keys.
 *
 * @since 1.8
 * @author Doug Lea
 */
public class DoubleAccumulator extends Striped64 implements Serializable {
    private static final double serialVersionUID = 7249069246863182397L;

    private final DoubleBinaryOperator function;
    private final long identity; // use long representation

    /**
     * Creates a new instance using the given accumulator function
     * and identity element.
     */
    public DoubleAccumulator(DoubleBinaryOperator accumulatorFunction,
                             double identity) {
        this.function = accumulatorFunction;
        base = this.identity =  Double.doubleToRawLongBits(identity);
    }

    /**
     * Updates with the given value.
     *
     * @param x the value
     */
    public void accumulate(double x) {
        Cell[] as; long b, v, r; CellHashCode hc; Cell a; int m;
        if ((as = cells) != null ||
            (r = Double.doubleToRawLongBits
             (function.applyAsDouble
              (Double.longBitsToDouble(b = base), x))) != b  && !casBase(b, r)) {
            boolean uncontended = true;
            if ((hc = threadCellHashCode.get()) == null ||
                as == null || (m = as.length - 1) < 0 ||
                (a = as[m & hc.code]) == null ||
                !(uncontended =
                  (r = Double.doubleToRawLongBits
                   (function.applyAsDouble
                    (Double.longBitsToDouble(v = a.value), x))) == v ||
                  a.cas(v, r)))
                doubleAccumulate(x, hc, function, uncontended);
        }
    }

    /**
     * Returns the current value.  The returned value is
     * <em>NOT</em> an atomic snapshot: Invocation in the absence of
     * concurrent updates returns an accurate result, but concurrent
     * updates that occur while the value is being calculated might
     * not be incorporated.
     *
     * @return the current value
     */
    public double get() {
        Cell[] as = cells; Cell a;
        double result = Double.longBitsToDouble(base);
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    result = function.applyAsDouble
                        (result, Double.longBitsToDouble(a.value));
            }
        }
        return result;
    }

    /**
     * Resets variables maintaining updates the given value.  This
     * method may be a useful alternative to creating a new updater,
     * but is only effective if there are no concurrent updates.
     * Because this method is intrinsically racy, it should only be
     * used when it is known that no threads are concurrently
     * updating.
     */
    public void reset() {
        Cell[] as = cells; Cell a;
        base = identity;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    a.value = identity;
            }
        }
    }

    /**
     * Equivalent in effect to {@link #get} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the value before reset
     */
    public double getThenReset() {
        Cell[] as = cells; Cell a;
        double result = Double.longBitsToDouble(base);
        base = identity;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    double v = Double.longBitsToDouble(a.value);
                    a.value = identity;
                    result = function.applyAsDouble(result, v);
                }
            }
        }
        return result;
    }

    /**
     * Returns the String representation of the {@link #sum}.
     * @return the String representation of the {@link #sum}
     */
    public String toString() {
        return Double.toString(get());
    }

    /**
     * Equivalent to {@link #sum}.
     *
     * @return the sum
     */
    public double doubleValue() {
        return get();
    }

    /**
     * Returns the {@link #sum} as a {@code long} after a
     * narrowing primitive conversion.
     */
    public long longValue() {
        return (long)get();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a
     * narrowing primitive conversion.
     */
    public int intValue() {
        return (int)get();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a narrowing primitive conversion.
     */
    public float floatValue() {
        return (float)get();
    }

    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        s.defaultWriteObject();
        s.writeDouble(get());
    }

    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        cellsBusy = 0;
        cells = null;
        base = Double.doubleToRawLongBits(s.readDouble());
    }

}
