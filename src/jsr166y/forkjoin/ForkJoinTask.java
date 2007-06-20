/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Abstract base class for tasks that run within a ForkJoinPool.  A
 * ForkJoinTask is a thread-like entity that is much lighter weight
 * than a normal thread.  Huge numbers of tasks and subtasks may be
 * hosted by a small number of actual threads in a ForkJoinPool,
 * at the price of some usage limitations.
 *
 * <p> The <tt>ForkJoinTask</tt> class is not directly subclassable
 * outside of this package. Instead, you can subclass one of the
 * supplied abstract classes that support the various styles of
 * fork/join processing.  Normally, a concrete ForkJoinTask subclass
 * declares fields comprising its parameters, established in a
 * constructor, and then defines a <tt>protected compute</tt> method
 * that somehow uses the control methods supplied by this base
 * class. While these methods have <tt>public</tt> access, most may
 * only be called from within other ForkJoinTasks. Attempts to invoke
 * them in other contexts result in exceptions or errors including
 * ClassCastException.  The only generally accessible methods are
 * those for cancellation and status checking. The only way to invoke
 * a "main" driver task is to submit it to a ForkJoinPool. Normally,
 * once started, this will in turn start other subtasks.  Nearly all
 * of these base support methods are <tt>final</tt> because their
 * implementations are intrinsically tied to the underlying
 * lightweight task scheduling framework, and so cannot in general be
 * overridden.
 *
 * <p> ForkJoinTasks play similar roles as <tt>Futures</tt> but
 * support a more limited range of use.  The "lightness" of
 * ForkJoinTasks is due to a set of restrictions (that are only
 * partially statically enforceable) reflecting their intended use as
 * purely computational tasks -- calculating pure functions or
 * operating on purely isolated objects.  The only coordination
 * mechanisms supported for ForkJoinTasks are <tt>fork</tt>, that
 * arranges asynchronous execution, and <tt>join</tt>, that doesn't
 * proceed until the task's result has been computed. (A simple form
 * of cancellation is also supported).  The computation defined in the
 * <tt>compute</tt> method should not in general perform any other
 * form of blocking synchronization, should not perform IO, and should
 * be independent of other tasks. Minor breaches of these
 * restrictions, for example using shared output streams, may be
 * tolerable in practice, but frequent use will result in poor
 * performance, and the potential to indefinitely stall if the number
 * of threads not waiting for external synchronization becomes
 * exhausted. This usage restriction is in part enforced by not
 * permitting checked exceptions to be thrown. However, ForkJoinTask
 * computations may encounter RuntimeExceptions, that are rethrown to
 * any callers attempting join them or get their values. On the other
 * hand, Errors and other unchecked Throwables are not trapped, and so
 * will usually cause ForkJoinPool threads to die and be handled using
 * the pool's UncaughtExceptionHandler.  You can also deal with Errors
 * arising within computations by catching and handling them before
 * returning from <tt>compute</tt>.
 *
 * <p> Note the conventions similar to those in class Thread:
 * <tt>static</tt> methods implicitly refer to the current execution
 * context, while non-static ones are specific to a given task.
 *
 */
public abstract class ForkJoinTask<V> {
    /*
     * The main implementations of most methods are provided by
     * ForkJoinPool.Worker, so internals are package protected.  This
     * class is mainly responsible for maintaining its exception and
     * status fields.
     */

    /**
     * The exception thrown within compute method, or via cancellation.
     * Updated only via CAS, to arbitrate cancellations vs normal
     * exceptions.  
     */
    volatile RuntimeException exception;

