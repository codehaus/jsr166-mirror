package java.util.concurrent;

/**
 * Conditions provide distinct condition variables for Objects or Locks.
 * Use <tt>Locks.newConditionFor(Object lock)</tt> to create
 * a Condition for a given builtin monitor object.
 * 
 **/
public interface Condition {

    /**
     * Release lock and suspend.
     * @throws InterruptedException if interrupted during wait.
     **/
    public void await() throws InterruptedException;

    /**
     * Release lock and suspend, even if the current thread
     * has been interrupted or is interrupted while waiting.
     **/
    public void awaitUninterruptibly();

    /**
     * Release lock and suspend for msecs or until notified.
     * @return true if notified, false if timed out and not notified
     * release lock and suspend
     * @param time the maximum time to wait
     * @param granularity the time unit of the time argument.
     * @throws InterruptedException if interrupted during wait.
     */
    public boolean await(long time, Clock granularity) throws InterruptedException;

    /**
     * Wake up one waiter.
     **/
    public void signal();

    /**
     * Wake up all waiters.
     **/
    public void signalAll();

}

