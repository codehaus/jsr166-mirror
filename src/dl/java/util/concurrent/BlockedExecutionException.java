/*
 * @(#)BlockedExecutionException.java
 */

package java.util.concurrent;

/**
 * Thrown by an <tt>Executor</tt> when a task cannot be accepted for execution.
 *
 * @since 1.5
 * @see Executor#execute
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/26 12:25:44 $
 * @editor $Author: tim $
 */
public class BlockedExecutionException extends RuntimeException {

    /**
     * Constructs a <tt>BlockedExecutionException</tt> with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public BlockedExecutionException() { }

    /**
     * Constructs a <tt>BlockedExecutionException</tt> with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the detail message
     */
    public BlockedExecutionException(String message) {
        super(message);
    }

    /**
     * Constructs a <tt>BlockedExecutionException</tt> with the specified detail
     * message and cause.
     *
     * @param  message the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public BlockedExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a <tt>BlockedExecutionException</tt> with the specified cause.
     * The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public BlockedExecutionException(Throwable cause) {
        super(cause);
    }
}
