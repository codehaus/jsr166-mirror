package java.util.concurrent;

import java.util.*;

/**
 * A ThreadExecutor asynchronously executes many tasks without
 * necessarily using many threads. The most typical use is to
 * configure a ThreadExecutor as a thread pool. Thread pools can be
 * used to solve two different problems at the same time: They usually
 * provide faster performance when executing large numbers of
 * asynchronous tasks, and they provide a means of managing the
 * associated threads and resources.
 *
 * <p> To be useful across a wide range of contexts, this class
 * provides a large number of adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * factory methods <tt>newCachedThreadPool</tt> <tt>newFixedThreadPool</tt>
 * <tt>newSingleThreadExecutor</tt> and <tt>newThreadPerTaskExeceutor</tt>, that preconfigure settings for
 * the most common usage scenarios. Only if greater control is
 * needed, use the constructor with custom parameters, selectively
 * override <tt>ExecutorIntercepts</tt>, and/or dynamically change tuning
 * parameters.
 *
 * <p> This class also maintain some basic statistics, accessible for
 * example in<tt>getMaximumActiveCount</tt>, that may be of use in
 * monitoring and tuning executors.
 *
 * <p>
 * <b>Tuning guide</b> (Outline.)
 * <dl>
 *   <dt> Minimum and maximum Pool size
 *   <dt> Keep-alive times
 *   <dt> Queeing
 *   <dt> Creating new threads
 *   <dt> Before and after intercepts
 *   <dt> Blocked execution
 *   <dt> Termination
 * </dl>
 **/
public class ThreadExecutor implements Executor {

    /**
     * Create a new ThreadExecutor with the given initial parameters.
     * @param minThreads the minimum number of threads to keep in the
     * pool, even if they are idle.
     * @param maxThreads the maximum number of threads to allow in the pool.
     * @param keepAliveTime when the number of threads is greater than
     * the minimum, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param granularity the time unit for the keepAliveTime argument.
     * @param workQueue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt> tasks
     * submitted by the <tt>execute</tt> method.
     * @param handler the object providing policy control for creating
     * threads, handling termination, etc.
     * @throws IllegalArgumentException if minThreads, maxThreads, or
     * keepAliveTime less than zero, or if minThreads greater than maxThreads.
     * @throws NullPointerException if workQueue or handler are null.
     **/
    public ThreadExecutor(int minThreads,
        int maxThreads,
        long keepAliveTime,
        TimeUnit granularity,
        BlockingQueue workQueue,
        ExecutorIntercepts handler) {}

    /**
     * Construct a thread pool using parameters that cause it to use
     * fixed set of threads operating off a shared unbounded queue, and
     * a default set of intercepts.  This factory method arranges the
     * most common initial parameters for thread pools used in
     * multithreaded servers.
     *
     * <p> If you would like to selectively override apects of this
     * basic design, you can use the main constructor changing one or
     * more of the parameters: nthreads as both minimum and maximum
     * number of threads, zero keepalive, a
     * <tt>LinkedBlockingQueue</tt> for the work queue, and default
     * <tt>ExecutorIntercepts</tt>.
     *
     * @param nthreads the number of threads in the pool.
     **/
    public static ThreadExecutor newFixedThreadPool(int nthreads) {
        return new ThreadExecutor(nthreads,
            nthreads,
            0,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue(),
            new ExecutorIntercepts());
    }

    /**
     * Construct a thread executor using parameters that cause it to use
     * a single thread operating off an unbounded queue, and a default
     * set of intercepts. (Note however that if this single thread
     * terminates due to a failure during execution prior to shutdown, a
     * new one will take its place if needed to execute subsequent tasks.)
     *
     * <p> If you would like to selectively override apects of this
     * basic design, you can use the main constructor changing one or
     * more of the parameters: one as both minimum and maximum
     * number of threads, zero keepalive, a
     * <tt>LinkedBlockingsQueue</tt> for the work queue, and default
     * <tt>ExecutorIntercepts</tt>.
     *
     **/
    public static ThreadExecutor newSingleThreadExecutor() {
        return new ThreadExecutor(1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue(),
            new ExecutorIntercepts());
    }

    /**
     * Construct a thread pool using parameters that cause it to act as
     * cache-based pool.  These pools will typically improve the
     * performance of programs that execute many short-lived
     * asynchronous tasks.  Calls to <tt>execute</tt> reuse previously
     * constructed threads, if available, to execute new runnables, else
     * create new threads, adding them to the cache. Threads that have
     * not been used for sixty seconds are terminated and removed from
     * the cache.  Thus, a pool that remains idle for long enough will
     * not occupy any resources.
     *
     * <p> If you would like to selectively override apects of this
     * basic design, you can use the main constructor changing one or
     * more of the parameters: zero minimum and
     * <tt>Integer.MAX_VALUE</tt> maximum threads,
     * sixty second keep-alive time, a
     * <tt>SynchronousQueue</tt> for the work queue, and default
     * <tt>ExecutorIntercepts</tt>.
     *
     */
    public static ThreadExecutor newCachedThreadPool() {
        return new ThreadExecutor(0,
            Integer.MAX_VALUE,
            60000,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue(),
            new ExecutorIntercepts());
    }


