/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * Exception thrown when a thread tries to wait upon a barrier that is
 * in a broken state, or which enters the broken state while the thread
 * is waiting.
 *
 * @see CyclicBarrier
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/06/24 14:34:47 $
 * @editor $Author: dl $
 * @author Doug Lea
 *
 */
public class BrokenBarrierException extends Exception {
    /**
     * Constructs a <tt>BrokenBarrierException</tt> with no specified detail
     * message.
     */
    public BrokenBarrierException() {}

    /**
     * Constructs a <tt>BrokenBarrierException</tt> with the specified
     * detail message.
     *
     * @param message the detail message
     */
    public BrokenBarrierException(String message) {
        super(message);
    }
}
