/*
 * @(#)Executor.java
 */

package java.util.concurrent;

/**
 * An object that executes submitted tasks. This interface provides a way
 * of decoupling task submission from the mechanics of how each task will
 * be run, including details of thread use, scheduling, etc.
 *
 * <p>An executor can run the submitted task immediately in the caller's
 * thread:
 *
 * <pre>
 * class DirectExecutor implements Executor {
 *     public void execute(Runnable r) {
 *         r.run();
 *     }
 * }</pre>
 *
 * However, tasks are typically executed in a different thread:
 *
 * <pre>
 * class ConcurrentExecutor implements Executor {
 *     public void execute(Runnable r) {
 *         new Thread(r).start();
 *     }
 * }</pre>
 *
 * Tasks submitted to the executor below are executed one at a time by a
 * second executor.
 *
 * <pre>
 * class SerialExecutor implements Executor {
 *     LinkedQueue tasks = new LinkedQueue(<Runnable>);
 *     Executor threadPool;
 *     Runnable active;
 *
 *     SerialExecutor(Executor threadPool) {
 *         this.threadPool = threadPool;
 *     }
 *
 *     public synchronized void execute(final Runnable r) {
 *         tasks.offer(new Runnable() {
 *             public void run() {
 *                 try {
 *                     r.run();
 *                 } finally {
 *                     scheduleNext();
 *                 }
 *             }
 *         });
 *         if (active == null) {
 *             scheduleNext();
 *         }
 *     }
 *
 *     protected synchronized void scheduleNext() {
 *         if ((active = tasks.poll()) != null) {
 *             threadPool.execute(active);
 *         }
 *     }
 * }</pre>
 *
 * The <tt>Executor</tt> implementations provided in <tt>java.util.concurrent</tt>
 * implement <tt>ThreadedExecutor</tt>, which is a more extensive interface.
 * For even more advanced users, the <tt>ThreadPoolExecutor</tt> class provides
 * a powerful, extensible solution. The <tt>Executors</tt> class provides
 * factory methods for these executors.
 *
 * @fixme Move the following to package documentation.
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
 * a background thread, and ScheduledExecutor, which adds support for timed,
 * delayed and periodic task execution.  Executor can be used in conjunction
 * with FutureTask (which implements Runnable) to asynchronously start
 * a potentially long-running computation and query the FutureTask to
 * determine if its execution has completed.
 *
 * <p> The <tt>Executors</tt> class provides factory methods for all of the
 * types of executors provided in <tt>java.util.concurrent</tt>.
 *
 * @since 1.5
 * @see Executors
 * @see FutureTask
 *
 * @spec JSR-166
 * @revised $Date: 2003/02/26 10:48:09 $
 * @editor $Author: jozart $
 */
public interface Executor {

    /**
     * Executes the given command at some time in the future.  The command
     * may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the <tt>Executor</tt> implementation.
     *
     * @param command the runnable task
     * @throws CannotExecuteException if command cannot be submitted for
     * execution
     */
    void execute(Runnable command);
}
