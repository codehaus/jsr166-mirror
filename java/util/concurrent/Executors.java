/*
 * @(#)Executors.java
 */

package java.util.concurrent;

/**
 * A factory for the <tt>Executor</tt> classes defined in
 * <tt>java.util.concurrent</tt>.
 *
 * @see Executor
 * @see ThreadExecutor
 * @since 1.5
 * @spec JSR-166
 */
public class Executors {

    /**
     * Constructs a thread pool using parameters that cause it to use
     * fixed set of threads operating off a shared unbounded queue,
     * and a default set of intercepts.  This factory method arranges
     * the most common initial parameters for thread pools used in
     * multithreaded servers.
     *
     * @param nThreads the number of threads in the pool
     * @return fixed thread executor
     */
    public static ThreadPoolExecutor newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue());
    }

    /**
     * Constructs a thread pool using parameters that cause it to act
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
     * @return cached thread pool
     */
    public static ThreadPoolExecutor newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60000, TimeUnit.MILLISECONDS,
                                      new SynchronousQueue());
    }

    /**
     * Constructs a thread pool using parameters that cause it to act
     * as cache-based pool.
     *
     * @param minThreads the minimum number of threads to keep in the
     * pool, even if they are idle.
     * @param maxThreads the maximum number of threads to allow in the
     * pool.
     * @param keepAliveTime when the number of threads is greater than
     * the minimum, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param granularity the time unit for the keepAliveTime
     * argument.
     * @param queue the queue to use for holding tasks before they
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @return cached thread pool
     * @throws IllegalArgumentException if minThreads, maxThreads, or
     * keepAliveTime less than zero, or if minThreads greater than
     * maxThreads.  
     * @throws NullPointerException if queue is null
     */
    public static ThreadPoolExecutor newThreadPool(int minThreads,
                                                   int maxThreads,
                                                   long keepAliveTime,
                                                   TimeUnit granularity,
                                                   BlockingQueue queue) {
        return new ThreadPoolExecutor(minThreads, maxThreads,
                                      keepAliveTime, granularity,
                                      queue);
    }

    /**
     * Constructs a thread executor using parameters that cause it to
     * use a single thread operating off an unbounded queue, and a
     * default set of intercepts. (Note however that if this single
     * thread terminates due to a failure during execution prior to
     * shutdown, a new one will take its place if needed to execute
     * subsequent tasks.)  Tasks are guaranteed to execute
     * sequentially, and no more than one task will be active at any
     * given time.
     *
     * @return single threaded executor
     */
    public static SingleThreadedExecutor newSingleThreadExecutor() {
        return new SingleThreadedExecutor();
    }

    /**
     * Constructs a thread pool using parameters that cause it to use a
     * new thread for each task.  This provides no efficiency savings
     * over manually creating new threads, but still offers the
     * manageability benefits of ThreadExecutor for tracking active
     * threads, shutdown, and so on.
     *
     * @return executor
     */
    public static ThreadPerTaskExecutor newThreadPerTaskExecutor() {
        return new ThreadPerTaskExecutor();
    }

    /**
     * Constructs a ScheduledExecutor.  A ScheduledExecutor is an Executor
     * which can schedule tasks to run at a given future time,
     * or execute periodically.
     *
     * @return scheduled executor
     */
    public static ScheduledExecutor newScheduledExecutor(int minThreads,
                                                         int maxThreads,
                                                         long keepAliveTime,
                                                         TimeUnit granularity) {
        return new ScheduledExecutor(minThreads, maxThreads,
                                     keepAliveTime, granularity);
    }

    /**
     * Executes a task and returns a future for the completion of that task.
     *
     * @param executor ??
     * @param task ??
     * @param value ??
     * @return future representing pending completion of the task
     * @throws CannotExecuteException if task cannot be scheduled for execution
     */
    public static <T> Future<T> execute(Executor executor, Runnable task, T value) {
        FutureTask<T> ftask = new FutureTask<T>(task, value);
        executor.execute(ftask);
        return ftask;
    }

    /**
     * Executes a value-returning task and returns a future representing the
     * pending results of the task.
     *
     * @param executor ??
     * @param task ??
     * @return future representing pending completion of the task
     * @throws CannotExecuteException if task cannot be scheduled for execution
     */
    public static <T> Future<T> execute(Executor executor, Callable<T> task) {
        FutureTask<T> ftask = new FutureTask<T>(task);
        executor.execute(ftask);
        return ftask;
    }

    /**
     * Executes a task and blocks until it completes normally or throws
     * an exception.
     *
     * @param executor ??
     * @param task ??
     * @throws CannotExecuteException if task cannot be scheduled for execution
     */
    public static void invoke(Executor executor, Runnable task)
            throws ExecutionException, InterruptedException {
        FutureTask ftask = new FutureTask(task, Boolean.TRUE);
        executor.execute(ftask);
        ftask.get();
    }

    /**
     * Executes a value-returning task and blocks until it returns a value
     * or throws an exception.
     *
     * @param executor ??
     * @param task ??
     * @return future representing pending completion of the task
     * @throws CannotExecuteException if task cannot be scheduled for execution
     */
    public static <T> T invoke(Executor executor, Callable<T> task)
            throws ExecutionException, InterruptedException {
        FutureTask<T> ftask = new FutureTask<T>(task);
        executor.execute(ftask);
        return ftask.get();
    }
}
