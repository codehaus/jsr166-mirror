/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;

/**
 * An AbstractBlockingQueueFromQueue places blocking concurrency control
 * around a non-synchronized, non-thread-safe Queue.
 **/
abstract class AbstractBlockingQueueFromQueue<E> extends AbstractQueue<E> implements  BlockingQueue<E>, java.io.Serializable {

    /*
     * Concurrency control via the classic two-condition algorithm
     * found in any textbook.
     */

    private transient final FairReentrantLock lock = new FairReentrantLock();
    private transient final Condition notEmpty = lock.newCondition();
    private transient final Condition notFull =  lock.newCondition();
    private final Queue<E> q;
    private final int capacity;

    protected AbstractBlockingQueueFromQueue(Queue<E> queue, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        q = queue;
    }


    public void put(E x) throws InterruptedException {
        if (x == null) throw new IllegalArgumentException();
        lock.lockInterruptibly();
        try {
            try {
                while (q.size() == capacity)
                    notFull.await();
            }
            catch (InterruptedException ie) {
                notFull.signal(); // propagate to non-interrupted thread
                throw ie;
            }
            boolean ok = q.offer(x);
            assert ok;
            notEmpty.signal();
        }
        finally {
            lock.unlock();
        }
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
            notFull.signal();
            return x;
        }
        finally {
            lock.unlock();
        }
    }

    public boolean offer(E x) {
        if (x == null) throw new IllegalArgumentException();
        lock.lock();
        try {
            if (q.size() == capacity) 
                return false;
            else {
                boolean ok = q.offer(x);
                assert ok;
                notEmpty.signal();
                return true;
            }
        }
        finally {
            lock.unlock(); 
        }
    }

    public E poll() {
        lock.lock();
        try {
            E x = q.poll();
            if (x != null)
                notFull.signal();
            return x;
        }
        finally {
            lock.unlock(); 
        }
    }

    public boolean offer(E x, long timeout, TimeUnit unit) throws InterruptedException {
        if (x == null) throw new IllegalArgumentException();
        lock.lockInterruptibly();
        long nanos = unit.toNanos(timeout);
        try {
            for (;;) {
                if (q.size() != capacity) {
                    boolean ok = q.offer(x);
                    assert ok;
                    notEmpty.signal();
                    return true;
                }
                if (nanos <= 0)
                    return false;
                try {
                    nanos = notFull.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notFull.signal(); // propagate to non-interrupted thread
                    throw ie;
                }
            }
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
                if (x != null) {
                    notFull.signal();
                    return x;
                }
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

    public int remainingCapacity() {
        lock.lock();
        try {
            return capacity - q.size();
        }
        finally {
            lock.unlock();
        }
    }

    public boolean remove(Object x) {
        lock.lock();
        try {
            boolean removed = q.remove(x);
            if (removed)
                notFull.signal();
            return removed;
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
                notFull.signal();
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

        
    
