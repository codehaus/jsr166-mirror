package java.util.concurrent;

import java.util.*;

/**
 * A ThreadExecutor asynchronously executes many tasks without
 * necessarily using many threads.  Depending on its configuration,
 * ThreadExecutor can create a new thread for each task, execute tasks
 * sequentially in a single thread, or implement a thread pool with
 * reusable task threads.  
 *
 * <p>The most common configuration is the thread pool. Thread pools
 * can solve two different problems at the same time: They usually
 * provide faster performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed in executing a collection of tasks.
 *
 * <p> To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility hooks.
 * However, programmers are urged to use the more convenient factory
 * methods <tt>newCachedThreadPool</tt> (unbounded thread pool, with
 * automatic thread reclamation), <tt>newFixedThreadPool</tt> (fixed
 * size thread pool), <tt>newSingleThreadExecutor</tt> (single
 * background thread for execution of tasks), and
 * <tt>newThreadPerTaskExeceutor</tt> (execute each task in a new
 * thread), that preconfigure settings for the most common usage
 * scenarios. If greater control is needed, you can use the
 * constructor with custom parameters, selectively override
 * <tt>ExecutorIntercepts</tt>, and/or dynamically change tuning
 * parameters.
 *
 * <p> This class also maintain some basic statistics, such as the
 * maximum number of active threads, or the maximum queue length, that
 * may be useful for monitoring and tuning executors.
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
 * @see ExecutorIntercepts
 **/
public class ThreadExecutor implements Executor {

    /**
     * Create a new ThreadExecutor with the given initial parameters.  
     * If possible, it is better to use one of the factory methods instead
     * of the general constructor.  
     * @param minThreads the minimum number of threads to keep in the
     * pool, even if they are idle.
     * @param maxThreads the maximum number of threads to allow in the
     * pool.
     * @param keepAliveTime when the number of threads is greater than
     * the minimum, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param granularity the time unit for the keepAliveTime
     * argument.
     * @param workQueue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @param handler the object providing policy control for creating
     * threads, handling termination, etc.  
     * @throws IllegalArgumentException if minThreads, maxThreads, or
     * keepAliveTime less than zero, or if minThreads greater than
     * maxThreads.  
     * @throws NullPointerException if workQueue or
     * handler are null.
     **/
    public ThreadExecutor(int minThreads,
        int maxThreads,
        long keepAliveTime,
        TimeUnit granularity,
        BlockingQueue workQueue,
        ExecutorIntercepts handler) {}

    /**
     * Construct a thread pool using parameters that cause it to use
     * fixed set of threads operating off a shared unbounded queue,
     * and a default set of intercepts.  This factory method arranges
     * the most common initial parameters for thread pools used in
     * multithreaded servers.
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
     * Construct a thread executor using parameters that cause it to
     * use a single thread operating off an unbounded queue, and a
     * default set of intercepts. (Note however that if this single
     * thread terminates due to a failure during execution prior to
     * shutdown, a new one will take its place if needed to execute
     * subsequent tasks.)  Tasks are guaranteed to execute
     * sequentially, and no more than one task will be active at any
     * given time.
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
     * Construct a thread pool using parameters that cause it to act
     * as cache-based pool.  These pools will typically improve the
     * performance of programs that execute many short-lived
     * asynchronous tasks.  Calls to <tt>execute</tt> reuse previously
     * constructed threads, if available, to execute new Runnables.
     * If no existing thread is available, a new thread will be
     * created and added to the cache. Threads that have not been used
     * for sixty seconds are terminated and removed from the cache.
     * Thus, a pool that remains idle for long enough will not consume
     * any resources.
     *
     * */
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
     * Set the minimum allowed number of threads.  This overrides any
     * value set in the constructor.  
     * @param minThreads the new minimum
     * @throws IllegalArgumentException if minhThreads less than zero
     */
    public void setMinimumPoolSize(int minThreads) {}

    /**
     * Set the maximum allowed number of threads. This overrides any
     * value set in the constructor.  
     * @param maxThreads the new maximum
     * @throws IllegalArgumentException if maxThreads less than zero or
     * less than getMinimumPoolSize.
     *
     */
    public void setMaximumPoolSize(int maxThreads) {}

    /**
     * Set the time limit for which threads may remain idle before
     * being terminated.  If there are more than the minimum number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     * @param time the time to wait.  A time value of zero will cause
     * excess threads to terminate immediately after executing tasks.
     * @param granularity  the time unit of the time argument
     * @throws IllegalArgumentException if msecs less than zero
     * */
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
     * Get the thread keep-alive time, which is the amount of time
     * which threads in excess of the minimum pool size may remain
     * idle before being terminated.  
     * @param granularity the desired time unit of the result
     * @return the time limit
     */
    public long getKeepAliveTime(TimeUnit granularity) { return 0; }

    // statistics

    /**
     * Get the current number of threads in the pool.
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
     * using the current handler will continue to use it; future
     * actions will use the new one.  In general, this method should
     * invoked only when the executor is known to be in a quiescent
     * state.
     *
     * @param handler the new Intercept handler */
    public void setIntercepts(ExecutorIntercepts handler) {
    }


    /**
     * Return the task queue used by the ThreadExecutor.  Note that
     * this queue may be in active use.  Retrieveing the task queue
     * does not prevent queued tasks from executing.
     **/
    public BlockingQueue getQueue() {
        return null;
    }

    /**
     * Interrupt the processing of all current tasks.  Depending on
     * whether the tasks ignore the InterruptedException, this may or
     * may not speed the completion of queued tasks, and may cause
     * improperly written tasks to fail.  The Executor remains enabled
     * for future executions.
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

