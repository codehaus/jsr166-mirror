/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * Indicates that the result of a value-producing task, such as a
 * {@link FutureTask}, cannot be retrieved because the task was cancelled.
 *
 * @since 1.5
 * @see Cancellable
 * @see FutureTask#get
 * @see FutureTask#get(long, TimeUnit)
 *
 * @spec JSR-166
 * @revised $Date: 2003/08/25 19:27:58 $
 * @editor $Author: dl $
 * @author Doug Lea
 */
public class CancellationException extends IllegalStateException {
    private static final long serialVersionUID = -9202173006928992231L;

    /**
     * Constructs a <tt>CancellationException</tt> with no detail message.
     */
    public CancellationException() {}

    /**
     * Constructs a <tt>CancellationException</tt> with the specified detail
     * message.
     *
     * @param message the detail message
     */
    public CancellationException(String message) {
        super(message);
    }
}
