package java.util.concurrent;

import java.util.*;

/**
 * An unbounded thread-safe queue based on linked nodes.
 *
 * <p> This implementation employs an efficient "wait-free" algorithm
 * using <tt>AtomicReferences</tt> (based on the described in <a
 * href="http://www.cs.rochester.edu/u/michael/PODC96.html"> Simple,
 * Fast, and Practical Non-Blocking and Blocking Concurrent Queue
 * Algorithms</a> by Maged M. Michael and Michael L. Scott.)
 *
 **/
public class LinkedQueue<E> extends AbstractCollection<E>
        implements Queue<E>, java.io.Serializable {

    public LinkedQueue() {}
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
