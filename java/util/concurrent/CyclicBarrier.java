package java.util.concurrent;

/**
 * A <tt>CyclicBarrier</tt> allows a set threads to all wait for each 
 * other to reach a common barrier point.  They are useful in programs
 * involving a fixed sized party of threads that must occasionally
 * wait for each other. The barrier is <em>cyclic</em> because it can
 * be re-used after the waiting threads are released. 
 *
 * <p>A <tt>CyclicBarrier</tt> supports an optional {@link Runnable} command
 * that is run once per barrier point, after the last thread in the party
 * arrives, but before any threads are released. 
 * This <em>barrier action</em> is useful
 * for updating shared-state before any of the parties continue.
 * 
 * <p><b>Sample usage:</b> Here is a code sketch of 
 *  using a barrier in a parallel decomposition design:
 * <pre>
 * class Solver {
 *   final int N;
 *   final float[][] data;
 *   final CyclicBarrier barrier;
 *   
 *   class Worker implements Runnable {
 *     int myRow;
 *     Worker(int row) { myRow = row; }
 *     public void run() {
 *       while (!done()) {
 *         processRow(myRow);
 *
 *         try {
 *           barrier.await(); 
 *         }
 *         catch (InterruptedException ex) { return; }
 *         catch (BrokenBarrierException ex) { return; }
 *       }
 *     }
 *   }
 *
 *   public Solver(float[][] matrix) {
 *     data = matrix;
 *     N = matrix.length;
 *     barrier = new CyclicBarrier(N, 
 *                                 new Runnable() {
 *                                   public void run() { 
 *                                     mergeRows(...); 
 *                                   }
 *                                 });
 *     for (int i = 0; i < N; ++i) 
 *       new Thread(new Worker(i)).start();
 *
 *     waitUntilDone();
 *   }
 * }
 * </pre>
 * Here, each worker thread processes a row of the matrix then waits at the 
 * barrier until all rows have been processed. When all rows are processed
 * the supplied {@link Runnable} barrier action is executed and merges the 
 * rows. If the merger
 * determines that a solution has been found then <tt>done()</tt> will return
 * <tt>true</tt> and each worker will terminate.
 *
 * <p>If the barrier action does not rely on the parties being suspended when
 * it is executed, then any of the threads in the party could execute that
 * action when it is released. To facilitate this, each invocation of
 * {@link #await} returns the arrival index of that thread at the barrier.
 * You can then choose which thread should execute the barrier action, for 
 * example:
 * <pre>  if (barrier.await() == 0) {
 *     // log the completion of this iteration
 *   }</pre>
 *
 * <p>The <tt>CyclicBarrier</tt> uses an all-or-none breakage model for failed
 * synchronization attempts: If a thread leaves a barrier point
 * prematurely because of interruption, all others will also
 * leave abnormally (via {@link BrokenBarrierException}), until the barrier is
 * {@link #reset}. This is usually the simplest and best
 * strategy for sharing knowledge about failures among cooperating
 * threads in the most common usage contexts of barriers.  
 *
 * <h3>Implementation Considerations</h3>
 * <p>This implementation has the property that interruptions among newly
 * arriving threads can cause as-yet-unresumed threads from a previous
 * barrier cycle to return out as broken. This transmits breakage as
 * early as possible, but with the possible byproduct that only some
 * threads returning out of a barrier will realize that it is newly
 * broken. (Others will not realize this until a future cycle.)
 *
 *
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/28 06:56:53 $
 * @editor $Author: dholmes $
 *
 * @fixme Is the above property actually true in this implementation?
 * @fixme Should we have a timeout version of await()?
 */
public class CyclicBarrier {

    /**
     * Create a new <tt>CyclicBarrier</tt> that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     * before the barrier is tripped.
     * @param barrierAction the command to execute when the barrier is
     * tripped.
     *
     * @throws IllegalArgumentException if <tt>parties</tt> is less than 1.
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
    }

    /**
     * Create a new <tt>CyclicBarrier</tt> that will trip when the
     * given number of parties (threads) are waiting upon it.
     *
     * <p>This is equivalent to <tt>CyclicBarrier(parties, null)</tt>.
     *
     * @param parties the number of threads that must invoke {@link #await}
     * before the barrier is tripped.
     *
     * @throws IllegalArgumentException if <tt>parties</tt> is less than 1.
     */
    public CyclicBarrier(int parties) {
    }

    /**
     * Return the number of parties required to trip this barrier.
     * @return the number of parties required to trip this barrier.
     **/
    public int getParties() {
        return 0; // for now
    }

    /**
     * Wait until all {@link #getParties parties} have invoked <tt>await</tt>
     * on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of four things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>Some other thread  {@link Thread#interrupt interrupts} one of the
     * other waiting threads; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting, or if 
     * the barrier {@link #isBroken is broken} when <tt>await</tt> is invoked
     * then {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@link Thread#interrupt interrupted} while waiting,
     * then all other waiting threads will throw 
     * {@link BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to 
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread.
     *
     * @return the arrival index of the current thread, where index
     *  <tt>{@link #getParties()} - 1</tt> indicates the first to arrive and 
     * zero indicates the last to arrive.
     *
     * @throws InterruptedException if the current thread was interrupted 
     * while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     * interrupted while the current thread was waiting, or the barrier was
     * reset, or the barrier was broken when <tt>await</tt> was called.
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        return 0; // for now
    }

    /**
     * Query if this barrier is in a broken state.
     * @return <tt>true</tt> if one or more parties broke out of this
     * barrier due to interruption since construction or the last reset;
     * and <tt>false</tt> otherwise.
     */
    public boolean isBroken() {
        return false; // for now
    }

    /**
     * Reset the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException} before the barrier is fully reset.
     */
    public void reset() {
        // for now
    }

    /**
     * Return the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     **/
    public int getNumberWaiting() {
        return 0; // for now
    }

}


