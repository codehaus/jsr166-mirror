package java.util.concurrent;

import java.util.List;

/**
 * An Executor asynchronously executes commands; an ExecutorService
 * is an executor which provides lifecycle-management functions.
 *
 * <p> An ExecutorService can be shut down, which will cause it to stop
 * processing submitted tasks.  After being shut down, it will
 * normally reach a quiescent state in which no tasks are executing
 * and no new ones will be processed.
 *
 * <p> Several concrete implementations of ExecutorService are included in
 * java.util.concurrent, including ThreadExecutor, a flexible thread
 * pool, and ScheduledExecutor, which adds support for timed, delayed
 * and periodic task execution.
 *
 * <p>The Executors class provides factory methods for all of the types
 * of executors provided in java.util.concurrent.
 *
 * @see Executor
 * @see Executors
 **/
public interface ExecutorService extends Executor {

    /**
     * Initiate an orderly shutdown in which previously submitted tasks
     * are executed, but new tasks submitted to execute() subsequent to
     * calling shutdown() are not.
     *
     * <p> The exact fate of tasks submitted in subsequent calls to
     * <tt>execute</tt> is left unspecified in this
     * interface. Implementations may provide different options, such
     * as ignoring them, or causing <tt>execute</tt> to throw an
     * (unchecked) <tt>IllegalStateException</tt>.
     **/
    void shutdown();

    /**
     * Attempt to stop processing all actively executing tasks, never
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
     **/
    List shutdownNow();

    /**
     * Return true if the Executor has been shut down.
     **/
    boolean isShutdown();

    /**
     * Interrupt the processing of all current tasks.  Depending on
     * whether the tasks ignore the InterruptedException, this may or
     * may not speed the completion of queued tasks, and may cause
     * improperly written tasks to fail.  The Executor remains enabled
     * for future executions.
     **/
    void interrupt();

    /**
     * Return true if all tasks have completed following shut down.
     * Note that isTerminated is never true unless <tt>shutdown</tt>
     * or <tt>shutdownNow</tt> have been invoked.
     **/
    boolean isTerminated();

    /**
     * Block until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current Thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @throws java.lang.InterruptedException if interrupted while waiting.
     * @throws java.lang.IllegalStateException if not shut down.
     **/
    void awaitTermination(long timeout, TimeUnit granularity) throws InterruptedException;

    /**
     * Interface containing methods that are invoked during various points of
     * execution of a thread-based, allowing fine-grained control and
     * monitoring.
     **/
    public interface Callbacks {

        /**
         * Method invoked prior to executing the given Runnable in given
         * thread.  This method may be used to re-initialize ThreadLocals,
         * or to perform logging.
         * @param t the thread that will run task r.
         * @param r the task that will be executed.
         * @param e the Executor
         **/
        void beforeExecute(Thread t, Runnable r, ExecutorService e);

        /**
         * Method invoked upon completion of execution of the given
         * Runnable.  If non-null, the Throwable is the uncaught exception
         * that caused execution to terminate abruptly.
         * @param r the runnable that has completed.
         * @param t the exception that cause termination, or null if
         * execution completed normally.
         * @param e the Executor
         **/
        void afterExecute(Runnable r, Throwable t, ExecutorService e);

        /**
         * Method invoked when <tt>execute</tt> cannot execute a
         * task. This may occur when no more threads or queue slots are
         * available because their bounds would be exceeded, or upon
         * shutdown of the Executor. You can distinguish among these cases
         * by querying the Executor.
         * @param r the runnable task requested to be executed.
         * @param e the Executor
         * @return false if <tt>execute</tt> should be retried, else true.
         * You may alternatively throw an unchecked exception, which
         * will be propagated to the caller of <tt>execute</tt>.
         **/
        boolean cannotExecute(Runnable r, ExecutorService e);

        /**
         * Method invoked when the Executor has terminated.  Default
         * implementation does nothing.
         * @param e the Executor
         **/
        void terminated(ExecutorService e);
    }

}
