/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util;

/**
 * <tt>AbstractQueue</tt> provides default implementations of
 * {@link #add add}, {@link #remove remove}, and {@link #element element}
 * based on
 * {@link #offer offer}, {@link #poll poll}, and {@link #peek peek},
 * respectively but that
 * throw exceptions instead of indicating failure via <tt>false</tt> or
 * <tt>null</tt> returns.
 * <p>The provided implementations all assume that the base implementation
 * does <em>not</em> allow <tt>null</tt> elements.
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractQueue<E>
    extends AbstractCollection<E>
    implements Queue<E> {

    // note that optional methods are not optional here or in our subclasses,
    // so we redefine each optional method to document that it is not optional
    // We also inherit, or define, all necessary @throws comments

    //    /**
    //     * @throws NullPointerException if the specified element is <tt>null</tt>
    //     */
    //    public abstract boolean offer(E o);

    /**
     * Adds the specified element to this queue.
     * @return <tt>true</tt> (as per the general contract of
     * <tt>Collection.add</tt>).
     *
     * @throws NullPointerException if the specified element is <tt>null</tt>
     */
    public boolean add(E o) {
        if (offer(o))
            return true;
        else
            throw new IllegalStateException("Queue full");
    }

    /**
     * Adds all of the elements in the specified collection to this queue.
     * The behavior of this operation is undefined if
     * the specified collection is modified while the operation is in
     * progress.  (This implies that the behavior of this call is undefined if
     * the specified collection is this queue, and this queue is nonempty.)
     * <p>
     * This implementation iterates over the specified collection, and adds
     * each object returned by the iterator to this queue, in turn.
     *
     * @param c collection whose elements are to be added to this queue
     * @return <tt>true</tt> if this collection changed as a result of the
     *         call.
     * @throws NullPointerException if <tt>c</tt> or any element in <tt>c</tt>
     * is <tt>null</tt>
     * 
     */
    public boolean addAll(Collection<? extends E> c) {
        return super.addAll(c);
    }

    /** @throws NoSuchElementException {@inheritDoc} */
    public E remove() {
        E x = poll();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }

    /** @throws NoSuchElementException {@inheritDoc} */
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

    // XXX Remove this redundant declaration, pending response from Neal Gafter.
    public abstract Iterator<E> iterator();
}





