/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/**
 * Factory and utility methods for {@link Executor}, {@link
 * ExecutorService}, {@link ThreadFactory}, {@link Future}, and {@link
 * Cancellable} classes defined in this package.
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
        Callable<Object> task = new PrivilegedActionAdapter(action);
        FutureTask<Object> future = new PrivilegedFutureTask<Object>(task);
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
        Callable<Object> task = new PrivilegedExceptionActionAdapter(action);
        FutureTask<Object> future = new PrivilegedFutureTask<Object>(task);
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
        
    /**
     * Return a default thread factory used to create new threads.
     * This factory creates all new threads used by an Executor in the
     * same {@link ThreadGroup}. If there is a {@link
     * java.lang.SecurityManager}, it uses the group of {@link
     * System#getSecurityManager}, else the group of the thread
     * invoking this <tt>defaultThreadFactory</tt> method. Each new
     * thread is created as a non-daemon thread with priority
     * <tt>Thread.NORM_PRIORITY</tt>. New threads have names
     * accessible via {@link Thread#getName} of
     * <em>pool-N-thread-M</em>, where <em>N</em> is the sequence
     * number of this factory, and <em>M</em> is the sequence number
     * of the thread created by this factory.
     * @return the thread factory
     */
    public static ThreadFactory defaultThreadFactory() {
	return new DefaultThreadFactory();
    }

    /**
     * Return a default thread factory used to create new threads.
     * This factory creates threads with the same settings as {@link
     * Executors#defaultThreadFactory}, additionally setting the
     * AccessControlContext and contextClassLoader of new threads to
     * be the same as the thread invoking this
     * <tt>privilegedThreadFactory</tt> method.  A new
     * <tt>privilegedThreadFactory</tt> can be created within an
     * {@link AccessController#doPrivileged} action  to create
     * threads with the selected permission settings holding within
     * that action.  Alternatively, a factory can be used to create
     * threads with any available access control context by first
     * setting them in the current thread, and then invoking this
     * method. 
     *
     * <p> Note that while tasks running within such threads will have
     * the same access control and class loader settings as the
     * current thread, they need not have the same {@link
     * java.lang.ThreadLocal} or {@link
     * java.lang.InheritableThreadLocal} values. If necessary,
     * particular values of thread locals can be set or reset before
     * any task runs in {@link ThreadPoolExecutor} subclasses using
     * {@link ThreadPoolExecutor#beforeExecute}. Also, if it is
     * necessary to initialize worker threads to have the same
     * InheritableThreadLocal settings as some other designated
     * thread, you can create a custom ThreadFactory in which that
     * thread waits for and services requests to create others that
     * will inherit its values.
     *
     * @return the thread factory
     * @throws AccessControlException if the current access control
     * context does not have permission to both get and set context
     * class loader.
     * @see PrivilegedFutureTask
     */
    public static ThreadFactory privilegedThreadFactory() {
	return new PrivilegedThreadFactory();
    }

    static class DefaultThreadFactory implements ThreadFactory {
	static final AtomicInteger poolNumber = new AtomicInteger(1);
	final ThreadGroup group;
	final AtomicInteger threadNumber = new AtomicInteger(1);
	final String namePrefix;

	DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null)? s.getThreadGroup() :
                                 Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" + 
                          poolNumber.getAndIncrement() + 
                         "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, 
                                  namePrefix + threadNumber.getAndIncrement(),
                                  0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    static class PrivilegedThreadFactory extends DefaultThreadFactory {
        private final ClassLoader ccl;
        private final AccessControlContext acc;

        PrivilegedThreadFactory() {
            super();
            this.ccl = Thread.currentThread().getContextClassLoader();
            this.acc = AccessController.getContext();
            acc.checkPermission(new RuntimePermission("setContextClassLoader"));
        }
        
        public Thread newThread(final Runnable r) {
            return super.newThread(new Runnable() {
                public void run() {
                    AccessController.doPrivileged(new PrivilegedAction() {
                        public Object run() { 
                            Thread.currentThread().setContextClassLoader(ccl);
                            r.run();
                            return null; 
                        }
                    }, acc);
                }
            });
        }
        
    }

        
    /** Cannot instantiate. */
    private Executors() {}
}
