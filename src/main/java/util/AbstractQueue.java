/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util;

/**
 * AbstractQueue provides default implementations of add, remove, and
 * element based on offer, poll, and peek, respectively but that throw
 * exceptions instead of indicating failure via false or null returns.
 * The provided implementations all assume that the base implementation
 * does <em>not</em> allow <tt>null</tt> elements.
 * @since 1.5
 * @author Doug Lea
 */

public abstract class AbstractQueue<E> extends AbstractCollection<E> implements Queue<E> {

    public boolean add(E x) {
        if (offer(x))
            return true;
        else
            throw new IllegalStateException("Queue full");
    }

    public E remove() {
        E x = poll();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }

    public E element() {
        E x = peek();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }

    public void clear() {
        while (poll() != null)
            ;
    }

    // XXX Remove this redundant declaration, pending response from Neal Gafter.
    public abstract Iterator<E> iterator();
}





