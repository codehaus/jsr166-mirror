/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;

/**
 * Factory and utility methods for the <tt>Executor</tt> classes
 * defined in <tt>java.util.concurrent</tt>.
 *
 * <p>An Executor is a framework for executing Runnables.  The
 * Executor manages queueing and scheduling of tasks, and creation and
 * teardown of threads.  Depending on which concrete Executor class is
 * being used, tasks may execute in a newly created thread, an
 * existing task-execution thread, or the thread calling execute(),
 * and may execute sequentially or concurrently.
 *
 * @since 1.5
 * @see Executor
 * @see ExecutorService
 * @see Future
 *
 * @spec JSR-166
 * @revised $Date: 2003/06/03 16:44:36 $
 * @editor $Author: dl $
 */
public class Executors {

    /**
     * A wrapper class that exposes only the ExecutorService methods
     * of an implementation.
     */ 
    static private class DelegatedExecutorService implements ExecutorService {
        private final ExecutorService e;
        DelegatedExecutorService(ExecutorService executor) { e = executor; }
        public void execute(Runnable command) { e.execute(command); }
        public void shutdown() { e.shutdown(); }
        public List shutdownNow() { return e.shutdownNow(); }
        public boolean isShutdown() { return e.isShutdown(); }
        public boolean isTerminated() { return e.isTerminated(); }
        public boolean remove(Runnable r) { return e.remove(r) ; }
        public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
            return e.awaitTermination(timeout, unit);
        }
    }

    /**
     * Creates a thread pool that reuses a fixed set of threads
     * operating off a shared unbounded queue.
     *
     * @param nThreads the number of threads in the pool
     * @return the newly created thread pool
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(nThreads, nThreads,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>()));
    }

    /**
     * Creates a thread pool that reuses a fixed set of threads
     * operating off a shared unbounded queue, using the provided
     * ThreadFactory to create new threads when needed.
     *
     * @param nThreads the number of threads in the pool
     * @param threadfactory the factory to use when creating new threads
     * @return the newly created thread pool
     */
    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(nThreads, nThreads,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>(),
                                    threadFactory, null));
    }

    /**
     * Creates an Executor that uses a single worker thread operating
     * off an unbounded queue. (Note however that if this single
     * thread terminates due to a failure during execution prior to
     * shutdown, a new one will take its place if needed to execute
     * subsequent tasks.)  Tasks are guaranteed to execute
     * sequentially, and no more than one task will be active at any
     * given time.
     *
     * @return the newly-created single-threaded Executor
     */
    public static ExecutorService newSingleThreadExecutor() {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(1, 1, 
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>()));
    }

    /**
     * Creates an Executor that uses a single worker thread operating
     * off an unbounded queue, and uses the provided ThreadFactory to
     * create new threads when needed.
     * @param threadfactory the factory to use when creating new
     * threads
     *
     * @return the newly-created single-threaded Executor
     */
    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(1, 1, 
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>(),
                                    threadFactory, null));
    }

    /**
     * Creates a thread pool that creates new threads as needed, but
     * will reuse previously constructed threads when they are
     * available.  These pools will typically improve the performance
     * of programs that execute many short-lived asynchronous tasks.
     * Calls to <tt>execute</tt> will reuse previously constructed
     * threads if available. If no existing thread is available, a new
     * thread will be created and added to the pool. Threads that have
     * not been used for sixty seconds are terminated and removed from
     * the cache. Thus, a pool that remains idle for long enough will
     * not consume any resources.
     *
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool() {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                    60, TimeUnit.SECONDS,
                                    new SynchronousQueue<Runnable>()));
    }

    /**
     * Creates a thread pool that creates new threads as needed, but
     * will reuse previously constructed threads when they are
     * available, and uses the provided 
     * ThreadFactory to create new threads when needed.
     * @param threadfactory the factory to use when creating new threads
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                    60, TimeUnit.SECONDS,
                                    new SynchronousQueue<Runnable>(),
                                    threadFactory, null));
    }

    /**
     * Executes a Runnable task and returns a Future representing that
     * task.
     *
     * @param executor the Executor to which the task will be submitted
     * @param task the task to submit
     * @param value the value which will become the return value of
     * the task upon task completion
     * @return a CancellableTask that allows cancellation.
     * @throws CannotExecuteException if the task cannot be scheduled
     * for execution
     */
    public static CancellableTask execute(Executor executor, Runnable task) {
        CancellableTask ftask = new CancellableTask(task);
        executor.execute(ftask);
        return ftask;
    }

    /**
     * Executes a value-returning task and returns a Future
     * representing the pending results of the task.
     *
     * @param executor the Executor to which the task will be submitted
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws CannotExecuteException if task cannot be scheduled for execution
     */
    public static <T> FutureTask<T> execute(Executor executor, Callable<T> task) {
        FutureTask<T> ftask = new FutureTask<T>(task);
        executor.execute(ftask);
        return ftask;
    }

    /**
     * Executes a Runnable task and blocks until it completes normally
     * or throws an exception.
     *
     * @param executor the Executor to which the task will be submitted
     * @param task the task to submit
     * @throws CannotExecuteException if task cannot be scheduled for execution
     */
    public static void invoke(Executor executor, Runnable task)
            throws ExecutionException, InterruptedException {
        FutureTask<Boolean> ftask = new FutureTask(task, Boolean.TRUE);
        executor.execute(ftask);
        ftask.get();
    }

    /**
     * Executes a value-returning task and blocks until it returns a
     * value or throws an exception.
     *
     * @param executor the Executor to which the task will be submitted
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws CannotExecuteException if task cannot be scheduled for execution
     */
    public static <T> T invoke(Executor executor, Callable<T> task)
            throws ExecutionException, InterruptedException {
        FutureTask<T> ftask = new FutureTask<T>(task);
        executor.execute(ftask);
        return ftask.get();
    }
}
