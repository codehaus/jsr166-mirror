/*
 * @(#)CancellationException.java
 */

package java.util.concurrent;

/**
 * Thrown when attempting to retrieve the result of a task
 * that was cancelled before it completed.
 *
 * @see Cancellable
 * @since 1.5
 * @spec JSR-166
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
