package java.util.concurrent;

/**
 * A CyclicBarrier allows a set threads to all wait for each other to
 * reach a common barrier point.  They are useful in programs
 * involving a fixed sized group of threads that must occasionally
 * wait for each other.
 *
 * <p> CyclicBarriers use an all-or-none breakage model for failed
 * synchronization attempts: If threads leave a barrier point
 * prematurely because of timeout or interruption, others will also
 * leave abnormally (via BrokenBarrierException), until the barrier is
 * <code>restart</code>ed. This is usually the simplest and best
 * strategy for sharing knowledge about failures among cooperating
 * threads in the most common usages contexts of Barriers.  This
 * implementation has the property that interruptions among newly
 * arriving threads can cause as-yet-unresumed threads from a previous
 * barrier cycle to return out as broken. This transmits breakage as
 * early as possible, but with the possible byproduct that only some
 * threads returning out of a barrier will realize that it is newly
 * broken. (Others will not realize this until a future cycle.)
 *
 * <p>
 * Barriers support an optional Runnable command
 * that is run once per barrier point.
 * <p>
 * <b>Sample usage</b> Here is a code sketch of 
 *  a  barrier in a parallel decomposition design.
 * <pre>
 * class Solver {
 *   final int N;
 *   final float[][] data;
 *   final CyclicBarrier barrier;
 *   
 *   class Worker implements Runnable {
 *      int myRow;
 *      Worker(int row) { myRow = row; }
 *      public void run() {
 *         while (!done()) {
 *            processRow(myRow);
 *
 *            try {
 *              barrier.await(); 
 *            }
 *            catch (InterruptedException ex) { return; }
 *            catch (BrokenBarrierException ex) { return; }
 *         }
 *      }
 *   }
 *
 *   public Solver(float[][] matrix) {
 *     data = matrix;
 *     N = matrix.length;
 *     barrier = new CyclicBarrier(N, 
 *      new Runnable() {
 *       public void run() { mergeRows(...); }
 *     });
 *     for (int i = 0; i < N; ++i) {
 *       new Thread(new Worker(i)).start();
 *     waitUntilDone();
 *    }
 * }
 * </pre>
 **/
public class CyclicBarrier {
    /**
     * Create a new cyclic barrier for the given number of parties
     * (threads required to trip barrier), and the given action
     * to perform every time the barrier is tripped.
     * @throws IllegalArgumentException if parties is less than 1.
     **/
    public CyclicBarrier(int parties, Runnable barrierAction) {
    }

    /**
     * Create a new cyclic barrier for the given number of parties
     * (threads required to trip barrier), and a null action.
     **/
    public CyclicBarrier(int parties) {
    }

    /**
     * Return the number of parties (threads required to trip barrier).
     **/
    public int getParties() {
        return 0; // for now
    }

    /**
     * Wait until all parties have arrived at the barrier. If the
     * current thread is the last thread to arrive, and a
     * non-null barrier action supplied in concstructor, the
     * thread runs the action before allowing other threads to continue.
     * @return the arrival index of the current thread, where index
     *  <tt>parties - 1<tt> indicates the first to arrive and zero
     * indicates the last to arrive.
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     * interrupted while waiting.
     **/
    public int await() throws InterruptedException, BrokenBarrierException {
        return 0; // for now
    }

    /**
     * Return true if one or more parties broke out of this
     * barrier due to interruption since construction or last reset.
     **/
    public boolean isBroken() {
        return false; // for now
    }

    /**
     * Reset the barrier to its initial state.  If Any threads are
     * currently waiting at the barrier, they will return with a
     * BrokenBarrierException before the barrier if fully reset.
     **/
    public void reset() {
        // for now
    }
}
