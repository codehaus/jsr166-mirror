package java.util.concurrent;

import java.util.*;

/**
 * An unbounded queue in which elements cannot be taken until
 * their indicated delays have elapsed.
 **/

public class DelayQueue<E> extends AbstractCollection<E>
        implements BlockingQueue<E>, java.io.Serializable {

    public DelayQueue() {}

    /**
     * Add the given element to the queue, to be taken after the given delay.
     * @param unit the time to delay
     * @param unit the granularity of the time unit
     * @param x the element
     */
    public boolean add(long delay, Clock granularity, E x) {
        return false;
    }

    /**
     * Return the time until the given element may be taken,
     * in the requested time granularity.
     * @param element the element
     * @param granularity the time granularity
     * @throws NoSuchElementException if element not present
     */
    public long getDelay(E element, Clock granularity) {
        return 0;
    }

    /**
     * Return the time until the earliest element may be taken,
     * in the requested time granularity.
     * @param granularity the time granularity
     * @throws NoSuchElementException if empty
     */
    public long getEarliestDelay(Clock granularity) {
        return 0;
    }


    /**
     * Equivalent to add(0, [any time unit], x).
     **/
    public void put(E x) {
    }
    /**
     * Equivalent to add(0, [any time unit], x).
     **/
    public boolean offer(E x, long time, Clock granularity) {
        return false;
    }
    /**
     * Equivalent to add(0, [any time unit], x).
     **/
    public boolean add(E x) {
        return false;
    }
    /**
     * Equivalent to add(0, [any time unit], x).
     **/
    public boolean offer(E x) {
        return false;
    }


    public E take() throws InterruptedException {
        return null;
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
}
