/*
 * @(#)ThreadPoolExecutor.java
 */

package java.util.concurrent;

import java.util.List;

/**
 * A {@link ThreadedExecutor} that executes each submitted task on one
 * of several pooled threads.
 *
 * <p>Thread pools address two different problems at the same time:
 * they usually provide improved performance when executing large
 * numbers of asynchronous tasks, due to reduced per-task invocation
 * overhead, and they provide a means of bounding and managing the
 * resources, including threads, consumed in executing a collection of
 * tasks.
 *
 * <p>This class is very configurable and can be configured to create
 * a new thread for each task, or even to execute tasks sequentially
 * in a single thread, in addition to its most common configuration,
 * which reuses a pool of threads.
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility hooks.
 * However, programmers are urged to use the more convenient factory
 * methods <tt>newCachedThreadPool</tt> (unbounded thread pool, with
 * automatic thread reclamation), <tt>newFixedThreadPool</tt> (fixed
 * size thread pool), <tt>newSingleThreadExecutor</tt> (single
 * background thread for execution of tasks), and
 * <tt>newThreadPerTaskExeceutor</tt> (execute each task in a new
 * thread), that preconfigure settings for the most common usage
 * scenarios.
 *
 *
 * <p>This class also maintain some basic statistics, such as the
 * maximum number of active threads, or the maximum queue length, that
 * may be useful for monitoring and tuning executors.
 *
 * <h3>Tuning guide</h3>
 * <dl>
 * <dt>Minimum and maximum pool size</dt>
 * <dd>ThreadExecutor will
 * automatically adjust the pool size within the bounds set by
 * minimumPoolSize and maximumPoolSize.  When a new task is submitted,
 * and fewer than the minimum number of threads are running, a new
 * thread is created to handle the request, even if other worker
 * threads are idle.  If there are more than the minimum but less than
 * the maximum number of threads running, a new thread will be created
 * only if all other threads are busy.  By setting minimumPoolSize and
 * maximumPoolSize to N, you create a fixed-size thread pool.</dd>
 *
 * <dt>Keep-alive</dt>
 * <dd>The keepAliveTime determines what happens to idle
 * threads.  If the pool currently has more than the minimum number of
 * threads, excess threads will be terminated if they have been idle
 * for more than the keepAliveTime.</dd>
 *
 * <dt>Queueing</dt>
 * <dd>You are free to specify the queuing mechanism used
 * to handle submitted tasks.  The newCachedThreadPool factory method
 * uses queueless synchronous channels to to hand off work to threads.
 * This is a safe, conservative policy that avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * The newFixedThreadPool factory method uses a LinkedBlockingQueue,
 * which will cause new tasks to be queued in cases where all
 * MaximumPoolSize threads are busy.  Queues are sometimes appropriate
 * when each task is completely independent of others, so tasks cannot
 * affect each others execution. For example, in an http server.  When
 * given a choice, this pool always prefers adding a new thread rather
 * than queueing if there are currently fewer than the current
 * getMinimumPoolSize threads running, but otherwise always prefers
 * queuing a request rather than adding a new thread.
 *
 * <p>While queuing can be useful in smoothing out transient bursts of
 * requests, especially in socket-based services, it is not very well
 * behaved when commands continue to arrive on average faster than
 * they can be processed.  Using a bounded queue implements an overflow
 * policy which drops requests which cannot be handled due to insufficient
 * capacity.
 *
 * Queue sizes and maximum pool sizes can often be traded off for each
 * other. Using large queues and small pools minimizes CPU usage, OS
 * resources, and context-switching overhead, but can lead to
 * artifically low throughput.  If tasks frequently block (for example
 * if they are I/O bound), a JVM and underlying OS may be able to
 * schedule time for more threads than you otherwise allow. Use of
 * small queues or queueless handoffs generally requires larger pool
 * sizes, which keeps CPUs busier but may encounter unacceptable
 * scheduling overhead, which also decreases throughput.
 * </dd>
 * <dt>Creating new threads</dt>
 * <dd>New threads are created through the
 * Callbacks.  By default, threads are created simply with
 * the new Thread(Runnable) constructor, but by overriding
 * Callbacks.newThread, you can alter the thread's name,
 * thread group, priority, daemon status, etc.
 * </dd>
 * <dt>Before and after intercepts</dt>
 * <dd>The Callbacks class has
 * methods which are called before and after execution of a task.
 * These can be used to manipulate the execution environment (for
 * example, reinitializing ThreadLocals), gather statistics, or
 * perform logging.
 * </dd>
 * <dt>Blocked execution</dt>
 * <dd>There are a number of factors which can
 * bound the number of tasks which can execute at once, including the
 * maximum pool size and the queuing mechanism used.  If you are using
 * a synchronous queue, the execute() method will block until threads
 * are available to execute.  If you are using a bounded queue, then
 * tasks will be discarded if the bound is reached.  If the executor
 * determines that a task cannot be executed because it has been
 * refused by the queue and no threads are available, the
 * Callbacks.cannotExecute method will be called.
 * </dd>
 * <dt>Termination</dt>
 * <dd>ThreadExecutor supports two shutdown options,
 * immediate and graceful.  In an immediate shutdown, any threads
 * currently executing are interrupted, and any tasks not yet begun
 * are returned from the shutdownNow call.  In a graceful shutdown,
 * all queued tasks are allowed to run, but new tasks may not be
 * submitted.
 * </dd>
 * </dl>
 *
 * @since 1.5
 * @see CannotExecuteHandler
 * @see Executors
 * @see ThreadFactory
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/14 21:30:48 $
 * @editor $Author: tim $
 *
 * @fixme If greater control is needed, you can use the
 *        constructor with custom parameters, selectively override
 *        <tt>Callbacks</tt>, and/or dynamically change tuning
 *        parameters
 *
 * @fixme <br/> Brian copied some stuff from dl.u.c for the tuning guide; please
 *        review to make sure that it is still correct
 *
 * @fixme <br/> Please check if Brian's statements about queuing and blocking
 *        in the tuning guide are correct.
 */
