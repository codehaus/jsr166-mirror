/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;

/**
 * A blocking priority queue.  Ordering follows the
 * java.util.Collection conventions: Either the elements must be
 * Comparable, or a Comparator must be supplied. Elements with tied
 * priorities are returned in arbitrary order. Comparison failures
 * throw ClassCastExceptions during insertions and extractions.
 **/
public class PriorityBlockingQueue<E> extends AbstractBlockingQueueFromQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    /**
     * Create a new priority queue with the default initial capacity (11)
     * that orders its elements according to their natural ordering.
     */
    public PriorityBlockingQueue() {
        super(new PriorityQueue<E>(), Integer.MAX_VALUE);
    }

    /**
     * Create a new priority queue with the specified initial capacity
     * that orders its elements according to their natural ordering.
     *
     * @param initialCapacity the initial capacity for this priority queue.
     */
    public PriorityBlockingQueue(int initialCapacity) {
        super(new PriorityQueue<E>(initialCapacity, null), Integer.MAX_VALUE);
    }

    /**
     * Create a new priority queue with the specified initial capacity (11)
     * that orders its elements according to the specified comparator.
     *
     * @param initialCapacity the initial capacity for this priority queue.
     * @param comparator the comparator used to order this priority queue.
     */
    public PriorityBlockingQueue(int initialCapacity, Comparator<E> comparator) {
        super(new PriorityQueue<E>(initialCapacity, comparator), Integer.MAX_VALUE);
    }

    /**
     * Create a new priority queue containing the elements in the specified
     * collection.  The priority queue has an initial capacity of 110% of the
     * size of the specified collection. If the specified collection
     * implements the {@link Sorted} interface, the priority queue will be
     * sorted according to the same comparator, or according to its elements'
     * natural order if the collection is sorted according to its elements'
     * natural order.  If the specified collection does not implement the
     * <tt>Sorted</tt> interface, the priority queue is ordered according to
     * its elements' natural order.
     *
     * @param initialElements the collection whose elements are to be placed
     *        into this priority queue.
     * @throws ClassCastException if elements of the specified collection
     *         cannot be compared to one another according to the priority
     *         queue's ordering.
     * @throws NullPointerException if the specified collection or an
     *         element of the specified collection is <tt>null</tt>.
     */
    public PriorityBlockingQueue(Collection<E> initialElements) {
        super(new PriorityQueue<E>(initialElements), Integer.MAX_VALUE);
    }

}
