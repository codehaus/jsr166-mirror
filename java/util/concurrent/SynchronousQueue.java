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
public class SynchronousQueue extends AbstractCollection implements BlockingQueue, java.io.Serializable {

    public SynchronousQueue() {}
    public void put(Object x) throws InterruptedException {
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
    public boolean remove(Object x) {
        return false;
    }
    public Object remove() {
        return null;
    }
    public Iterator iterator() { 
      return null;
    }

    public Object element() {
        return null;
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
    public Object[] toArray() {
        return null;
    }

    public Object[] toArray(Object[] array) {
        return null;
    }

  
}
