/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */


package java.util.concurrent;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * An unbounded queue of <tt>Delayed</tt> elements, in which
 * elements can only be taken when their delay has expired.
 * @since 1.5
 * @author Doug Lea
*/

public class DelayQueue<E extends Delayed> extends AbstractQueue<E>
    implements BlockingQueue<E> {

    private transient final ReentrantLock lock = new ReentrantLock();
    private transient final Condition available = lock.newCondition();
    private final PriorityQueue<E> q = new PriorityQueue<E>();

    /**
     * Creates a new DelayQueue with no elements
     */
    public DelayQueue() {}

    /**
     * Create a new DelayQueue with elements taken from the given
     * collection of {@link Delayed} instances.
     *
     * @fixme Should the body be wrapped with try-lock-finally-unlock?
     */
    public DelayQueue(Collection<? extends E> c) {
        this.addAll(c);
    }

    public boolean offer(E x) {
        lock.lock();
        try {
            E first = q.peek();
            q.offer(x);
            if (first == null || x.compareTo(first) < 0)
                available.signalAll();
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    public void put(E x) {
        offer(x);
    }

    public boolean offer(E x, long time, TimeUnit unit) {
        return offer(x);
    }

    public boolean add(E x) {
        return offer(x);
    }

    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            for (;;) {
                E first = q.peek();
                if (first == null)
                    available.await();
                else {
                    long delay =  first.getDelay(TimeUnit.NANOSECONDS);
                    if (delay > 0)
                        available.awaitNanos(delay);
                    else {
                        E x = q.poll();
                        assert x != null;
                        if (q.size() != 0)
                            available.signalAll(); // wake up other takers
                        return x;

                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    public E poll(long time, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        long nanos = unit.toNanos(time);
        try {
            for (;;) {
                E first = q.peek();
                if (first == null) {
                    if (nanos <= 0)
                        return null;
                    else
                        nanos = available.awaitNanos(nanos);
                }
                else {
                    long delay =  first.getDelay(TimeUnit.NANOSECONDS);
                    if (delay > 0) {
                        if (delay > nanos)
                            delay = nanos;
                        long timeLeft = available.awaitNanos(delay);
                        nanos -= delay - timeLeft;
                    }
                    else {
                        E x = q.poll();
                        assert x != null;
                        if (q.size() != 0)
                            available.signalAll();
                        return x;
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }


    public E poll() {
        lock.lock();
        try {
            E first = q.peek();
            if (first == null || first.getDelay(TimeUnit.NANOSECONDS) > 0)
                return null;
            else {
                E x = q.poll();
                assert x != null;
                if (q.size() != 0)
                    available.signalAll();
                return x;
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

    public void clear() {
        lock.lock();
        try {
            q.clear();
        }
        finally {
            lock.unlock();
        }
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
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

    public <T> T[] toArray(T[] array) {
        lock.lock();
        try {
            return q.toArray(array);
        }
        finally {
            lock.unlock();
        }
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

}
