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
 * permitting checked exceptions to be thrown. However, computations
 * may still encounter unchecked exceptions, that are rethrown to
 * callers attempting join them. These exceptions may additionally
 * include RejectedExecutionExceptions stemming from internal resource
 * exhaustion such as failure to allocate internal task queues.
 *
 */
public abstract class ForkJoinTask<V> {
    /*
     * The main implementations of execution methods are provided by
     * ForkJoinWorkerThread, so internals are package protected.  This
     * class is mainly responsible for maintaining its exception and
     * status fields.
     */

    /**
     * Workaround for not being able to rethrow unchecked exceptions.
     */
    static final class Rethrower<T extends Throwable> {
        void rethrow(Throwable ex) throws T { 
            if (ex != null) {
                if (ex instanceof RuntimeException)
                    throw (RuntimeException)ex;
                else if (ex instanceof Error)
                    throw (Error)ex;
                else // exploit erasure
                    throw (T) ex; 
            }
        }
    }

    /**
     * Global rethrower
     */
    static final Rethrower<Error> rethrower = new Rethrower<Error>();

    static void rethrowException(Throwable ex) {
        rethrower.rethrow(ex);
    }


    /**
     * The exception thrown within compute method, or via cancellation.
     * Updated only via CAS, to arbitrate cancellations vs normal
     * exceptions.  
     */
    volatile Throwable exception;

    /**
     * Updater to enable CAS of exception field
     */
    static final
        AtomicReferenceFieldUpdater<ForkJoinTask, Throwable>
        exceptionUpdater = AtomicReferenceFieldUpdater.newUpdater
        (ForkJoinTask.class, Throwable.class, "exception");

    /**
     * Status, taking values:
     *    0: initial
     *   -1: completed 
     *   >0: stolen (or external)
     *
     * Status is set negative when task completes normally.  A task is
     * considered completed if the exception is non-null or status is
     * negative (or both); and must always be checked accordingly. The
     * status field need not be volatile so long as it is written in
     * correct order.  Even though according to the JMM, writes to
     * status (or other per-task fields) need not be immediately
     * visible to other threads, they must eventually be so here: They
     * will always be visible to any stealing thread, since these
     * reads will be fresh. In other situations, in the worst case of
     * not being seen earlier, since Worker threads eventually scan
     * all other threads looking for work, any subsequent read must
     * see any writes occuring before last volatile bookkeeping
     * operation, which all workers must eventually perform. And on
     * the issuing side, status is set after rechecking exception
     * field which prevents premature writes.
     *
     * The positive range could hold the pool index of the origin of
     * non-complete stolen tasks but isn't currently done.
     */
    int status;

    /**
     * Sets status to stolen (or an external submission). Called only
     * by task-stealing and submission code.
     */
    final void setStolen() {
        status = 1;
    }

    /**
     * Sets status to indicate this task is done.
     */
    final Throwable setDone() {
        Throwable ex = exception;
        int s = status;
        status = -1;
        if (s != 0) // conservatively signal on any nonzero
            ForkJoinWorkerThread.signalTaskCompletion();
        return ex;
    }

    /**
     * setDone for exceptional termination
     */
    final Throwable setDoneExceptionally(Throwable rex) {
        exceptionUpdater.compareAndSet(this, null, rex);
        ForkJoinWorkerThread.signalTaskCompletion();
        return exception;
    }

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
        ((ForkJoinWorkerThread)(Thread.currentThread())).pushTask(this);
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
     * @throws Throwable if the underlying computation did so.
     */
    public final V join() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doJoinTask(this);
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
     * an exception, or whether these behaviors will remain consistent
     * upon repeated invocation. This method may be overridden in
     * subclasses, but if so, must still ensure that these minimal
     * properties hold.
     */
    public void cancel() {
        setDoneExceptionally(new CancellationException());
    }

    /**
     * Returns the exception thrown by method <tt>compute</tt>, or a
     * CancellationException if cancelled, or null if none or if the
     * method has not yet completed.
     * @return the exception, or null if none
     */
    public final Throwable getException() {
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
     * Resets the internal bookkeeping state of this task, allowing a
     * subsequent <tt>fork</tt>. This method allows repeated reuse of
     * this task, but only if reuse occurs when this task has either
     * never been forked, or has been forked, then completed and all
     * outstanding joins of this task have also completed. Effects
     * under any other usage conditions are not guaranteed, and are
     * almost surely wrong. This method may be useful when repeatedly
     * executing pre-constructed trees of subtasks.
     */
    public void reinitialize() {
        if (exception != null)
            exception = null;
        status = 0;
    }

    /**
     * Returns true if this task was stolen from some other worker in the 
     * pool and has not yet completed. This method should be called
     * only by the thread currently executing this task. The results
     * of calling this method in any other context are undefined.
     * @return true is this task is stolen
     */
    public final boolean isStolen() {
        return status > 0;
    }

    /**
     * Joins this task, without returning its result or throwing
     * exception, but returning the exception that <tt>join</tt> would
     * throw. This method may be useful when processing collections of
     * tasks when some have been cancelled or otherwise known to have
     * aborted. This method may be invoked only from within other
     * ForkJoinTask computations. Attempts to invoke in other contexts
     * result in exceptions or errors including ClassCastException.
     * @return the exception that would be thrown by <tt>join</tt>, or
     * null if this task completed normally.
     */
    public final Throwable quietlyJoin() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doQuietlyJoinTask(this);
    }

    /**
     * Immediately commences execution of this task by the current
     * worker thread unless already cancelled, returning any exception
     * thrown by its <tt>compute</tt> method.
     * @return exception thrown by compute (or via cancellation), or
     * null if none
     */
    public abstract Throwable exec();

    /**
     * Completes this task, and if not already aborted or cancelled,
     * returning the given result upon <tt>join</tt> and related
     * operations. 
     * @param result the result to return
     */
    public abstract void finish(V result);

    /**
     * Completes this task abnormally, and if not already aborted or
     * cancelled, causes it to throw the given exception upon
     * <tt>join</tt> and related operations.
     * @param ex the exception to throw. While not necessarily
     * statically enforced, this must be a RuntimeException or Error.
     */
    public abstract void finishExceptionally(Throwable ex);

}
