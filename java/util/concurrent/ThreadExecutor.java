/*
 * @(#)ThreadExecutor.java
 */

package java.util.concurrent;

import java.util.List;

/**
 * An <tt>ExecutorService</tt> that can execute many tasks concurrently.
 * Depending on its configuration, a <tt>ThreadExecutor</tt> can create a new
 * thread for each task, execute tasks sequentially in a single thread, or
 * implement a thread pool with reusable threads.  
 *
 * <p>The most common configuration of ThreadExecutor is a thread
 * pool. Thread pools address solve two different problems at the same
 * time: they usually provide faster performance when executing large
 * numbers of asynchronous tasks, due to reduced per-task invocation
 * overhead, and they provide a means of bounding and managing the
 * resources, including threads, consumed in executing a collection of
 * tasks.
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
 * @fixme If greater control is needed, you can use the
 * constructor with custom parameters, selectively override
 * <tt>Callbacks</tt>, and/or dynamically change tuning
 * parameters.
 *
 * <p>This class also maintain some basic statistics, such as the
 * maximum number of active threads, or the maximum queue length, that
 * may be useful for monitoring and tuning executors.
 *
 * <p>
 * <b>Tuning guide</b> 
 * @@@brian I have copied some stuff from dl.u.c; please review to make sure
 * that it is still correct.  
 * @@@brian Also please check if my statements about queuing and blocking
 * are correct.
 * <dl>
 *
 * <dt>Minimum and maximum pool size.  ThreadExecutor will
 * automatically adjust the pool size within the bounds set by
 * minimumPoolSize and maximumPoolSize.  When a new task is submitted,
 * and fewer than the minimum number of threads are running, a new
 * thread is created to handle the request, even if other worker
 * threads are idle.  If there are more than the minimum but less than
 * the maximum number of threads running, a new thread will be created
 * only if all other threads are busy.  By setting minimumPoolSize and
 * maximumPoolSize to N, you create a fixed-size thread pool.
 *
 * <dt>Keep-alive.  The keepAliveTime determines what happens to idle
 * threads.  If the pool currently has more than the minimum number of
 * threads, excess threads will be terminated if they have been idle
 * for more than the keepAliveTime.  
 *
 * <dt>Queueing.  You are free to specify the queuing mechanism used
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
 *
 * <dt>Creating new threads.  New threads are created through the
 * Callbacks.  By default, threads are created simply with
 * the new Thread(Runnable) constructor, but by overriding
 * Callbacks.newThread, you can alter the thread's name,
 * thread group, priority, daemon status, etc.
 *
 * <dt>Before and after intercepts.  The Callbacks class has
 * methods which are called before and after execution of a task.
 * These can be used to manipulate the execution environment (for
 * example, reinitializing ThreadLocals), gather statistics, or
 * perform logging.
 *
 * <dt>Blocked execution.  There are a number of factors which can
 * bound the number of tasks which can execute at once, including the
 * maximum pool size and the queuing mechanism used.  If you are using
 * a synchronous queue, the execute() method will block until threads
 * are available to execute.  If you are using a bounded queue, then
 * tasks will be discarded if the bound is reached.  If the executor
 * determines that a task cannot be executed because it has been
 * refused by the queue and no threads are available, the
 * Callbacks.cannotExecute method will be called.
 *
 * <dt>Termination.  ThreadExecutor supports two shutdown options,
 * immediate and graceful.  In an immediate shutdown, any threads
 * currently executing are interrupted, and any tasks not yet begun
 * are returned from the shutdownNow call.  In a graceful shutdown,
 * all queued tasks are allowed to run, but new tasks may not be
 * submitted.
 * </dl>
 *
 * @fixme change name to ThreadedExecutor?
 *
 * @see CannotExecuteHandler
 * @see ThreadFactory
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/02/19 10:53:58 $
 * @editor $Author: jozart $
 */
public class ThreadExecutor implements ExecutorService {

    /** JAVADOC?? */
    private ThreadFactory threadFactory;
    
