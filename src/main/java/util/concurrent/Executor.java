/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * An object that executes submitted tasks. This interface provides a
 * way of decoupling task submission from the mechanics of how each
 * task will be run, including details of thread use, scheduling, etc.
 *
 * <p>In the simplest case, an executor could run the submitted task
 * immediately in the caller's thread:
 *
 * <pre>
 * class DirectExecutor implements Executor {
 *     public void execute(Runnable r) {
 *         r.run();
 *     }
 * }</pre>
 *
 * However, tasks are typically executed in a different thread than
 * the caller's thread.  The example below shows a simple Executor
 * which spawns a new thread for each task, but most Executor
 * implementations will impose some sort of limitation on how and when
 * tasks are scheduled:
 *
 * <pre>
 * class ThreadPerTaskExecutor implements Executor {
 *     public void execute(Runnable r) {
 *         new Thread(r).start();
 *     }
 * }</pre>
 *
 * The Executor example below uses a second Executor as part of its
 * implementation, and acts as a gatekeeper, submitting tasks to the
 * underlying Executor sequentially, one at a time:
 *
 * <pre>
 * class SerialExecutor implements Executor {
 *     LinkedQueue tasks = new LinkedQueue<Runnable>();
 *     Executor executor;
 *     Runnable active;
 *
 *     SerialExecutor(Executor executor) {
 *         this.executor = executor;
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
 *             executor.execute(active);
 *         }
 *     }
 * }</pre>
 *
 * The <tt>Executor</tt> implementations provided in
 * <tt>java.util.concurrent</tt> implement <tt>ExecutorService</tt>,
 * which is a more extensive interface.  For more advanced users, the
 * <tt>ThreadPoolExecutor</tt> class provides a powerful, extensible
 * thread pool implementation. The <tt>Executors</tt> class provides
 * convenient factory methods for these executors.
 *
 * @fixme Move the following to package documentation.
 *
 * <p> An Executor can be thought of as a framework for executing
 * Runnables.  The Executor manages queueing and scheduling of tasks,
 * and creation and teardown of threads.  Depending on which concrete
 * Executor class is being used, tasks may execute in a newly created
 * thread, an existing task-execution thread, or the thread calling
 * execute(), and may execute sequentially or concurrently.
 *
 * <p> Several concrete implementations of Executor are included in
 * java.util.concurrent, including ThreadPoolExecutor, a flexible
 * thread pool, SingleThreadedExecutor, which executes commands
 * sequentially in a background thread, and ScheduledExecutor, which
 * adds support for timed, delayed and periodic task execution.
 * Executor can be used in conjunction with FutureTask (which
 * implements Runnable) to asynchronously start a potentially
 * long-running computation and query the FutureTask to determine if
 * its execution has completed.
 *
 * <p> The <tt>Executors</tt> class provides factory methods for all
 * of the types of executors provided in
 * <tt>java.util.concurrent</tt>.
 *
 * @since 1.5
 * @see Executors
 * @see FutureTask
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/27 18:14:40 $
 * @editor $Author: dl $
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
