/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166x;
import java.util.*;

/**
 * A linear collection in which elements may be inserted and removed
 * from both the beginning and end. A <tt>Deque</tt> (short for
 * "double ended queue") provides uniformly named methods to
 * <tt>get</tt>, <tt>peek</tt>, <tt>poll</tt>, <tt>remove</tt>,
 * <tt>offer</tt>, and <tt>add</tt> the <tt>first</tt> and
 * <tt>last</tt> element of the collection (for example, methods
 * <tt>addFirst</tt>, <tt>pollLast</tt>). Unlike interface {@link
 * List} the Deque interface does not define support for indexed
 * operations or sublists.
 *
 * <p>A view of a subset of Deque operations can be obtained using
 * method {@link #asFifoQueue} to support only Last-In-First-Out (LIFO)
 * stack behavior, as well as method {@link #asFifoQueue} to support only
 * First-in-First-Out (FIFO) queue behavior.  More commonly, a Deque
 * is used when various mixtures of LIFO and FIFO operations are
 * required.
 *
 * <p>Deques additionally provide a few methods to remove elements
 * embedded within a deque, proceding from either direction using
 * <tt>removeFirstOccurrence</tt> and <tt>removeLastOccurrence</tt>.
 * They also support {@link Collection} operations including
 * <tt>contains</tt>, <tt>iterator</tt>, and so on.
 *
 * <p>The {@link #offerFirst} and {@link #offerLast} methods insert an
 * element if possible, otherwise returning <tt>false</tt>.  They
 * differ from {@link java.util.Collection#add Collection.add}, as
 * well as {@link #addFirst} and {@link #addLast} methods, which can
 * fail to add an element only by throwing an unchecked exception.
 * The <tt>offer</tt> methods are designed for use when failure is a
 * normal, rather than exceptional occurrence, for example, in
 * fixed-capacity (or &quot;bounded&quot;) deques.
 *
 * <p><tt>Deque</tt> implementations generally do not allow insertion
 * of <tt>null</tt> elements.  Even in implementations that permit it,
 * <tt>null</tt> should not be inserted into a <tt>Deque</tt>, as
 * <tt>null</tt> is also used as a special return value by the poll
 * methods to indicate that the deque contains no elements.
 * 
 * <p><tt>Deque</tt> implementations generally do not define
 * element-based versions of methods <tt>equals</tt> and
 * <tt>hashCode</tt> but instead inherit the identity based versions
 * from class <tt>Object</tt>.
 *
 * <p>This interface is a member of the <a
 * href="{@docRoot}/../guide/collections/index.html"> Java Collections
 * Framework</a>.
 *
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public interface Deque<E> extends Collection<E> {

    /**
     * Inserts the specified element to the front this deque, if
     * possible.  When using deques that may impose insertion
     * restrictions (for example capacity bounds), method
     * <tt>offerFirst</tt> is generally preferable to method
     * <tt>addFirst</tt> which can fail to insert a non-duplicate
     * element only by throwing an exception.
     *
     * @param o the element to insert.
     * @return <tt>true</tt> if it was possible to add the element to
     * this deque, else <tt>false</tt>
     */
    boolean offerFirst(E o);

    /**
     * Inserts the specified element to the end this deque, if
     * possible.  When using deques that may impose insertion
     * restrictions (for example capacity bounds), method
     * <tt>offerFirst</tt> is generally preferable to method
     * <tt>addLast</tt> which can fail to insert a non-duplicate
     * element only by throwing an exception.
     *
     * @param o the element to insert.
     * @return <tt>true</tt> if it was possible to add the element to
     * this deque, else <tt>false</tt>
     */
    boolean offerLast(E o);

    /**
     * Inserts the specified element to the front this deque, if
     * this deque permits insertion of the given element.  This
     * method has the same semantics as {@link Collection#add}.
     *
     * @param o the element to insert.
     * @return <tt>true</tt> if this deque changed as a result of this
     * call, else <tt>false</tt>
     */
    boolean addFirst(E o);

    /**
     * Inserts the specified element to the end this deque, if
     * this deque permits insertion of the given element.  This
     * method has the same semantics as {@link Collection#add}.
     *
     * @param o the element to insert.
     * @return <tt>true</tt> if this deque changed as a result of this
     * call, else <tt>false</tt>
     */
    boolean addLast(E o);

    /**
     * Retrieves and removes the first element of this deque, or
     * <tt>null</tt> if this deque is empty.
     *
     * @return the first element of this deque, or <tt>null</tt> if
     * this deque is empty.
     */
    E pollFirst();

    /**
     * Retrieves and removes the last element of this deque, or
     * <tt>null</tt> if this deque is empty.
     *
     * @return the last element of this deque, or <tt>null</tt> if
     * this deque is empty.
     */
    E pollLast();

    /**
     * Retrieves and removes the first element of this deque.  This method
     * differs from the <tt>pollFirst</tt> method in that it throws an
     * exception if this deque is empty.
     *
     * @return the first element of this deque.
     * @throws NoSuchElementException if this deque is empty.
     */
    E removeFirst();

    /**
     * Retrieves and removes the last element of this deque.  This method
     * differs from the <tt>pollLast</tt> method in that it throws an
     * exception if this deque is empty.
     *
     * @return the last element of this deque.
     * @throws NoSuchElementException if this deque is empty.
     */
    E removeLast();

    /**
     * Retrieves, but does not remove, the first element of this deque,
     * returning <tt>null</tt> if this deque is empty.
     *
     * @return the first element of this deque, or <tt>null</tt> if
     * this deque is empty.
     */
    E peekFirst();

    /**
     * Retrieves, but does not remove, the last element of this deque,
     * returning <tt>null</tt> if this deque is empty.
     *
     * @return the last element of this deque, or <tt>null</tt> if this deque
     * is empty.
     */
    E peekLast();

    /**
     * Retrieves, but does not remove, the first element of this
     * deque.  This method differs from the <tt>peek</tt> method only
     * in that it throws an exception if this deque is empty.
     *
     * @return the first element of this deque.
     * @throws NoSuchElementException if this deque is empty.
     */
    E getFirst();

    /**
     * Retrieves, but does not remove, the last element of this
     * deque.  This method differs from the <tt>peek</tt> method only
     * in that it throws an exception if this deque is empty.
     *
     * @return the last element of this deque.
     * @throws NoSuchElementException if this deque is empty.
     */
    E getLast();

    /**
     * Removes the first occurrence of the specified element in this
     * deque.  If the deque does not contain the element, it is
     * unchanged.  More formally, removes the first element <tt>e</tt>
     * such that <tt>(o==null ? e==null : o.equals(e))</tt> (if
     * such an element exists).
     *
     * @param o element to be removed from this deque, if present.
     * @return <tt>true</tt> if the deque contained the specified element.
     * @throws NullPointerException if the specified element is <tt>null</tt>
     */
    boolean removeFirstOccurrence(E o);

    /**
     * Removes the last occurrence of the specified element in this
     * deque.  If the deque does not contain the element, it is
     * unchanged.  More formally, removes the last element <tt>e</tt>
     * such that <tt>(o==null ? e==null : o.equals(e))</tt> (if
     * such an element exists).
     *
     * @param o element to be removed from this deque, if present.
     * @return <tt>true</tt> if the deque contained the specified element.
     * @throws NullPointerException if the specified element is <tt>null</tt>
     */
    boolean removeLastOccurrence(E o);

    /**
     * Returns a view of this deque as a first-in-first-out queue,
     * mapping {@link Queue#offer} to <tt>offerLast</tt>, {@link
     * Queue#poll} to <tt>pollFirst</tt>, and other operations
     * accordingly.
     * @return a first-in-first-out view of this deque.
     */
    public Queue<E> asFifoQueue();

    /**
     * Returns a view of this deque as a last-in-first-out stack,
     * mapping {@link Queue#offer} to <tt>offerFirst</tt>, {@link
     * Queue#poll} to <tt>pollFirst</tt>, and other operations
     * accordingly.
     * @return a first-in-last-out view of this deque.
     */
    public Queue<E> asLifoQueue();

}
