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
 * ExecutorService}, and {@link ThreadFactory} classes defined in this
 * package.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class Executors {

    /**
     * A wrapper class that exposes only the ExecutorService methods
     * of an implementation.
     */
    private static class DelegatedExecutorService extends AbstractExecutorService {
        private final ExecutorService e;
        DelegatedExecutorService(ExecutorService executor) { e = executor; }
        public void execute(Runnable command) { e.execute(command); }
        public void shutdown() { e.shutdown(); }
        public List<Runnable> shutdownNow() { return e.shutdownNow(); }
        public boolean isShutdown() { return e.isShutdown(); }
        public boolean isTerminated() { return e.isTerminated(); }
        public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
            return e.awaitTermination(timeout, unit);
        }
    }
    
    /**
     * A wrapper class that exposes only the ExecutorService and 
     * ScheduleExecutor methods of a ScheduledThreadPoolExecutor.
     */
    private static class DelegatedScheduledExecutorService
            extends DelegatedExecutorService 
            implements ScheduledExecutorService {
        private final ScheduledExecutorService e;
        DelegatedScheduledExecutorService(ScheduledExecutorService executor) {
            super(executor);
            e = executor;
        }
        public ScheduledFuture<?> schedule(Runnable command, long delay,  TimeUnit unit) {
            return e.schedule(command, delay, unit);
        }
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return e.schedule(callable, delay, unit);
        }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,  long period, TimeUnit unit) {
            return e.scheduleAtFixedRate(command, initialDelay, period, unit);
        }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,  long delay, TimeUnit unit) {
            return e.scheduleWithFixedDelay(command, initialDelay, delay, unit);
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
     * Creates a thread pool that can schedule commands to run after a 
     * given delay, or to execute periodically.
     * @return a newly created scheduled thread pool with termination management
     */
    public static ScheduledExecutorService newScheduledThreadPool() {
        return newScheduledThreadPool(1);
    }
    
    /**
     * Creates a thread pool that can schedule commands to run after a 
     * given delay, or to execute periodically.
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @return a newly created scheduled thread pool with termination management
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new DelegatedScheduledExecutorService
            (new ScheduledThreadPoolExecutor(corePoolSize));
    }

    /**
     * Creates a thread pool that can schedule commands to run after a 
     * given delay, or to execute periodically.
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param threadFactory the factory to use when the executor
     * creates a new thread. 
     * @return a newly created scheduled thread pool with termination management
     */
    public static ScheduledExecutorService newScheduledThreadPool(
            int corePoolSize, ThreadFactory threadFactory) {
        return new DelegatedScheduledExecutorService
            (new ScheduledThreadPoolExecutor(corePoolSize, threadFactory));
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
     * Return a thread factory used to create new threads that
     * have the same permissions as the current thread.
     * This factory creates threads with the same settings as {@link
     * Executors#defaultThreadFactory}, additionally setting the
     * AccessControlContext and contextClassLoader of new threads to
     * be the same as the thread invoking this
     * <tt>privilegedThreadFactory</tt> method.  A new
     * <tt>privilegedThreadFactory</tt> can be created within an
     * {@link AccessController#doPrivileged} action setting the
     * current thread's access control context to create threads with
     * the selected permission settings holding within that action.
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
