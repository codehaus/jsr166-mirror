package java.util.concurrent;

/**
 * Exception thrown when trying to obtain the results of a task that has been
 * cancelled.
 **/
public class CancellationException extends IllegalStateException {  
  /**
   * Constructs a CancellationException with no specified detail
   * message.
   **/
  public CancellationException() {}

  /**
   * Constructs a CancellationException with specified detail
   * message.
   **/
  public CancellationException(String detail) {
    super(detail);
  }
}
