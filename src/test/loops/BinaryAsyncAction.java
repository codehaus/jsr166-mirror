/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * AsyncActions that are always linked in binary parent-child
 * relationships. Compared to Recursive tasks, BinaryAsyncActions may
 * have smaller stack space footprints and faster completion mechanics
 * but higher per-task footprints. Compared to LinkedAsyncActions,
 * BinaryAsyncActions are simpler to use and have less overhead in
 * typical usages but are restricted to binary computation trees.
 *
 * <p>Upon construction, a BinaryAsyncAction does not bear any
 * linkages. For non-root tasks, links must be established using
 * method {@link #linkSubtasks} before use.
 *
 * <p><b>Sample Usage.</b>  A version of Fibonacci:
 * <pre>
 * class Fib extends BinaryAsyncAction {
 *   final int n;
 *   int result;
 *   Fib(int n) { this.n = n; }
 *   protected void compute() {
 *     if (n &gt; 1) {
 *       linkAndForkSubtasks(new Fib(n-1), new Fib(n-2));
 *     else {
 *       result = n; // fib(0)==0; fib(1)==1
 *       complete();
 *     }
 *   }
 *   protected void onComplete(BinaryAsyncAction x, BinaryAsyncAction y) {
 *     result = ((Fib)x).result + ((Fib)y).result;
 *   }
 * }
 * </pre>
 * An alternative, and usually faster, strategy is to instead use a
 * loop to fork subtasks:
 * <pre>
 *   protected void compute() {
 *     Fib f = this;
 *     while (f.n &gt; 1) {
 *       Fib left = new Fib(f.n - 1);
 *       Fib right = new Fib(f.n - 2);
 *       f.linkSubtasks(left, right);
 *       right.fork(); // fork right
 *       f = left;     // loop on left
 *     }
 *     f.result = f.n;
 *     f.complete();
 *   }
 * }
 * </pre>
 */
public abstract class BinaryAsyncAction extends ForkJoinTask<Void> {
    private volatile int controlState;

    static final AtomicIntegerFieldUpdater<BinaryAsyncAction> controlStateUpdater =
        AtomicIntegerFieldUpdater.newUpdater(BinaryAsyncAction.class, "controlState");

    /**
     * Parent to propagate completion; nulled after completion to
     * avoid retaining entire tree as garbage
     */
    private BinaryAsyncAction parent;

    /**
     * Sibling to access on subtask joins, also nulled after completion.
     */
    private BinaryAsyncAction sibling;

    /**
     * Creates a new action. Unless this is a root task, you will need
     * to link it using method {@link #linkSubtasks} before forking as
     * a subtask.
     */
    protected BinaryAsyncAction() {
    }

    public final Void getRawResult() { return null; }
    protected final void setRawResult(Void mustBeNull) { }

    /**
     * Establishes links for the given tasks to have the current task
     * as parent, and each other as siblings.
     * @param x one subtask
     * @param y the other subtask
     * @throws NullPointerException if either argument is null
     */
    public final void linkSubtasks(BinaryAsyncAction x, BinaryAsyncAction y) {
        x.parent = y.parent = this;
        x.sibling = y;
        y.sibling = x;
    }

    /**
     * Overridable callback action triggered upon {@code complete} of
     * subtasks.  Upon invocation, both subtasks have completed.
     * After return, this task {@code isDone} and is joinable by
     * other tasks. The default version of this method does nothing.
     * But it may be overridden in subclasses to perform some action
     * (for example a reduction) when this task is completes.
     * @param x one subtask
     * @param y the other subtask
     */
    protected void onComplete(BinaryAsyncAction x, BinaryAsyncAction y) {
    }

    /**
     * Overridable callback action triggered by
     * {@code completeExceptionally}.  Upon invocation, this task has
     * aborted due to an exception (accessible via
     * {@code getException}). If this method returns {@code true},
     * the exception propagates to the current task's
     * parent. Otherwise, normal completion is propagated.  The
     * default version of this method does nothing and returns
     * {@code true}.
     * @return true if this task's exception should be propagated to
     * this task's parent
     */
    protected boolean onException() {
        return true;
    }

    /**
     * Equivalent in effect to invoking {@link #linkSubtasks} and then
     * forking both tasks.
     * @param x one subtask
     * @param y the other subtask
     */
    public void linkAndForkSubtasks(BinaryAsyncAction x, BinaryAsyncAction y) {
        linkSubtasks(x, y);
        y.fork();
        x.fork();
    }

    /** Basic per-task complete */
    private void completeThis() {
        super.complete(null);
    }

    /** Basic per-task completeExceptionally */
    private void completeThisExceptionally(Throwable ex) {
        super.completeExceptionally(ex);
    }

    /*
     * We use one bit join count on taskState. The first arriving
     * thread CAS's from 0 to 1. The second ultimately sets status
     * to signify completion.
     */

    /**
     * Completes this task, and if this task has a sibling that is
     * also complete, invokes {@code onComplete} of parent task, and so
     * on. If an exception is encountered, tasks instead
     * {@code completeExceptionally}.
     */
    public final void complete() {
        // todo: Use tryUnfork without possibly blowing stack
        BinaryAsyncAction a = this;
        for (;;) {
            BinaryAsyncAction s = a.sibling;
            BinaryAsyncAction p = a.parent;
            a.sibling = null;
            a.parent = null;
            a.completeThis();
            if (p == null || p.compareAndSetControlState(0, 1))
                break;
            try {
                p.onComplete(a, s);
            } catch (Throwable rex) {
                p.completeExceptionally(rex);
                return;
            }
            a = p;
        }
    }

    /**
     * Completes this task abnormally. Unless this task already
     * cancelled or aborted, upon invocation, this method invokes
     * {@code onException}, and then, depending on its return value,
     * completes parent (if one exists) exceptionally or normally.  To
     * avoid unbounded exception loops, this method aborts if an
     * exception is encountered in any {@code onException}
     * invocation.
     * @param ex the exception to throw when joining this task
     * @throws NullPointerException if ex is null
     * @throws Throwable if any invocation of
     * {@code onException} does so
     */
    public final void completeExceptionally(Throwable ex) {
        BinaryAsyncAction a = this;
        while (!a.isCompletedAbnormally()) {
            a.completeThisExceptionally(ex);
            BinaryAsyncAction s = a.sibling;
            if (s != null)
                s.cancel(false);
            if (!a.onException() || (a = a.parent) == null)
                break;
        }
    }

    /**
     * Returns this task's parent, or null if none or this task
     * is already complete.
     * @return this task's parent, or null if none
     */
    public final BinaryAsyncAction getParent() {
        return parent;
    }

    /**
     * Returns this task's sibling, or null if none or this task is
     * already complete.
     * @return this task's sibling, or null if none
     */
    public BinaryAsyncAction getSibling() {
        return sibling;
    }

    /**
     * Resets the internal bookkeeping state of this task, erasing
     * parent and child linkages.
     */
    public void reinitialize() {
        parent = sibling = null;
        super.reinitialize();
    }

    /**
     * Gets the control state, which is initially zero, or negative if
     * this task has completed or cancelled. Once negative, the value
     * cannot be changed.
     * @return control state
     */
    protected final int getControlState() {
        return controlState;
    }

    /**
     * Atomically sets the control state to the given updated value if
     * the current value is and equal to the expected value.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful
     */
    protected final boolean compareAndSetControlState(int expect,
                                                      int update) {
        return controlStateUpdater.compareAndSet(this, expect, update);
    }

    /**
     * Attempts to set the control state to the given value, failing if
     * this task is already completed or the control state value would be
     * negative.
     * @param value the new value
     * @return true if successful
     */
    protected final void setControlState(int value) {
        controlState = value;
    }

    /**
     * Increments the control state.
     * @param value the new value
     */
    protected final void incrementControlState() {
        controlStateUpdater.incrementAndGet(this);
    }

    /**
     * Decrements the control state.
     * @return true if successful
     */
    protected final void decrementControlState() {
        controlStateUpdater.decrementAndGet(this);
    }

}
