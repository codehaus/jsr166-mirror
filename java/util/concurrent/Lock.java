package java.util.concurrent;

/**
 * A Lock provides more flexible locking operations than
 * can be obtained using <tt>synchronized</tt> methods and blocks.
 * 
 * 
 **/
public interface Lock {

    /**
     * Acquire the lock.
     **/
    public void lock();

    /**
     * Acquire the lock only if the current Thread is not Interrupted.
     **/
    public void lockInterruptibly() throws InterruptedException;


    /**
     * Acquire the lock only if it is free at the time of invocation.
     * @return true if acquired.
     **/
    public boolean tryLock();

    /**
     * Acquire the lock if it is free within the given wait time and the
     * current thread has not been interrupted. The given wait time is a
     * best-effort lower bound. If the time is less than or equal to
     * zero, the method will not wait at all, but may still throw an
     * InterruptedException if the thread is interrupted.
     *
     * @param time the maximum time to wait
     * @param granularity the time unit of the time argument.
     * @return true if acquired.
     * @throws InterruptedException if interrupted during wait for lock.
     **/
    public boolean tryLock(long time, Clock granularity) throws InterruptedException;

    /**
     * Release the lock.
     **/
    public void unlock();

    /**
     * Construct a new Condition for this Lock
     **/
    public Condition newCondition();

}
