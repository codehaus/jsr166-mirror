package java.util.concurrent;

import java.util.*;

/**
 * A Queue in which each put must wait for a take, and vice versa.
 * SynchronousQueues are similar to rendezvous channels used in CSP
 * and Ada. They are well suited for handoff designs, in which an
 * object running in one thread must synch up with an object running
 * in another thread in order to hand it some information, event, or
 * task.
 **/
public class SynchronousQueue<E> extends AbstractCollection<E>
        implements BlockingQueue<E>, java.io.Serializable {

    public SynchronousQueue() {}
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
    public boolean offer(E x, long time, Clock granularity) throws InterruptedException {
        return false;
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
    public Object[] toArray() {
        return null;
    }

    public <T> T[] toArray(T[] array) {
        return null;
    }


}