    //FIXME: private CannotExecuteHandler cannotExecuteHandler;

    /**
     * Creates a new ThreadExecutor with the given initial parameters.  
     * It may be more convenient to use one of the factory methods instead
     * of this general purpose constructor.
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
     * @throws NullPointerException if workQueue is null
     */
    public ThreadExecutor(int minThreads,
        int maxThreads,
        long keepAliveTime,
        TimeUnit granularity,
        BlockingQueue workQueue) {}

    /**
     * Sets the minimum allowed number of threads.  This overrides any
     * value set in the constructor.  
     * @param minThreads the new minimum
     * @throws IllegalArgumentException if minhThreads less than zero
     */
    protected void setMinimumPoolSize(int minThreads) {}

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor.  
     * @param maxThreads the new maximum
     * @throws IllegalArgumentException if maxThreads less than zero or
     * less than getMinimumPoolSize.
     */
    protected void setMaximumPoolSize(int maxThreads) {}

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
    protected void setKeepAliveTime(long time, TimeUnit granularity) {}

    /**
     * Gets the minimum allowed number of threads.  
     * @return the minimum
     *
     */
    protected int getMinimumPoolSize() { return 0; }

    /**
     * Gets the maximum allowed number of threads.
     * @return the maximum
     *
     */
    protected int getMaximumPoolSize() { return 0; }

    /**
     * Gets the thread keep-alive time, which is the amount of time
     * which threads in excess of the minimum pool size may remain
     * idle before being terminated.  
     * @param granularity the desired time unit of the result
     * @return the time limit
     */
    protected long getKeepAliveTime(TimeUnit granularity) { return 0; }

    // statistics

    /**
     * Gets the current number of threads in the pool.
     * @return the number of threads
     */
    protected int getPoolSize() { return 0; }

    /**
     * Gets the current number of threads that are actively
     * executing tasks.
     * @return the number of threads
     */
    protected int getActiveCount() { return 0; }

    /**
     * Gets the maximum number of threads that have ever simultaneously
     * executed tasks.
     * @return the number of threads
     */
    protected int getMaximumActiveCount() { return 0; }

    /**
     * Gets the number of tasks that have been queued but not yet executed
     * @return the number of tasks.
     */
    protected int getQueueCount() { return 0; }

    /**
     * Gets the maximum number of tasks that have ever been queued
     * waiting for execution.
     * @return the number of tasks.
     */
    protected int getMaximumQueueCount() { return 0; }

    /**
     * Gets the total number of tasks that have been scheduled for execution.
     * @return the number of tasks.
     */
    protected int getCumulativeTaskCount() { return 0; }

    /**
     * Gets the total number of tasks that have completed execution.
     * @return the number of tasks.
     */
    protected int getCumulativeCompletedTaskCount() { return 0; }

    /**
     * Returns the task queue used by the ThreadExecutor.  Note that
     * this queue may be in active use.  Retrieveing the task queue
     * does not prevent queued tasks from executing.
     */
    protected BlockingQueue getQueue() {
        return null;
    }

    /** Returns the thread factory used to create new threads */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /** Sets the thread factory used to create new threads */
    public void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the CannotExecuteHandler.
     */
    public CannotExecuteHandler getCannotExecuteHandler() {
        return null;
    }

    /**
     * Sets a new CannotExecuteHandler. Actions that are already underway
     * using the current handler will continue to use it; future
     * actions will use the new one.  In general, this method should
     * invoked only when the executor is known to be in a quiescent
     * state.
     *
     * @param handler the new CannotExecuteHandler
     */
    public void setCannotExecuteHandler(CannotExecuteHandler handler) {
    }

    /* Various CannotExecuteHandler implementations. */

    /**
     * A handler for unexecutable tasks that runs these tasks directly in the
     * calling thread of the <tt>execute</tt> method.  This is the default
     * <tt>CannotExecuteHandler</tt>.
     */
   public class CallerRunsCannotExecuteHandler implements CannotExecuteHandler {

