package java.util.concurrent;

/**
 * A semaphore that guarantees that waiting threads return from
 * <tt>acquire</tt> in oldest-first order.
 * 
 **/
public class FifoSemaphore extends Semaphore {
    public FifoSemaphore(long initialCount) { super(initialCount); }
}
