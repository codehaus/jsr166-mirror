package java.util.concurrent;

/**
 * Exception thrown when one thread is interrupted while
 * waiting for a barrier.
 **/
public class BrokenBarrierException extends Exception {  
    /**
     * Constructs a BrokenBarrierException with no specified detail
     * message.
     **/
    public BrokenBarrierException() {}

    /**
     * Constructs a BrokenBarrierException with specified detail
     * message.
     **/
    public BrokenBarrierException(String detail) {
        super(detail);
    }
}
