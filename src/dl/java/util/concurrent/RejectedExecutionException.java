/*
 * @(#)RejectedExecutionException.java
 */

package java.util.concurrent;

/**
 * Thrown by an <tt>Executor</tt> when a task cannot be accepted for execution.
 *
 * @since 1.5
 * @see Executor#execute
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/26 14:55:59 $
 * @editor $Author: tim $
 */
public class RejectedExecutionException extends RuntimeException {

    /**
     * Constructs a <tt>RejectedExecutionException</tt> with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public RejectedExecutionException() { }

    /**
     * Constructs a <tt>RejectedExecutionException</tt> with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the detail message
     */
    public RejectedExecutionException(String message) {
        super(message);
    }

    /**
     * Constructs a <tt>RejectedExecutionException</tt> with the specified detail
     * message and cause.
     *
     * @param  message the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public RejectedExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a <tt>RejectedExecutionException</tt> with the specified cause.
     * The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public RejectedExecutionException(Throwable cause) {
        super(cause);
    }
}
