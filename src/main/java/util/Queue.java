/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util;

/**
 * A Collection designed for holding elements prior to processing.
 * Besides basic {@link Collection} operations, queues provide
 * additional insertion, extraction, and inspection operations.
0 *
 * <p>Queues typically, but do not necessarily, order elements in a
 * FIFO (first-in-first-out) manner.  Among the exceptions are
 * priority queues, which order elements according to a supplied
 * comparator, or the elements' natural ordering.  Every Queue
 * implementation must specify its ordering guarantees.
 *
 * <p>The {@link #offer(E)} method adds an element if possible, otherwise
 * returning <tt>false</tt>.  This differs from the {@link
 * Collections#add(Object)} method, which throws an unchecked exception upon
 * failure. It is designed for use in collections in which failure to
 * add is a normal, rather than exceptional occurrence, for example,
 * in fixed-capacity (or &ldquo;bounded&rdquo;) queues.

 * 
 * <p>The {@link #remove()} and {@link #poll()} methods remove and return an
 * element in accord with the implementation's ordering policy. 
 * Exactly which element is removed from the queue is a function
 * of the queue's ordering policy, which differs from implementation
 * to implementation.  Possible orderings include (but are not limited
 * to) first-in-first-out (FIFO), last-in-first-out (LIFO), element priority, and arbitrary.
 * The <tt>remove()</tt> and <tt>poll()</tt> methods differ only in their
 * behavior when the queue is empty: the <tt>remove()</tt> method throws an
 * exception, while the <tt>poll()</tt> method returns <tt>null</tt>.
 *
 * <p>The {@link #element()} and {@link #peek()} methods return but do
 * not delete the element that would be obtained by a call to
 * the <tt>remove</tt> and <tt>poll</tt> methods respectively.
 *
 * <p>The <tt>Queue</tt> interface does not define the <i>blocking queue
 * methods</i>, which are common in concurrent programming.  These methods,
 * which wait for elements to appear or for space to become available, are
 * defined in the {@link java.util.concurrent.BlockingQueue} interface, which
 * extends this interface.
 *
 * <p><tt>Queue</tt> implementations generally do not allow insertion of
 * <tt>null</tt> elements, although some implementations, such as
 * {@link LinkedList}, do not prohibit insertion of <tt>null</tt>.
 * Even in the implementations that permit it, <tt>null</tt> should not be inserted into
 * a <tt>Queue</tt>, as <tt>null</tt> is also used as a special return value
 * by the <tt>poll</tt> method to indicate that the queue contains no
 * elements.
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @see Collection
 * @see LinkedList
 * @see PriorityQueue
 * @see java.util.concurrent.LinkedQueue
 * @see java.util.concurrent.BlockingQueue
 * @see java.util.concurrent.ArrayBlockingQueue
 * @see java.util.concurrent.LinkedBlockingQueue
 * @see java.util.concurrent.PriorityBlockingQueue
 */
public interface Queue<E> extends Collection<E> {
    /**
     * Add the specified element to this queue, if possible.
     *
     * @param element the element to add.
     * @return true if it was possible to add the element to the queue.
     */
    public boolean offer(E element);

    /**
     * Remove and return an element from the queue if one is available.
     *
     * @return an element previously on the queue, or <tt>null</tt> if the
     *         queue is empty. 
     */
    public E poll();

    /**
     * Remove and return an element from the queue.  This method differs
     * from the <tt>poll</tt> method in that it throws an exception if the
     * queue is empty. 
     *
     * @return an element previously on the queue.
     * @throws NoSuchElementException if the queue is empty.
     */
    public E remove() throws NoSuchElementException;

    /**
     * Return, but do not remove, an element from the queue, or <tt>null</tt>
     * if the queue is empty.  This method returns the same object reference
     * that would be returned by by the <tt>poll</tt> method.  The two methods
     * differ in that this method does not remove the element from the queue.
     *
     * @return an element on the queue, or <tt>null</tt> if the queue is empty.
     */
    public E peek();

    /**
     * Return, but do not remove, an element from the queue.  This method
     * differs from the <tt>peek</tt> method in that it throws an exception if
     * the queue is empty.
     *
     * @return an element on the queue.
     * @throws NoSuchElementException if the queue is empty.
     */
    public E element() throws NoSuchElementException;
}
