package java.util;

/**
 * Queues support a means to <tt>add</tt> elements, and to <tt>poll</tt>
 * elements (to remove one if any exist).
 *
 * <p> Queues typically, but do not necessarily order elements in FIFO
 * (first-in-first-out). The exceptions are priority queues, that
 * order elements in accord with supplied Comparators.
 * 
 * <p> Queues are not necessarily java.util.Collections, but some
 * classes may implememnt both Queue and Collection or one of its
 * subinterfacers, since these share a compatible susbset of
 * methods. Not all Queues are traversable.  However, it is always
 * possible to obtain a separate snapshot of the elements in a Queue
 * via the toArray method.
 * 
 * <p> The Queue interface does not define blocking queue methods
 * (i.e., those that wait for elements to appear and/or for space to
 * be available) that are common in concurrent programming. These are
 * defined in the extended java.util.concurrent.BlockingQueue
 * interface.
 *
 * <p> As with Collections, <tt>add</tt> method returns a boolean
 * telling whther the element was added. In most implementations, this
 * method will never return false (failing only in case of memory
 * exhaustion), but other implementations may impose capacity limits
 * that cause <tt>add</tt> to return false when space is not
 * available, or other restrictions.
 *
 * <p> In accord with the Collections conventions, it is not
 * considered an error to <tt>add(null)</tt>. However, it is a very
 * bad idea, since <tt>null</tt> is also used as a sentinel by
 * <tt>poll</tt> to indicate that no elements exist.
 **/
public interface Queue {
    /**
     * Add the given object to the queue if space is currently
     * available.
     * @param x the object to add
     * @return true if successful
     **/
    public boolean add(Object x);

    /**
     * Take an object from the queue if one is available.
     * @return the object, or null if the queue is empty.
     **/
    public Object poll();


    /**
     * Return but do not remove the element that will be returned by
     * the next call to poll.
     * @return an element, or null if empty
     **/
    public Object peek();

    /**
     * Return true if there are no elements in this queue.
     **/
    public boolean isEmpty();

    /**
     * Return the number of elements in this queue.
     **/
    public int size();

    /**
     * Return a snapshot of the elements in this queue, in the
     * order that they would be returned by poll().
     **/
    public Object[] toArray();

    /**
     * Returns an array containing all of the elements in this queue;
     * the runtime type of the returned array is that of the specified
     * array.
     **/
    public Object[] toArray(Object[] array);

}