public class ThreadPoolExecutor implements ThreadedExecutor {

    /**
     * Creates a new <tt>ThreadPoolExecutor</tt> with the given initial
     * parameters.  It may be more convenient to use one of the factory
     * methods instead of this general purpose constructor.
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
     * @param workQueue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @throws IllegalArgumentException if minThreads, maxThreads, or
     * keepAliveTime less than zero, or if minThreads greater than
     * maxThreads.
     * @throws NullPointerException if <tt>workQueue</tt> is null
     */
    public ThreadPoolExecutor(int minThreads,
                              int maxThreads,
                              long keepAliveTime,
                              TimeUnit granularity,
                              BlockingQueue workQueue) {}

    /* Executor implementation. Inherit javadoc from ThreadedExecutor. */

    public void execute(Runnable command) {}

    /* ThreadedExecutor implementation.  Inherit javadoc. */

    public void setThreadFactory(ThreadFactory threadFactory) {
    }

    public ThreadFactory getThreadFactory() {
        return null;
    }

    public void setCannotExecuteHandler(CannotExecuteHandler handler) {
    }

    public CannotExecuteHandler getCannotExecuteHandler() {
        return null;
    }

    public BlockingQueue getQueue() {
        return null;
    }

    public void shutdown() {}

    public List shutdownNow() {
        return null;
    }

    public boolean isShutdown() {
        return false;
    }

    public void interrupt() {}

    public boolean isTerminated() {
        return false;
    }

    public boolean awaitTermination(long timeout, TimeUnit granularity)
            throws InterruptedException {
        return false;
    }

    /* @fixme Should any of these be included in ThreadedExecutor interface? */

    /**
     * Sets the minimum allowed number of threads.  This overrides any
     * value set in the constructor.
     *
     * @param minThreads the new minimum
     * @throws IllegalArgumentException if <tt>minThreads</tt> less than zero
     */
    public void setMinimumPoolSize(int minThreads) {}

