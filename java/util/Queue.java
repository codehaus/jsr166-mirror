package java.util;

/**
 * Queues are Collections supporting additional basic insertion,
 * extraction, and inspection operations.
 *
 * <p> Queues typically, but do not necessarily order elements in a
 * FIFO (first-in-first-out) manner. Among the exceptions are priority
 * queues, that order elements in accord with supplied
 * Comparators. Every Queue implementation must specify its ordering
 * guarantees,
 *
 * <p> The <tt>offer</tt> method adds an element if possible,
 * otherwise returning <tt>false</tt>. This differs from the
 * Collections.add method, that throws an unchecked exception upon
 * failure. It is designed for use in collections in which failure to
 * add is a normal, rather than exceptional occurrence, for example,
 * in fixed-capacity queues.
 *
 * <p> The <tt>remove</tt> and <tt>poll</tt> methods delete and return
 * an element in accord with the implementation's ordering policies --
 * for example, in FIFO queues, it will return the oldest element.
 * The <tt>remove</tt> and <tt>poll</tt> differ only in their behavior
 * when the queue is empty: <tt>poll</tt> returns <tt>null</tt> while
 * <tt>remove</tt> throws an exception. These are designed for usage
 * contexts in which emptiness is considered to be normal versus
 * exceptional.
 *
 * <p> The <tt>element</tt> and <tt>peek</tt> methods return but do
 * not delete the element that would be obtained by a call to
 * <tt>remove</tt> and <tt>poll</tt> respectively.
 *
 * <p> The Queue interface does not define blocking queue methods
 * (i.e., those that wait for elements to appear and/or for space to
 * be available) that are common in concurrent programming. These are
 * defined in the extended java.util.concurrent.BlockingQueue
 * interface.
 *
 * <p> Queue implementations generally do not allow insertion of
 * <tt>null</tt>. Even in those that allow it, it is a very bad idea
 * to do so, since <tt>null</tt> is also used as a sentinel by
 * <tt>poll</tt> to indicate that no elements exist.
 **/
public interface Queue<E> extends Collection<E> {

    /**
     * Add the given object to this queue if possible.
     * @param x the object to add
     * @return true if successful
     **/
    public boolean offer(E x);

    /**
     * Delete and return an object from the queue if one is available.
     * @return the object, or null if the queue is empty.
     **/
    public E poll();

    /**
     * Delete and return the element produced by poll, if the queue is
     * not empty.
     * @return an element
     * @throws NoSuchElementException if empty
     **/
    public E remove() throws NoSuchElementException;

    /**
     * Return but do not delete the element that will be returned by
     * the next call to poll.
     * @return an element, or null if empty
     **/
    public E peek();

    /**
     * Return but do not delete the element that will be returned by
     * the next call to poll, if the queue is not empty.
     * @return an element
     * @throws NoSuchElementException if empty
     **/
    public E element() throws NoSuchElementException;
}
