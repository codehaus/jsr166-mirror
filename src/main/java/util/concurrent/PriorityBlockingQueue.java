/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * An unbounded blocking queue based on a {@link PriorityQueue},
 * obeying its ordering rules and implementation characteristics.
 * @since 1.5
 * @author Doug Lea
**/
public class PriorityBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    private final PriorityQueue<E> q;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    /**
     * Create a new priority queue with the default initial capacity (11)
     * that orders its elements according to their natural ordering.
     */
    public PriorityBlockingQueue() {
        q = new PriorityQueue<E>();
    }

    /**
     * Create a new priority queue with the specified initial capacity
     * that orders its elements according to their natural ordering.
     *
     * @param initialCapacity the initial capacity for this priority queue.
     */
    public PriorityBlockingQueue(int initialCapacity) {
        q = new PriorityQueue<E>(initialCapacity, null);
    }

    /**
     * Create a new priority queue with the specified initial capacity (11)
     * that orders its elements according to the specified comparator.
     *
     * @param initialCapacity the initial capacity for this priority queue.
     * @param comparator the comparator used to order this priority queue.
     */
    public PriorityBlockingQueue(int initialCapacity, Comparator<E> comparator) {
        q = new PriorityQueue<E>(initialCapacity, comparator);
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
        q = new PriorityQueue<E>(initialElements);
    }

    /**
     * Returns the comparator associated with this priority queue, or
     * <tt>null</tt> if it uses its elements' natural ordering.
     *
     * @return the comparator associated with this priority queue, or
     *         <tt>null</tt> if it uses its elements' natural ordering.
     */
    public Comparator comparator() {
        return q.comparator();
    }

    public boolean offer(E x) {
        if (x == null) throw new NullPointerException();
        lock.lock();
        try {
            boolean ok = q.offer(x);
            assert ok;
            notEmpty.signal();
            return true;
        }
        finally {
            lock.unlock(); 
        }
    }

    public void put(E x) throws InterruptedException {
        offer(x); // never need to block
    }

    public boolean offer(E x, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(x); // never need to block
    }

    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            try {
                while (q.size() == 0)
                    notEmpty.await();
            }
            catch (InterruptedException ie) {
                notEmpty.signal(); // propagate to non-interrupted thread
                throw ie;
            }
            E x = q.poll();
            assert x != null;
            return x;
        }
        finally {
            lock.unlock();
        }
    }


    public E poll() {
        lock.lock();
        try {
            return q.poll();
        }
        finally {
            lock.unlock(); 
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        long nanos = unit.toNanos(timeout);
        try {
            for (;;) {
                E x = q.poll();
                if (x != null) 
                    return x;
                if (nanos <= 0)
                    return null;
                try {
                    nanos = notEmpty.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notEmpty.signal(); // propagate to non-interrupted thread
                    throw ie;
                }

            }
        }
        finally {
            lock.unlock();
        }
    }

    public E peek() {
        lock.lock();
        try {
            return q.peek();
        }
        finally {
            lock.unlock(); 
        }
    }

    public int size() {
        lock.lock();
        try {
            return q.size();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Always returns <tt>Integer.MAX_VALUE</tt> because
     * PriorityBlockingQueues are not capacity constrained.
     * @return <tt>Integer.MAX_VALUE</tt>
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    public boolean remove(Object x) {
        lock.lock();
        try {
            return q.remove(x);
        }
        finally {
            lock.unlock();
        }
    }

    public boolean contains(Object x) {
        lock.lock();
        try {
            return q.contains(x);
        }
        finally {
            lock.unlock();
        }
    }

    public Object[] toArray() {
        lock.lock();
        try {
            return q.toArray();
        }
        finally {
            lock.unlock();
        }
    }


    public String toString() {
        lock.lock();
        try {
            return q.toString();
        }
        finally {
            lock.unlock();
        }
    }

    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            return q.toArray(a);
        }
        finally {
            lock.unlock();
        }
    }

    public Iterator<E> iterator() {
        lock.lock();
        try {
            return new Itr(q.iterator());
        }
        finally {
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
            }
            finally {
                lock.unlock();
            }
	}
        
	public void remove() {
            lock.lock();
            try {
                iter.remove();
            }
            finally {
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
        }
        finally {
            lock.unlock();
        }
    }

}
