package java.util.concurrent;

import java.util.*;

/**
 * An unbounded blocking priority queue.  Ordering follows the
 * java.util.Collection conventions: Either the elements must be
 * Comparable, or a Comparator must be supplied. Elements with tied
 * priorities are returned in arbitrary order. Comparison failures
 * throw ClassCastExceptions during insertions and extractions.
 **/
public class PriorityBlockingQueue<E> extends AbstractCollection<E>
        implements BlockingQueue<E>, java.io.Serializable {

    public PriorityBlockingQueue() {}
    public PriorityBlockingQueue(Comparator comparator) {}

    public void put(E x) {
    }
    public boolean offer(E x) {
        return false;
    }
    public boolean remove(Object x) {
        return false;
    }
    public E remove() {
        return null;
    }

    public E element() {
        return null;
    }
    public boolean offer(E x, long time, Clock granularity) {
        return false;
    }
    public E take() throws InterruptedException {
        return null;
    }
    public boolean add(E x) {
        return false;
    }
    public E poll() {
        return null;
    }
    public E poll(long time, Clock granularity) throws InterruptedException {
        return null;
    }
    public E peek() {
        return null;
    }
    public boolean isEmpty() {
        return false;
    }
    public int size() {
        return 0;
    }
    public E[] toArray() {
        return null;
    }

    public <T> T[] toArray(T[] array) {
        return null;
    }

    public Iterator<E> iterator() {
      return null;
    }
}
