package java.util.concurrent;

/**
 * Thrown when trying to retrieve the results of an asynchronous task
 * to indicate that the task was cancelled before its completion.
 * THROWN BY WHAT??
 */
public class CancellationException extends IllegalStateException {  

    /**
     * Constructs a <tt>CancellationException</tt> with no detail message.
     */
    public CancellationException() {}

    /**
     * Constructs a <tt>CancellationException</tt> with specified detail
     * message.
     *
     * @param message the detail message pertaining to this exception.
     */
    public CancellationException(String detail) {
        super(detail);
    }
}
