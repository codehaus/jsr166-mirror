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
 * @revised $Date: 2003/03/31 03:50:08 $
 * @editor $Author: dholmes $
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
