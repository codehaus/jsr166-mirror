/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

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
 * @revised $Date: 2003/08/25 19:27:58 $
 * @editor $Author: dl $
 * @author Doug Lea
 */
public class TimeoutException extends Exception {
    private static final long serialVersionUID = 1900926677490660714L;

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
