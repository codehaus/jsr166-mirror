/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

import java.util.concurrent.locks.*;
import java.util.*;

/**
 * An unbounded {@linkplain BlockingQueue blocking queue} based on a
 * {@link PriorityQueue},
 * obeying its ordering rules and implementation characteristics.
 * While this queue is logically unbounded,
 * attempted additions may fail due to resource exhaustion (causing
 * <tt>OutOfMemoryError</tt>).
 * @since 1.5
 * @author Doug Lea
*/
public class PriorityBlockingQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {

    private final PriorityQueue<E> q;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition notEmpty = lock.newCondition();

    /**
     * Creates a <tt>PriorityBlockingQueue</tt> with the default initial 
     * capacity
     * (11) that orders its elements according to their natural
     * ordering (using <tt>Comparable</tt>).
     */
    public PriorityBlockingQueue() {
        q = new PriorityQueue<E>();
    }

    /**
     * Creates a <tt>PriorityBlockingQueue</tt> with the specified initial
     * capacity
     * that orders its elements according to their natural ordering
     * (using <tt>Comparable</tt>).
     *
     * @param initialCapacity the initial capacity for this priority queue.
     * @throws IllegalArgumentException if <tt>initialCapacity</tt> is less
     * than 1
     */
    public PriorityBlockingQueue(int initialCapacity) {
        q = new PriorityQueue<E>(initialCapacity, null);
    }

    /**
     * Creates a <tt>PriorityBlockingQueue</tt> with the specified initial
     * capacity
     * that orders its elements according to the specified comparator.
     *
     * @param initialCapacity the initial capacity for this priority queue.
     * @param comparator the comparator used to order this priority queue.
     * If <tt>null</tt> then the order depends on the elements' natural
     * ordering.
     * @throws IllegalArgumentException if <tt>initialCapacity</tt> is less
     * than 1
     */
    public PriorityBlockingQueue(int initialCapacity,
                                 Comparator<? super E> comparator) {
        q = new PriorityQueue<E>(initialCapacity, comparator);
    }

    /**
     * Creates a <tt>PriorityBlockingQueue</tt> containing the elements
     * in the specified collection.  The priority queue has an initial
     * capacity of 110% of the size of the specified collection. If
     * the specified collection is a {@link SortedSet} or a {@link
     * PriorityQueue}, this priority queue will be sorted according to
     * the same comparator, or according to its elements' natural
     * order if the collection is sorted according to its elements'
     * natural order.  Otherwise, this priority queue is ordered
     * according to its elements' natural order.
     *
     * @param c the collection whose elements are to be placed
     *        into this priority queue.
     * @throws ClassCastException if elements of the specified collection
     *         cannot be compared to one another according to the priority
     *         queue's ordering.
     * @throws NullPointerException if <tt>c</tt> or any element within it
     * is <tt>null</tt>
     */
    public PriorityBlockingQueue(Collection<? extends E> c) {
        q = new PriorityQueue<E>(c);
    }


    // these first few override just to update doc comments

    /**
     * Adds the specified element to this queue.
     * @return <tt>true</tt> (as per the general contract of
     * <tt>Collection.add</tt>).
     *
     * @throws NullPointerException {@inheritDoc}
     * @throws ClassCastException if the specified element cannot be compared
     * with elements currently in the priority queue according
     * to the priority queue's ordering.
     */
    public boolean add(E o) {
        return super.add(o);
    }

    /**
     * Adds all of the elements in the specified collection to this queue.
     * The behavior of this operation is undefined if
     * the specified collection is modified while the operation is in
     * progress.  (This implies that the behavior of this call is undefined if
     * the specified collection is this queue, and this queue is nonempty.)
     * <p>
     * This implementation iterates over the specified collection, and adds
     * each object returned by the iterator to this collection, in turn.
     * @throws NullPointerException {@inheritDoc}
     * @throws ClassCastException if any element cannot be compared
     * with elements currently in the priority queue according
     * to the priority queue's ordering.
     */
    public boolean addAll(Collection<? extends E> c) {
        return super.addAll(c);
    }

    /**
     * Returns the comparator used to order this collection, or <tt>null</tt>
     * if this collection is sorted according to its elements natural ordering
     * (using <tt>Comparable</tt>).
     *
     * @return the comparator used to order this collection, or <tt>null</tt>
     * if this collection is sorted according to its elements natural ordering.
     */
    public Comparator comparator() {
        return q.comparator();
    }