    /**
     * Returns the minimum allowed number of threads.
     *
     * @return the minimum number of threads
     */
    public int getMinimumPoolSize() { return 0; }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor.
     *
     * @param maxThreads the new maximum
     * @throws IllegalArgumentException if maxThreads less than zero or
     * the {@link #getMinimumPoolSize minimum pool size}
     */
    public void setMaximumPoolSize(int maxThreads) {}

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum number of threads
     */
    public int getMaximumPoolSize() { return 0; }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the minimum number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     * @param time the time to wait.  A time value of zero will cause
     * excess threads to terminate immediately after executing tasks.
     * @param granularity  the time unit of the time argument
     * @throws IllegalArgumentException if msecs less than zero
     */
    public void setKeepAliveTime(long time, TimeUnit granularity) {}

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * which threads in excess of the minimum pool size may remain
     * idle before being terminated.
     *
     * @param granularity the desired time unit of the result
     * @return the time limit
     */
    public long getKeepAliveTime(TimeUnit granularity) { return 0; }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() { return 0; }

    /**
     * Returns the current number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() { return 0; }

    /**
     * Returns the maximum number of threads that have ever simultaneously
     * executed tasks.
     *
     * @return the number of threads
     */
    public int getMaximumActiveCount() { return 0; }

    /**
     * Returns the number of tasks that have been queued but not yet executed.
     *
     * @return the number of tasks
     */
    public int getQueueCount() { return 0; }

    /**
     * Returns the maximum number of tasks that have ever been queued
     * waiting for execution.
     *
     * @return the number of tasks
     */
    public int getMaximumQueueCount() { return 0; }

    /**
     * Returns the total number of tasks that have been scheduled for execution.
     *
     * @return the number of tasks
     */
    public int getCumulativeTaskCount() { return 0; }

    /**
     * Returns the total number of tasks that have completed execution.
     *
     * @return the number of tasks
     */
    public int getCumulativeCompletedTaskCount() { return 0; }

    /* @fixme Various CannotExecuteHandler implementations. */

    /**
     * A handler for unexecutable tasks that runs these tasks directly in the
     * calling thread of the <tt>execute</tt> method.  This is the default
     * <tt>CannotExecuteHandler</tt>.
     */
   public class CallerRunsPolicy implements CannotExecuteHandler {

        /**
         * Constructs a <tt>CallerRunsPolicy</tt>.
         */
        public CallerRunsPolicy() { }

        public boolean cannotExecute(Runnable r, boolean isShutdown) {
            if (!isShutdown) {
                r.run();
            }
            return true;
        }
    }

    /**
     * A handler for unexecutable tasks that throws a <tt>CannotExecuteException</tt>.
     */
    public class AbortPolicy implements CannotExecuteHandler {

        /**
         * Constructs a <tt>AbortPolicy</tt>.
         */
        public AbortPolicy() { }

        public boolean cannotExecute(Runnable r, boolean isShutdown) {
            if (!isShutdown) {
                throw new CannotExecuteException();
            }
            return true;
        }
    }

    /**
     * A handler for unexecutable tasks that waits until the task can be
     * submitted for execution.
     */
    public class WaitPolicy implements CannotExecuteHandler {

        /**
         * Constructs a <tt>WaitPolicy</tt>.
         */
        public WaitPolicy() { }

        public boolean cannotExecute(Runnable r, boolean isShutdown) {
            if (!isShutdown) {
                // FIXME: wait here
                // FIXME: throw CannotExecuteException if interrupted
                return false;
            }
            return true;
        }
    }

    /**
     * A handler for unexecutable tasks that silently discards these tasks.
     */
    public class DiscardPolicy implements CannotExecuteHandler {

        /**
         * Constructs <tt>DiscardPolicy</tt>.
         */
        public DiscardPolicy() { }

        public boolean cannotExecute(Runnable r, boolean isShutdown) {
            return true;
        }
    }

    /**
     * A handler for unexecutable tasks that discards the oldest unhandled request.
     */
    public class DiscardOldestPolicy implements CannotExecuteHandler {

        /**
         * Constructs a <tt>DiscardOldestPolicy</tt>.
         */
        public DiscardOldestPolicy() { }

        public boolean cannotExecute(Runnable r, boolean isShutdown) {
            if (!isShutdown) {
                // FIXME: discard oldest here
                return false;
            }
            return true;
        }
    }

    /*
     * Methods invoked during various points of execution, allowing fine-grained
     * control and monitoring.
     */

    /**
     * Method invoked prior to executing the given Runnable in given
     * thread.  This method may be used to re-initialize ThreadLocals,
     * or to perform logging.
     *
     * @param t the thread that will run task r.
     * @param r the task that will be executed.
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given
     * Runnable.  If non-null, the Throwable is the uncaught exception
     * that caused execution to terminate abruptly.
     *
     * @param r the runnable that has completed.
     * @param t the exception that cause termination, or null if
     * execution completed normally.
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing.
     */
    protected void terminated() { }
}
