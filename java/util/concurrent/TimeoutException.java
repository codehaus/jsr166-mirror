package java.util.concurrent;

/**
 * Exception thrown when an operation times out.
 * <p>
 * This exception extends {@link InterruptedException}, as an indication
 * that a blocking operation has been aborted due to timeout.
 * However, receiving a <tt>TimeoutException</tt> does not necessarily imply
 * that the current thread had been {@link Thread#interrupt interrupted}.
 * When these cases
 * must be distinguished, you can use the following construction:
 * <pre>
 * try {
 *   blockingOperation(timeout, timeunit);
 * }
 * catch(TimeoutException te) {
 *    // deal with timeout
 * }
 * catch(InterruptedException ie) {
 *     // deal with thread interruption
 *  }
 * </pre>
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/31 00:13:35 $
 * @editor $Author: tim $
 */
public class TimeoutException extends InterruptedException {
    /**
     * Constructs a <tt>TimeoutException</tt> with no specified detail
     * message.
     */
    public TimeoutException() {}

    /**
     * Constructs a <tt>TimeoutException</tt> with the specified detail
     * message.
     *
     * @param message the detail message
     */
    public TimeoutException(String message) {
        super(message);
    }
}
