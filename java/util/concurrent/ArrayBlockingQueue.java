package java.util.concurrent;

import java.util.*;

/**
 * A bounded queue based on a fixed-sized array.
 **/
public class ArrayBlockingQueue<E> extends AbstractCollection<E>
        implements BlockingQueue<E>, java.io.Serializable {

    public ArrayBlockingQueue(int capacity) {}

    public ArrayBlockingQueue(int capacity, Collection<E> initialElements) {}

    public void put(E x) throws InterruptedException {
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
    public boolean remove(Object x) {
        return false;
    }
    public E remove() {
        return null;
    }
    public Iterator<E> iterator() {
      return null;
    }
    public E element() {
        return null;
    }
    public E poll() {
        return null;
    }
    public boolean offer(E x, long timeout, TimeUnit granularity) throws InterruptedException {
        return false;
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

    /**
     * Return the capacity, i.e., number of elements that can be
     * <tt>put</tt> into this queue without blocking.
     **/
    public int capacity() {
        return 0;
    }
    public Object[] toArray() {
        return null;
    }

    public <T> T[] toArray(T[] array) {
        return null;
    }

}
