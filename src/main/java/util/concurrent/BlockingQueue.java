/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.Queue;

/**
 * A {@link java.util.Queue} that additionally supports operations
 * that wait for elements to exist when retrieving them, and wait for
 * space to exist when putting them.
 *
 * <p> <tt>BlockingQueues</tt> do not accept <tt>null</tt> elements.
 * Implementations throw <tt>IllegalArgumentException</tt> on attempts
 * to <tt>add</tt>, <tt>put</tt> or <tt>offer</tt> a <tt>null</tt>.  A
 * <tt>null</tt> is used as a sentinel value to indicate failure of
 * <tt>poll</tt> operations.
 *
 * <p><tt>BlockingQueues</tt> may be capacity bounded. At any given
 * time they may have a <tt>remainingCapacity</tt> beyond which no
 * additional elements can be <tt>put</tt> without blocking.
 * BlockingQueues without any intrinsic capacity constraints always
 * report a remaining capacity of <tt>Integer.MAX_VALUE</tt>.
 *
 * <p> While <tt>BlockingQueues</tt> are designed to be used primarily
 * as producer-consumer queues, they additionally support the
 * <tt>Collection</tt> interface.  So, for example, it is possible to
 * remove an arbitrary element from within a queue using
 * <tt>remove(x)</tt>. However, such operations are in general
 * <em>NOT</em> performed very efficiently, and are intended for only
 * occasional use, such as when a queued message is cancelled.  Also,
 * the bulk operations, most notably <tt>addAll</tt> are <em>NOT</em>
 * performed atomically, so it is possible for <tt>addAll(c)</tt> to
 * fail (throwing an exception) after adding only some of the elements
 * in <tt>c</tt>.
 *
 * <p><tt>BlockingQueue</tt>s do <em>not</em> intrinsically support
 * any kind of &quot;close&quot; or &quot;shutdown&quot; operation to
 * indicate that no more items will be added.  The needs and usage of
 * such features tend to be implementation-dependent. For example, a
 * common tactic is for producers to insert special
 * <em>end-of-stream</em> or <em>poison</em> objects, that are
 * interpreted accordingly when taken by consumers.
 *
 * <p>
 * Usage example, based on a typical producer-consumer scenario.
 * Note that Blocking queues can safely be used with multiple producers
 * and multiple consumers.
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
 *     Consumer c1 = new Consumer(q);
 *     Consumer c2 = new Consumer(q);
 *     new Thread(p).start();
 *     new Thread(c1).start();
 *     new Thread(c2).start();
 *   }
 * }
 * </pre>
 *
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/06/24 14:34:47 $
 * @editor $Author: dl $
 * @author Doug Lea
 */
public interface BlockingQueue<E> extends Queue<E> {
    /**
     * Retrieve and remove the first element from the queue, waiting
     * if no objects are present on the queue.
     * @return the object
     * @throws InterruptedException if interrupted while waiting.
     */
    E take() throws InterruptedException;

    /**
     * Retrieve and remove the first element from the queue, waiting
     * if necessary up to a specified wait time if no objects are
     * present on the queue.
     * @param timeout how long to wait before giving up, in units of
     * <tt>unit</tt>
     * @param unit a TimeUnit determining how to interpret the timeout
     * parameter
     * @return the object, or <tt>null</tt> if the specified waiting
     * time elapses before an object is present.
     * @throws InterruptedException if interrupted while waiting.
     */
    E poll(long timeout, TimeUnit unit) 
        throws InterruptedException;

    /**
     * Add the given object to the queue, waiting if necessary for
     * space to become available.
     * @param x the object to add
     * @throws InterruptedException if interrupted while waiting.
     */
    void put(E x) throws InterruptedException;

    /**
     * Add the given object to the queue, waiting if necessary up to a
     * specified wait time for space to become available.
     * @param x the object to add
     * @param timeout how long to wait before giving up, in units of
     * <tt>unit</tt>
     * @param unit a TimeUnit determining how to interpret the timeout
     * parameter
     * @return <tt>true</tt> if successful, or <tt>false</tt> if
     * the specified waiting time elapses before space is available.
     * @throws InterruptedException if interrupted while waiting.
     */
    boolean offer(E x, long timeout, TimeUnit unit) 
        throws InterruptedException;

    /**
     * Return the number of elements that this queue can ideally (in
     * the absence of memory or resource constraints) accept without
     * blocking, or <tt>Integer.MAX_VALUE</tt> if there is no
     * intrinsic limit.  Note that you <em>cannot</em> always tell if
     * an attempt to <tt>add</tt> an element will succeed by
     * inspecting <tt>remainingCapacity</tt> because it may be the
     * case that a waiting consumer is ready to <tt>take</tt> an
     * element out of an otherwise full queue.
     * @return the remaining capacity
     */
    int remainingCapacity();

}
