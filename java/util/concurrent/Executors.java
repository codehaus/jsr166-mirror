package java.util.concurrent;

/**
 * Executors contains factory methods for the Executor classes defined
 * in java.util.concurrent.
 *
 * @see Executor
 * @see ExecutorService
 * @see ThreadPoolExecutor
 * @see SingleThreadedExecutor
 * @see ScheduledExecutor
 * @see ThreadPerTaskExecutor
 */
public class Executors {

    /**
     * Construct a thread pool using parameters that cause it to use
     * fixed set of threads operating off a shared unbounded queue,
     * and a default set of intercepts.  This factory method arranges
     * the most common initial parameters for thread pools used in
     * multithreaded servers.
     *
     * @param nThreads the number of threads in the pool.
     */
    public static ThreadPoolExecutor newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue(), new ThreadExecutor.DefaultCallbacks());
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
     */
    public static ThreadPoolExecutor newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60000, TimeUnit.MILLISECONDS,
                                      new SynchronousQueue(), new ThreadExecutor.DefaultCallbacks());
    }

    /** JAVADOC?? */
    public static ThreadPoolExecutor newThreadPool(int minThreads,
                                                   int maxThreads,
                                                   long keepAliveTime,
                                                   TimeUnit keepAliveGranularity,
                                                   BlockingQueue queue,
                                                   ExecutorService.Callbacks callbacks) {
        return new ThreadPoolExecutor(minThreads, maxThreads, keepAliveTime, keepAliveGranularity,
                                      queue, callbacks);
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
     */
    public static SingleThreadedExecutor newSingleThreadExecutor() {
        return new SingleThreadedExecutor();
    }

    /**
     * Construct a thread pool using parameters that cause it to use a
     * new thread for each task.  This provides no efficiency savings
     * over manually creating new threads, but still offers the
     * manageability benefits of ThreadExecutor for tracking active
     * threads, shutdown, and so on.
     */
    public static ThreadPerTaskExecutor newThreadPerTaskExecutor() {
        return new ThreadPerTaskExecutor();
    }

    /**
     * Construct a ScheduledExecutor.  A ScheduledExecutor is an Executor
     * which can schedule tasks to run at a given future time,
     * or execute periodically.
     */
    public static ScheduledExecutor newScheduledExecutor(int minThreads,
                                                         int maxThreads,
                                                         long keepAliveTime,
                                                         TimeUnit granularity,
                                                         ExecutorService.Callbacks handler) {
        return new ScheduledExecutor(minThreads, maxThreads, keepAliveTime, granularity, handler);
    }

    /**
     * Execute a task and return a future for the completion of that task.
     */
    public static <T> Future<T> execute(Executor executor, Runnable task, T value) {
        FutureTask<T> ftask = new FutureTask<T>(task, value);
        executor.execute(ftask);
        return ftask;
    }

    /**
     * Execute a value-returning task and return a future for the task's
     * return value.
     */
    public static <T> Future<T> execute(Executor executor, Callable<T> task) {
        FutureTask<T> ftask = new FutureTask<T>(task);
        executor.execute(ftask);
        return ftask;
    }

    /**
     * Execute a task and block until it completes normally or throws
     * an exception.
     */
    public static <T> T invoke(Executor executor, Runnable task, T value)
            throws ExecutionException, InterruptedException {
        FutureTask<T> ftask = new FutureTask<T>(task, value);
        executor.execute(ftask);
        return ftask.get();
    }

    /**
     * Execute a value-returning task and block until it returns a value
     * or throws an exception.
     */
    public static <T> T invoke(Executor executor, Callable<T> task)
            throws ExecutionException, InterruptedException {
        FutureTask<T> ftask = new FutureTask<T>(task);
        executor.execute(ftask);
        return ftask.get();
    }
}