    /**
     * Adds the specified element to this priority queue.
     *
     * @return <tt>true</tt>
     * @throws ClassCastException if the specified element cannot be compared
     * with elements currently in the priority queue according
     * to the priority queue's ordering.
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E o) {
        if (o == null) throw new NullPointerException();
        lock.lock();
        try {
            boolean ok = q.offer(o);
            assert ok;
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds the specified element to this priority queue. As the queue is
     * unbounded this method will never block.
     * @throws ClassCastException if the element cannot be compared
     * with elements currently in the priority queue according
     * to the priority queue's ordering.
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E o) {
        offer(o); // never need to block
    }

    /**
     * Adds the specified element to this priority queue. As the queue is
     * unbounded this method will never block.
     * @param o {@inheritDoc}
     * @param timeout This parameter is ignored as the method never blocks
     * @param unit This parameter is ignored as the method never blocks
     * @throws ClassCastException if the element cannot be compared
     * with elements currently in the priority queue according
     * to the priority queue's ordering.
     * @throws NullPointerException {@inheritDoc}
     * @return <tt>true</tt>
     */
    public boolean offer(E o, long timeout, TimeUnit unit) {
        return offer(o); // never need to block
    }

    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            try {
                while (q.size() == 0)
                    notEmpty.await();
            } catch (InterruptedException ie) {
                notEmpty.signal(); // propagate to non-interrupted thread
                throw ie;
            }
            E x = q.poll();
            assert x != null;
            return x;
        } finally {
            lock.unlock();
        }
    }


    public E poll() {
        lock.lock();
        try {
            return q.poll();
        } finally {
            lock.unlock();
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            for (;;) {
                E x = q.poll();
                if (x != null)
                    return x;
                if (nanos <= 0)
                    return null;
                try {
                    nanos = notEmpty.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    notEmpty.signal(); // propagate to non-interrupted thread
                    throw ie;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public E peek() {
        lock.lock();
        try {
            return q.peek();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return q.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Always returns <tt>Integer.MAX_VALUE</tt> because
     * a <tt>PriorityBlockingQueue</tt> is not capacity constrained.
     * @return <tt>Integer.MAX_VALUE</tt>
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    /**
     * Removes a single instance of the specified element from this
     * queue, if it is present.  More formally,
     * removes an element <tt>e</tt> such that <tt>(o==null ? e==null :
     * o.equals(e))</tt>, if the queue contains one or more such
     * elements.  Returns <tt>true</tt> if the queue contained the
     * specified element (or equivalently, if the queue changed as a
     * result of the call).
     *
     * <p>This implementation iterates over the queue looking for the
     * specified element.  If it finds the element, it removes the element
     * from the queue using the iterator's remove method.<p>
     *
     */
    public boolean remove(Object o) {
        lock.lock();
        try {
            return q.remove(o);
        } finally {
            lock.unlock();
        }
    }

    public boolean contains(Object o) {
        lock.lock();
        try {
            return q.contains(o);
        } finally {
            lock.unlock();
        }
    }

    public Object[] toArray() {
        lock.lock();
        try {
            return q.toArray();
        } finally {
            lock.unlock();
        }
    }


    public String toString() {
        lock.lock();
        try {
            return q.toString();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this delay queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        lock.lock();
        try {
            q.clear();
        } finally {
            lock.unlock();
        }
    }

    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            return q.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over the elements in this queue. The iterator
     * does not return the elements in any particular order.
     * The
     * returned iterator is a "fast-fail" iterator that will
     * throw {@link java.util.ConcurrentModificationException}
     * upon detected interference.     
     *
     * @return an iterator over the elements in this queue.
     */
    public Iterator<E> iterator() {
        lock.lock();
        try {
            return new Itr(q.iterator());
        } finally {
            lock.unlock();
        }
    }

    private class Itr<E> implements Iterator<E> {
        private final Iterator<E> iter;
        Itr(Iterator<E> i) {
            iter = i;
        }

        public boolean hasNext() {
            /*
             * No sync -- we rely on underlying hasNext to be
             * stateless, in which case we can return true by mistake
             * only when next() willl subsequently throw
             * ConcurrentModificationException.
             */
            return iter.hasNext();
        }

        public E next() {
            lock.lock();
            try {
                return iter.next();
            } finally {
                lock.unlock();
            }
        }

        public void remove() {
            lock.lock();
            try {
                iter.remove();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Save the state to a stream (that is, serialize it).  This
     * merely wraps default serialization within lock.  The
     * serialization strategy for items is left to underlying
     * Queue. Note that locking is not needed on deserialization, so
     * readObject is not defined, just relying on default.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        lock.lock();
        try {
            s.defaultWriteObject();
        } finally {
            lock.unlock();
        }
    }

}
