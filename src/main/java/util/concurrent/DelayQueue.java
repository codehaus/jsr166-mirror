/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */


package java.util.concurrent;
import java.util.*;

/**
 * An unbounded queue of <tt>DelayEntry</tt> elements, in which
 * elements can only be taken when their delay has expired.
 **/

public class DelayQueue<E> extends AbstractQueue<DelayEntry<E>>
        implements BlockingQueue<DelayEntry<E>> {

    private transient final ReentrantLock lock = new ReentrantLock();
    private transient final Condition canTake = lock.newCondition();
    private final PriorityQueue<DelayEntry<E>> q = new PriorityQueue<DelayEntry<E>>();

    public DelayQueue() {}

    public boolean offer(DelayEntry<E> x) {
        lock.lock();
        try {
            DelayEntry<E> first = q.peek();
            q.offer(x);
            if (first == null || x.compareTo(first) < 0)
                canTake.signalAll();
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    public void put(DelayEntry<E> x) {
        offer(x);
    }

    public boolean offer(DelayEntry<E> x, long time, TimeUnit unit) {
        return offer(x);
    }

    public boolean add(DelayEntry<E> x) {
        return offer(x);
    }

    public DelayEntry<E> take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            for (;;) {
                DelayEntry first = q.peek();
                if (first == null)
                    canTake.await();
                else {
                    long delay =  first.getDelay(TimeUnit.NANOSECONDS);
                    if (delay > 0)
                        canTake.awaitNanos(delay);
                    else {
                        DelayEntry<E> x = q.poll();
                        assert x != null;
                        if (q.size() != 0)
                            canTake.signalAll(); // wake up other takers
                        return x;
                        
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    public DelayEntry<E> poll(long time, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        long nanos = unit.toNanos(time);
        try {
            for (;;) {
                DelayEntry first = q.peek();
                if (first == null) {
                    if (nanos <= 0)
                        return null;
                    else
                        nanos = canTake.awaitNanos(nanos);
                }
                else {
                    long delay =  first.getDelay(TimeUnit.NANOSECONDS);
                    if (delay > 0) {
                        if (delay > nanos)
                            delay = nanos;
                        long timeLeft = canTake.awaitNanos(delay);
                        nanos -= delay - timeLeft;
                    }
                    else {
                        DelayEntry<E> x = q.poll();
                        assert x != null;
                        if (q.size() != 0)
                            canTake.signalAll(); 
                        return x;
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }


    public DelayEntry<E> poll() {
        lock.lock();
        try {
            DelayEntry first = q.peek();
            if (first == null || first.getDelay(TimeUnit.NANOSECONDS) > 0)
                return null;
            else {
                DelayEntry<E> x = q.poll();
                assert x != null;
                if (q.size() != 0)
                    canTake.signalAll(); 
                return x;
            }
        }
        finally {
            lock.unlock();
        }
    }

    public DelayEntry<E> peek() {
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

    public Iterator<DelayEntry<E>> iterator() {
        lock.lock();
        try {
            return new Itr(q.iterator());
        }
        finally {
            lock.unlock();
        }
    }

    private class Itr<E> implements Iterator<DelayEntry<E>> {
        private final Iterator<DelayEntry<E>> iter;
        Itr(Iterator<DelayEntry<E>> i) { 
            iter = i; 
        }

	public boolean hasNext() {
            return iter.hasNext();
	}
        
	public DelayEntry<E> next() {
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
