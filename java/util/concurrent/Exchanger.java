package java.util.concurrent;

/**
 * An Exchanger provides a synchronization point at which two threads
 * may exchange objects.  Each thread presents some object on entry to
 * the <tt>exchange<tt> method, and returns the object presented by
 * the other thread on release.
 *
 * <p>
 * <b>Sample Usage</b><p>
 * Here are the highlights of a class that uses a Exchanger to
 * swap buffers between threads so that the thread filling the
 * buffer  gets a freshly
 * emptied one when it needs it, handing off the filled one to
 * the thread emptying the buffer.
 * <pre>
 * class FillAndEmpty {
 *   Exchanger exchanger = new Exchanger();
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
 *             currentBuffer = (Buffer)(exchanger.exchange(currentBuffer));
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
 *             currentBuffer = (Buffer)(exchanger.exchange(currentBuffer));
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
 **/
public class Exchanger<V> {

    /**
     * Create a new Exchanger
     **/
    public Exchanger() {
    }

    /**
     * Wait for another thread to arrive at this exchange point,
     * and then transfer the given Object to it, and vice versa.
     * @param x the object to exchange
     * @return the value provided by the other thread.
     * @throws InterruptedException if current thread was interrupted while waiting
     **/
    public V exchange(V x) throws InterruptedException {
        return null; // for now
    }

    /**
     * Wait for another thread to arrive at this exchange point,
     * and then transfer the given Object to it, and vice versa,
     * unless time-out occurs first.
     * @param x the object to exchange
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @return the value provided by the other thread.
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeoutException if timed out while waiting.
     **/
    public V exchange(V x, long timeout, TimeUnit granularity) throws InterruptedException {
        return null; // for now
    }

}
