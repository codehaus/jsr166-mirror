/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util;

/**
 * This class provides skeletal implementations of some {@link Queue}
 * operations. The implementations in this class are appropriate when
 * the base implementation does <em>not</em> allow <tt>null</tt>
 * elements.  Methods {@link #add add}, {@link #remove remove}, and
 * {@link #element element} are based on {@link #offer offer}, {@link
 * #poll poll}, and {@link #peek peek}, respectively but throw
 * exceptions instead of indicating failure via <tt>false</tt> or
 * <tt>null</tt> returns.
 *
 * <p> A <tt>Queue</tt> implementation that extends this class must
 * minimally define a method {@link Queue#offer} which does not permit
 * insertion of <tt>null</tt> elements, along with methods {@link
 * Queue#peek}, {@link Queue#poll}, {@link Collection#size}, {@link
 * Collection#remove}, and a {@link Collection#iterator} supporting
 * {@link Iterator#remove}. If these requirements cannot be met,
 * consider instead subclassing {@link AbstractCollection}.
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractQueue<E>
    extends AbstractCollection<E>
    implements Queue<E> {

    /**
     * Constructor for use by subclasses.
     */
    protected AbstractQueue() {
    }

    /**
     * Inserts the specified element to this queue, if possible.
     *
     * @param o the element to add.
     * @return <tt>true</tt> if it was possible to add the element to
     *         this queue, else <tt>false</tt>
     * @throws NullPointerException if the specified element is <tt>null</tt>
     */
    public boolean offer(E o) { return false; }
    // FIXME: Replace above no-op with following abstract version
    // when javac allows it.
    //    public abstract boolean offer(E o);

    /**
     * Adds the specified element to this queue. This implementation
     * returns <tt>true</tt> if <tt>offer</tt> succeeds, else
     * throws an IllegalStateException. 
     * th
     * @param o the element
     * @return <tt>true</tt> (as per the general contract of
     *         <tt>Collection.add</tt>).
     *
     * @throws NullPointerException if the specified element is <tt>null</tt>
     * @throws IllegalStateException if element cannot be added
     */
    public boolean add(E o) {
        if (offer(o))
            return true;
        else
            throw new IllegalStateException("Queue full");
    }

    /**
     * Retrieves and removes the head of this queue.
     * This implementation returns the result of <tt>poll</tt>
     * unless the queue is empty.
     *
     * @return the head of this queue.
     * @throws NoSuchElementException if this queue is empty.
     */
    public E remove() {
        E x = poll();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }


    /**
     * Retrieves, but does not remove, the head of this queue.  
     * This implementation returns the result of <tt>peek</tt>
     * unless the queue is empty.
     *
     * @return the head of this queue.
     * @throws NoSuchElementException if this queue is empty.
     */
    public E element() {
        E x = peek();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }

    /**
     * Removes all of the elements from this collection.
     * The collection will be empty after this call returns.
     * <p>This implementation repeatedly invokes {@link #poll poll} until it
     * returns <tt>null</tt>.
     */
    public void clear() {
        while (poll() != null)
            ;
    }

    // Declarations that mostly just remove optionality clauses.

    /**
     * Removes a single instance of the specified element from this
     * queue, if one is present.  More formally, removes an element
     * <tt>e</tt> such that <tt>(o==null ? e==null :
     * o.equals(e))</tt>, if the queue contains such an element.
     * Returns <tt>true</tt> if the queue contained the specified
     * element (or equivalently, if the queue changed as a result of
     * the call).
     *
     * @param o the element to remove
     * @return true if the element was removed
     */
    public abstract boolean remove(Object o);

    /**
     * Removes from this queue all of its elements that are contained in
     * the specified collection. <p>
     *
     * This implementation iterates over this queue, checking each
     * element returned by the iterator in turn to see if it's contained
     * in the specified collection.  If it's so contained, it's removed from
     * this queue with the iterator's <tt>remove</tt> method.<p>
     *
     * @param c elements to be removed from this collection.
     * @return <tt>true</tt> if this queue changed as a result of the
     *         call.
     * @throws NullPointerException if the specified collection is null.
     */
    public boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    /**
     * Retains only the elements in this queue that are contained in the
     * specified collection.  In other words, removes
     * from this queue all of its elements that are not contained in the
     * specified collection. <p>
     *
     * This implementation iterates over this queue, checking each
     * element returned by the iterator in turn to see if it's contained
     * in the specified collection.  If it's not so contained, it's removed
     * from this queue with the iterator's <tt>remove</tt> method.<p>
     *
     * @param c elements to be retained in this collection.
     * @return <tt>true</tt> if this queue changed as a result of the
     *         call.
     * @throws NullPointerException if the specified collection is null.
     *
     */
    public boolean retainAll(Collection<?> c) {
        return super.retainAll(c);
    }

    /**
     * Adds all of the elements in the specified collection to this
     * queue.  The behavior of this operation is undefined if the
     * specified collection is modified while the operation is in
     * progress.  (This implies that the behavior of this call is
     * undefined if the specified collection is this queue, and this
     * queue is nonempty.)
     *
     * <p> This implementation iterates over the specified collection,
     * and uses the <tt>add</tt> method to insert each object returned by
     * the iterator to this queue, in turn.
     *
     * @param c collection whose elements are to be added to this queue
     * @return <tt>true</tt> if this queue changed as a result of the
     *         call.
     * @throws NullPointerException if <tt>c</tt> or any element in <tt>c</tt>
     * is <tt>null</tt>
     * @throws IllegalStateException if any element cannot be added.
     * 
     */
    public boolean addAll(Collection<? extends E> c) {
        return super.addAll(c);
    }

}