        /**
         * Creates new <tt>CallerRunsCannotExecuteHandler</tt>.
         */
        public CallerRunsCannotExecuteHandler() { }

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
    public class AbortCannotExecuteHandler implements CannotExecuteHandler {

        /**
         * Creates new <tt>AbortCannotExecuteHandler</tt>.
         */
        public AbortCannotExecuteHandler() { }

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
    public class WaitCannotExecuteHandler implements CannotExecuteHandler {

        /**
         * Creates new <tt>WaitCannotExecuteHandler</tt>.
         */
        public WaitCannotExecuteHandler() { }

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
    public class DiscardCannotExecuteHandler implements CannotExecuteHandler {

        /**
         * Creates new <tt>DiscardCannotExecuteHandler</tt>.
         */
        public DiscardCannotExecuteHandler() { }

        public boolean cannotExecute(Runnable r, boolean isShutdown) {
            return true;
        }
    }

    /**
     * A handler for unexecutable tasks that discards the oldest unhandled request.
     */
    public class DiscardOldestCannotExecuteHandler implements CannotExecuteHandler {

        /**
         * Creates new <tt>DiscardOldestCannotExecuteHandler</tt>.
         */
        public DiscardOldestCannotExecuteHandler() { }

        public boolean cannotExecute(Runnable r, boolean isShutdown) {
            if (!isShutdown) {
                // FIXME: discard oldest here
                return false;
            }
            return true;
        }
    }

    /* Executor methods. INHERIT this javadoc from interface??? */

    /**
     * Executes the given command sometime in the future.  The command
     * may execute in the calling thread, in a new thread, or in a
     * pool thread, at the discretion of the Executor implementation.
     *
     * @throws CannotExecuteException if command cannot be submitted for
     * execution
     */
    public void execute(Runnable command) {}

    /* ExecutorService methods. INHERIT this javadoc from interface??? */

    /**
     * Interrupts the processing of all current tasks.  Depending on
     * whether the tasks ignore the InterruptedException, this may or
     * may not speed the completion of queued tasks, and may cause
     * improperly written tasks to fail.  The Executor remains enabled
     * for future executions.
     */
    public void interrupt() {}

    /**
     * Returns true if all tasks have completed following shut down.
     * Note that isTerminated is never true unless <tt>shutdown</tt>
     * or <tt>shutdownNow</tt> have been invoked.
     */
    public boolean isTerminated() {
        return false;
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current Thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @return <tt>true</tt> if this executor terminated and <tt>false</tt>
     * if waiting time elapsed before termination.
     * @throws java.lang.InterruptedException if interrupted while waiting.
     * @throws java.lang.IllegalStateException if not shut down.
     */
    public boolean awaitTermination(long timeout, TimeUnit granularity)
    throws InterruptedException {
        return true;
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks
     * are executed, but new tasks submitted to execute() subsequent to
     * calling shutdown() are not.
     *
     * <p> The exact fate of tasks submitted in subsequent calls to
     * <tt>execute</tt> is left unspecified in this
     * interface. Implementations may provide different options, such
     * as ignoring them, or causing <tt>execute</tt> to throw an
     * (unchecked) <tt>IllegalStateException</tt>.
     */
    public void shutdown() {}

    /**
     * Attempts to stop processing all actively executing tasks, never
     * start processing previously submitted tasks that have not yet
     * commenced execution, and cause subsequently submitted tasks not
     * to be processed.  The exact fate of tasks submitted in
     * subsequent calls to <tt>execute</tt> is left unspecified in
     * this interface. Implementations may provide different options,
     * such as ignoring them, or causing <tt>execute</tt> to throw an
     * (unchecked) <tt>IllegalStateException</tt>.  Similarly, there
     * are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example typical
     * thread-based Executors will cancel via
     * <tt>Thread.interrupt</tt>, so if any tasks mask or fail to
     * respond to interrupts, they might never terminate.
     * @return a list of all tasks that never commenced execution.
     */
    public List shutdownNow() {
        return null;
    }

    /**
     * Returns true if the Executor has been shut down.
     */
    public boolean isShutdown() {
        return false;
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

