package java.util.concurrent;

import java.util.*;

/**
 * An unbounded queue in which elements cannot be taken until
 * their indicated delays have elapsed.
 **/

public class DelayQueue extends AbstractCollection implements BlockingQueue, java.io.Serializable {

    public DelayQueue() {}

    /**
     * Add the given element to the queue, to be taken after the given delay.
     * @param unit the time to delay
     * @param unit the granularity of the time unit
     * @param x the element
     */
    public boolean add(long delay, Clock granularity, Object x) {
        return false;
    }

    /**
     * Return the time until the given element may be taken,
     * in the requested time granularity.
     * @param element the element
     * @param granularity the time granularity
     * @throws NoSuchElementException if element not present
     */
    public long getDelay(Object element, Clock granularity) {
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
    public void put(Object x) {
    }
    /**
     * Equivalent to add(0, [any time unit], x).
     **/
    public boolean offer(Object x, long time, Clock granularity) {
        return false;
    }
    /**
     * Equivalent to add(0, [any time unit], x).
     **/
    public boolean add(Object x) {
        return false;
    }
    /**
     * Equivalent to add(0, [any time unit], x).
     **/
    public boolean offer(Object x) {
        return false;
    }


    public Object take() throws InterruptedException {
        return null;
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
