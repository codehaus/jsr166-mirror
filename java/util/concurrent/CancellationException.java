package java.util.concurrent;

/**
 * Thrown when trying to retrieve the results of an asynchronous task
 * to indicate that the task was cancelled before its completion.
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
