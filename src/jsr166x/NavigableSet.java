/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166x;
import java.util.*;

/**
 * A {@link SortedSet} extended with navigation methods reporting
 * closest matches for given search targets. Methods {@code lower},
 * {@code floor}, {@code ceiling}, and {@code higher} return keys
 * respectively less than, less than or equal, greater than or equal,
 * and greater than a given key, returning {@code null} if there is
 * no such key.  A {@code NavigableSet} may be viewed and traversed
 * in either ascending or descending order.  The {@code Collection}
 * {@code iterator} method returns an ascending {@code Iterator} and
 * the additional method {@code descendingIterator} returns
 * descending iterator. The performance of ascending traversals is
 * likely to be faster than descending traversals.  This interface
 * additionally defines methods {@code pollFirst} and
 * <t>pollLast</tt> that return and remove the lowest and highest key,
 * if one exists, else returning {@code null}.
 *
 * <p>The return values of navigation methods may be ambiguous in
 * implementations that permit {@code null} elements. However, even
 * in this case the result can be disambiguated by checking
 * {@code contains(null)}. To avoid such issues, implementations of
 * this interface are encouraged <em>not</em> to permit insertion of
 * {@code null} elements. (Note that sorted sets of {@link
 * Comparable} elements intrinsically do not permit {@code null}.)
 *
 * @author Doug Lea
 * @param <E> the type of elements maintained by this set
 */
public interface NavigableSet<E> extends SortedSet<E> {
    /**
     * Returns an element greater than or equal to the given element, or
     * {@code null} if there is no such element.
     *
     * @param o the value to match
     * @return an element greater than or equal to given element, or
     * {@code null} if there is no such element.
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set.
     * @throws NullPointerException if o is {@code null}
     * and this set deas not permit {@code null} elements
     */
    public E ceiling(E o);

    /**
     * Returns an element strictly less than the given element, or
     * {@code null} if there is no such element.
     *
     * @param o the value to match
     * @return the greatest element less than the given element, or
     * {@code null} if there is no such element.
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set
     * @throws NullPointerException if o is {@code null}
     * and this set deas not permit {@code null} elements
     */
    public E lower(E o);

    /**
     * Returns an element less than or equal to the given element, or
     * {@code null} if there is no such element.
     *
     * @param o the value to match
     * @return the greatest element less than or equal to given
     * element, or {@code null} if there is no such element
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set
     * @throws NullPointerException if o is {@code null}
     * and this set deas not permit {@code null} elements
     */
    public E floor(E o);

    /**
     * Returns an element strictly greater than the given element, or
     * {@code null} if there is no such element.
     *
     * @param o the value to match
     * @return the least element greater than the given element, or
     * {@code null} if there is no such element.
     * @throws ClassCastException if o cannot be compared with the elements
     *            currently in the set
     * @throws NullPointerException if o is {@code null}
     * and this set deas not permit {@code null} elements
     */
    public E higher(E o);

    /**
     * Retrieves and removes the first (lowest) element.
     *
     * @return the first element, or {@code null} if empty
     */
    public E pollFirst();

    /**
     * Retrieves and removes the last (highest) element.
     *
     * @return the last element, or {@code null} if empty
     */
    public E pollLast();

    /**
     * Returns an iterator over the elements in this collection, in
     * descending order.
     *
     * @return an {@code Iterator} over the elements in this collection
     */
    Iterator<E> descendingIterator();

    /**
     * Returns a view of the portion of this set whose elements range from
     * {@code fromElement}, inclusive, to {@code toElement}, exclusive.  (If
     * {@code fromElement} and {@code toElement} are equal, the returned
     * sorted set is empty.)  The returned sorted set is backed by this set,
     * so changes in the returned sorted set are reflected in this set, and
     * vice-versa.
     * @param fromElement low endpoint (inclusive) of the subSet
     * @param toElement high endpoint (exclusive) of the subSet
     * @return a view of the portion of this set whose elements range from
     *         {@code fromElement}, inclusive, to {@code toElement},
     *         exclusive
     * @throws ClassCastException if {@code fromElement} and
     *         {@code toElement} cannot be compared to one another using
     *         this set's comparator (or, if the set has no comparator,
     *         using natural ordering)
     * @throws IllegalArgumentException if {@code fromElement} is
     * greater than {@code toElement}
     * @throws NullPointerException if {@code fromElement} or
     *         {@code toElement} is {@code null}
     * and this set deas not permit {@code null} elements
     */
    public NavigableSet<E> subSet(E fromElement, E toElement);

    /**
     * Returns a view of the portion of this set whose elements are strictly
     * less than {@code toElement}.  The returned sorted set is backed by
     * this set, so changes in the returned sorted set are reflected in this
     * set, and vice-versa.
     * @param toElement high endpoint (exclusive) of the headSet
     * @return a view of the portion of this set whose elements are strictly
     *         less than toElement
     * @throws ClassCastException if {@code toElement} is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if {@code toElement} does not implement {@code Comparable})
     * @throws NullPointerException if {@code toElement} is {@code null}
     * and this set deas not permit {@code null} elements
     */
    public NavigableSet<E> headSet(E toElement);

    /**
     * Returns a view of the portion of this set whose elements are
     * greater than or equal to {@code fromElement}.  The returned
     * sorted set is backed by this set, so changes in the returned
     * sorted set are reflected in this set, and vice-versa.
     * @param fromElement low endpoint (inclusive) of the tailSet
     * @return a view of the portion of this set whose elements are
     * greater than or equal to {@code fromElement}
     * @throws ClassCastException if {@code fromElement} is not
     * compatible with this set's comparator (or, if the set has no
     * comparator, if {@code fromElement} does not implement
     * {@code Comparable})
     * @throws NullPointerException if {@code fromElement} is {@code null}
     * and this set deas not permit {@code null} elements
     */
    public NavigableSet<E> tailSet(E fromElement);
}
