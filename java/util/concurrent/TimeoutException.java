package java.util.concurrent;

/**
 * Exception thrown when an operation times out.
 * <p>
 * This exception extends InterruptedException, as an indication
 * that a blocking operation has been aborted due to timeout.
 * However, receiving a TimeoutException does not necessarily imply
 * that the current Thread had been interrupted. When these cases
 * must be distinguished, you can use the follwoing construction:
 * <pre>
 * try {
 *   operation();
 * }
 * catch(TimeoutException te) { 
 *    // deal with timeout
 * }
 * catch(InterruptedException ie) {
 *     // deal with thread interruption
 *  }
 * </pre>
 **/
public class TimeoutException extends InterruptedException {  
  /**
   * Constructs a TimeoutException with no specified detail
   * message.
   **/
  public TimeoutException() {}

  /**
   * Constructs a TimeoutException with specified detail
   * message.
   **/
  public TimeoutException(String detail) {
    super(detail);
  }
}
