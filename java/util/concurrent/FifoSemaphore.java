package java.util.concurrent;

/**
 * A semaphore that guarantees that threads invoking
 * the {@link Semaphore#acquire acquire} methods 
 * are allocated permits in the order in 
 * which their invocation of those methods was processed (first-in-first-out).
 *
 * <p>This class introduces a fairness guarantee that can be useful
 * in some contexts.
 * However, it needs to be realized that the order in which invocations are
 * processed can be different from the order in which an application perceives
 * those invocations to be processed. Effectively, when no permit is available
 * each thread is stored in a FIFO queue. As permits are released, they
 * are allocated to the thread at the head of the queue, and so the order
 * in which threads enter the queue, is the order in which they come out.  
 * This order need not have any relationship to the order in which requests 
 * were made, nor the order in which requests actually return to the caller as 
 * these depend on the underlying thread scheduling, which is not guaranteed 
 * to be predictable or fair.
  *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/28 01:25:02 $
 * @editor $Author: dholmes $
 *
 */
public class FifoSemaphore extends Semaphore {

    /**
     * Construct a <tt>FiFoSemaphore</tt> with the given number of
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

