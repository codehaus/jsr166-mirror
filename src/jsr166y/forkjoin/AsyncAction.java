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
 * Resultless ForkJoinTasks with explicit completions.  Unlike other
 * kinds of tasks, AsyncActions do not intrinisically complete upon
 * exit from their <tt>compute</tt> methods, but instead require
 * explicit invocation of their <tt>finish</tt> methods and completion
 * of subtasks.
 *
 * <p> Upon construction, an AsyncAction may register as a subtask of
 * a given parent task. In this case, completion of this task
 * will propagate to its parent. If the parent's pending completion
 * count becomes zero, it too will finish.
 *
 * <p> In addition to supporting this different computation style
 * compared to Recursive tasks, AsyncActions will often have smaller
 * footprints while executing (in particular, they do not require
 * stack space recursively awaiting joins), at the expense of somewhat
 * greater per-task overhead.
 *
 * <p> <b>Sample Usage.</b> Here is a sketch of an AsyncAction that
 * visits all of the nodes of a graph. The details of the graph's Node
 * and Edge classes are omitted, but we assume each node contains an
 * <tt>AtomicBoolean</tt> mark that starts out false. To execute this,
 * you would create a GraphVisitor for the root node with null parent,
 * and <tt>invoke</tt> in a ForkJoinPool. Upon return, all reachable
 * nodes will have been visited.
 * 
 * <pre>
 * class GraphVisitor extends AsyncAction {
 *    final Node node;
 *    GraphVisitor(GraphVistor parent, Node node) {
 *      super(parent); this.node = node;
 *    }
 *    protected void compute() {
 *      if (node.mark.compareAndSet(false, true)) {
 *         for (Edge e : node.edges()) {
 *            Node dest = e.getDestination();
 *            if (!dest.mark.get())
 *               new GraphVisitor(this, dest).fork();
 *         }
 *         visit(node);
 *      }
 *      finish();
 *   }
 * }
 * </pre>
 *
 * <p> While perhaps typical, it is not necessary for each task to
 * <tt>finish</tt> itself. For example, it is possible to treat one
 * subtask as a "continuation" of the current task by not registering
 * it on construction.  In this case, a <tt>finish</tt> of the subtask
 * will trigger <tt>finish</tt> of the parent without the parent
 * explicitly doing so. This is often a bit more efficient.
 */
public abstract class AsyncAction extends ForkJoinTask<Void> {
    /**
     * Parent to notify on completion
     */
    private AsyncAction parent;

    /**
     * Count of outstanding subtask joins
     */
    private volatile int pendingCount;

    private static final AtomicIntegerFieldUpdater<AsyncAction> pendingCountUpdater =
        AtomicIntegerFieldUpdater.newUpdater(AsyncAction.class, "pendingCount");

    /**
     * Creates a new action with the given parent. If the parent
     * is non-null, this tasks registers with the parent, in
     * which case, the parent task cannot complete until this
     * task completes.
     * @param parent the parent task, or null if none
     */
    protected AsyncAction(AsyncAction parent) {
        this.parent = parent;
        if (parent != null) 
            pendingCountUpdater.incrementAndGet(parent);
    }

    /**
     * Creates a new action with the given parent, optionally
     * registering with the parent. If the parent is non-null and
     * <tt>register</tt> is true, this tasks registers with the
     * parent, in which case, the parent task cannot complete until
     * this task completes.
     * @param parent the parent task, or null if none
     * @param register true if parent must wait for this task
     * to complete before it completes
     */
    protected AsyncAction(AsyncAction parent, boolean register) {
        this.parent = parent;
        if (parent != null && register) 
            pendingCountUpdater.incrementAndGet(parent);
    }

    /**
     * The asynchronous part of the computation performed by this
     * task.  While you must define this method, you should not in
     * general call it directly. If this method throws a
     * RuntimeException, <tt>finishExceptionally</tt> is immediately invoked.
     */
    protected abstract void compute();

    /**
     * Callback action triggered by <tt>finish</tt>.  Upon invocation,
     * all subtasks have completed.  After return, this task
     * <tt>isDone</tt> and is joinable by other tasks. The default
     * version of this method does nothing. But it may may be
     * overridden in subclasses to perform some action when this task
     * and all of its subtasks are about to complete.
     */
    protected void onCompletion() {
    }

    /**
     * Returns this task's parent, or null if none.
     * @return this task's parent, or null if none.
     */
    public AsyncAction getParent() { 
        return parent; 
    }

    /**
     * Returns the number of subtasks that have not yet completed.
     * @return the number of subtasks that have not yet completed.
     */
    public int getPendingSubtaskCount() { 
        return pendingCount; 
    }

    /**
     * If pending completion count is zero, invokes
     * <tt>onCompletion</tt>, then causes this task to be joinable
     * (<tt>isDone</tt> becomes true), and then recursively applies to
     * this tasks's parent. If an exception is encountered in any
     * <tt>onCompletion</tt> invocation, that task and its ancestors
     * <tt>finishExceptionally</tt>.
     */
    public final void finish() {
        AsyncAction a = this;
        while (a != null) {
            int c = a.pendingCount;
            if (c == 0) {
                try {
                    a.onCompletion();
                } catch (RuntimeException rex) {
                    a.finishExceptionally(rex);
                    return;
                } 
                a.setDone();
                a = a.parent;
            }
            else if (pendingCountUpdater.compareAndSet(a, c, c-1))
                return;
        }
    }

    /**
     * Causes this task and its parent (and further ancestors) to be
     * immediately joinable, reporting or throwing the given
     * exception. Has no effect if this task has already terminated
     * exceptionally.  To avoid the possibility of unbounded exception
     * loops, the <tt>onCompletion</tt> method is <em>not</em>
     * invoked.
     * @param rex the exception to throw when joining this task
     */
    public final void finishExceptionally(RuntimeException rex) {
        AsyncAction a = this;
        while (a != null) {
            if (!exceptionUpdater.compareAndSet(a, null, rex))
                break;
            a = a.parent;
        }
    }

    final RuntimeException exec() {
        if (exception == null) {
            try {
                compute();
            } catch(RuntimeException rex) {
                finishExceptionally(rex);
            }
        }
        return exception;
    }


    public final Void join() {
        return ((ForkJoinPool.Worker)(Thread.currentThread())).joinAction(this);
    }

    /**
     * Always returns null.
     * @return null
     */
    public final Void getResult() { 
        return null; 
    }

    public final Void invoke() {
        exec();
        return ((ForkJoinPool.Worker)(Thread.currentThread())).joinAction(this);
    }

    public void reinitialize() {
        super.reinitialize();
        if (pendingCount != 0)
            pendingCount = 0;
    }

    /**
     * Reinitialize with the given parent, optionally registering.
     * @param parent the parent task, or null if none
     * @param register true if parent must wait for this task
     * to complete before it completes
     */
    public void reinitialize(AsyncAction parent, boolean register) {
        super.reinitialize();
        if (pendingCount != 0)
            pendingCount = 0;
        this.parent = parent;
        if (parent != null && register) 
            pendingCountUpdater.incrementAndGet(parent);
    }

}