    /**
     * Status becoming negative when task completes normally.  When a
     * computation completes normally (i.e., not via exceptions or
     * cancellation), the "status" is set negative.  So a task is
     * considered completed if either the exception is non-null or
     * status is negative; and must always be checked in that
     * order. (When not complete, status bits can are used for other
     * purposes). This avoids requiring volatile reads and writes for
     * other fields. Even though according to the JMM, writes to
     * status (or other per-task fields) need not be immediately
     * visible to other threads, they must eventually be so here: They
     * will always be visible to any stealing thread, since these
     * reads will be fresh. In other situations, in the worst case of
     * not being seen earlier, since Worker threads eventually, with
     * probability 1 scan all other threads, any subsequent read
     * must see any writes occuring before last volatile bookkeeping
     * operation, which all workers must eventually perform. And on
     * the issuing side, "status" is asserted after rechecking
     * exception field after compute returns, which prevents premature
     * writes.
     */
    int status;

    static final int DONE   = (1 << 31);
    static final int STOLEN = (1 << 30);

    /**
     * Sets status to indicate this task is done, ensuring
     * that the write is correctly ordered
     * @return the current exception
     */
    final RuntimeException setDone() {
        RuntimeException ex = exception;
        status = DONE;
        return ex;
    }

    /**
     * Set status field to indicate task was stolen. Set only inside
     * Worker steal code. The bit disappears when task completes.
     */
    final void setStolen() {
        status |= STOLEN;
    }

    /**
     * Updater to enable CAS of exception field
     */
    static final
        AtomicReferenceFieldUpdater<ForkJoinTask, RuntimeException>
        exceptionUpdater = AtomicReferenceFieldUpdater.newUpdater
        (ForkJoinTask.class, RuntimeException.class, "exception");


    /**
     * CAS exception field from null to given exception
     */
    final boolean casException(RuntimeException rex) {
        return (exception == null && 
                exceptionUpdater.compareAndSet(this, null, rex));
    }

    /**
     * Runs this task unless already cancelled, and sets status.
     * @return exception thrown by compute (or via cancellation)
     */
    abstract RuntimeException exec();

    /**
     * Arranges to asynchronously execute this task, which will later
     * be directly or indirectly joined by the caller of this method.
     * While it is not necessarily enforced, it is a usage error to
     * fork a task more than once unless it has completed and been
     * reinitialized.  This method may be invoked only from within
     * other ForkJoinTask computations. Attempts to invoke in other
     * contexts result in exceptions or errors including
     * ClassCastException.
     */
    public final void fork() {
        ((ForkJoinPool.Worker)(Thread.currentThread())).pushTask(this);
    }

    /**
     * Returns the result of the computation when it is ready.
     * Monitoring note: Callers of this method need not block, but may
     * instead assist in performing computations that may directly or
     * indirectly cause the result to be ready.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     *
     * @return the computed result
     * @throws RuntimeException if the underlying computation did so.
     */
    public final V join() {
        return ((ForkJoinPool.Worker)(Thread.currentThread())).joinTask(this);
    }

    /**
     * Equivalent in effect to the sequence <tt>fork(); join();</tt>
     * but may be more efficient.
     */
    public abstract V invoke();

    /**
     * Returns true if the computation performed by this task has
     * completed (or has been cancelled).
     */
    public final boolean isDone() {
        return exception != null || status < 0;
    }

    /**
     * Returns true if this task was cancelled.
     */
    public final boolean isCancelled() {
        return exception instanceof CancellationException;
    }

    /**
     * Asserts that the results of this task's computation will not be
     * used. If a cancellation occurs before this task is processed,
     * then its <tt>compute</tt> method will not be executed,
     * <tt>isCancelled</tt> will report true, and <tt>join</tt> will
     * result in a CancellationException being thrown. Otherwise,
     * there are no guarantees about whether <tt>isCancelled</tt> will
     * report true, whether <tt>join</tt> will return normally or via
     * an exception, or whether these behaviors will remain
     * consistent upon repeated invocation.
     */
    public final void cancel() {
        casException(new CancellationException());
        if (this instanceof Future<?>) // propagate for external submissions
            ((Future<?>)this).cancel(false);
    }

    /**
     * Returns the exception thrown by method <tt>compute</tt>, or a
     * CancellationException if cancelled, or null if none or if the
     * method has not yet completed.
     * @return the exception, or null if none
     */
    public final RuntimeException getException() {
        return exception;
    }

