package java.util.concurrent;

/**
 * A semaphore that guarantees that waiting threads return from
 * the {@link Semaphore#acquire acquire} methods in the order in 
 * which they invoked those methods (first-in-first-out).
 *
 * <p>This class introduces a fairness guarantee that can be useful
 * in some contexts.
 *
 * @fixme Need to see exactly what guarantees the implementation gives.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/20 23:58:58 $
 * @editor $Author: dholmes $
 *
 */
public class FifoSemaphore extends Semaphore {

    /**
     * Construct a <tt>Semaphore</tt> with the given number of
     * permits.
     * @param permits the initial number of permits available
     */
    public FifoSemaphore(long permits) { 
        super(permits); 
    }

    /**
     * Releases a permit, returning it to the semaphore.
     * <p>Releases a permit, increasing the number of available permits
     * by one.
     * If any threads are blocking trying to acquire a permit, then the
     * one that has been waiting the longest
     * is selected and given the permit that was just released.
     * That thread is re-enabled for thread scheduling purposes.
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link Semaphore#acquire acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     */
    public void release() {}

}

