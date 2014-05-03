/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.LockSupport;

/**
 * A {@link Future} that may be explicitly completed (setting its
 * value and status), and may be used as a {@link CompletionStage},
 * supporting dependent functions and actions that trigger upon its
 * completion.
 *
 * <p>When two or more threads attempt to
 * {@link #complete complete},
 * {@link #completeExceptionally completeExceptionally}, or
 * {@link #cancel cancel}
 * a CompletableFuture, only one of them succeeds.
 *
 * <p>In addition to these and related methods for directly
 * manipulating status and results, CompletableFuture implements
 * interface {@link CompletionStage} with the following policies: <ul>
 *
 * <li>Actions supplied for dependent completions of
 * <em>non-async</em> methods may be performed by the thread that
 * completes the current CompletableFuture, or by any other caller of
 * a completion method.</li>
 *
 * <li>All <em>async</em> methods without an explicit Executor
 * argument are performed using the {@link ForkJoinPool#commonPool()}
 * (unless it does not support a parallelism level of at least two, in
 * which case, a new Thread is used). To simplify monitoring,
 * debugging, and tracking, all generated asynchronous tasks are
 * instances of the marker interface {@link
 * AsynchronousCompletionTask}. </li>
 *
 * <li>All CompletionStage methods are implemented independently of
 * other public methods, so the behavior of one method is not impacted
 * by overrides of others in subclasses.  </li> </ul>
 *
 * <p>CompletableFuture also implements {@link Future} with the following
 * policies: <ul>
 *
 * <li>Since (unlike {@link FutureTask}) this class has no direct
 * control over the computation that causes it to be completed,
 * cancellation is treated as just another form of exceptional
 * completion.  Method {@link #cancel cancel} has the same effect as
 * {@code completeExceptionally(new CancellationException())}. Method
 * {@link #isCompletedExceptionally} can be used to determine if a
 * CompletableFuture completed in any exceptional fashion.</li>
 *
 * <li>In case of exceptional completion with a CompletionException,
 * methods {@link #get()} and {@link #get(long, TimeUnit)} throw an
 * {@link ExecutionException} with the same cause as held in the
 * corresponding CompletionException.  To simplify usage in most
 * contexts, this class also defines methods {@link #join()} and
 * {@link #getNow} that instead throw the CompletionException directly
 * in these cases.</li> </ul>
 *
 * @author Doug Lea
 * @since 1.8
 */
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {

    /*
     * Overview:
     *
     * A CompletableFuture may have dependent completion actions,
     * collected in a linked stack. It atomically completes by CASing
     * a result field, and then pops off and runs those actions. This
     * applies across normal vs exceptional outcomes, sync vs async
     * actions, binary triggers, and various forms of completions.
     *
     * Non-nullness of field result (set via CAS) indicates done.  An
     * AltResult is used to box null as a result, as well as to hold
     * exceptions.  Using a single field makes completion simple to
     * detect and trigger.  Encoding and decoding is straightforward
     * but adds vertical sprawl. One minor simplification relies on
     * the (static) NIL (to box null results) being the only AltResult
     * with a null exception field, so we don't usually need explicit
     * comparisons with NIL.  Exception propagation mechanics
     * surrounding decoding rely on unchecked casts of decoded results
     * really being unchecked, and user type errors being caught at
     * point of use, as is currently the case in Java. These are
     * highlighted by using SuppressWarnings annotated temporaries.
     *
     * Dependent actions are represented by Completion objects linked
     * as Treiber stacks headed by field completions. There are four
     * kinds of Completions: single-source (UniCompletion), two-source
     * (BiCompletion), shared (CoBiCompletion, used by the second
     * source of a BiCompletion), and Signallers that unblock waiters.
     *
     * The same patterns of methods and classes are used for each form
     * of Completion (apply, combine, etc), and are written in a
     * similar style.  For each form X there is, when applicable:
     *
     * * Method nowX (for example nowApply) that immediately executes
     *   a supplied function and sets result
     * * Class AsyncX class (for example AsyncApply) that calls nowX
     *   from another task,
     * * Class DelayedX (for example DelayedApply) that holds
     *   arguments and calls nowX when ready.
     *
     * For each public CompletionStage method M* (for example
     * thenApply{Async}), there is a method doM (for example
     * doThenApply) that creates and/or invokes the appropriate form.
     * Each deals with three cases that can arise when adding a
     * dependent completion to CompletableFuture f:
     *
     * * f is already complete, so the dependent action is run
     *   immediately, via  a "now" method, which, if async,
     *   starts the action in a new task.
     * * f is not complete, so a Completion action is created and
     *   pushed to f's completions. It is triggered via
     *   f.postComplete when f completes.
     * * f is not complete, but completes while adding the completion
     *   action, so we try to trigger it upon adding (see method
     *   unipush and derivatives) to cover races.
     *
     * Methods with two sources (for example thenCombine) must deal
     * with races across both while pushing actions.  The second
     * completion is an CoBiCompletion pointing to the first, shared
     * to ensure that at most one claims and performs the action.  The
     * multiple-arity method allOf does this pairwise to form a tree
     * of completions. (Method anyOf just uses a depth-one Or tree.)
     *
     * Upon setting results, method postComplete is called unless
     * the target is guaranteed not to be observable (i.e., not yet
     * returned or linked). Multiple threads can call postComplete,
     * which atomically pops each dependent action, and tries to
     * trigger it via method tryAct. Any such action must be performed
     * only once, even if called from several threads, so Completions
     * maintain status via CAS, and on success run one of the "now"
     * methods.  Triggering can propagate recursively, so tryAct
     * returns a completed dependent (if one exists) for further
     * processing by its caller.
     *
     * Blocking methods get() and join() rely on Signaller Completions
     * that wake up waiting threads.  The mechanics are similar to
     * Treiber stack wait-nodes used in FutureTask, Phaser, and
     * SynchronousQueue. See their internal documentation for
     * algorithmic details.
     *
     * Without precautions, CompletableFutures would be prone to
     * garbage accumulation as chains of completions build up, each
     * pointing back to its sources. So we detach (null out) most
     * Completion fields as soon as possible.  To support this,
     * internal methods check for and harmlessly ignore null arguments
     * that may have been obtained during races with threads nulling
     * out fields. (Some of these checked cases cannot currently
     * happen.)  Fields of Async classes can be but currently are not
     * fully detached, because they do not in general form cycles.
     */

    volatile Object result;             // Either the result or boxed AltResult
    volatile Completion<?> completions; // Treiber stack of dependent actions

    final boolean internalComplete(Object r) { // CAS from null to r
        return UNSAFE.compareAndSwapObject(this, RESULT, null, r);
    }

    final boolean casCompletions(Completion<?> cmp, Completion<?> val) {
        return UNSAFE.compareAndSwapObject(this, COMPLETIONS, cmp, val);
    }

    /* ------------- Encoding and decoding outcomes -------------- */

    static final class AltResult { // See above
        final Throwable ex;        // null only for NIL
        AltResult(Throwable x) { this.ex = x; }
    }

    static final AltResult NIL = new AltResult(null);

    /**
     * Returns the encoding of the given (non-null) exception as a
     * wrapped CompletionException unless it is one already.
     */
    static AltResult altThrowable(Throwable x) {
        return new AltResult((x instanceof CompletionException) ? x :
                             new CompletionException(x));
    }

    /**
     * Returns the encoding of the given arguments: if the exception
     * is non-null, encodes as altThrowable.  Otherwise uses the given
     * value, boxed as NIL if null.
     */
    static Object encodeOutcome(Object v, Throwable x) {
        return (x != null) ? altThrowable(x) : (v == null) ? NIL : v;
    }

    /**
     * Decodes outcome to return result or throw unchecked exception.
     */
    private static <T> T reportJoin(Object r) {
        if (r instanceof AltResult) {
            Throwable x;
            if ((x = ((AltResult)r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException)x;
            if (x instanceof CompletionException)
                throw (CompletionException)x;
            throw new CompletionException(x);
        }
        @SuppressWarnings("unchecked") T tr = (T) r;
        return tr;
    }

    /**
     * Reports result using Future.get conventions.
     */
    private static <T> T reportGet(Object r)
        throws InterruptedException, ExecutionException {
        if (r == null) // by convention below, null means interrupted
            throw new InterruptedException();
        if (r instanceof AltResult) {
            Throwable x, cause;
            if ((x = ((AltResult)r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException)x;
            if ((x instanceof CompletionException) &&
                (cause = x.getCause()) != null)
                x = cause;
            throw new ExecutionException(x);
        }
        @SuppressWarnings("unchecked") T tr = (T) r;
        return tr;
    }

    /* ------------- Async Tasks -------------- */

    /**
     * A marker interface identifying asynchronous tasks produced by
     * {@code async} methods. This may be useful for monitoring,
     * debugging, and tracking asynchronous activities.
     *
     * @since 1.8
     */
    public static interface AsynchronousCompletionTask {
    }

    /**
     * Base class for tasks that can act as either FJ or plain
     * Runnables. Abstract method compute calls an associated "now"
     * method.  Method exec calls compute if its CompletableFuture is
     * not already done, and runs completions if done. Fields are not
     * in general final and can be nulled out after use (but most
     * currently are not).  Classes include serialVersionUIDs even
     * though they are currently never serialized.
     */
    abstract static class Async<T> extends ForkJoinTask<Void>
        implements Runnable, AsynchronousCompletionTask {
        CompletableFuture<T> dep; // the CompletableFuture to trigger
        Async(CompletableFuture<T> dep) { this.dep = dep; }

        abstract void compute(); // call the associated "now" method

        public final boolean exec() {
            CompletableFuture<T> d;
            if ((d = dep) != null) {
                if (d.result == null) // suppress if cancelled
                    compute();
                if (d.result != null)
                    d.postComplete();
                dep = null; // detach
            }
            return true;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final void run() { exec(); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Default executor -- ForkJoinPool.commonPool() unless it cannot
     * support parallelism.
     */
    static final Executor asyncPool =
        (ForkJoinPool.getCommonPoolParallelism() > 1) ?
        ForkJoinPool.commonPool() : new ThreadPerTaskExecutor();

    /** Fallback if ForkJoinPool.commonPool() cannot support parallelism */
    static final class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) { new Thread(r).start(); }
    }

    /**
     * Null-checks user executor argument, and translates uses of
     * commonPool to asyncPool in case parallelism disabled.
     */
    static Executor screenExecutor(Executor e) {
        if (e == null) throw new NullPointerException();
        return (e == ForkJoinPool.commonPool()) ? asyncPool : e;
    }

    /* ------------- Completions -------------- */

    abstract static class Completion<T> { // See above
        volatile Completion<?> next;      // Treiber stack link

        /**
         * Performs completion action if enabled, returning a
         * completed dependent CompletableFuture, if one exists.
         */
        abstract CompletableFuture<?> tryAct();
    }

    /**
     * Triggers all reachable enabled dependents.  Call only when
     * known to be done.
     */
    final void postComplete() {
        /*
         * On each step, variable f holds current completions to pop
         * and run.  It is extended along only one path at a time,
         * pushing others to avoid StackOverflowErrors on recursion.
         */
        CompletableFuture<?> f = this; Completion<?> h;
        while ((h = f.completions) != null ||
               (f != this && (h = (f = this).completions) != null)) {
            CompletableFuture<?> d; Completion<?> t;
            if (f.casCompletions(h, t = h.next)) {
                if (t != null) {
                    if (f != this) {  // push
                        do {} while (!casCompletions(h.next = completions, h));
                        continue;
                    }
                    h.next = null;    // detach
                }
                f = (d = h.tryAct()) == null ? this : d;
            }
        }
    }

    /* ------------- One-source Completions -------------- */

    /**
     * A Completion with a source and dependent.  The "dep" field acts
     * as a claim, nulled out to disable further attempts to
     * trigger. Fields can only be observed by other threads upon
     * successful push; and should be nulled out after claim.
     */
    abstract static class UniCompletion<T> extends Completion<T> {
        Executor async;                    // executor to use (null if none)
        CompletableFuture<T> dep;          // the dependent to complete
        CompletableFuture<?> src;          // source of value for tryAct

        UniCompletion(Executor async, CompletableFuture<T> dep,
                      CompletableFuture<?> src) {
            this.async = async; this.dep = dep; this.src = src;
        }

        /** Tries to claim completion action by CASing dep to null */
        final boolean claim(CompletableFuture<T> d) {
            return UNSAFE.compareAndSwapObject(this, DEP, d, null);
        }

        private static final sun.misc.Unsafe UNSAFE;
        private static final long DEP;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = UniCompletion.class;
                DEP = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("dep"));
            } catch (Exception x) {
                throw new Error(x);
            }
        }
    }

    /** Pushes c on to completions, and triggers c if done. */
    private void unipush(UniCompletion<?> c) {
        if (c != null) {
            CompletableFuture<?> d;
            while (result == null && !casCompletions(c.next = completions, c))
                c.next = null;            // clear on CAS failure
            if ((d = c.tryAct()) != null) // cover races
                d.postComplete();
            if (result != null)           // clean stack
                postComplete();
        }
    }

    // Immediate, async, delayed, and routing support for Function/apply

    static <T,U> void nowApply(Executor e, CompletableFuture<U> d, Object r,
                               Function<? super T,? extends U> f) {
        if (d != null && f != null) {
            T t; U u; Throwable x;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            if (x == null) {
                try {
                    if (e != null) {
                        e.execute(new AsyncApply<T,U>(d, t, f));
                        return;
                    }
                    u = f.apply(t);
                } catch (Throwable ex) {
                    x = ex;
                    u = null;
                }
            }
            else
                u = null;
            d.internalComplete(encodeOutcome(u, x));
        }
    }

    static final class AsyncApply<T,U> extends Async<U> {
        T arg;  Function<? super T,? extends U> fn;
        AsyncApply(CompletableFuture<U> dep, T arg,
                   Function<? super T,? extends U> fn) {
            super(dep); this.arg = arg; this.fn = fn;
        }
        final void compute() { nowApply(null, dep, arg, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class DelayedApply<T,U> extends UniCompletion<U> {
        Function<? super T,? extends U> fn;
        DelayedApply(Executor async, CompletableFuture<U> dep,
                     CompletableFuture<?> src,
                     Function<? super T,? extends U> fn) {
            super(async, dep, src); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<U> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowApply(async, d, r, fn);
                src = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private <U> CompletableFuture<U> doThenApply(
        Function<? super T,? extends U> fn, Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> d = new CompletableFuture<U>();
        Object r = result;
        if (r == null)
            unipush(new DelayedApply<T,U>(e, d, this, fn));
        else
            nowApply(e, d, r, fn);
        return d;
    }

    // Consumer/accept

    static <T,U> void nowAccept(Executor e, CompletableFuture<U> d,
                                Object r, Consumer<? super T> f) {
        if (d != null && f != null) {
            T t; Throwable x;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            if (x == null) {
                try {
                    if (e != null) {
                        e.execute(new AsyncAccept<T,U>(d, t, f));
                        return;
                    }
                    f.accept(t);
                } catch (Throwable ex) {
                    x = ex;
                }
            }
            d.internalComplete(encodeOutcome(null, x));
        }
    }

    static final class AsyncAccept<T,U> extends Async<U> {
        T arg; Consumer<? super T> fn;
        AsyncAccept(CompletableFuture<U> dep, T arg,
                    Consumer<? super T> fn) {
            super(dep); this.arg = arg; this.fn = fn;
        }
        final void compute() { nowAccept(null, dep, arg, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class DelayedAccept<T> extends UniCompletion<Void> {
        Consumer<? super T> fn;
        DelayedAccept(Executor async, CompletableFuture<Void> dep,
                      CompletableFuture<?> src, Consumer<? super T> fn) {
            super(async, dep, src); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<Void> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowAccept(async, d, r, fn);
                src = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private CompletableFuture<Void> doThenAccept(Consumer<? super T> fn,
                                                 Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        Object r = result;
        if (r == null)
            unipush(new DelayedAccept<T>(e, d, this, fn));
        else
            nowAccept(e, d, r, fn);
        return d;
    }

    // Runnable/run

    static <T> void nowRun(Executor e, CompletableFuture<T> d, Object r,
                           Runnable f) {
        if (d != null && f != null) {
            Throwable x = (r instanceof AltResult) ? ((AltResult)r).ex : null;
            if (x == null) {
                try {
                    if (e != null) {
                        e.execute(new AsyncRun<T>(d, f));
                        return;
                    }
                    f.run();
                } catch (Throwable ex) {
                    x = ex;
                }
            }
            d.internalComplete(encodeOutcome(null, x));
        }
    }

    static final class AsyncRun<T> extends Async<T> {
        Runnable fn;
        AsyncRun(CompletableFuture<T> dep, Runnable fn) {
            super(dep); this.fn = fn;
        }
        final void compute() { nowRun(null, dep, null, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class DelayedRun extends UniCompletion<Void> {
        Runnable fn;
        DelayedRun(Executor async, CompletableFuture<Void> dep,
                   CompletableFuture<?> src, Runnable fn) {
            super(async, dep, src); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<Void> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowRun(async, d, r, fn);
                src = null; fn = null; // clear refs
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private CompletableFuture<Void> doThenRun(Runnable fn, Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        Object r = result;
        if (r == null)
            unipush(new DelayedRun(e, d, this, fn));
        else
            nowRun(e, d, r, fn);
        return d;
    }

    // Supplier/get

    static <T> void nowSupply(CompletableFuture<T> d, Supplier<T> f) {
        if (d != null && f != null) {
            T t; Throwable x;
            try {
                t = f.get();
                x = null;
            } catch (Throwable ex) {
                x = ex;
                t = null;
            }
            d.internalComplete(encodeOutcome(t, x));
        }
    }

    static final class AsyncSupply<T> extends Async<T> {
        Supplier<T> fn;
        AsyncSupply(CompletableFuture<T> dep, Supplier<T> fn) {
            super(dep); this.fn = fn;
        }
        final void compute() { nowSupply(dep, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    // WhenComplete

    static <T> void nowWhen(Executor e, CompletableFuture<T> d, Object r,
                            BiConsumer<? super T,? super Throwable> f) {
        if (d != null && f != null) {
            T t; Throwable x, dx;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            try {
                if (e != null) {
                    e.execute(new AsyncWhen<T>(d, r, f));
                    return;
                }
                f.accept(t, x);
                dx = null;
            } catch (Throwable ex) {
                dx = ex;
            }
            d.internalComplete(encodeOutcome(t, x != null ? x : dx));
        }
    }

    static final class AsyncWhen<T> extends Async<T> {
        Object arg; BiConsumer<? super T,? super Throwable> fn;
        AsyncWhen(CompletableFuture<T> dep, Object arg,
                  BiConsumer<? super T,? super Throwable> fn) {
            super(dep); this.arg = arg; this.fn = fn;
        }
        final void compute() { nowWhen(null, dep, arg, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class DelayedWhen<T> extends UniCompletion<T> {
        BiConsumer<? super T, ? super Throwable> fn;
        DelayedWhen(Executor async, CompletableFuture<T> dep,
                    CompletableFuture<?> src,
                    BiConsumer<? super T, ? super Throwable> fn) {
            super(async, dep, src); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<T> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowWhen(async, d, r, fn);
                src = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private CompletableFuture<T> doWhenComplete(
        BiConsumer<? super T, ? super Throwable> fn, Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<T> d = new CompletableFuture<T>();
        Object r = result;
        if (r == null)
            unipush(new DelayedWhen<T>(e, d, this, fn));
        else
            nowWhen(e, d, r, fn);
        return d;
    }

    // Handle

    static <T,U> void nowHandle(Executor e, CompletableFuture<U> d, Object r,
                                BiFunction<? super T, Throwable, ? extends U> f) {
        if (d != null && f != null) {
            T t; U u; Throwable x, dx;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            try {
                if (e != null) {
                    e.execute(new AsyncCombine<T,Throwable,U>(d, t, x, f));
                    return;
                }
                u = f.apply(t, x);
                dx = null;
            } catch (Throwable ex) {
                dx = ex;
                u = null;
            }
            d.internalComplete(encodeOutcome(u, dx));
        }
    }

    static final class DelayedHandle<T,U> extends UniCompletion<U> {
        BiFunction<? super T, Throwable, ? extends U> fn;
        DelayedHandle(Executor async, CompletableFuture<U> dep,
                      CompletableFuture<?> src,
                      BiFunction<? super T, Throwable, ? extends U> fn) {
            super(async, dep, src); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<U> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowHandle(async, d, r, fn);
                src = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private <U> CompletableFuture<U> doHandle(
        BiFunction<? super T, Throwable, ? extends U> fn,
        Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> d = new CompletableFuture<U>();
        Object r = result;
        if (r == null)
            unipush(new DelayedHandle<T,U>(e, d, this, fn));
        else
            nowHandle(e, d, r, fn);
        return d;
    }

    // Exceptionally

    static <T> void nowExceptionally(CompletableFuture<T> d, Object r,
                                     Function<? super Throwable, ? extends T> f) {
        if (d != null && f != null) {
            T t; Throwable x, dx;
            if ((r instanceof AltResult) && (x = ((AltResult)r).ex) != null) {
                try {
                    t = f.apply(x);
                    dx = null;
                } catch (Throwable ex) {
                    dx = ex;
                    t = null;
                }
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                dx = null;
            }
            d.internalComplete(encodeOutcome(t, dx));
        }
    }

    static final class DelayedExceptionally<T> extends UniCompletion<T> {
        Function<? super Throwable, ? extends T> fn;
        DelayedExceptionally(CompletableFuture<T> dep, CompletableFuture<?> src,
                             Function<? super Throwable, ? extends T> fn) {
            super(null, dep, src); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<T> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowExceptionally(d, r, fn);
                src = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private CompletableFuture<T> doExceptionally(
        Function<Throwable, ? extends T> fn) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<T> d = new CompletableFuture<T>();
        Object r = result;
        if (r == null)
            unipush(new DelayedExceptionally<T>(d, this, fn));
        else
            nowExceptionally(d, r, fn);
        return d;
    }

    // Identity function used by nowCompose and anyOf

    static <T> void nowCopy(CompletableFuture<T> d, Object r) {
        if (d != null && d.result == null) {
            Throwable x;
            d.internalComplete(((r instanceof AltResult) &&
                                (x = ((AltResult)r).ex) != null &&
                                !(x instanceof CompletionException)) ?
                               new AltResult(new CompletionException(x)): r);
        }
    }

    static final class DelayedCopy<T> extends UniCompletion<T> {
        DelayedCopy(CompletableFuture<T> dep, CompletableFuture<?> src) {
            super(null, dep, src);
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<T> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowCopy(d, r);
                src = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    // Compose

    static <T,U> void nowCompose(Executor e, CompletableFuture<U> d, Object r,
                                 Function<? super T, ? extends CompletionStage<U>> f) {
        if (d != null && f != null) {
            T t; Throwable x;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            if (x == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncCompose<T,U>(d, t, f));
                    else {
                        CompletableFuture<U> c =
                            f.apply(t).toCompletableFuture();
                        Object s = c.result;
                        if (s == null)
                            c.unipush(new DelayedCopy<U>(d, c));
                        else
                            nowCopy(d, s);
                    }
                    return;
                } catch (Throwable ex) {
                    x = ex;
                }
            }
            d.internalComplete(encodeOutcome(null, x));
        }
    }

    static final class AsyncCompose<T,U> extends Async<U> {
        T arg; Function<? super T, ? extends CompletionStage<U>> fn;
        AsyncCompose(CompletableFuture<U> dep, T arg,
                     Function<? super T, ? extends CompletionStage<U>> fn) {
            super(dep); this.arg = arg; this.fn = fn;
        }
        final void compute() { nowCompose(null, dep, arg, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class DelayedCompose<T,U> extends UniCompletion<U> {
        Function<? super T, ? extends CompletionStage<U>> fn;
        DelayedCompose(Executor async, CompletableFuture<U> dep,
                       CompletableFuture<?> src,
                       Function<? super T, ? extends CompletionStage<U>> fn) {
            super(async, dep, src); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<U> d; CompletableFuture<?> a; Object r;
            if ((d = dep) != null && (a = src) != null &&
                (r = a.result) != null && claim(d)) {
                nowCompose(async, d, r, fn);
                src = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private <U> CompletableFuture<U> doThenCompose(
        Function<? super T, ? extends CompletionStage<U>> fn, Executor e) {
        if (fn == null) throw new NullPointerException();
        Object r = result;
        if (r == null || e != null) {
            CompletableFuture<U> d = new CompletableFuture<U>();
            if (r == null)
                unipush(new DelayedCompose<T,U>(e, d, this, fn));
            else
                nowCompose(e, d, r, fn);
            return d;
        }
        else { // try to return function result
            T t; Throwable x;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            if (x == null) {
                try {
                    return fn.apply(t).toCompletableFuture();
                } catch (Throwable ex) {
                    x = ex;
                }
            }
            CompletableFuture<U> d = new CompletableFuture<U>();
            d.result = encodeOutcome(null, x);
            return d;
        }
    }

    /* ------------- Two-source Completions -------------- */

    /** A Completion with two sources */
    abstract static class BiCompletion<T> extends UniCompletion<T> {
        CompletableFuture<?> snd; // second source for tryAct
        BiCompletion(Executor async, CompletableFuture<T> dep,
                     CompletableFuture<?> src, CompletableFuture<?> snd) {
            super(async, dep, src); this.snd = snd;
        }
    }

    /** A Completion delegating to a shared BiCompletion */
    static final class CoBiCompletion<T> extends Completion<T> {
        BiCompletion<T> completion;
        CoBiCompletion(BiCompletion<T> completion) {
            this.completion = completion;
        }
        final CompletableFuture<?> tryAct() {
            BiCompletion<T> c;
            return (c = completion) == null ? null : c.tryAct();
        }
    }

    /* ------------- Two-source Anded -------------- */

    /* Pushes c on to completions and o's completions unless both done. */
    private <U> void bipushAnded(CompletableFuture<?> o, BiCompletion<U> c) {
        if (c != null && o != null) {
            Object r; CompletableFuture<?> d;
            while ((r = result) == null &&
                   !casCompletions(c.next = completions, c))
                c.next = null;
            if (o.result == null) {
                Completion<U> q = (r != null) ? c : new CoBiCompletion<U>(c);
                while (o.result == null &&
                       !o.casCompletions(q.next = o.completions, q))
                    q.next = null;
            }
            if ((d = c.tryAct()) != null)
                d.postComplete();
            if (o.result != null)
                o.postComplete();
            if (result != null)
                postComplete();
        }
    }

    // BiFunction/combine

    static <T,U,V> void nowCombine(Executor e, CompletableFuture<V> d,
                                   Object r, Object s,
                                   BiFunction<? super T,? super U,? extends V> f) {
        if (d != null && f != null) {
            T t; U u; V v; Throwable x;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            if (x != null)
                u = null;
            else if (s instanceof AltResult) {
                x = ((AltResult)s).ex;
                u = null;
            }
            else {
                @SuppressWarnings("unchecked") U us = (U) s; u = us;
            }
            if (x == null) {
                try {
                    if (e != null) {
                        e.execute(new AsyncCombine<T,U,V>(d, t, u, f));
                        return;
                    }
                    v = f.apply(t, u);
                } catch (Throwable ex) {
                    x = ex;
                    v = null;
                }
            }
            else
                v = null;
            d.internalComplete(encodeOutcome(v, x));
        }
    }

    static final class AsyncCombine<T,U,V> extends Async<V> {
        T arg1; U arg2; BiFunction<? super T,? super U,? extends V> fn;
        AsyncCombine(CompletableFuture<V> dep, T arg1, U arg2,
                     BiFunction<? super T,? super U,? extends V> fn) {
            super(dep); this.arg1 = arg1; this.arg2 = arg2; this.fn = fn;
        }
        final void compute() { nowCombine(null, dep, arg1, arg2, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class DelayedCombine<T,U,V> extends BiCompletion<V> {
        BiFunction<? super T,? super U,? extends V> fn;
        DelayedCombine(Executor async, CompletableFuture<V> dep,
                       CompletableFuture<?> src, CompletableFuture<?> snd,
                       BiFunction<? super T,? super U,? extends V> fn) {
            super(async, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<V> d; CompletableFuture<?> a, b; Object r, s;
            if ((d = dep) != null && (a = src) != null && (b = snd) != null &&
                (r = a.result) != null && (s = b.result) != null && claim(d)) {
                nowCombine(async, d, r, s, fn);
                src = null; snd = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private <U,V> CompletableFuture<V> doThenCombine(
        CompletableFuture<? extends U> o,
        BiFunction<? super T,? super U,? extends V> fn,
        Executor e) {
        if (o == null || fn == null) throw new NullPointerException();
        CompletableFuture<V> d = new CompletableFuture<V>();
        Object r = result, s = o.result;
        if (r == null || s == null)
            bipushAnded(o, new DelayedCombine<T,U,V>(e, d, this, o, fn));
        else
            nowCombine(e, d, r, s, fn);
        return d;
    }

    // BiConsumer/AcceptBoth

    static <T,U,V> void nowAcceptBoth(Executor e, CompletableFuture<V> d,
                                      Object r, Object s,
                                      BiConsumer<? super T,? super U> f) {
        if (d != null && f != null) {
            T t; U u; Throwable x;
            if (r instanceof AltResult) {
                t = null;
                x = ((AltResult)r).ex;
            }
            else {
                @SuppressWarnings("unchecked") T tr = (T) r; t = tr;
                x = null;
            }
            if (x != null)
                u = null;
            else if (s instanceof AltResult) {
                x = ((AltResult)s).ex;
                u = null;
            }
            else {
                @SuppressWarnings("unchecked") U us = (U) s; u = us;
            }
            if (x == null) {
                try {
                    if (e != null) {
                        e.execute(new AsyncAcceptBoth<T,U,V>(d, t, u, f));
                        return;
                    }
                    f.accept(t, u);
                } catch (Throwable ex) {
                    x = ex;
                }
            }
            d.internalComplete(encodeOutcome(null, x));
        }
    }

    static final class AsyncAcceptBoth<T,U,V> extends Async<V> {
        T arg1; U arg2; BiConsumer<? super T,? super U> fn;
        AsyncAcceptBoth(CompletableFuture<V> dep, T arg1, U arg2,
                        BiConsumer<? super T,? super U> fn) {
            super(dep); this.arg1 = arg1; this.arg2 = arg2; this.fn = fn;
        }
        final void compute() { nowAcceptBoth(null, dep, arg1, arg2, fn); }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class DelayedAcceptBoth<T,U> extends BiCompletion<Void> {
        BiConsumer<? super T,? super U> fn;
        DelayedAcceptBoth(Executor async, CompletableFuture<Void> dep,
                          CompletableFuture<?> src, CompletableFuture<?> snd,
                          BiConsumer<? super T,? super U> fn) {
            super(async, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<Void> d; CompletableFuture<?> a, b; Object r, s;
            if ((d = dep) != null && (a = src) != null && (b = snd) != null &&
                (r = a.result) != null && (s = b.result) != null && claim(d)) {
                nowAcceptBoth(async, d, r, s, fn);
                src = null; snd = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private <U> CompletableFuture<Void> doThenAcceptBoth(
        CompletableFuture<? extends U> o,
        BiConsumer<? super T, ? super U> fn,
        Executor e) {
        if (o == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        Object r = result, s = o.result;
        if (r == null || s == null)
            bipushAnded(o, new DelayedAcceptBoth<T,U>(e, d, this, o, fn));
        else
            nowAcceptBoth(e, d, r, s, fn);
        return d;
    }

    // Runnable/both

    static final class DelayedRunAfterBoth extends BiCompletion<Void> {
        Runnable fn;
        DelayedRunAfterBoth(Executor async, CompletableFuture<Void> dep,
                            CompletableFuture<?> src, CompletableFuture<?> snd,
                            Runnable fn) {
            super(async, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<Void> d; CompletableFuture<?> a, b; Object r, s;
            if ((d = dep) != null && (a = src) != null && (b = snd) != null &&
                (r = a.result) != null && (s = b.result) != null && claim(d)) {
                Throwable x = (r instanceof AltResult) ?
                    ((AltResult)r).ex : null;
                nowRun(async, d, (x == null) ? s : r, fn);
                src = null; snd = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private CompletableFuture<Void> doRunAfterBoth(
        CompletableFuture<?> o, Runnable fn, Executor e) {
        if (o == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        Object r = result, s = o.result;
        if (r == null || s == null)
            bipushAnded(o, new DelayedRunAfterBoth(e, d, this, o, fn));
        else {
            Throwable x = (r instanceof AltResult) ? ((AltResult)r).ex : null;
            nowRun(e, d, (x == null) ? s : r, fn);
        }
        return d;
    }

    // allOf

    static <T> void nowAnd(CompletableFuture<T> d, Object r, Object s) {
        if (d != null) {
            Throwable x = (r instanceof AltResult) ? ((AltResult)r).ex : null;
            if (x == null && (s instanceof AltResult))
                x = ((AltResult)s).ex;
            d.internalComplete(encodeOutcome(null, x));
        }
    }

    static final class DelayedAnd extends BiCompletion<Void> {
        DelayedAnd(CompletableFuture<Void> dep,
                   CompletableFuture<?> src, CompletableFuture<?> snd) {
            super(null, dep, src, snd);
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<Void> d; CompletableFuture<?> a, b; Object r, s;
            if ((d = dep) != null && (a = src) != null && (b = snd) != null &&
                (r = a.result) != null && (s = b.result) != null && claim(d)) {
                nowAnd(d, r, s);
                src = null; snd = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    /** Recursively constructs a tree of And completions */
    private static CompletableFuture<Void> doAllOf(CompletableFuture<?>[] cfs,
                                                   int lo, int hi) {
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (lo > hi) // empty
            d.result = NIL;
        else {
            int mid = (lo + hi) >>> 1;
            CompletableFuture<?> fst = (lo == mid ? cfs[lo] :
                                        doAllOf(cfs, lo,    mid));
            CompletableFuture<?> snd = (lo == hi ? fst : // and fst with self
                                        (hi == mid+1) ? cfs[hi] :
                                        doAllOf(cfs, mid+1, hi));
            Object r = fst.result, s = snd.result; // throw NPE if null elements
            if (r == null || s == null) {
                DelayedAnd a = new DelayedAnd(d, fst, snd);
                if (fst == snd)
                    fst.unipush(a);
                else
                    fst.bipushAnded(snd, a);
            }
            else
                nowAnd(d, r, s);
        }
        return d;
    }

    /* ------------- Two-source Ored -------------- */

    /* Pushes c on to completions and o's completions unless either done. */
    private <U> void bipushOred(CompletableFuture<?> o, BiCompletion<U> c) {
        if (c != null && o != null) {
            CompletableFuture<?> d;
            while (o.result == null && result == null) {
                if (casCompletions(c.next = completions, c)) {
                    CoBiCompletion<U> q = new CoBiCompletion<U>(c);
                    while (result == null && o.result == null &&
                           !o.casCompletions(q.next = o.completions, q))
                        q.next = null;
                    break;
                }
                c.next = null;
            }
            if ((d = c.tryAct()) != null)
                d.postComplete();
            if (o.result != null)
                o.postComplete();
            if (result != null)
                postComplete();
        }
    }

    // Function/applyEither

    static final class DelayedApplyToEither<T,U> extends BiCompletion<U> {
        Function<? super T,? extends U> fn;
        DelayedApplyToEither(Executor async, CompletableFuture<U> dep,
                             CompletableFuture<?> src, CompletableFuture<?> snd,
                             Function<? super T,? extends U> fn) {
            super(async, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<U> d; CompletableFuture<?> a, b; Object r;
            if ((d = dep) != null && (a = src) != null && (b = snd) != null &&
                ((r = a.result) != null || (r = b.result) != null) &&
                claim(d)) {
                nowApply(async, d, r, fn);
                src = null; snd = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private <U> CompletableFuture<U> doApplyToEither(
        CompletableFuture<? extends T> o,
        Function<? super T, U> fn, Executor e) {
        if (o == null || fn == null) throw new NullPointerException();
        CompletableFuture<U> d = new CompletableFuture<U>();
        Object r = result;
        if (r == null && (r = o.result) == null)
            bipushOred(o, new DelayedApplyToEither<T,U>(e, d, this, o, fn));
        else
            nowApply(e, d, r, fn);
        return d;
    }

    // Consumer/acceptEither

    static final class DelayedAcceptEither<T> extends BiCompletion<Void> {
        Consumer<? super T> fn;
        DelayedAcceptEither(Executor async, CompletableFuture<Void> dep,
                            CompletableFuture<?> src, CompletableFuture<?> snd,
                            Consumer<? super T> fn) {
            super(async, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<Void> d; CompletableFuture<?> a, b; Object r;
            if ((d = dep) != null && (a = src) != null && (b = snd) != null &&
                ((r = a.result) != null || (r = b.result) != null) &&
                claim(d)) {
                nowAccept(async, d, r, fn);
                src = null; snd = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private CompletableFuture<Void> doAcceptEither(
        CompletableFuture<? extends T> o,
        Consumer<? super T> fn, Executor e) {
        if (o == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        Object r = result;
        if (r == null && (r = o.result) == null)
            bipushOred(o, new DelayedAcceptEither<T>(e, d, this, o, fn));
        else
            nowAccept(e, d, r, fn);
        return d;
    }

    // Runnable/runEither

    static final class DelayedRunAfterEither extends BiCompletion<Void> {
        Runnable fn;
        DelayedRunAfterEither(Executor async, CompletableFuture<Void> dep,
                              CompletableFuture<?> src,
                              CompletableFuture<?> snd, Runnable fn) {
            super(async, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<?> tryAct() {
            CompletableFuture<Void> d; CompletableFuture<?> a, b; Object r;
            if ((d = dep) != null && (a = src) != null && (b = snd) != null &&
                ((r = a.result) != null || (r = b.result) != null) &&
                claim(d)) {
                nowRun(async, d, r, fn);
                src = null; snd = null; fn = null;
                if (d.result != null) return d;
            }
            return null;
        }
    }

    private CompletableFuture<Void> doRunAfterEither(
        CompletableFuture<?> o, Runnable fn, Executor e) {
        if (o == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        Object r = result;
        if (r == null && (r = o.result) == null)
            bipushOred(o, new DelayedRunAfterEither(e, d, this, o, fn));
        else
            nowRun(e, d, r, fn);
        return d;
    }

    /* ------------- Signallers -------------- */

    /**
     * Heuristic spin value for waitingGet() before blocking on
     * multiprocessors
     */
    static final int SPINS = (Runtime.getRuntime().availableProcessors() > 1 ?
                              1 << 8 : 0);

    /**
     * Completion for recording and releasing a waiting thread.  See
     * other classes such as Phaser and SynchronousQueue for more
     * detailed explanation. This class implements ManagedBlocker to
     * avoid starvation when blocking actions pile up in
     * ForkJoinPools.
     */
    static final class Signaller extends Completion<Void>
        implements ForkJoinPool.ManagedBlocker {
        long nanos;          // wait time if timed
        final long deadline; // non-zero if timed
        volatile int interruptControl; // > 0: interruptible, < 0: interrupted
        volatile Thread thread;
        Signaller(boolean interruptible, long nanos, long deadline) {
            this.thread = Thread.currentThread();
            this.interruptControl = interruptible ? 1 : 0;
            this.nanos = nanos;
            this.deadline = deadline;
        }
        final CompletableFuture<?> tryAct() {
            Thread w = thread;
            if (w != null) {
                thread = null; // no need to CAS
                LockSupport.unpark(w);
            }
            return null;
        }
        public boolean isReleasable() {
            if (thread == null)
                return true;
            if (Thread.interrupted()) {
                int i = interruptControl;
                interruptControl = -1;
                if (i > 0)
                    return true;
            }
            if (deadline != 0L &&
                (nanos <= 0L || (nanos = deadline - System.nanoTime()) <= 0L)) {
                thread = null;
                return true;
            }
            return false;
        }
        public boolean block() {
            if (isReleasable())
                return true;
            else if (deadline == 0L)
                LockSupport.park(this);
            else if (nanos > 0L)
                LockSupport.parkNanos(this, nanos);
            return isReleasable();
        }
    }

    /**
     * Returns raw result after waiting, or null if interruptible and
     * interrupted.
     */
    private Object waitingGet(boolean interruptible) {
        Signaller q = null;
        boolean queued = false;
        int spins = SPINS;
        Object r;
        while ((r = result) == null) {
            if (spins > 0) {
                if (ThreadLocalRandom.nextSecondarySeed() >= 0)
                    --spins;
            }
            else if (q == null)
                q = new Signaller(interruptible, 0L, 0L);
            else if (!queued)
                queued = casCompletions(q.next = completions, q);
            else if (interruptible && q.interruptControl < 0) {
                q.thread = null;
                removeCancelledSignallers();
                return null;
            }
            else if (q.thread != null && result == null) {
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    q.interruptControl = -1;
                }
            }
        }
        if (q != null) {
            q.thread = null;
            if (q.interruptControl < 0) {
                if (interruptible)
                    r = null; // report interruption
                else
                    Thread.currentThread().interrupt();
            }
        }
        postComplete();
        return r;
    }

    /**
     * Returns raw result after waiting, or null if interrupted, or
     * throws TimeoutException on timeout.
     */
    private Object timedGet(long nanos) throws TimeoutException {
        if (Thread.interrupted())
            return null;
        if (nanos <= 0L)
            throw new TimeoutException();
        long d = System.nanoTime() + nanos;
        Signaller q = new Signaller(true, nanos, d == 0L ? 1L : d); // avoid 0
        boolean queued = false;
        Object r;
        while ((r = result) == null) {
            if (!queued)
                queued = casCompletions(q.next = completions, q);
            else if (q.interruptControl < 0 || q.nanos <= 0L) {
                q.thread = null;
                removeCancelledSignallers();
                if (q.interruptControl < 0)
                    return null;
                throw new TimeoutException();
            }
            else if (q.thread != null && result == null) {
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    q.interruptControl = -1;
                }
            }
        }
        q.thread = null;
        postComplete();
        return (q.interruptControl < 0) ? null : r;
    }

    /**
     * Unlinks cancelled Signallers to avoid accumulating garbage.
     * Internal nodes are simply unspliced without CAS since it is
     * harmless if they are traversed anyway.  To avoid effects of
     * unsplicing from already removed nodes, the list is retraversed
     * in case of an apparent race.
     */
    private void removeCancelledSignallers() {
        for (Completion<?> p = null, q = completions; q != null;) {
            Completion<?> s = q.next;
            if ((q instanceof Signaller) && ((Signaller)q).thread == null) {
                if (p != null) {
                    p.next = s;
                    if (!(p instanceof Signaller) ||
                        ((Signaller)p).thread != null)
                        break;
                }
                else if (casCompletions(q, s))
                    break;
                p = null; // restart
                q = completions;
            }
            else {
                p = q;
                q = s;
            }
        }
    }

    /* ------------- public methods -------------- */

    /**
     * Creates a new incomplete CompletableFuture.
     */
    public CompletableFuture() {
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} with
     * the value obtained by calling the given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param <U> the function's return type
     * @return the new CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        if (supplier == null) throw new NullPointerException();
        CompletableFuture<U> d = new CompletableFuture<U>();
        asyncPool.execute(new AsyncSupply<U>(d, supplier));
        return d;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the given executor with the value obtained
     * by calling the given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @param <U> the function's return type
     * @return the new CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                                                       Executor executor) {
        if (supplier == null) throw new NullPointerException();
        Executor e = screenExecutor(executor);
        CompletableFuture<U> d = new CompletableFuture<U>();
        e.execute(new AsyncSupply<U>(d, supplier));
        return d;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} after
     * it runs the given action.
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        if (runnable == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        asyncPool.execute(new AsyncRun<Void>(d, runnable));
        return d;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the given executor after it runs the given
     * action.
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable,
                                                   Executor executor) {
        if (runnable == null) throw new NullPointerException();
        Executor e = screenExecutor(executor);
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        e.execute(new AsyncRun<Void>(d, runnable));
        return d;
    }

    /**
     * Returns a new CompletableFuture that is already completed with
     * the given value.
     *
     * @param value the value
     * @param <U> the type of the value
     * @return the completed CompletableFuture
     */
    public static <U> CompletableFuture<U> completedFuture(U value) {
        CompletableFuture<U> d = new CompletableFuture<U>();
        d.result = (value == null) ? NIL : value;
        return d;
    }

    /**
     * Returns {@code true} if completed in any fashion: normally,
     * exceptionally, or via cancellation.
     *
     * @return {@code true} if completed
     */
    public boolean isDone() {
        return result != null;
    }

    /**
     * Waits if necessary for this future to complete, and then
     * returns its result.
     *
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException if this future completed exceptionally
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     */
    public T get() throws InterruptedException, ExecutionException {
        Object r;
        return reportGet((r = result) == null ?  waitingGet(true) : r);
    }

    /**
     * Waits if necessary for at most the given time for this future
     * to complete, and then returns its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException if this future completed exceptionally
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        Object r;
        long nanos = unit.toNanos(timeout);
        return reportGet((r = result) == null ?  timedGet(nanos) : r);
    }

    /**
     * Returns the result value when complete, or throws an
     * (unchecked) exception if completed exceptionally. To better
     * conform with the use of common functional forms, if a
     * computation involved in the completion of this
     * CompletableFuture threw an exception, this method throws an
     * (unchecked) {@link CompletionException} with the underlying
     * exception as its cause.
     *
     * @return the result value
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if this future completed
     * exceptionally or a completion computation threw an exception
     */
    public T join() {
        Object r;
        return reportJoin((r = result) == null ? waitingGet(false) : r);
    }

    /**
     * Returns the result value (or throws any encountered exception)
     * if completed, else returns the given valueIfAbsent.
     *
     * @param valueIfAbsent the value to return if not completed
     * @return the result value, if completed, else the given valueIfAbsent
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if this future completed
     * exceptionally or a completion computation threw an exception
     */
    public T getNow(T valueIfAbsent) {
        Object r;
        return ((r = result) == null) ? valueIfAbsent : reportJoin(r);
    }

    /**
     * If not already completed, sets the value returned by {@link
     * #get()} and related methods to the given value.
     *
     * @param value the result value
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean complete(T value) {
        boolean triggered = internalComplete(value == null ? NIL : value);
        postComplete();
        return triggered;
    }

    /**
     * If not already completed, causes invocations of {@link #get()}
     * and related methods to throw the given exception.
     *
     * @param ex the exception
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        boolean triggered = internalComplete(new AltResult(ex));
        postComplete();
        return triggered;
    }

    public <U> CompletableFuture<U> thenApply(
        Function<? super T,? extends U> fn) {
        return doThenApply(fn, null);
    }

    public <U> CompletableFuture<U> thenApplyAsync(
        Function<? super T,? extends U> fn) {
        return doThenApply(fn, asyncPool);
    }

    public <U> CompletableFuture<U> thenApplyAsync(
        Function<? super T,? extends U> fn, Executor executor) {
        return doThenApply(fn, screenExecutor(executor));
    }

    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return doThenAccept(action, null);
    }

    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return doThenAccept(action, asyncPool);
    }

    public CompletableFuture<Void> thenAcceptAsync(
        Consumer<? super T> action, Executor executor) {
        return doThenAccept(action, screenExecutor(executor));
    }

    public CompletableFuture<Void> thenRun(Runnable action) {
        return doThenRun(action, null);
    }

    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return doThenRun(action, asyncPool);
    }

    public CompletableFuture<Void> thenRunAsync(
        Runnable action, Executor executor) {
        return doThenRun(action, screenExecutor(executor));
    }

    public <U,V> CompletableFuture<V> thenCombine(
        CompletionStage<? extends U> other,
        BiFunction<? super T,? super U,? extends V> fn) {
        return doThenCombine(other.toCompletableFuture(), fn, null);
    }

    public <U,V> CompletableFuture<V> thenCombineAsync(
        CompletionStage<? extends U> other,
        BiFunction<? super T,? super U,? extends V> fn) {
        return doThenCombine(other.toCompletableFuture(), fn, asyncPool);
    }

    public <U,V> CompletableFuture<V> thenCombineAsync(
        CompletionStage<? extends U> other,
        BiFunction<? super T,? super U,? extends V> fn,
        Executor executor) {
        return doThenCombine(other.toCompletableFuture(), fn,
                             screenExecutor(executor));
    }

    public <U> CompletableFuture<Void> thenAcceptBoth(
        CompletionStage<? extends U> other,
        BiConsumer<? super T, ? super U> action) {
        return doThenAcceptBoth(other.toCompletableFuture(), action, null);
    }

    public <U> CompletableFuture<Void> thenAcceptBothAsync(
        CompletionStage<? extends U> other,
        BiConsumer<? super T, ? super U> action) {
        return doThenAcceptBoth(other.toCompletableFuture(), action, asyncPool);
    }

    public <U> CompletableFuture<Void> thenAcceptBothAsync(
        CompletionStage<? extends U> other,
        BiConsumer<? super T, ? super U> action,
        Executor executor) {
        return doThenAcceptBoth(other.toCompletableFuture(), action,
                                screenExecutor(executor));
    }

    public CompletableFuture<Void> runAfterBoth(
        CompletionStage<?> other, Runnable action) {
        return doRunAfterBoth(other.toCompletableFuture(), action, null);
    }

    public CompletableFuture<Void> runAfterBothAsync(
        CompletionStage<?> other, Runnable action) {
        return doRunAfterBoth(other.toCompletableFuture(), action, asyncPool);
    }

    public CompletableFuture<Void> runAfterBothAsync(
        CompletionStage<?> other, Runnable action, Executor executor) {
        return doRunAfterBoth(other.toCompletableFuture(), action,
                              screenExecutor(executor));
    }

    public <U> CompletableFuture<U> applyToEither(
        CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return doApplyToEither(other.toCompletableFuture(), fn, null);
    }

    public <U> CompletableFuture<U> applyToEitherAsync(
        CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return doApplyToEither(other.toCompletableFuture(), fn, asyncPool);
    }

    public <U> CompletableFuture<U> applyToEitherAsync
        (CompletionStage<? extends T> other, Function<? super T, U> fn,
         Executor executor) {
        return doApplyToEither(other.toCompletableFuture(), fn,
                               screenExecutor(executor));
    }

    public CompletableFuture<Void> acceptEither(
        CompletionStage<? extends T> other, Consumer<? super T> action) {
        return doAcceptEither(other.toCompletableFuture(), action, null);
    }

    public CompletableFuture<Void> acceptEitherAsync
        (CompletionStage<? extends T> other, Consumer<? super T> action) {
        return doAcceptEither(other.toCompletableFuture(), action, asyncPool);
    }

    public CompletableFuture<Void> acceptEitherAsync(
        CompletionStage<? extends T> other, Consumer<? super T> action,
        Executor executor) {
        return doAcceptEither(other.toCompletableFuture(), action,
                              screenExecutor(executor));
    }

    public CompletableFuture<Void> runAfterEither(
        CompletionStage<?> other, Runnable action) {
        return doRunAfterEither(other.toCompletableFuture(), action, null);
    }

    public CompletableFuture<Void> runAfterEitherAsync(
        CompletionStage<?> other, Runnable action) {
        return doRunAfterEither(other.toCompletableFuture(), action, asyncPool);
    }

    public CompletableFuture<Void> runAfterEitherAsync(
        CompletionStage<?> other, Runnable action, Executor executor) {
        return doRunAfterEither(other.toCompletableFuture(), action,
                                screenExecutor(executor));
    }

    public <U> CompletableFuture<U> thenCompose
        (Function<? super T, ? extends CompletionStage<U>> fn) {
        return doThenCompose(fn, null);
    }

    public <U> CompletableFuture<U> thenComposeAsync(
        Function<? super T, ? extends CompletionStage<U>> fn) {
        return doThenCompose(fn, asyncPool);
    }

    public <U> CompletableFuture<U> thenComposeAsync(
        Function<? super T, ? extends CompletionStage<U>> fn,
        Executor executor) {
        return doThenCompose(fn, screenExecutor(executor));
    }

    public CompletableFuture<T> whenComplete(
        BiConsumer<? super T, ? super Throwable> action) {
        return doWhenComplete(action, null);
    }

    public CompletableFuture<T> whenCompleteAsync(
        BiConsumer<? super T, ? super Throwable> action) {
        return doWhenComplete(action, asyncPool);
    }

    public CompletableFuture<T> whenCompleteAsync(
        BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return doWhenComplete(action, screenExecutor(executor));
    }

    public <U> CompletableFuture<U> handle(
        BiFunction<? super T, Throwable, ? extends U> fn) {
        return doHandle(fn, null);
    }

    public <U> CompletableFuture<U> handleAsync(
        BiFunction<? super T, Throwable, ? extends U> fn) {
        return doHandle(fn, asyncPool);
    }

    public <U> CompletableFuture<U> handleAsync(
        BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return doHandle(fn, screenExecutor(executor));
    }

    /**
     * Returns this CompletableFuture.
     *
     * @return this CompletableFuture
     */
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    // not in interface CompletionStage

    /**
     * Returns a new CompletableFuture that is completed when this
     * CompletableFuture completes, with the result of the given
     * function of the exception triggering this CompletableFuture's
     * completion when it completes exceptionally; otherwise, if this
     * CompletableFuture completes normally, then the returned
     * CompletableFuture also completes normally with the same value.
     * Note: More flexible versions of this functionality are
     * available using methods {@code whenComplete} and {@code handle}.
     *
     * @param fn the function to use to compute the value of the
     * returned CompletableFuture if this CompletableFuture completed
     * exceptionally
     * @return the new CompletableFuture
     */
    public CompletableFuture<T> exceptionally(
        Function<Throwable, ? extends T> fn) {
        return doExceptionally(fn);
    }

    /* ------------- Arbitrary-arity constructions -------------- */

    /**
     * Returns a new CompletableFuture that is completed when all of
     * the given CompletableFutures complete.  If any of the given
     * CompletableFutures complete exceptionally, then the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  Otherwise, the results,
     * if any, of the given CompletableFutures are not reflected in
     * the returned CompletableFuture, but may be obtained by
     * inspecting them individually. If no CompletableFutures are
     * provided, returns a CompletableFuture completed with the value
     * {@code null}.
     *
     * <p>Among the applications of this method is to await completion
     * of a set of independent CompletableFutures before continuing a
     * program, as in: {@code CompletableFuture.allOf(c1, c2,
     * c3).join();}.
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the
     * given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are
     * {@code null}
     */
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        return doAllOf(cfs, 0, cfs.length - 1);
    }

    /**
     * Returns a new CompletableFuture that is completed when any of
     * the given CompletableFutures complete, with the same result.
     * Otherwise, if it completed exceptionally, the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  If no CompletableFutures
     * are provided, returns an incomplete CompletableFuture.
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed with the
     * result or exception of any of the given CompletableFutures when
     * one completes
     * @throws NullPointerException if the array or any of its elements are
     * {@code null}
     */
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        CompletableFuture<Object> d = new CompletableFuture<Object>();
        for (int i = 0; i < cfs.length; ++i) {
            CompletableFuture<?> c = cfs[i];
            Object r = c.result; // throw NPE if null element
            if (d.result == null) {
                if (r == null)
                    c.unipush(new DelayedCopy<Object>(d, c));
                else
                    nowCopy(d, r);
            }
        }
        return d;
    }

    /* ------------- Control and status methods -------------- */

    /**
     * If not already completed, completes this CompletableFuture with
     * a {@link CancellationException}. Dependent CompletableFutures
     * that have not already completed will also complete
     * exceptionally, with a {@link CompletionException} caused by
     * this {@code CancellationException}.
     *
     * @param mayInterruptIfRunning this value has no effect in this
     * implementation because interrupts are not used to control
     * processing.
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = (result == null) &&
            internalComplete(new AltResult(new CancellationException()));
        postComplete();
        return cancelled || isCancelled();
    }

    /**
     * Returns {@code true} if this CompletableFuture was cancelled
     * before it completed normally.
     *
     * @return {@code true} if this CompletableFuture was cancelled
     * before it completed normally
     */
    public boolean isCancelled() {
        Object r;
        return ((r = result) instanceof AltResult) &&
            (((AltResult)r).ex instanceof CancellationException);
    }

    /**
     * Returns {@code true} if this CompletableFuture completed
     * exceptionally, in any way. Possible causes include
     * cancellation, explicit invocation of {@code
     * completeExceptionally}, and abrupt termination of a
     * CompletionStage action.
     *
     * @return {@code true} if this CompletableFuture completed
     * exceptionally
     */
    public boolean isCompletedExceptionally() {
        Object r;
        return ((r = result) instanceof AltResult) && r != NIL;
    }

    /**
     * Forcibly sets or resets the value subsequently returned by
     * method {@link #get()} and related methods, whether or not
     * already completed. This method is designed for use only in
     * error recovery actions, and even in such situations may result
     * in ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param value the completion value
     */
    public void obtrudeValue(T value) {
        result = (value == null) ? NIL : value;
        postComplete();
    }

    /**
     * Forcibly causes subsequent invocations of method {@link #get()}
     * and related methods to throw the given exception, whether or
     * not already completed. This method is designed for use only in
     * recovery actions, and even in such situations may result in
     * ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param ex the exception
     */
    public void obtrudeException(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        result = new AltResult(ex);
        postComplete();
    }

    /**
     * Returns the estimated number of CompletableFutures whose
     * completions are awaiting completion of this CompletableFuture.
     * This method is designed for use in monitoring system state, not
     * for synchronization control.
     *
     * @return the number of dependent CompletableFutures
     */
    public int getNumberOfDependents() {
        int count = 0;
        for (Completion<?> p = completions; p != null; p = p.next)
            ++count;
        return count;
    }

    /**
     * Returns a string identifying this CompletableFuture, as well as
     * its completion state.  The state, in brackets, contains the
     * String {@code "Completed Normally"} or the String {@code
     * "Completed Exceptionally"}, or the String {@code "Not
     * completed"} followed by the number of CompletableFutures
     * dependent upon its completion, if any.
     *
     * @return a string identifying this CompletableFuture, as well as its state
     */
    public String toString() {
        Object r = result;
        int count;
        return super.toString() +
            ((r == null) ?
             (((count = getNumberOfDependents()) == 0) ?
              "[Not completed]" :
              "[Not completed, " + count + " dependents]") :
             (((r instanceof AltResult) && ((AltResult)r).ex != null) ?
              "[Completed exceptionally]" :
              "[Completed normally]"));
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long RESULT;
    private static final long COMPLETIONS;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = CompletableFuture.class;
            RESULT = UNSAFE.objectFieldOffset
                (k.getDeclaredField("result"));
            COMPLETIONS = UNSAFE.objectFieldOffset
                (k.getDeclaredField("completions"));
        } catch (Exception x) {
            throw new Error(x);
        }
    }
}
