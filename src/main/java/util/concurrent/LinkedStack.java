/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * A stack based on a linked list using atomic operations for adding
 * (pushing) and removing (popping) elements. The <tt>add</tt>
 * method performs a <em>push</em>, <tt>remove</tt> performs a
 * <em>pop</em>. Other methods similarly map to stack-based
 * last-in-first-out (LIFO) operations.
 * 
 * In addition to its use as a stack, this class is useful as an
 * efficient concurrently accessible "bag", a collection to hold and
 * traverse items across many threads in which you do not care about
 * ordering.
 *
 * <p> This collection does not support the use of <tt>null</tt> as
 * elements.
 *
 * <p> Beware that, unlike in most collections, the <tt>size</tt>
 * method is <em>NOT</em> a constant-time operation. Because of the
 * asynchronous nature of these stacks, determining the current number
 * of elements requires an O(n) traversal.
 * @since 1.5
 * @author Doug Lea
**/

public class LinkedStack<E> extends AbstractQueue<E>
        implements Queue<E>, java.io.Serializable {

    /*
     * The basic strategy here relies on a classic linked-list stack,
     * using CAS to relink head on push and pop. The implementation is
     * a little more complicated than this though because the class
     * must support of arbitrary deletion (remove(Object x)). 
     * Rather than just lazily nulling out nodes, and skipping
     * them when they reach top, we detect and relink around nodes
     * as they are removed. This is still partially lazy though.
     * Multiple adjacent concurrent relinks may leave nulls in place,
     * so all traversals must detect and relink lingering nulls.
     */

    /** Head of the linked list */
    private transient volatile AtomicLinkedNode head;

    private static final AtomicReferenceFieldUpdater<LinkedStack, AtomicLinkedNode> headUpdater = new AtomicReferenceFieldUpdater<LinkedStack, AtomicLinkedNode>(new LinkedStack[0], new AtomicLinkedNode[0], "head");

    private boolean casHead(AtomicLinkedNode cmp, AtomicLinkedNode val) {
        return headUpdater.compareAndSet(this, cmp, val);
    }
    

    /**
     * Creates an initially empty LinkedStack.
     */
    public LinkedStack() {
    }

    /**
     * Creates a LinkedStack initially holding the elements
     * of the given collection. The elements are added in 
     * iterator traversal order.
     *
     * @param initialElements the collections whose elements are to be added.
     */
    public LinkedStack(Collection<E> initialElements) {
        for (Iterator<E> it = initialElements.iterator(); it.hasNext();) 
            add(it.next());
    }

    /**
     * Relink over a deleted item; return next node.  This is called
     * whenever a null item field is encountered during any traversal.
     * This is necessary (although rare) because a previous remove()
     * could have linked one node to another node that was also in the
     * process of being removed. Also, iterator.remove exploits the fact
     * that nulls are cleaned out later to allow fully lazy deletion
     * that would otherwise be O(n). 
     * @param deleted the deleted node. Precondition: deleted.item==null.
     * @param previous the node before deleted, or null if deleted is first node
     * @return the next node after previous, or first node if no previous.
     **/
    private AtomicLinkedNode skip(AtomicLinkedNode previous, AtomicLinkedNode deleted) {
        if (previous == null) {
            casHead(deleted, deleted.getNext());
            return head.getNext(); 
        }
        else {
            previous.casNext(deleted, deleted.getNext());
            return previous.getNext();
        }
    }

    /**
     * Pushes the given element on the stack.
     * @param x the element to insert
     * @return true -- (as per the general contract of Queue.offer). 
     * @throws NullPointerException if x is null
     **/
    public boolean offer(E x) {
        if (x == null) throw new NullPointerException();
        AtomicLinkedNode p = new AtomicLinkedNode(x);
        for (;;) {
            AtomicLinkedNode h = head;
            p.setNext(h); 
            if (casHead(h, p))
                return true;
        }
    }

    /**
     * Returns the top element of this stack, or null if empty.
     * @return the top element of this stack, or null if empty.
     **/
    public E peek() {
        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else 
                return (E)item;
        }
        return null;
    }
    
    /**
     * Removes and returns the top element of this stack, or null if empty.
     * @return the top element of this stack, or null if empty.
     **/
    public E poll() {
        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else {
                p.setItem(null);
                skip(previous, p);
                return (E)item;
            }
        }
        return null;
    }    

    public boolean isEmpty() {
        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else 
                return false;
        }
        return true;
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
        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else {
                ++count;
                previous = p;
                p = p.getNext();
            }
        }
        return count;
    }

    public boolean remove(Object x) {
        /*
         * Algorithm:
         *   1. Find node
         *        Clean up after any previous removes while doing so.
         *   2. Null out item field
         *        This will be noticed by other traversals, that will ignore 
         *        it and/or help remove it. 
         *   3. Relink previous node to next node.
         *        If the next node is also in the process of being removed,
         *        this may leave a nulled node in the list. We clean this up
         *        during any traversal.
         */

        if (x == null) return false; // nulls never present

        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else if (x.equals(item) && p.casItem(item, null)) {
                skip(previous, p);
                return true;
            }
            else {
                previous = p;
                p = p.getNext();
            }
        }
        return false;
    }

    public boolean contains(Object x) {
        if (x == null) return false; // nulls never present

        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else if (x.equals(item))
                return true;
            else {
                previous = p;
                p = p.getNext();
            }
        }
        return false;
    }

    public Object[] toArray() {
        // Use ArrayList to deal with resizing.
        ArrayList al = new ArrayList();
        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else {
                al.add(item);
                previous = p;
                p = p.getNext();
            }
        }

        return al.toArray();
    }

    public <T> T[] toArray(T[] a) {
        // try to use sent-in array
        int k = 0;
        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null && k < a.length) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else {
                a[k++] = (T)item;
                previous = p;
                p = p.getNext();
            }
        }
        if (p == null) {
            if (k < a.length)
                a[k] = null;
            return a;
        }

        // If won't fit, use ArrayList version
        ArrayList al = new ArrayList();
        previous = null;
        p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else {
                al.add(item);
                previous = p;
                p = p.getNext();
            }
        }
        return (T[])al.toArray(a);
    }

    public Iterator<E> iterator() { return new Itr(); }

    private class Itr implements Iterator<E> {
        /**
         * Next node to return item for.
         */
        private AtomicLinkedNode nextNode;

        /** 
         * We need to hold on to item fields here because once we claim
         * that an element exists in hasNext(), we must return it in the
         * following next() call even if it was in the process of being
         * removed when hasNext() was called.
         **/
        private E nextItem;

        /**
         * Node of the last returned item, to support remove.
         */
        private AtomicLinkedNode lastRet;

        /**
         * Move to next valid node. 
         * Return item to return for next(), or null if no such.
         */
        private E advance() { 
            lastRet = nextNode;
            E x = nextItem;
            
            AtomicLinkedNode p = (nextNode == null) ? head : nextNode.getNext();
            for (;;) {
                if (p == null) {
                    nextNode = null;
                    nextItem = null;
                    return x;
                }
                Object item = p.getItem();
                if (item == null) 
                    p = skip(nextNode, p);
                else {
                    nextNode = p;
                    nextItem = (E)item;
                    return x;
                }
            }
        }

        Itr() { 
            advance();
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

        AtomicLinkedNode previous = null;
        AtomicLinkedNode p = head;
        while (p != null) {
            Object item = p.getItem();
            if (item == null) 
                p = skip(previous, p);
            else {
                s.writeObject(item);
                previous = p;
                p = p.getNext();
            }
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

        // Read in all elements into array and then insert in reverse order.
        ArrayList<E> al = new ArrayList<E>();
        for (;;) {
            E item = (E)s.readObject();
            if (item == null)
                break;
            al.add(item);
        }

        ListIterator<E> it = al.listIterator(al.size());
        while (it.hasPrevious())
            add(it.previous());
    }


}

