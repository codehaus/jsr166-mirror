/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util;

/**
 * A collection designed for holding elements prior to processing.
 * Besides basic {@link java.util.Collection Collection} operations, queues provide
 * additional insertion, extraction, and inspection operations.
 *
 * <p>Queues typically, but do not necessarily, order elements in a
 * FIFO (first-in-first-out) manner.  Among the exceptions are
 * priority queues, which order elements according to a supplied
 * comparator, or the elements' natural ordering, and LIFO queues (or
 * stacks) which order the elements LIFO (last-in-first-out).
 * Whatever the ordering used, the <em>head</em> of the queue is that element
 * which would be removed by a call to {@link #remove() } or {@link #poll()}.
 * Every <tt>Queue</tt> implementation must specify its ordering guarantees.
 *
 * <p>The {@link #offer offer} method adds an element if possible, otherwise
 * returning <tt>false</tt>.  This differs from the 
 * {@link java.util.Collection#add Collection.add}
 * method, which throws an unchecked exception upon
 * failure. It is designed for use in collections in which failure to
 * add is a normal, rather than exceptional occurrence, for example,
 * in fixed-capacity (or &quot;bounded&quot;) queues.
 *
 * <p>The {@link #remove()} and {@link #poll()} methods remove and
 * return the head of the queue.
 * Exactly which element is removed from the queue is a
 * function of the queue's ordering policy, which differs from
 * implementation to implementation. The <tt>remove()</tt> and
 * <tt>poll()</tt> methods differ only in their behavior when the
 * queue is empty: the <tt>remove()</tt> method throws an exception,
 * while the <tt>poll()</tt> method returns <tt>null</tt>.
 *
 * <p>The {@link #element()} and {@link #peek()} methods return, but do
 * not remove, the head of the queue.
 *
 * <p>The <tt>Queue</tt> interface does not define the <i>blocking queue
 * methods</i>, which are common in concurrent programming.  These methods,
 * which wait for elements to appear or for space to become available, are
 * defined in the {@link java.util.concurrent.BlockingQueue} interface, which
 * extends this interface.
 *
 * <p><tt>Queue</tt> implementations generally do not allow insertion
 * of <tt>null</tt> elements, although some implementations, such as
 * {@link LinkedList}, do not prohibit insertion of <tt>null</tt>.
 * Even in the implementations that permit it, <tt>null</tt> should
 * not be inserted into a <tt>Queue</tt>, as <tt>null</tt> is also
 * used as a special return value by the <tt>poll</tt> method to
 * indicate that the queue contains no elements.
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @see java.util.Collection
 * @see LinkedList
 * @see PriorityQueue
 * @see java.util.concurrent.LinkedBlockingQueue
 * @see java.util.concurrent.BlockingQueue
 * @see java.util.concurrent.ArrayBlockingQueue
 * @see java.util.concurrent.LinkedBlockingQueue
 * @see java.util.concurrent.PriorityBlockingQueue
 * @since 1.5
 * @author Doug Lea
 */
public interface Queue<E> extends Collection<E> {

    /**
     * Adds the specified element to this queue, if possible.
     *
     * @param o the element to add.
     * @return <tt>true</tt> if it was possible to add the element to
     * this queue, else <tt>false</tt>
     */
    boolean offer(E o);

    /**
     * Retrieves and removes the head of this queue, if it is available.
     *
     * @return the head of this queue, or <tt>null</tt> if this
     *         queue is empty.
     */
    E poll();

    /**
     * Retrieves and removes the head of this queue.
     * This method differs
     * from the <tt>poll</tt> method in that it throws an exception if this
     * queue is empty.
     *
     * @return the head of this queue.
     * @throws NoSuchElementException if this queue is empty.
     */
    E remove();

    /**
     * Retrieves, but does not remove, the head of this queue.
     * This method differs from the <tt>poll</tt>
     * method only in that this method does not remove the head element from
     * this queue.
     *
     * @return the head of this queue, or <tt>null</tt> if this queue is empty.
     */
    E peek();

    /**
     * Retrieves, but does not remove, the head of this queue.  This method
     * differs from the <tt>peek</tt> method only in that it throws an
     * exception if this queue is empty.
     *
     * @return the head of this queue.
     * @throws NoSuchElementException if this queue is empty.
     */
    E element();
}