    /**
     * Returns the result that would be returned by <tt>join</tt>, or
     * null if this task has not yet completed.  This method is
     * designed primarily to aid debugging, as well as to support
     * extensions.
     * @return the result, or null if not completed.
     */
    public abstract V getResult();

    /**
     * Resets the internal bookkeeping state of this task. This method
     * allows repeated reuse of this task, but only if reuse occurs
     * when either this task has either never been forked or has
     * completed. Effects under any other usage conditions are not
     * guaranteed, and are almost surely wrong. This method may be
     * useful when repeatedly executing pre-constructed trees of
     * subtasks.
     */
    public void reinitialize() {
        status = 0;
        if (exception != null)
            exception = null;
    }

    /**
     * Returns the pool hosting the current task execution.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     * @return the pool
     */
    public static ForkJoinPool getPool() {
        return ((ForkJoinPool.Worker)(Thread.currentThread())).getPool();
    }

    /**
     * Returns the number of tasks waiting to be run by the thread
     * currently performing task execution. This value may be useful
     * for heuristic decisions about whether to fork other tasks.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     * @return the number of tasks
     */
    public static int getLocalQueueSize() {
        return ((ForkJoinPool.Worker)(Thread.currentThread())).getQueueSize();
    }

    /**
     * Helps this program complete by processing a ready task, if one
     * is available.  This method may be useful when several tasks are
     * forked, and only one of them must be joined, as in: 
     * <pre>
     *   while (!t1.isDone() &amp;&amp; !t2.isDone()) 
     *     help();
     * </pre>. 
     * Similarly, you can help process tasks until a computation
     * completes via 
     * <pre>
     *   while(help() || !getPool().isQuiescent()) 
     *      ;
     * </pre>
     *
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result in
     * exceptions or errors including ClassCastException.
     * @return true if a task was run; a false return indicates
     * that no ready task was available.
     */
    public final boolean help() {
        return ((ForkJoinPool.Worker)(Thread.currentThread())).help();
    }

    /**
     * Returns true if this task was stolen from some other worker in the 
     * pool and has not yet completed. This method should be called
     * only by the thread currently executing this task. The results
     * of calling this method in any other context are undefined.
     * @return true is this task is stolen
     */
    public final boolean isStolen() {
        return (status & STOLEN) != 0;
    }

    /**
     * Returns the index number of the current worker in its pool.
     * The return value is in the range
     * <tt>[0..getPool().getPoolSize()-1</tt>.  This method may be
     * useful for applications that track status or collect results
     * per-worker rather than per-task.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     * @return the index number.
     */
    public static int getWorkerIndex() {
        return ((ForkJoinPool.Worker)(Thread.currentThread())).getIndex();
    }

    /**
     * Submit the given task to the pool hosting the current task
     * execution.
     * @param task the task
     * @return a Future that can be used to get the task's results.
     */ 
    public static <T> Future<T> submit(ForkJoinTask<T> task) {
        return getPool().submit(task);
    }

    /**
     * Joins this task, ignoring any result or exception. This method
     * may be useful when processing collections of tasks when some
     * have been cancelled or otherwise known to have aborted. You can
     * still inspect the status of an ignored task using for example,
     * <tt>getException</tt>.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     */
    public final void joinAndIgnore() {
        if (!isDone())
            ((ForkJoinPool.Worker)(Thread.currentThread())).
                helpWhileJoining(this);
    }

    /**
     * Cancels all tasks that are currently held in any worker
     * thread's local queues. This method may be useful for bulk
     * cancellation of a set of tasks that may terminate when any one
     * of them finds a solution. However, this applies only if you are
     * sure that no other tasks have been submitted to, or are active
     * in, the pool. See {@link
     * ForkJoinPool#setMaximumActiveSubmissionCount}.
     */
    public final void cancelAllQueuedTasks() {
        ((ForkJoinPool.Worker)(Thread.currentThread())).cancelCurrentTasks();
    }

}
