/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util;
import java.util.*; // for javadoc (till 6280605 is fixed)

/**
 * A {@link SortedSet} extended with navigation methods reporting
 * closest matches for given search targets. Methods <tt>lower</tt>,
 * <tt>floor</tt>, <tt>ceiling</tt>, and <tt>higher</tt> return elements
 * respectively less than, less than or equal, greater than or equal,
 * and greater than a given element, returning <tt>null</tt> if there is
 * no such element.  A <tt>NavigableSet</tt> may be viewed and traversed
 * in either ascending or descending order.  The <tt>Collection</tt>
 * <tt>iterator</tt> method returns an ascending <tt>Iterator</tt> and
 * the additional method <tt>descendingIterator</tt> returns a
 * descending iterator. The performance of ascending traversals is
 * likely to be faster than descending traversals.  This interface
 * additionally defines methods <tt>pollFirst</tt> and
 * <tt>pollLast</tt> that return and remove the lowest and highest
 * element, if one exists, else returning <tt>null</tt>.
 * Methods <tt>navigableSubSet</tt>, <tt>navigableHeadSet</tt>, and
 * <tt>navigableTailSet</tt> differ from the similarly named
 * <tt>SortedSet</tt> methods only in their declared return types.
 * Subsets of any <tt>NavigableSet</tt> must implement the
 * <tt>NavigableSet</tt> interface.
 *
 * <p> The return values of navigation methods may be ambiguous in
 * implementations that permit <tt>null</tt> elements. However, even
 * in this case the result can be disambiguated by checking
 * <tt>contains(null)</tt>. To avoid such issues, implementations of
 * this interface are encouraged to <em>not</em> permit insertion of
 * <tt>null</tt> elements. (Note that sorted sets of {@link
 * Comparable} elements intrinsically do not permit <tt>null</tt>.)
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author Doug Lea
 * @param <E> the type of elements maintained by this set
 * @since 1.6
 */
public interface NavigableSet<E> extends SortedSet<E> {
    /**
     * Returns the greatest element in this set strictly less than the
     * given element, or <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return the greatest element less than <tt>e</tt>,
     *         or <tt>null</tt> if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E lower(E e);

    /**
     * Returns the greatest element in this set less than or equal to
     * the given element, or <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return the greatest element less than or equal to <tt>e</tt>,
     *         or <tt>null</tt> if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E floor(E e);

    /**
     * Returns the least element in this set greater than or equal to
     * the given element, or <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return the least element greater than or equal to <tt>e</tt>,
     *         or <tt>null</tt> if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E ceiling(E e);

    /**
     * Returns the least element in this set strictly greater than the
     * given element, or <tt>null</tt> if there is no such element.
     *
     * @param e the value to match
     * @return the least element greater than <tt>e</tt>,
     *         or <tt>null</tt> if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E higher(E e);

    /**
     * Retrieves and removes the first (lowest) element,
     * or returns <tt>null</tt> if this set is empty.
     *
     * @return the first element, or <tt>null</tt> if this set is empty
     */
    E pollFirst();

    /**
     * Retrieves and removes the last (highest) element,
     * or returns <tt>null</tt> if this set is empty.
     *
     * @return the last element, or <tt>null</tt> if this set is empty
     */
    E pollLast();

    /**
     * Returns an iterator over the elements in this set, in ascending order.
     *
     * @return an iterator over the elements in this set, in ascending order
     */
    Iterator<E> iterator();

    /**
     * Returns an iterator over the elements in this set, in descending order.
     *
     * @return an iterator over the elements in this set, in descending order
     */
    Iterator<E> descendingIterator();

    /**
     * Returns a view of the portion of this set whose elements range
     * from <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>,
     * exclusive.  (If <tt>fromElement</tt> and <tt>toElement</tt> are
     * equal, the returned set is empty.)  The returned set is backed
     * by this set, so changes in the returned set are reflected in
     * this set, and vice-versa.  The returned set supports all
     * optional set operations that this set supports.
     *
     * <p>The returned set will throw an <tt>IllegalArgumentException</tt>
     * on an attempt to insert an element outside its range.
     *
     * @param fromElement low endpoint (inclusive) of the returned set
     * @param toElement high endpoint (exclusive) of the returned set
     * @return a view of the portion of this set whose elements range from
     *         <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>, exclusive
     * @throws ClassCastException if <tt>fromElement</tt> and
     *         <tt>toElement</tt> cannot be compared to one another using this
     *         set's comparator (or, if the set has no comparator, using
     *         natural ordering).  Implementations may, but are not required
     *         to, throw this exception if <tt>fromElement</tt> or
     *         <tt>toElement</tt> cannot be compared to elements currently in
     *         the set.
     * @throws NullPointerException if <tt>fromElement</tt> or
     *         <tt>toElement</tt> is null and this set does
     *         not permit null elements
     * @throws IllegalArgumentException if <tt>fromElement</tt> is
     *         greater than <tt>toElement</tt>; or if this set itself
     *         has a restricted range, and <tt>fromElement</tt> or
     *         <tt>toElement</tt> lies outside the bounds of the range.
     */
    NavigableSet<E> navigableSubSet(E fromElement, E toElement);

    /**
     * Returns a view of the portion of this set whose elements are
     * strictly less than <tt>toElement</tt>.  The returned set is
     * backed by this set, so changes in the returned set are
     * reflected in this set, and vice-versa.  The returned set
     * supports all optional set operations that this set supports.
     *
     * <p>The returned set will throw an <tt>IllegalArgumentException</tt>
     * on an attempt to insert an element outside its range.
     *
     * @param toElement high endpoint (exclusive) of the returned set
     * @return a view of the portion of this set whose elements are strictly
     *         less than <tt>toElement</tt>
     * @throws ClassCastException if <tt>toElement</tt> is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if <tt>toElement</tt> does not implement {@link Comparable}).
     *         Implementations may, but are not required to, throw this
     *         exception if <tt>toElement</tt> cannot be compared to elements
     *         currently in the set.
     * @throws NullPointerException if <tt>toElement</tt> is null and
     *         this set does not permit null elements
     * @throws IllegalArgumentException if this set itself has a
     *         restricted range, and <tt>toElement</tt> lies outside the
     *         bounds of the range
     */
    NavigableSet<E> navigableHeadSet(E toElement);

    /**
     * Returns a view of the portion of this set whose elements are
     * greater than or equal to <tt>fromElement</tt>.  The returned
     * set is backed by this set, so changes in the returned set are
     * reflected in this set, and vice-versa.  The returned set
     * supports all optional set operations that this set supports.
     *
     * <p>The returned set will throw an <tt>IllegalArgumentException</tt>
     * on an attempt to insert an element outside its range.
     *
     * @param fromElement low endpoint (inclusive) of the returned set
     * @return a view of the portion of this set whose elements are greater
     *         than or equal to <tt>fromElement</tt>
     * @throws ClassCastException if <tt>fromElement</tt> is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if <tt>fromElement</tt> does not implement {@link Comparable}).
     *         Implementations may, but are not required to, throw this
     *         exception if <tt>fromElement</tt> cannot be compared to elements
     *         currently in the set.
     * @throws NullPointerException if <tt>fromElement</tt> is null
     *         and this set does not permit null elements
     * @throws IllegalArgumentException if this set itself has a
     *         restricted range, and <tt>fromElement</tt> lies outside the
     *         bounds of the range
     */
    NavigableSet<E> navigableTailSet(E fromElement);
}
