/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166x;
import java.util.*;

/**
 * A {@link SortedSet} extended with methods reporting closest matches
 * for given search targets. Methods <tt>lower</tt>, <tt>floor</tt>,
 * <tt>ceiling</tt>, and <tt>higher</tt> return keys respectively
 * less, less than or equal, greater than or equal, and greater than a
 * given key, returning <tt>null</tt> if there is no such key.  These
 * methods are designed for locating, not traversing entries. To
 * traverse, use view iterators and/or <tt>subset</tt>. This interface
 * additionally defines methods <tt>pollFirst</tt> and
 * <t>pollLast</tt> that return and remove the lowest and highest key,
 * if one exists, else returning <tt>null</tt>.
 *
 * <p> The return values of these methods may be ambiguous in
 * implementations that permit <tt>null</tt> elements. However, even
 * in this case the result can be diambiguated by checking
 * <tt>contains(null)</tt>.
 *
 * @author Doug Lea
 * @param <E> the type of elements maintained by this set
 */
public interface NavigableSet<E> extends SortedSet<E> {
    /**
     * Returns an element greater than or equal to the given element, or
     * null if there is no such element.
     * 
     * @param o the value to match
     * @return an element greater than or equal to given element, or null
     * if there is no such element.
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if o is <tt>null</tt> 
     * and this set deas not permit <tt>null</tt> elements
     */
    public E ceiling(E o);

    /**
     * Returns an element strictly less than the given element, or null if
     * there is no such element.
     * 
     * @param o the value to match
     * @return the greatest element less than the given element, or
     * null if there is no such element.
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>
     * and this set deas not permit <tt>null</tt> elements
     */
    public E lower(E o);

    /**
     * Returns an element less than or equal to the given element, or null
     * if there is no such element.
     * 
     * @param o the value to match
     * @return the greatest element less than or equal to given
     * element, or null if there is no such element.
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>.
     * and this set deas not permit <tt>null</tt> elements
     */
    public E floor(E o);

    /**
     * Returns an element strictly greater than the given element, or null
     * if there is no such element.
     * 
     * @param o the value to match
     * @return the least element greater than the given element, or
     * null if there is no such element.
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>
     * and this set deas not permit <tt>null</tt> elements
     */
    public E higher(E o);

    /**
     * Retrieves and removes the first (lowest) element.
     *
     * @return the first element, or <tt>null</tt> if empty.
     */
    public E pollFirst();

    /**
     * Retrieves and removes the last (highest) element.
     *
     * @return the last element, or <tt>null</tt> if empty.
     */
    public E pollLast();

    /**
     * Returns a view of the portion of this set whose elements range from
     * <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>, exclusive.  (If
     * <tt>fromElement</tt> and <tt>toElement</tt> are equal, the returned
     * sorted set is empty.)  The returned sorted set is backed by this set,
     * so changes in the returned sorted set are reflected in this set, and
     * vice-versa. 
     * @param fromElement low endpoint (inclusive) of the subSet.
     * @param toElement high endpoint (exclusive) of the subSet.
     * @return a view of the portion of this set whose elements range from
     * 	       <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>,
     * 	       exclusive.
     * @throws ClassCastException if <tt>fromElement</tt> and
     *         <tt>toElement</tt> cannot be compared to one another using
     *         this set's comparator (or, if the set has no comparator,
     *         using natural ordering).
     * @throws IllegalArgumentException if <tt>fromElement</tt> is
     * greater than <tt>toElement</tt>.
     * @throws NullPointerException if <tt>fromElement</tt> or
     *	       <tt>toElement</tt> is <tt>null</tt>.
     */
    public NavigableSet<E> subSet(E fromElement, E toElement);

    /**
     * Returns a view of the portion of this set whose elements are strictly
     * less than <tt>toElement</tt>.  The returned sorted set is backed by
     * this set, so changes in the returned sorted set are reflected in this
     * set, and vice-versa.  
     * @param toElement high endpoint (exclusive) of the headSet.
     * @return a view of the portion of this set whose elements are strictly
     * 	       less than toElement.
     * @throws ClassCastException if <tt>toElement</tt> is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if <tt>toElement</tt> does not implement <tt>Comparable</tt>).
     * @throws NullPointerException if <tt>toElement</tt> is <tt>null</tt>.
     */
    public NavigableSet<E> headSet(E toElement);

    /**
     * Returns a view of the portion of this set whose elements are
     * greater than or equal to <tt>fromElement</tt>.  The returned
     * sorted set is backed by this set, so changes in the returned
     * sorted set are reflected in this set, and vice-versa.
     * @param fromElement low endpoint (inclusive) of the tailSet.
     * @return a view of the portion of this set whose elements are
     * greater than or equal to <tt>fromElement</tt>.
     * @throws ClassCastException if <tt>fromElement</tt> is not
     * compatible with this set's comparator (or, if the set has no
     * comparator, if <tt>fromElement</tt> does not implement
     * <tt>Comparable</tt>).
     * @throws NullPointerException if <tt>fromElement</tt> is <tt>null</tt>.
     */
    public NavigableSet<E> tailSet(E fromElement);
}
