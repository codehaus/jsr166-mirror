/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;

/**
 * A synchronization aid that allows a set threads to all wait for
 * each other to reach a common barrier point.  CyclicBarriers are
 * useful in programs involving a fixed sized party of threads that
 * must occasionally wait for each other. The barrier is called
 * <em>cyclic</em> because it can be re-used after the waiting threads
 * are released.
 *
 * <p>A <tt>CyclicBarrier</tt> supports an optional {@link Runnable} command
 * that is run once per barrier point, after the last thread in the party
 * arrives, but before any threads are released. 
 * This <em>barrier action</em> is useful
 * for updating shared-state before any of the parties continue.
 * 
 * <p><b>Sample usage:</b> Here is an example of
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
 * <p>The <tt>CyclicBarrier</tt> uses an all-or-none breakage model
 * for failed synchronization attempts: If a thread leaves a barrier
 * point prematurely because of interruption or timeout, all others
 * will also leave abnormally (via {@link BrokenBarrierException}),
 * until the barrier is {@link #reset}. This is usually the simplest
 * and best strategy for sharing knowledge about failures among
 * cooperating threads in the most common usage contexts of barriers.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/08/05 22:50:28 $
 * @editor $Author: dl $
 * @see CountDownLatch
 *
 * @author Doug Lea
 */
public class CyclicBarrier {
    /** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();
    /** The number of parties */
    private final int parties;
    /* The command to run when tripped */
    private Runnable barrierCommand;

    /**
     * The generation number. Incremented mod Integer.MAX_VALUE every
     * time barrier tripped. Starts at 1 to simplify handling of
     * breakage indicator
     */
    private int generation = 1; 

    /** 
     * Breakage indicator: last generation of breakage, propagated
     * across barrier generations until reset. 
     */
    private int broken = 0; 

    /**
     * Number of parties still waiting. Counts down from parties to 0
     * on each cycle.
     */
    private int count; 

    /**
     * Update state on barrier trip.
     */  
    private void nextGeneration() {
        count = parties;
        int g = generation;
        // avoid generation == 0
        if (++generation < 0) generation = 1;
        // propagate breakage
        if (broken == g) broken = generation;
    }

    /**
     * Main barrier code, covering the various policies.
     */
    private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
        lock.lock();
        try {
            int index = --count;
            int g = generation;

            if (broken == g) 
                throw new BrokenBarrierException();

            if (Thread.interrupted()) {
                broken = g;
                trip.signalAll();
                throw new InterruptedException();
            }

            if (index == 0) {  // tripped
                nextGeneration();
                trip.signalAll();
                try {
                    if (barrierCommand != null) 
                        barrierCommand.run();
                    return 0;
                }
                catch (RuntimeException ex) {
                    broken = generation; // next generation is broken
                    throw ex;
                }
            }

            while (generation == g) {
                try {
                    if (!timed) 
                        trip.await();
                    else if (nanos > 0)
                        nanos = trip.awaitNanos(nanos);
                }
                catch (InterruptedException ex) {
                    // Only claim that broken if interrupted before reset
                    if (generation == g) { 
                        broken = g;
                        trip.signalAll();
                        throw ex;
                    }
                    else {
                        Thread.currentThread().interrupt(); // propagate
                        break;
                    }
                }
                
                if (timed && nanos <= 0) {
                    broken = g;
                    trip.signalAll();
                    throw new TimeoutException();
                }

                if (broken == g) 
                    throw new BrokenBarrierException();
                
            }
            return index;

        }
        finally {
            lock.unlock();
        }
    }

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
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties; 
        this.count = parties;
        this.barrierCommand = barrierAction;
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
        this(parties, null);
    }

    /**
     * Return the number of parties required to trip this barrier.
     * @return the number of parties required to trip this barrier.
     **/
    public int getParties() {
        return parties;
    }

    /**
     * Wait until all {@link #getParties parties} have invoked <tt>await</tt>
     * on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>Some other thread  {@link Thread#interrupt interrupts} one of the
     * other waiting threads; or
     * <li>Some other thread  times out while waiting for barrier; or
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
        try {
            return dowait(false, 0);
        }
        catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen;
        }
    }

    /**
     * Wait until all {@link #getParties parties} have invoked <tt>await</tt>
     * on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>The speceified timeout elapses; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>Some other thread  {@link Thread#interrupt interrupts} one of the
     * other waiting threads; or
     * <li>Some other thread  times out while waiting for barrier; or
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
     * @param timeout the time to wait for the barrier
     * @param unit the time unit of the timeout parameter
     * @return the arrival index of the current thread, where index
     *  <tt>{@link #getParties()} - 1</tt> indicates the first to arrive and 
     * zero indicates the last to arrive.
     *
     * @throws InterruptedException if the current thread was interrupted 
     * while waiting
     * @throws TimeoutException if the specified timeout elapses.
     * @throws BrokenBarrierException if <em>another</em> thread was
     * interrupted while the current thread was waiting, or the barrier was
     * reset, or the barrier was broken when <tt>await</tt> was called.
     */
    public int await(long timeout, TimeUnit unit) throws InterruptedException, BrokenBarrierException, TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * Query if this barrier is in a broken state.
     * @return <tt>true</tt> if one or more parties broke out of this
     * barrier due to interruption or timeout since construction or
     * the last reset; and <tt>false</tt> otherwise.
     */
    public boolean isBroken() {
        lock.lock();
        try {
            return broken >= generation;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Reset the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}.
     */
    public void reset() {
        lock.lock();
        try {
            int g = generation;
            nextGeneration();
            broken = g; // cause brokenness setting to stop at previous gen.
            trip.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Return the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     **/
    public int getNumberWaiting() {
        lock.lock();
        try {
            return parties - count;
        }
        finally {
            lock.unlock();
        }
    }

}


