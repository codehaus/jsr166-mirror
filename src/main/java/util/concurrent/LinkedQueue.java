/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.atomic.*;


/**
 * An unbounded thread-safe queue based on linked nodes.  LinkedQueues
 * are an especially good choice when many threads will share access
 * to a common  queue. 
 *
 * <p> This implementation employs an efficient "wait-free" algorithm
 * based on one described in <a
 * href="http://www.cs.rochester.edu/u/michael/PODC96.html"> Simple,
 * Fast, and Practical Non-Blocking and Blocking Concurrent Queue
 * Algorithms</a> by Maged M. Michael and Michael L. Scott.)
 *
 * Beware that, unlike most collections, the <tt>size</tt> method is
 * <em>NOT</em> a constant-time operation. Because of the asynchronous
 * nature of these queues, determining the current number of elements
 * requires an O(n) traversal.
 * 
 **/
public class LinkedQueue<E> extends AbstractQueue<E>
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

    static class Node {
        private volatile Object item;
        private volatile Node next;
        Node(Object x, Node n) { item = x; next = n; }
    }

    // Atomics support

    private final static AtomicReferenceFieldUpdater<LinkedQueue, Node> tailUpdater = new AtomicReferenceFieldUpdater<LinkedQueue, Node>(new LinkedQueue[0], new Node[0], "tail");
    private final static AtomicReferenceFieldUpdater<LinkedQueue, Node> headUpdater = new AtomicReferenceFieldUpdater<LinkedQueue, Node>(new LinkedQueue[0], new Node[0], "head");
    private final static AtomicReferenceFieldUpdater<Node, Node> nextUpdater =
    new AtomicReferenceFieldUpdater<Node, Node>(new Node[0], new Node[0], "next");
    private final static AtomicReferenceFieldUpdater<Node, Object> itemUpdater
     = new AtomicReferenceFieldUpdater<Node, Object>(new Node[0], new Object[0], "item");

    private boolean casTail(Node cmp, Node val) {
        return tailUpdater.compareAndSet(this, cmp, val);
    }

    private boolean casHead(Node cmp, Node val) {
        return headUpdater.compareAndSet(this, cmp, val);
    }

    private boolean casNext(Node node, Node cmp, Node val) {
        return nextUpdater.compareAndSet(node, cmp, val);
    }

    private boolean casItem(Node node, Object cmp, Object val) {
        return itemUpdater.compareAndSet(node, cmp, val);
    }


    /** 
     * Pointer to header node, initialized to a dummy node.  The first
     * actual node is at head.next.
     */
    private transient volatile Node head = new Node(null, null);

    /** Pointer to last node on list **/
    private transient volatile Node tail = head;

    /**
     * Return the first actual (non-header) node on list.
     */
    Node first() { return head.next; }

    public LinkedQueue() {}

    public LinkedQueue(Collection<E> initialElements) {
        for (Iterator<E> it = initialElements.iterator(); it.hasNext();) 
            add(it.next());
    }


    public boolean add(E x) {
        if (x == null) throw new IllegalArgumentException();
        Node n = new Node(x, null);
        for(;;) {
            Node t = tail;
            Node s = t.next;
            if (t == tail) {
                if (s == null) {
                    if (casNext(t, s, n)) {
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

    public boolean offer(E x) {
        return add(x);
    }

    public E poll() {
        for (;;) {
            Node h = head;
            Node t = tail;
            Node first = h.next;
            if (h == head) {
                if (h == t) {
                    if (first == null)
                        return null;
                    else
                        casTail(t, first);
                }
                else if (casHead(h, first)) {
                    E item = (E)first.item;
                    if (item != null) {
                        itemUpdater.set(first, null);
                        return item;
                    }
                    // else skip over deleted item, continue loop, 
                }
            }
        }
    }

    public E peek() { // same as poll except don't remove item
        for (;;) {
            Node h = head;
            Node t = tail;
            Node first = h.next;
            if (h == head) {
                if (h == t) {
                    if (first == null)
                        return null;
                    else
                        casTail(t, first);
                }
                else {
                    E item = (E)first.item;
                    if (item != null)
                        return item;
                    else // remove deleted node and continue
                        casHead(h, first);
                }
            }
        }
    }

    public boolean isEmpty() {
        return peek() == null;
    }

    /**
     * Returns the number of elements in this collection. 
     * 
     * Beware that, unlike most collection, this method> is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     * @return the number of elements in this collection
     */ 
    public int size() {
        int count = 0;
        for (Node p = first(); p != null; p = p.next) {
            if (p.item != null)
                ++count;
        }
        return count;
    }

    public boolean contains(Object x) {
        if (x == null) return false;
        for (Node p = first(); p != null; p = p.next) {
            Object item = p.item;
            if (item != null && 
                x.equals(item))
                return true;
        }
        return false;
    }

    public boolean remove(Object x) {
        if (x == null) return false;
        for (Node p = first(); p != null; p = p.next) {
            Object item = p.item;
            if (item != null && 
                x.equals(item) &&
                casItem(p, item, null))
                return true;
        }
        return false;
    }
    
    public Object[] toArray() {
        // Use ArrayList to deal with resizing.
        ArrayList al = new ArrayList();
        for (Node p = first(); p != null; p = p.next) {
            Object item = p.item;
            if (item != null)
                al.add(item);
        }
        return al.toArray();
    }

    public <T> T[] toArray(T[] a) {
        // try to use sent-in array
        int k = 0;
        Node p;
        for (p = first(); p != null && k < a.length; p = p.next) {
            Object item = p.item;
            if (item != null)
                a[k++] = (T)item;
        }
        if (p == null) {
            if (k < a.length)
                a[k] = null;
            return a;
        }

        // If won't fit, use ArrayList version
        ArrayList al = new ArrayList();
        for (Node q = first(); q != null; q = q.next) {
            Object item = q.item;
            if (item != null)
                al.add(item);
        }
        return (T[])al.toArray(a);
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        private Node current;
        /** 
         * currentItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         **/
        private E currentItem;

        Itr() { 
            for (current = first(); current != null; current = current.next) {
                E item = (E)current.item;
                if (item != null) {
                    currentItem = item;
                    return;
                }
            }
        }
        
        /**
         * Move to next valid node. 
         * Return previous item, or null if no such.
         */
        private E advance() { 
            E x = (E)currentItem;
            for (;;) {
                current = current.next;
                if (current == null) {
                    currentItem = null;
                    return x;
                }
                E item = (E)current.item;
                if (item != null) {
                    currentItem = item;
                    return x;
                }
            }
        }
        
        public boolean hasNext() {
            return current != null;
        }
        
        public E next() {
            if (current == null) throw new NoSuchElementException();
            return advance();
        }
        
        public void remove() {
            if (current == null) throw new NoSuchElementException();
            // java.util.Iterator contract requires throw if already removed
            if (currentItem == null) throw new IllegalStateException();
            // rely on a future traversal to relink.
            currentItem = null;
            itemUpdater.set(current, null);
        }
    }

    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @serialData All of the elements (each an <tt>E</tt>) in
     * the proper order, followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        // Write out any hidden stuff
        s.defaultWriteObject();
        
        // Write out all elements in the proper order.
        for (Node p = first(); p != null; p = p.next) 
            s.writeObject(p.item);

        // Use trailing null as sentinel
        s.writeObject(null);
    }

    /**
     * Reconstitute the Queue instance from a stream (that is,
     * deserialize it).
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
