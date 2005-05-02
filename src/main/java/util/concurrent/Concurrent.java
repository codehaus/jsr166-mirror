/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent;
import java.util.*;

/**
 * Static methods that operate on or return instances of collection
 * and synchronizer classes and interfaces defined in this package.
 *
 * @since 1.6
 * @author Doug Lea
 */


public class Concurrent {
    /**
     * Returns a view of a {@link BlockingDeque} as a stack-based
     * Last-in-first-out (Lifo) {@link BlockingQueue}. Method
     * <tt>put</tt> is mapped to <tt>putFirst</tt>, <tt>take</tt> is
     * mapped to <tt>takeFirst</tt> and so on. This view can be useful
     * when you would like to use a method requiring a
     * <tt>BlockingQueue</tt> but you need Lifo ordering.
     * @param deque the BlockingDeque
     * @return the queue
     */
    public static <T> BlockingQueue<T> asLifoBlockingQueue(BlockingDeque<T> deque){
        return new AsLIFOBlockingQueue<T>(deque);
    }

    static class AsLIFOBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
        private final BlockingDeque<E> q;
        AsLIFOBlockingQueue(BlockingDeque<E> q) { this.q = q; }
        public boolean offer(E e)            { return q.offerFirst(e); }
        public E poll()                      { return q.pollFirst(); }
        public E remove()                    { return q.removeFirst(); }
        public E peek()                      { return q.peekFirst(); }
        public E element()                   { return q.getFirst(); }
        public int size()                    { return q.size(); }
        public boolean isEmpty()             { return q.isEmpty(); }
        public boolean contains(Object o)    { return q.contains(o); }
        public Iterator<E> iterator()        { return q.iterator(); }
        public Object[] toArray()            { return q.toArray(); }
        public <T> T[] toArray(T[] a)        { return q.toArray(a); }
        public boolean add(E e)              { return q.offerFirst(e); }
        public boolean remove(Object o)      { return q.remove(o); }
        public void clear()                  { q.clear(); }
        public int remainingCapacity()       { return q.remainingCapacity(); }
        public int drainTo(Collection<? super E> c) { return q.drainTo(c); }
        public int drainTo(Collection<? super E> c, int m) {
            return q.drainTo(c, m);
        }
        public void put(E e) throws InterruptedException { q.putFirst(e); }
        public E take() throws InterruptedException { return q.takeFirst(); }
        public boolean offer(E e, long timeout, TimeUnit unit)
            throws InterruptedException {
            return q.offerFirst(e, timeout, unit);
        }
        public E poll(long timeout, TimeUnit unit)
            throws InterruptedException {
            return q.pollFirst(timeout, unit);
        }
    }

}
