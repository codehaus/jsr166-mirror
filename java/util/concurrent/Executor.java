package java.util.concurrent;

import java.util.List;

/**
 * An Executor asynchronously executes commands.  This interface
 * provides a way of decoupling asynchronous task execution from the
 * explicit construction of new Threads.
 *
 * <p> An Executor can be thought of as a framework for executing
 * Runnables.  The executor manages queueing and scheduling of tasks,
 * and creation and teardown of threads.  Depending on which concrete
 * Executor class is being used, tasks may execute in a newly created
 * thread, an existing task-execution thread, or the thread calling
 * execute(), and may execute sequentially or concurrently.
 *
 * <p> Several concrete implementations of Executor are included in
 * java.util.concurrent, including ThreadExecutor, a flexible thread
 * pool, and ScheduledExecutor, which adds support for timed, delayed
 * and periodic task execution.  Executor can be used in conjunction
 * with FutureTask (which implements Runnable) to asynchronously start
 * a potentially long-running computation and query the FutureTask to
 * determine if its execution has completed.
 *
 * <p> An Executor can be shut down, which will cause it to stop
 * processing submitted tasks.  After being shut down, it will
 * normally reach a quiescent state in which no tasks are executing
 * and no new ones will be processed.
 *
 * @see ThreadExecutor, ScheduledExecutor
 * @see Runnable, FutureTask
 **/
public interface Executor {

    /**
     * Execute the given command sometime in the future.  The command
     * may execute in the calling thread, in a new thread, or in a
     * pool thread, at the discretion of the Executor implementation.
     **/
    public void execute(Runnable command);

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
    public void shutdown();

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
    public List shutdownNow();

    /**
     * Return true if the Executor has been shut down.
     **/
    public boolean isShutdown();
    
}
