package java.util.concurrent;

import java.util.Queue;

/**
 * A <tt>BlockingQueue</tt> is a {@link java.util.Queue} that additionally 
 * supports
 * operations that wait for elements to exist when taking them, and/or
 * wait for space to exist when putting them.
 *
 * <p><tt>BlockingQueue</tt> implementations might or might not have bounded
 * capacity or other insertion constraints, so in general, you cannot
 * tell if a given <tt>put</tt> will block.
 *
 * <p>A <tt>BlockingQueue</tt> should not accept <tt>null</tt> elements as
 * <tt>null</tt> is used as a sentinel value to indicate failure of
 * <tt>poll</tt> operations. If it necessary to allow <tt>null</tt> as an
 * element then it is the responsibility of the client code to deal with
 * this ambiguity. It is preferable to use a special object to represent 
 * &quot;no element&quot; rather than using the value <tt>null</tt>.
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
 *     catch (InterruptedException ex) { ... handle ...}
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
 *     catch (InterruptedException ex) { ... handle ...}
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
 * <p><tt>BlockingQueue</tt>s do <em>not</em> intrinsically support any kind 
 * of &quot;close&quot; or &quot;shutdown&quot; operation to indicate that 
 * no more items will be added. 
 * The needs and usage of such features tend to be implementation
 * dependent. For example, a common tactic is for producers to insert
 * special <em>end-of-stream</em> or <em>poison</em> objects, that are 
 * interpreted accordingly when taken by consumers.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/03/31 03:50:08 $
 * @editor $Author: dholmes $
 */
public interface BlockingQueue<E> extends Queue<E> {
    /**
     * Take an object from the queue, waiting if necessary for
     * an object to be present.
     * @return the object
     * @throws InterruptedException if interrupted while waiting.
     */
    public E take() throws InterruptedException;

    /**
     * Take an object from the queue if one is available within given wait 
     * time
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @return the object, or <tt>null</tt> if the specified
     * waiting time elapses before an object is present.
     * @throws InterruptedException if interrupted while waiting.
     */
    public E poll(long timeout, TimeUnit granularity) 
        throws InterruptedException;

    /**
     * Add the given object to the queue, waiting if necessary for
     * space to become available.
     * @param x the object to add
     * @throws InterruptedException if interrupted while waiting.
     */
    public void put(E x) throws InterruptedException;

    /**
     * Add the given object to the queue if space is available within
     * given wait time.
     * @param x the object to add
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument.
     * @return <tt>true</tt> if successful, or <tt>false</tt> if 
     * the specified waiting time elapses before space is available.
     * @throws InterruptedException if interrupted while waiting.
     */
    public boolean offer(E x, long timeout, TimeUnit granularity) 
        throws InterruptedException;

}
