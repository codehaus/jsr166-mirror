package java.util.concurrent;

/**
 * Thrown when attempting to retrieve the results of an asychronous
 * task that aborted, failing to produce a result.
 * THROWN BY WHO??  MENTION THAT THIS IS WRAPPER FOR REAL CAUSE
 */
public class ExecutionException extends Exception {

    /**
     * Constructs a <tt>ExecutionException</tt> with <tt>null</tt> as its
     * detail message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    protected ExecutionException() { }

    /**
     * Constructs a <tt>ExecutionException</tt> with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the detail message.
     */
    protected ExecutionException(String message) {
        super(message);
    }

    /**
     * Constructs a <tt>ExecutionException</tt> with the specified detail
     * message and cause.
     *
     * @param  message the detail message.
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).
     */
    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a <tt>ExecutionException</tt> with the specified cause and
     * a detail message of <tt>(cause==null ? null : cause.toString())</tt>
     * (which typically contains the class and detail message of <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).
     */
    public ExecutionException(Throwable cause) {
        super(cause);
    }
}
