/*
 * @(#)CannotExecuteException.java
 */

package java.util.concurrent;

/**
 * Thrown by an <tt>Executor</tt> when a task cannot be scheduled for execution.
 * 
 * @see Executor#execute
 * @since 1.5
 * @spec JSR-166
 */
public class CannotExecuteException extends RuntimeException {

    /**
     * Constructs a <tt>CannotExecuteException</tt> with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public CannotExecuteException() { }

    /**
     * Constructs a <tt>CannotExecuteException</tt> with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the detail message
     */
    public CannotExecuteException(String message) {
        super(message);
    }

    /**
     * Constructs a <tt>CannotExecuteException</tt> with the specified detail
     * message and cause.
     *
     * @param  message the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public CannotExecuteException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a <tt>CannotExecuteException</tt> with the specified cause.
     * The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public CannotExecuteException(Throwable cause) {
        super(cause);
    }
}
