package java.util.concurrent;

/**
 * An <tt>Exchanger</tt> provides a synchronization point at which two threads
 * can exchange objects.  Each thread presents some object on entry to
 * the {@link #exchange exchange} method, and receives the object presented by
 * the other thread on return.
 *
 * <p><b>Sample Usage:</b>
 * Here are the highlights of a class that uses an <tt>Exchanger</tt> to
 * swap buffers between threads so that the thread filling the
 * buffer gets a freshly
 * emptied one when it needs it, handing off the filled one to
 * the thread emptying the buffer.
 * <pre>
 * class FillAndEmpty {
 *   Exchanger&lt;Buffer&gt; exchanger = new Exchanger();
 *   Buffer initialEmptyBuffer = ... a made-up type
 *   Buffer initialFullBuffer = ...
 *
 *   class FillingLoop implements Runnable {
 *     public void run() {
 *       Buffer currentBuffer = initialEmptyBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           addToBuffer(currentBuffer);
 *           if (currentBuffer.full())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       }
 *       catch (InterruptedException ex) { }
 *     }
 *   }
 *
 *   class EmptyingLoop implements Runnable {
 *     public void run() {
 *       Buffer currentBuffer = initialFullBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           takeFromBuffer(currentBuffer);
 *           if (currentBuffer.empty())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       }
 *       catch (InterruptedException ex) { }
 *     }
 *   }
 *
 *   void start() {
 *     new Thread(new FillingLoop()).start();
 *     new Thread(new EmptyingLoop()).start();
 *   }
 * }
 * </pre>
 *
 * @fixme change example to use a bounded queue?
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/29 23:05:50 $
 * @editor $Author: dholmes $
 */
public class Exchanger<V> {

    /**
     * Create a new Exchanger
     **/
    public Exchanger() {
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * it is {@link Thread#interrupt interrupted}),
     * and then transfers the given object to it, receiving its object
     * in return.
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread. The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     * <p>If no other thread is already waiting at the exchange then the 
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of two things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for the exchange, 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     *
     * @param x the object to exchange
     * @return the object provided by the other thread.
     * @throws InterruptedException if current thread was interrupted while waiting
     **/
    public V exchange(V x) throws InterruptedException {
        return null; // for now
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * it is {@link Thread#interrupt interrupted}, or the specified waiting
     * time elapses),
     * and then transfers the given object to it, receiving its object
     * in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread. The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the 
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of three things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for the exchange, 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     *
     * <p>If the specified waiting time elapses then {@link TimeoutException}
     * is thrown.
     * The given waiting time is a best-effort lower bound. If the time is 
     * less than or equal to zero, the method will not wait at all.
     *
     * @param x the object to exchange
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the <tt>timeout</tt> argument.
     * @return the object provided by the other thread.
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeoutException if the specified waiting time elapses before
     * another thread enters the exchange.
     **/
    public V exchange(V x, long timeout, TimeUnit granularity) throws InterruptedException {
        return null; // for now
    }

}


