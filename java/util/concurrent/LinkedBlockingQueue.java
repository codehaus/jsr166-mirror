package java.util.concurrent;

import java.util.*;

/**
 * An unbounded queue based on linked nodes.
 **/
public class LinkedBlockingQueue<E> extends AbstractCollection<E>
        implements BlockingQueue<E>, java.io.Serializable {

    public LinkedBlockingQueue() {}
    public void put(E x) {
    }
    public boolean offer(E x, long timeout, TimeUnit granularity) {
        return false;
    }
    public E take() throws InterruptedException {
        return null;
    }
    public boolean add(E x) {
        return false;
    }
    public boolean offer(E x) {
        return false;
    }
    public E remove() {
        return null;
    }
    public Iterator<E> iterator() {
      return null;
    }

    public boolean remove(Object x) {
        return false;
    }
    public E element() {
        return null;
    }
    public E poll() {
        return null;
    }
    public E poll(long timeout, TimeUnit granularity) throws InterruptedException {
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
    public Object[] toArray() {
        return null;
    }

    public <T> T[] toArray(T[] array) {
        return null;
    }
}
