/*
 * @(#)CancellationException.java
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
 * @revised $Date: 2003/05/14 21:30:45 $
 * @editor $Author: tim $
 */
public class CancellationException extends IllegalStateException {

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
