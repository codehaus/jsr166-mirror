/*
 * @(#)Executors.java
 */

package java.util.concurrent;

/**
 * A factory for the <tt>Executor</tt> classes defined in
 * <tt>java.util.concurrent</tt>.
 *
 * @since 1.5
 * @see Executor
 * @see ThreadedExecutor
 *
 * @spec JSR-166
 * @revised $Date: 2003/02/26 10:48:09 $
 * @editor $Author: jozart $
 */
public class Executors {

    /**
     * Creates a threaded executor that reuses a fixed set of threads
     * operating off a shared unbounded queue.
     *
     * @param nThreads the number of threads in the pool
     * @return fixed thread executor
     */
    public static ThreadedExecutor newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue());
    }

    /**
     * Creates a threaded executor that creates new threads as needed, but
     * will reuse previously constructed threads when they are available.
     * These pools will typically improve the performance of programs that
     * execute many short-lived asynchronous tasks.  Calls to <tt>execute</tt>
     * reuse previously constructed threads, if available. If no existing
     * thread is available, a new thread will be created and added to the
     * cache. Threads that have not been used for sixty seconds are terminated
     * and removed from the cache. Thus, a pool that remains idle for long
     * enough will not consume any resources.
     *
     * @return cached thread pool
     */
    public static ThreadedExecutor newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60000, TimeUnit.MILLISECONDS,
                                      new SynchronousQueue());
    }

    /**
     * Creates a threaded executor that reuses a limited pool of cached threads.
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
    public static ThreadedExecutor newThreadPool(int minThreads,
                                                   int maxThreads,
                                                   long keepAliveTime,
                                                   TimeUnit granularity,
                                                   BlockingQueue queue) {
        return new ThreadPoolExecutor(minThreads, maxThreads,
                                      keepAliveTime, granularity,
                                      queue);
    }

    /**
     * Creates a threaded executor that uses a single thread operating off an
     * unbounded queue. (Note however that if this single thread terminates
     * due to a failure during execution prior to shutdown, a new one will
     * take its place if needed to execute subsequent tasks.)  Tasks are
     * guaranteed to execute sequentially, and no more than one task will be
     * active at any given time.
     *
     * @fixme return (ThreadedExecutor) newFixedThreadExecutor(1) ?
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
     * manageability benefits of <tt>ThreadedExecutor</tt> for tracking
     * active threads, shutdown, and so on.
     *
     * @fixme move to javadoc example in ThreadPoolExecutor
     *
     * @return executor
     */
    public static ThreadedExecutor newThreadPerTaskExecutor() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      0, TimeUnit.MILLISECONDS,
                                      new SynchronousQueue());
    }

    /**
     * Constructs a ScheduledExecutor.  A ScheduledExecutor is an Executor
     * which can schedule tasks to run at a given future time, or to execute
     * periodically.
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
