package java.util.concurrent;

/**
 * Thrown when trying to retrieve the results of an asynchronous task
 * to indicate that the task was cancelled before its completion.
 * @fixme THROWN BY WHAT??
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/29 23:07:44 $
 * @editor $Author: dholmes $
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
     * @param detail the detail message
     */
    public CancellationException(String detail) {
        super(detail);
    }
}
