package java.util.concurrent;

import java.util.*;

/**
 * An unbounded queue based on linked nodes.
 **/
public class LinkedBlockingQueue extends AbstractCollection implements BlockingQueue, java.io.Serializable {

    public LinkedBlockingQueue() {}
    public void put(Object x) {
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
    public boolean offer(Object x) {
        return false;
    }
    public Object remove() {
        return null;
    }
    public Iterator iterator() { 
      return null;
    }

    public boolean remove(Object x) {
        return false;
    }
    public Object element() {
        return null;
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
}
