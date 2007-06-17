/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;

/**
 * A computation that is broken into a series of task executions, each
 * separated by a TaskBarrier arrival.  Concrete subclasses must
 * define method <tt>compute</tt>, that performs the action occurring
 * at each step of the barrier.  Upon invocation of this task, the
 * <tt>compute</tt> method is repeatedly invoked until the barrier
 * <tt>isTerminated</tt> or until its execution throws an exception.
 *
 * <p> <b>Sample Usage.</b> Here is a sketch of a set of CyclicActions
 * that each perform 500 iterations of an imagined image smoothing
 * operation. Note that the aggregate ImageSmoother task itself is not
 * a CyclicTask.
 * 
 * <pre>
 * class ImageSmoother extends RecursiveAction {
 *   protected void compute() {
 *     TaskBarrier b = new TaskBarrier() {
 *       protected boolean terminate(int cycle, int registeredParties) {
 *          return registeredParties &lt;= 0 || cycle &gt;= 500;
 *       }
 *     }
 *     int n = pool.getPoolSize();
 *     CyclicAction[] actions = new CyclicAction[n];
 *     for (int i = 0; i &lt; n; ++i) {
 *       action[i] = new CyclicAction(b) {
 *         protected void compute() {
 *           smoothImagePart(i);
 *         }
 *       }
 *     }
 *     for (int i = 0; i &lt; n; ++i) 
 *       actions[i].fork();
 *     for (int i = 0; i &lt; n; ++i) 
 *       actions[i].join();
 *   }
 * }
 * </pre>
 */
public abstract class CyclicAction extends ForkJoinTask<Void> {
    final TaskBarrier barrier;
    int phase = -1;

    final RuntimeException exec() {
        TaskBarrier b = barrier;
        RuntimeException ex = exception;
        if (ex != null) {
            if (status >= 0)
                b.arriveAndDeregister();
            return setDone();
        }
        if (phase < 0)
            phase = b.getCycle();
        else
            phase = b.awaitCycleAdvance(phase);
        if (phase < 0)
            return setDone();
        try {
            compute();
        } catch (RuntimeException rex) {
            b.arriveAndDeregister();
            casException(rex);
            return setDone();
        }
        b.arrive();
        this.fork();
        return null;
    }

    /**
     * Constructs a new CyclicAction using the supplied barrier,
     * registering for this barrier upon construction.
     * @param barrier the barrier
     */
    public CyclicAction(TaskBarrier barrier) {
        this.barrier = barrier;
        barrier.register();
    }


    /**
     * The computation performed by this task on each cycle of the
     * barrier.  While you must define this method, you should not in
     * general call it directly.
     */
    protected abstract void compute();

    /**
     * Returns the barrier
     */
    public final TaskBarrier getBarrier() { return barrier; }

    /**
     * Returns the current cycle of the barrier
     */
    public final int getCycle() { return barrier.getCycle(); }


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
        fork();
        return join();
    }

}
