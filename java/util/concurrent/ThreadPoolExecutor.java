/*
 * @(#)ThreadPoolExecutor.java
 */

package java.util.concurrent;

/**
 * An <tt>Executor</tt> that executes each submitted task on one of several
 * pooled threads.
 *
 * @since 1.5
 * @spec JSR-166
 */
public class ThreadPoolExecutor extends ThreadExecutor {

    /**
     * Constructs an executor using parameters that cause it to use a
     * pool of threads for executing submitted tasks.
     *
     * @param minThreads the minimum number of threads to keep in the
     * pool, even if they are idle
     * @param maxThreads the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the minimum, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param granularity the time unit for the keepAliveTime
     * argument.
     * @param queue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @throws IllegalArgumentException if minThreads, maxThreads, or
     * keepAliveTime less than zero, or if minThreads greater than
     * maxThreads.  
     * @throws NullPointerException if queue is null
     */
    public ThreadPoolExecutor(int minThreads,
                              int maxThreads,
                              long keepAliveTime,
                              TimeUnit granularity,
                              BlockingQueue queue) {
        super(minThreads, maxThreads, keepAliveTime, granularity, queue);
    }

    public void setMinimumPoolSize(int minThreads) {
        super.setMinimumPoolSize(minThreads);
    }

    public void setMaximumPoolSize(int maxThreads) {
        super.setMaximumPoolSize(maxThreads);
    }

    public void setKeepAliveTime(long time, TimeUnit granularity) {
        super.setKeepAliveTime(time, granularity);
    }

    public int getMinimumPoolSize() {
        return super.getMinimumPoolSize();
    }

    public int getMaximumPoolSize() {
        return super.getMaximumPoolSize();
    }

    public long getKeepAliveTime(TimeUnit granularity) {
        return super.getKeepAliveTime(granularity);
    }

    public int getPoolSize() {
        return super.getPoolSize();
    }

    public int getActiveCount() {
        return super.getActiveCount();
    }

    public int getMaximumActiveCount() {
        return super.getMaximumActiveCount();
    }

    public int getQueueCount() {
        return super.getQueueCount();
    }

    public int getMaximumQueueCount() {
        return super.getMaximumQueueCount();
    }

    public int getCumulativeTaskCount() {
        return super.getCumulativeTaskCount();
    }

    public int getCumulativeCompletedTaskCount() {
        return super.getCumulativeCompletedTaskCount();
    }

    public BlockingQueue getQueue() {
        return super.getQueue();
    }
}
