package java.util.concurrent;

import java.util.Queue;

/**
 * BlockingQueues are java.util.Queues that additionally support
 * operations that wait for elements to exist when taking them, and/or
 * wait for space to exist when putting them.
 *
 * <p> BlockingQueue implementations might or might not have bounded
 * capacity or other insertion constraints, so in general, you cannot
 * tell if a given put will block.
 *
 * <p>
 * Usage example. Here is a sketch of a classic producer-consumer program.
 * <pre>
 * class Producer implements Runnable {
 *   private final BlockingQueue queue;
 *   Producer(BlockingQueue q) { queue = q; }
 *   public void run() {
 *     try {
 *       while(true) { queue.put(produce()); }
 *     }
 *     catch (InterruptedException ex) {}
 *   }
 *   Object produce() { ... }
 * }
 *
 *
 * class Consumer implements Runnable {
 *   private final BlockingQueue queue;
 *   Concumer(BlockingQueue q) { queue = q; }
 *   public void run() {
 *     try {
 *       while(true) { consume(queue.take()); }
 *     }
 *     catch (InterruptedException ex) {}
 *   }
 *   void consume(Object x) { ... }
 * }
 *
 * class Setup {
 *   void main() {
 *     BlockingQueue q = new SomeQueueImplementation();
 *     Producer p = new Producer(q);
 *     Consumer c = new Consumer(q);
 *     new Thread(p).start();
 *     new Thread(c).start();
 *   }
 * }
 * </pre>
 *
 * <p> BlockingQueues do NOT intrinsically support any kind of "close"
 * or "shutdown" operation to indicate that no more items will be
 * added. The needs and usage of such features tend to be implementation
 * dependent. For example, a common tactic is for producers to insert
 * special EndOfStream or Poison objects, that are interpreted
 * accordingly when taken by consumers.

 **/
public interface BlockingQueue<E> extends Queue<E> {
    /**
     * Take an object from the queue, waiting if necessary for
     * an object to be present.
     * @return the object
     * @throws InterruptedException if interrupted while waiting.
     **/
    public E take() throws InterruptedException;

    /**
     * Take an object from the queue if one is available within given wait timeout
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @return the object, or null if the queue is empty.
     * @throws InterruptedException if interrupted while waiting.
     * @throws TimeoutException if timed out while waiting.
     **/
    public E poll(long timeout, TimeUnit granularity) throws InterruptedException;

    /**
     * Add the given object to the queue, waiting if necessary for
     * space to become available.
     * @param x the object to add
     * @throws InterruptedException if interrupted while waiting.
     **/
    public void put(E x) throws InterruptedException;

    /**
     * Add the given object to the queue if space is available within
     * given wait time.
     * @param x the object to add
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @return true if successful
     * @throws InterruptedException if interrupted while waiting.
     * @throws TimeoutException if timed out while waiting.
     **/
    public boolean offer(E x, long timeout, TimeUnit granularity) throws InterruptedException;

}
