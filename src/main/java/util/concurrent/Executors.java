/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/**
 * Factory and utility methods for {@link Executor}, {@link
 * ExecutorService}, {@link Future}, and {@link Cancellable} classes
 * defined in this package.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class Executors {

    /**
     * A wrapper class that exposes only the ExecutorService methods
     * of an implementation.
     */
    private static class DelegatedExecutorService implements ExecutorService {
        private final ExecutorService e;
        DelegatedExecutorService(ExecutorService executor) { e = executor; }
        public void execute(Runnable command) { e.execute(command); }
        public void shutdown() { e.shutdown(); }
        public List shutdownNow() { return e.shutdownNow(); }
        public boolean isShutdown() { return e.isShutdown(); }
        public boolean isTerminated() { return e.isTerminated(); }
        public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
            return e.awaitTermination(timeout, unit);
        }
    }

    /**
     * Creates a thread pool that reuses a fixed set of threads
     * operating off a shared unbounded queue. If any thread
     * terminates due to a failure during execution prior to shutdown,
     * a new one will take its place if needed to execute subsequent
     * tasks.
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
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created thread pool
     */
    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(nThreads, nThreads,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>(),
                                    threadFactory));
    }

    /**
     * Creates an Executor that uses a single worker thread operating
     * off an unbounded queue. (Note however that if this single
     * thread terminates due to a failure during execution prior to
     * shutdown, a new one will take its place if needed to execute
     * subsequent tasks.)  Tasks are guaranteed to execute
     * sequentially, and no more than one task will be active at any
     * given time. This method is equivalent in effect to
     *<tt>new FixedThreadPool(1)</tt>.
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
     * @param threadFactory the factory to use when creating new
     * threads
     *
     * @return the newly-created single-threaded Executor
     */
    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>(),
                                    threadFactory));
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
     * not consume any resources. Note that pools with similar
     * properties but different details (for example, timeout parameters)
     * may be created using {@link ThreadPoolExecutor} constructors.
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
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new DelegatedExecutorService
            (new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                    60, TimeUnit.SECONDS,
                                    new SynchronousQueue<Runnable>(),
                                    threadFactory));
    }

    /**
     * Executes a Runnable task and returns a Cancellable representing that
     * task.
     *
     * @param executor the Executor to which the task will be submitted
     * @param task the task to submit
     * @return a Cancellable representing pending completion of the task
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     */
    public static Cancellable execute(Executor executor, Runnable task) {
        FutureTask<Boolean> ftask = new FutureTask<Boolean>(task, Boolean.TRUE);
        executor.execute(ftask);
        return ftask;
    }

    /**
     * Executes a Runnable task and returns a Future representing that
     * task.
     *
     * @param executor the Executor to which the task will be submitted
     * @param task the task to submit
     * @param value the value which will become the return value of
     * the task upon task completion
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     */
    public static <T> Future<T> execute(Executor executor, Runnable task, T value) {
        FutureTask<T> ftask = new FutureTask<T>(task, value);
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
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     */
    public static <T> Future<T> execute(Executor executor, Callable<T> task) {
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
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     * @throws ExecutionException if the task encountered an exception
     * while executing
     */
    public static void invoke(Executor executor, Runnable task)
            throws ExecutionException, InterruptedException {
        FutureTask<Boolean> ftask = new FutureTask<Boolean>(task, Boolean.TRUE);
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
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     * @throws InterruptedException if interrupted while waiting for
     * completion
     * @throws ExecutionException if the task encountered an exception
     * while executing
     */
    public static <T> T invoke(Executor executor, Callable<T> task)
            throws ExecutionException, InterruptedException {
        FutureTask<T> ftask = new FutureTask<T>(task);
        executor.execute(ftask);
        return ftask.get();
    }


    /**
     * Executes a privileged action under the current access control 
     * context and returns a Future representing the pending result 
     * object of that action.
     *
     * @param executor the Executor to which the task will be submitted
     * @param action the action to submit
     * @return a Future representing pending completion of the action
     * @throws RejectedExecutionException if action cannot be scheduled
     * for execution
     */
    public static Future<Object> execute(Executor executor, PrivilegedAction action) {
        return execute(executor, action, AccessController.getContext());
    }
    
    /**
     * Executes a privileged action under the given access control 
     * context and returns a Future representing the pending result 
     * object of that action.
     *
     * @param executor the Executor to which the task will be submitted
     * @param action the action to submit
     * @param acc the access control context under which action should run
     * @return a Future representing pending completion of the action
     * @throws RejectedExecutionException if action cannot be scheduled
     * for execution
     */
    public static Future<Object> execute(Executor executor, PrivilegedAction action,
                                         AccessControlContext acc) {
        Callable<Object> task = new PrivilegedActionAdapter(action);
        FutureTask<Object> future = new PrivilegedFutureTask<Object>(task, acc);
        executor.execute(future);
        return future;
    }

    /**
     * Executes a privileged exception action under the current access control 
     * context and returns a Future representing the pending result 
     * object of that action.
     *
     * @param executor the Executor to which the task will be submitted
     * @param action the action to submit
     * @return a Future representing pending completion of the action
     * @throws RejectedExecutionException if action cannot be scheduled
     * for execution
     */
    public static Future<Object> execute(Executor executor, PrivilegedExceptionAction action) {
        return execute(executor, action, AccessController.getContext());
    }
    
    /**
     * Executes a privileged exception action under the given access control 
     * context and returns a Future representing the pending result 
     * object of that action.
     *
     * @param executor the Executor to which the task will be submitted
     * @param action the action to submit
     * @param acc the access control context under which action should run
     * @return a Future representing pending completion of the action
     * @throws RejectedExecutionException if action cannot be scheduled
     * for execution
     */
    public static Future<Object> execute(Executor executor, PrivilegedExceptionAction action,
                                         AccessControlContext acc) {
        Callable<Object> task = new PrivilegedExceptionActionAdapter(action);
        FutureTask<Object> future = new PrivilegedFutureTask<Object>(task, acc);
        executor.execute(future);
        return future;
    }
    

    private static class PrivilegedActionAdapter implements Callable<Object> {
        PrivilegedActionAdapter(PrivilegedAction action) {
            this.action = action;
        }
        public Object call () {
            return action.run();
        }
        private final PrivilegedAction action;
    }
    
    private static class PrivilegedExceptionActionAdapter implements Callable<Object> {
        PrivilegedExceptionActionAdapter(PrivilegedExceptionAction action) {
            this.action = action;
        }
        public Object call () throws Exception {
            return action.run();
        }
        private final PrivilegedExceptionAction action;
    }
        
        
    /** Cannot instantiate. */
    private Executors() {}
}