    /**
     * Construct a thread pool using parameters that cause it to use a
     * new thread for each task.  This provides no efficiency savings
     * over manually creating new threads, but still offers the
     * manageability benefits of ThreadExecutor for tracking active
     * threads, shutdown, and so on.
     */
    public static ThreadExecutor newThreadPerTaskExecutor() {
        return new ThreadExecutor(0,
            Integer.MAX_VALUE,
            0,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue(),
            new ExecutorIntercepts());
    }


    /**
     * Set the minimum allowed number of threads.
     * @param minThreads the new minimum
     * @throws IllegalArgumentException if minhThreads less than zero
     */
    public void setMinimumPoolSize(int minThreads) {}

    /**
     * Set the maximum allowed number of threads.
     * @param maxThreads the new maximum
     * @throws IllegalArgumentException if maxThreads less than zero or
     * less than getMinimumPoolSize.
     *
     */
    public void setMaximumPoolSize(int maxThreads) {}

    /**
     * Set the time limit for idle threads to wait for tasks before
     * terminating.  If there are more than the minimum number of
     * threads, after waiting this amount of time without
     * processing a task, excess threads will terminate. A time value of
     * zero will cause excess threads to terminate immediately after
     * executing tasks.
     * @param time the time to wait.
     * @param granularity  the time unit of the time argument
     * @throws IllegalArgumentException if msecs less than zero
     *
     */
    public void setKeepAliveTime(long time, TimeUnit granularity) {}

    /**
     * Get the minimum allowed number of threads.
     * @return the minimum
     *
     */
    public int getMinimumPoolSize() { return 0; }

    /**
     * Get the maximum allowed number of threads.
     * @return the maximum
     *
     */
    public int getMaximumPoolSize() { return 0; }

    /**
     * Get the time limit for idle threads to wait before dying.  If
     * there are more than the minimum allowed number of threads,
     * after waiting this amount of time without processing a task,
     * excess threads will terminate.
     * @param granularity the desired time unit of the result
     * @return the time limit
     *
     */
    public long getKeepAliveTime(TimeUnit granularity) { return 0; }

    // statistics

    /**
     * Get the current number of threads.
     * @return the number of threads
     */
    public int getPoolSize() { return 0; }

    /**
     * Get the current number of threads that are actively
     * executing tasks.
     * @return the number of threads
     */
    public int getActiveCount() { return 0; }

    /**
     * Get the maximum number of threads that have ever simultaneously
     * executed tasks.
     * @return the number of threads
     */
    public int getMaximumActiveCount() { return 0; }

    /**
     * Get the number of tasks that have been queued but not yet executed
     * @return the number of tasks.
     */
    public int getQueueCount() { return 0; }

    /**
     * Get the maximum number of tasks that have ever been queued
     * waiting for execution.
     * @return the number of tasks.
     */
    public int getMaximumQueueCount() { return 0; }

    /**
     * Get the total number of tasks that have been scheduled for execution.
     * @return the number of tasks.
     */
    public int getCumulativeTaskCount() { return 0; }

    /**
     * Get the total number of tasks that have completed execution.
     * @return the number of tasks.
     */
    public int getCumulativeCompletedTaskCount() { return 0; }

    /**
     * Return the Intercepts handler.
     */
    public ExecutorIntercepts getIntercepts() {
        return null;
    }

    /**
     * Set a new Intercepts handler. Actions that are already underway
     * using the current handler will continue to use it; future actions
     * will use the new one.  In general, this method should invoked
     * only when the executor is known to be in a quiescent state.
     *
     * @param handler the new Intercept handler
     */
    public void setIntercepts(ExecutorIntercepts handler) {
    }


    /**
     * Return the task queue. Note that this queue may be in active use.
     **/
    public BlockingQueue getQueue() {
        return null;
    }

    /**
     * Interrupt the processing of all current tasks.
     * The Executor remains enabled for future executions.
     **/
    public void interrupt() {}

    /**
     * Return true if all tasks have completed following shut down.
     * Note that isTerminated is never true unless <tt>shutdown</tt>
     * or <tt>shutdownNow</tt> have been invoked.
     **/
    public boolean isTerminated() {
        return false;
    }

    /**
     * Block until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current Thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @throws InterruptedException if interrupted while waiting.
     * @throws IllegalStateException if not shut down.
     **/
    public void awaitTermination(long timeout, TimeUnit granularity) throws InterruptedException {}

    // Executor methods

    public void execute(Runnable command) {}

    public void shutdown() {}
    public List shutdownNow() {
        return null;
    }

    public boolean isShutdown() {
        return false;
    }

}

