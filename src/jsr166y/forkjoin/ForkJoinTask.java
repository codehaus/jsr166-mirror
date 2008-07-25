/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import sun.misc.Unsafe;
import java.lang.reflect.*;

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
     * Disallow direct construction outside this package.
     */
    ForkJoinTask() {
    }

    /**
     * The exception thrown within compute method, or via cancellation.
     * Updated only via CAS, to arbitrate cancellations vs normal
     * exceptions.
     */
    volatile Throwable exception;

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
     * see any writes occurring before last volatile bookkeeping
     * operation, which all workers must eventually perform. And on
     * the issuing side, status is set after rechecking exception
     * field which prevents premature writes.
     *
     * The positive range could hold the pool index of the origin of
     * non-complete stolen tasks but isn't currently done.
     */
    int status;

    // within-package utilities

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
            ForkJoinWorkerThread.signalWork();
        return ex;
    }

    /**
     * setDone for exceptional termination
     */
    final Throwable setDoneExceptionally(Throwable rex) {
        casException(null, rex);
        ForkJoinWorkerThread.signalWork();
        return exception;
    }

    /**
     * Version of setDoneExceptionally that screens argument
     */
    final void checkedSetDoneExceptionally(Throwable rex) {
        if (!(rex instanceof RuntimeException) && !(rex instanceof Error))
            throw new IllegalArgumentException(rex);
        setDoneExceptionally(rex);
    }

    /**
     * Returns result or throws exception.
     * Only call when isDone known to be true.
     */
    final V reportAsForkJoinResult() {
        Throwable ex = getException();
        if (ex != null)
            rethrowException(ex);
        return rawResult();
    }

    /**
     * Returns result or throws exception using j.u.c.Future conventions
     * Only call when isDone known to be true.
     */
    final V reportAsFutureResult() throws ExecutionException {
        Throwable ex = getException();
        if (ex != null) {
            if (ex instanceof CancellationException)
                throw (CancellationException)ex;
            else
                throw new ExecutionException(ex);
        }
        return rawResult();
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
     * @throws Throwable (a RuntimeException, Error, or unchecked
     * exception) if the underlying computation did so.
     */
    public final V join() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doJoinTask(this);
    }

    /**
     * Equivalent in effect to the sequence <tt>fork(); join();</tt>
     * but may be more efficient.
     * @throws Throwable (a RuntimeException, Error, or unchecked
     * exception) if the underlying computation did so.
     * @return the computed result
     */
    public abstract V forkJoin();

    /**
     * Returns true if the computation performed by this task has
     * completed (or has been cancelled). This method will eventually
     * return true upon task completion, but return values are not
     * otherwise guaranteed to be uniform across observers.  Thus,
     * when task <tt>t</tt> finishes, concurrent invocations of
     * <tt>t.isDone()</tt> from other tasks or threads on may
     * transiently return different results, but on repeated
     * invocation, will eventually agree that <tt>t.isDone()</tt>.
     * @return true if this computation has completed
     */
    public final boolean isDone() {
        return exception != null || status < 0;
    }

    /**
     * Returns true if this task was cancelled.
     * @return true if this task was cancelled
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
     * null if this task is not known to have been completed.  This
     * method is designed to aid debugging, as well as to support
     * extensions. Its use in any other context is strongly
     * discouraged.
     * @return the result, or null if not completed.
     */
    public abstract V rawResult();

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
     * Joins this task, without returning its result or throwing an
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
     * operations. This method may be invoked only from within other
     * ForkJoinTask computations. This method may be used to provide
     * results, mainly for asynchronous tasks. Upon invocation, the
     * task itself, if running, must exit (return) in a small finite
     * number of steps.
     * @param result the result to return
     */
    public abstract void finish(V result);

    /**
     * Completes this task abnormally, and if not already aborted or
     * cancelled, causes it to throw the given exception upon
     * <tt>join</tt> and related operations. Upon invocation, the task
     * itself, if running, must exit (return) in a small finite number
     * of steps.
     * @param ex the unchecked exception (RuntimeException or Error)
     * to throw.
     * @throws IllegalArgumentException if the given exception is not
     * a RuntimeException or Error.
     */
    public abstract void finishExceptionally(Throwable ex);

    /**
     * Forks both tasks and returns when <tt>isDone</tt> holds for
     * both.. If both tasks encounter exceptions, only one of them
     * (arbitrarily chosen) is thrown from this method.  You can check
     * individual status using method <tt>getException</tt>.  This
     * method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result in
     * exceptions or errors including ClassCastException.
     * @throws NullPointerException if t1 or t2 are null.
     */
    public static void forkJoin(ForkJoinTask<Void> t1, ForkJoinTask<Void> t2) {
        ((ForkJoinWorkerThread)(Thread.currentThread())).doForkJoin(t1, t2);
    }

    /**
     * Forks all tasks in the array, returning when <tt>isDone</tt>
     * holds for all of them. If any task encounters an exception,
     * others are cancelled.  This method may be invoked only from
     * within other ForkJoinTask computations. Attempts to invoke in
     * other contexts result in exceptions or errors including
     * ClassCastException.
     * @throws NullPointerException if array or any element of array are null
     */
    public static void forkJoin(ForkJoinTask<Void>[] tasks) {
        int last = tasks.length - 1;
        Throwable ex = null;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<Void> t = tasks[i];
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            else if (ex != null)
                t.cancel();
            else if (i != 0)
                t.fork();
            else
                ex = t.exec();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<Void> t = tasks[i];
            if (t != null) {
                boolean pop = ForkJoinWorkerThread.removeIfNextLocalTask(t);
                if (ex != null)
                    t.cancel();
                else if (!pop)
                    ex = t.quietlyJoin();
                else
                    ex = t.exec();
            }
        }
        if (ex != null)
            rethrowException(ex);
    }

    /**
     * Forks all tasks in the list, returning when <tt>isDone</tt>
     * holds for all of them. If any task encounters an exception,
     * others are cancelled.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     * @throws NullPointerException if list or any element of list are null.
     */
    public static void forkJoin(List<? extends ForkJoinTask<Void>> tasks) {
        int last = tasks.size() - 1;
        Throwable ex = null;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<Void> t = tasks.get(i);
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            else if (i != 0)
                t.fork();
            else if (ex != null)
                t.cancel();
            else
                ex = t.exec();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<Void> t = tasks.get(i);
            if (t != null) {
                boolean pop = ForkJoinWorkerThread.removeIfNextLocalTask(t);
                if (ex != null)
                    t.cancel();
                else if (!pop)
                    ex = t.quietlyJoin();
                else
                    ex = t.exec();
            }
        }
        if (ex != null)
            rethrowException(ex);
    }

    // Temporary Unsafe mechanics for preliminary release

    private static Unsafe getUnsafe() {
        try {
            if (ForkJoinTask.class.getClassLoader() != null) {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return (Unsafe)f.get(null);
            }
            else
                return Unsafe.getUnsafe();
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize intrinsics",
                                       e);
        }
    }

    private static final Unsafe _unsafe = getUnsafe();

    /**
     * Workaround for not being able to rethrow unchecked exceptions.
     */
    static final void rethrowException(Throwable ex) {
        if (ex != null)
            _unsafe.throwException(ex);
    }

    private static final long exceptionOffset;

    static {
        try {
            exceptionOffset = _unsafe.objectFieldOffset
                (ForkJoinTask.class.getDeclaredField("exception"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    final boolean casException(Throwable cmp, Throwable val) {
        return _unsafe.compareAndSwapObject(this, exceptionOffset, cmp, val);
    }

}
