/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.atomic.*;


/**
 * An unbounded thread-safe queue based on linked nodes.  ConcurrentLinkedQueues
 * are an especially good choice when many threads will share access
 * to a common  queue.
 *
 * <p> This implementation employs an efficient "wait-free" algorithm
 * based on one described in <a
 * href="http://www.cs.rochester.edu/u/michael/PODC96.html"> Simple,
 * Fast, and Practical Non-Blocking and Blocking Concurrent Queue
 * Algorithms</a> by Maged M. Michael and Michael L. Scott.)
 *
 * Beware that, unlike in most collections, the <tt>size</tt> method
 * is <em>NOT</em> a constant-time operation. Because of the
 * asynchronous nature of these queues, determining the current number
 * of elements requires an O(n) traversal.
 * @since 1.5
 * @author Doug Lea
 *
 **/
public class ConcurrentLinkedQueue<E> extends AbstractQueue<E>
        implements Queue<E>, java.io.Serializable {

    /*
     * This is a straight adaptation of Michael & Scott algorithm.
     * For explanation, read the paper.  The only (minor) algorithmic
     * difference is that this version supports lazy deletion of
     * internal nodes (method remove(Object)) -- remove CAS'es item
     * fields to null. The normal queue operations unlink but then
     * pass over nodes with null item fields. Similarly, iteration
     * methods ignore those with nulls.
     */

    // Atomics support

    private static final AtomicReferenceFieldUpdater<ConcurrentLinkedQueue, AtomicLinkedNode> tailUpdater = new AtomicReferenceFieldUpdater<ConcurrentLinkedQueue, AtomicLinkedNode>(ConcurrentLinkedQueue.class, AtomicLinkedNode.class, "tail");
    private static final AtomicReferenceFieldUpdater<ConcurrentLinkedQueue, AtomicLinkedNode> headUpdater = new AtomicReferenceFieldUpdater<ConcurrentLinkedQueue, AtomicLinkedNode>(ConcurrentLinkedQueue.class,  AtomicLinkedNode.class, "head");

    private boolean casTail(AtomicLinkedNode cmp, AtomicLinkedNode val) {
        return tailUpdater.compareAndSet(this, cmp, val);
    }

    private boolean casHead(AtomicLinkedNode cmp, AtomicLinkedNode val) {
        return headUpdater.compareAndSet(this, cmp, val);
    }


    /**
     * Pointer to header node, initialized to a dummy node.  The first
     * actual node is at head.getNext().
     */
    private transient volatile AtomicLinkedNode head = new AtomicLinkedNode(null, null);

    /** Pointer to last node on list **/
    private transient volatile AtomicLinkedNode tail = head;


    /**
     * Creates an initially empty ConcurrentLinkedQueue.
     */
    public ConcurrentLinkedQueue() {}

    /**
     * Creates a ConcurrentLinkedQueue initially holding the elements
     * of the given collection. The elements are added in
     * iterator traversal order.
     *
     * @param initialElements the collections whose elements are to be added.
     */
    public ConcurrentLinkedQueue(Collection<? extends E> initialElements) {
        for (Iterator<? extends E> it = initialElements.iterator(); it.hasNext();)
            add(it.next());
    }

    public boolean offer(E x) {
        if (x == null) throw new NullPointerException();
        AtomicLinkedNode n = new AtomicLinkedNode(x, null);
        for(;;) {
            AtomicLinkedNode t = tail;
            AtomicLinkedNode s = t.getNext();
            if (t == tail) {
                if (s == null) {
                    if (t.casNext(s, n)) {
                        casTail(t, n);
                        return true;
                    }
                }
                else {
                    casTail(t, s);
                }
            }
        }
    }

    public E poll() {
        for (;;) {
            AtomicLinkedNode h = head;
            AtomicLinkedNode t = tail;
            AtomicLinkedNode first = h.getNext();
            if (h == head) {
                if (h == t) {
                    if (first == null)
                        return null;
                    else
                        casTail(t, first);
                }
                else if (casHead(h, first)) {
                    E item = (E)first.getItem();
                    if (item != null) {
                        first.setItem(null);
                        return item;
                    }
                    // else skip over deleted item, continue loop,
                }
            }
        }
    }

    public E peek() { // same as poll except don't remove item
        for (;;) {
            AtomicLinkedNode h = head;
            AtomicLinkedNode t = tail;
            AtomicLinkedNode first = h.getNext();
            if (h == head) {
                if (h == t) {
                    if (first == null)
                        return null;
                    else
                        casTail(t, first);
                }
                else {
                    E item = (E)first.getItem();
                    if (item != null)
                        return item;
                    else // remove deleted node and continue
                        casHead(h, first);
                }
            }
        }
    }

    /**
     * Return the first actual (non-header) node on list.  This is yet
     * another variant of poll/peek; here returning out the first
     * node, not element (so we cannot collapse with peek() without
     * introducing race.)
     */
    AtomicLinkedNode first() {
        for (;;) {
            AtomicLinkedNode h = head;
            AtomicLinkedNode t = tail;
            AtomicLinkedNode first = h.getNext();
            if (h == head) {
                if (h == t) {
                    if (first == null)
                        return null;
                    else
                        casTail(t, first);
                }
                else {
                    if (first.getItem() != null)
                        return first;
                    else // remove deleted node and continue
                        casHead(h, first);
                }
            }
        }
    }


    public boolean isEmpty() {
        return first() == null;
    }

    /**
     * Returns the number of elements in this collection.
     *
     * Beware that, unlike in most collection, this method> is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     * @return the number of elements in this collection
     */
    public int size() {
        int count = 0;
        for (AtomicLinkedNode p = first(); p != null; p = p.getNext()) {
            if (p.getItem() != null)
                ++count;
        }
        return count;
    }

    public boolean contains(Object x) {
        if (x == null) return false;
        for (AtomicLinkedNode p = first(); p != null; p = p.getNext()) {
            Object item = p.getItem();
            if (item != null &&
                x.equals(item))
                return true;
        }
        return false;
    }

    public boolean remove(Object x) {
        if (x == null) return false;
        for (AtomicLinkedNode p = first(); p != null; p = p.getNext()) {
            Object item = p.getItem();
            if (item != null &&
                x.equals(item) &&
                p.casItem(item, null))
                return true;
        }
        return false;
    }

    public Object[] toArray() {
        // Use ArrayList to deal with resizing.
        ArrayList<E> al = new ArrayList<E>();
        for (AtomicLinkedNode p = first(); p != null; p = p.getNext()) {
            E item = (E) p.getItem();
            if (item != null)
                al.add(item);
        }
        return al.toArray();
    }

    public <T> T[] toArray(T[] a) {
        // try to use sent-in array
        int k = 0;
        AtomicLinkedNode p;
        for (p = first(); p != null && k < a.length; p = p.getNext()) {
            Object item = p.getItem();
            if (item != null)
                a[k++] = (T)item;
        }
        if (p == null) {
            if (k < a.length)
                a[k] = null;
            return a;
        }

        // If won't fit, use ArrayList version
        ArrayList<E> al = new ArrayList<E>();
        for (AtomicLinkedNode q = first(); q != null; q = q.getNext()) {
            E item = (E) q.getItem();
            if (item != null)
                al.add(item);
        }
        return (T[])al.toArray(a);
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /**
         * Next node to return item for.
         */
        private AtomicLinkedNode nextNode;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         **/
        private E nextItem;

        /**
         * Node of the last returned item, to support remove.
         */
        private AtomicLinkedNode lastRet;

        Itr() {
            advance();
        }

        /**
         * Move to next valid node.
         * Return item to return for next(), or null if no such.
         */
        private E advance() {
            lastRet = nextNode;
            E x = (E)nextItem;

            AtomicLinkedNode p = (nextNode == null)? first() : nextNode.getNext();
            for (;;) {
                if (p == null) {
                    nextNode = null;
                    nextItem = null;
                    return x;
                }
                E item = (E)p.getItem();
                if (item != null) {
                    nextNode = p;
                    nextItem = item;
                    return x;
                }
                else // skip over nulls
                    p = p.getNext();
            }
        }

        public boolean hasNext() {
            return nextNode != null;
        }

        public E next() {
            if (nextNode == null) throw new NoSuchElementException();
            return advance();
        }

        public void remove() {
            AtomicLinkedNode l = lastRet;
            if (l == null) throw new IllegalStateException();
            // rely on a future traversal to relink.
            l.setItem(null);
            lastRet = null;
        }
    }

    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @serialData All of the elements (each an <tt>E</tt>) in
     * the proper order, followed by a null
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out all elements in the proper order.
        for (AtomicLinkedNode p = first(); p != null; p = p.getNext()) {
            Object item = p.getItem();
            if (item != null)
                s.writeObject(item);
        }

        // Use trailing null as sentinel
        s.writeObject(null);
    }

    /**
     * Reconstitute the Queue instance from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject();

        // Read in all elements and place in queue
        for (;;) {
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }

}
