/*
 * @(#)AtomicInteger.java
 */

package java.util.concurrent.atomic;

/**
 * An <tt>AtomicInteger</tt> maintains an <tt>int</tt> value that is updated
 * atomically. See the package specification for description of the
 * general properties it shares with other atomics.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/02/19 08:30:13 $
 * @editor $Author: jozart $
 */
public class AtomicInteger implements java.io.Serializable { 

    /**
     * Creates a new <tt>AtomicInteger</tt> with the given initial value.
     *
     * @param initialValue the intial value
     */
    public AtomicInteger(int initialValue) {
        // for now
    }

    /**
     * Returns the current value.
     *
     * @return the current value
     */
    public final int get() {
        return 0; // for now
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
    public final boolean attemptUpdate(int expect, int update) {
        return false; // for now
    }

    /**
     * Unconditionally sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(int newValue) {
        // for now
    }

    /**
     * Sets to the given value and returns the previous value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final int getAndSet(int newValue) {
        return 0; // for now
    }

    /**
     * Atomically increments the current value -- the atomic version
     * of postincrement <code>v++</code>.
     *
     * @return the previous value
     */
    public final int getAndIncrement() {
        return 0; // for now
    }
  
  
    /**
     * Atomically decrements the current value -- the atomic version
     * of postdecrement <code>v--</code>.
     *
     * @return the previous value
     */
    public final int getAndDecrement() {
        return 0; // for now
    }
  
  
    /**
     * Atomically adds the given value to current value.
     *
     * @param x the value to be added
     * @return the previous value
     */
    public final int getAndAdd(int x) {
        return 0; // for now
    }
}
