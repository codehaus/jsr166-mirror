package java.util.concurrent;

/**
 * An Executor asynchronously executes commands.  This interface
 * provides a way of decoupling asynchronous task execution from the
 * mechanics of how that task will be run, including construction of
 * new Threads, scheduling, etc.
 *
 * <p> An Executor can be thought of as a framework for executing
 * Runnables.  The executor manages queueing and scheduling of tasks,
 * and creation and teardown of threads.  Depending on which concrete
 * Executor class is being used, tasks may execute in a newly created
 * thread, an existing task-execution thread, or the thread calling
 * execute(), and may execute sequentially or concurrently.
 *
 * <p> Several concrete implementations of Executor are included in
 * java.util.concurrent, including ThreadPoolExecutor, a flexible thread
 * pool, SingleThreadedExecutor, which executes commands sequentially in
 * a background thread, and ScheduledExecutor, which adds support for timed, delayed
 * and periodic task execution.  Executor can be used in conjunction
 * with FutureTask (which implements Runnable) to asynchronously start
 * a potentially long-running computation and query the FutureTask to
 * determine if its execution has completed.
 *
 * <p>The Executors class provides factory methods for all of the types
 * of executors provided in java.util.concurrent.
 *
 * @see ExecutorService
 * @see ThreadPoolExecutor, SingleThreadedExecutor, ScheduledExecutor
 * @see Executors
 * @see Runnable, FutureTask
 */
public interface Executor {

    /**
     * Execute the given command sometime in the future.  The command
     * may execute in the calling thread, in a new thread, or in a
     * pool thread, at the discretion of the Executor implementation.
     */
    void execute(Runnable command);
}
