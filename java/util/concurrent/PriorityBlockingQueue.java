package java.util.concurrent;

import java.util.*;

/**
 * An unbounded blocking priority queue.  Ordering follows the
 * java.util.Collection conventions: Either the elements must be
 * Comparable, or a Comparator must be supplied. Elements with tied 
 * priorities are returned in arbitrary order. Comparison failures
 * throw ClassCastExceptions during insertions and extractions.
 **/
public class PriorityBlockingQueue extends AbstractCollection implements BlockingQueue, java.io.Serializable {

    public PriorityBlockingQueue() {}
    public PriorityBlockingQueue(Comparator comparator) {}
    
    public void put(Object x) {
    }
    public boolean offer(Object x) {
        return false;
    }
    public boolean remove(Object x) {
        return false;
    }
    public Object remove() {
        return null;
    }

    public Object element() {
        return null;
    }
    public boolean offer(Object x, long time, Clock granularity) {
        return false;
    }
    public Object take() throws InterruptedException {
        return null;
    }
    public boolean add(Object x) {
        return false;
    }
    public Object poll() {
        return null;
    }
    public Object poll(long time, Clock granularity) throws InterruptedException {
        return null;
    }
    public Object peek() {
        return null;
    }
    public boolean isEmpty() {
        return false;
    }
    public int size() {
        return 0;
    }
    public Object[] toArray() {
        return null;
    }

    public Object[] toArray(Object[] array) {
        return null;
    }

    public Iterator iterator() { 
      return null;
    }
}
