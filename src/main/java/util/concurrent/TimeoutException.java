package java.util.concurrent;

/**
 * Exception thrown when a blocking operation times out.
 * <p>
 * Blocking operations for which a timeout is specified need a means to
 * indicate that the timeout has occurred. For many such operations it is
 * possible to return a value that indicates timeout; when that is not
 * possible then <tt>TimeoutException</tt> should be declared and thrown.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/05/14 21:30:48 $
 * @editor $Author: tim $
 */
public class TimeoutException extends Exception {
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
