package java.util.concurrent;

import java.util.*;

/**
 * A bounded queue based on a fixed-sized array.
 **/
public class ArrayBlockingQueue implements BlockingQueue, java.io.Serializable {

    public ArrayBlockingQueue(int capacity) {}

    public ArrayBlockingQueue(int capacity, Collection initialElements) {}

    public void put(Object x) throws InterruptedException {
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
    public boolean offer(Object x, long time, Clock granularity) throws InterruptedException {
        return false;
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

    public Object[] toArray(Object[] array) {
        return null;
    }
    
}
