package java.util.concurrent;

/**
 * Thrown when attempting to retrieve the results of an asychronous
 * task that aborted, failing to produce a result.
 * @fixme THROWN BY WHO??  MENTION THAT THIS IS WRAPPER FOR REAL CAUSE
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/29 23:02:23 $
 * @editor $Author: dholmes $
 */
public class ExecutionException extends Exception {

    /**
     * Constructs a <tt>ExecutionException</tt> with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    protected ExecutionException() { }

    /**
     * Constructs a <tt>ExecutionException</tt> with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param detail the detail message.
     */
    protected ExecutionException(String detail) {
        super(message);
    }

    /**
     * Constructs a <tt>ExecutionException</tt> with the specified detail
     * message and cause.
     *
     * @param  detail the detail message.
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).
     */
    public ExecutionException(String detail, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a <tt>ExecutionException</tt> with the specified cause. 
     * The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of 
     * <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).
     */
    public ExecutionException(Throwable cause) {
        super(cause);
    }
}
