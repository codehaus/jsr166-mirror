/*
 * Written by Doug Lea and Josh Bloch with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util;

/**
 * A {@link SortedSet} extended with navigation methods reporting
 * closest matches for given search targets. Methods {@code lower},
 * {@code floor}, {@code ceiling}, and {@code higher} return elements
 * respectively less than, less than or equal, greater than or equal,
 * and greater than a given element, returning {@code null} if there
 * is no such element.  A {@code NavigableSet} may be accessed and
 * traversed in either ascending or descending order.  The {@code
 * descendingSet} method returns a view of the set with the senses of
 * all relational and directional methods inverted. The performance of
 * ascending operations and views is likely to be faster than that of
 * descending ones.  This interface additionally defines methods
 * {@code pollFirst} and {@code pollLast} that return and remove the
 * lowest and highest element, if one exists, else returning {@code
 * null}.  Methods {@code subSet}, {@code headSet},
 * and {@code tailSet} differ from the like-named {@code
 * SortedSet} methods in accepting additional arguments describing
 * whether lower and upper bounds are inclusive versus exclusive.
 * Subsets of any {@code NavigableSet} must implement the {@code
 * NavigableSet} interface.
 *
 * <p> The return values of navigation methods may be ambiguous in
 * implementations that permit {@code null} elements. However, even
 * in this case the result can be disambiguated by checking
 * {@code contains(null)}. To avoid such issues, implementations of
 * this interface are encouraged to <em>not</em> permit insertion of
 * {@code null} elements. (Note that sorted sets of {@link
 * Comparable} elements intrinsically do not permit {@code null}.)
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author Doug Lea
 * @author Josh Bloch
 * @param <E> the type of elements maintained by this set
 * @since 1.6
 */
public interface NavigableSet<E> extends SortedSet<E> {
    /**
     * Returns the greatest element in this set strictly less than the
     * given element, or {@code null} if there is no such element.
     *
     * @param e the value to match
     * @return the greatest element less than {@code e},
     *         or {@code null} if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E lower(E e);

    /**
     * Returns the greatest element in this set less than or equal to
     * the given element, or {@code null} if there is no such element.
     *
     * @param e the value to match
     * @return the greatest element less than or equal to {@code e},
     *         or {@code null} if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E floor(E e);

    /**
     * Returns the least element in this set greater than or equal to
     * the given element, or {@code null} if there is no such element.
     *
     * @param e the value to match
     * @return the least element greater than or equal to {@code e},
     *         or {@code null} if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E ceiling(E e);

    /**
     * Returns the least element in this set strictly greater than the
     * given element, or {@code null} if there is no such element.
     *
     * @param e the value to match
     * @return the least element greater than {@code e},
     *         or {@code null} if there is no such element
     * @throws ClassCastException if the specified element cannot be
     *         compared with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     *         and this set does not permit null elements
     */
    E higher(E e);

    /**
     * Retrieves and removes the first (lowest) element,
     * or returns {@code null} if this set is empty.
     *
     * @return the first element, or {@code null} if this set is empty
     */
    E pollFirst();

    /**
     * Retrieves and removes the last (highest) element,
     * or returns {@code null} if this set is empty.
     *
     * @return the last element, or {@code null} if this set is empty
     */
    E pollLast();

    /**
     * Returns an iterator over the elements in this set, in ascending order.
     *
     * @return an iterator over the elements in this set, in ascending order
     */
    Iterator<E> iterator();

    /**
     * Returns a {@link NavigableSet} view of the elements contained in this
     * set in descending order.  The descending set is backed by this set, so
     * changes to the set are reflected in the descending set, and vice-versa.
     * If either set is modified while an iteration over the other set is in
     * in progress (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.
     *
     * @return a navigable set view of the mappings contained in this set,
     *     sorted in descending order
     */
    NavigableSet<E> descendingSet();

    /**
     * Returns an iterator over the elements in this set, in descending order.
     * Equivalent in effect to <tt>descendingSet().iterator()</tt>.
     *
     * @return an iterator over the elements in this set, in descending order
     */
    Iterator<E> descendingIterator();

    /**
     * Returns a view of the portion of this set whose elements range from
     * {@code fromElement} to {@code toElement}.  If {@code fromElement} and
     * {@code toElement} are equal, the returned set is empty unless {@code
     * fromExclusive} and {@code toExclusive} are both true.  The returned set
     * is backed by this set, so changes in the returned set are reflected in
     * this set, and vice-versa.  The returned set supports all optional set
     * operations that this set supports.
     *
     * <p>The returned set will throw an {@code IllegalArgumentException}
     * on an attempt to insert an element outside its range.
     *
     * @param fromElement low endpoint of the returned set
     * @param fromInclusive true if the low endpoint ({@code fromElement}) is
     *        to be included in the returned view
     * @param toElement high endpoint of the returned set
     * @param toInclusive true if the high endpoint ({@code toElement}) is
     *        to be included in the returned view
     * @return a view of the portion of this set whose elements range from
     *         {@code fromElement}, inclusive, to {@code toElement}, exclusive
     * @throws ClassCastException if {@code fromElement} and
     *         {@code toElement} cannot be compared to one another using this
     *         set's comparator (or, if the set has no comparator, using
     *         natural ordering).  Implementations may, but are not required
     *         to, throw this exception if {@code fromElement} or
     *         {@code toElement} cannot be compared to elements currently in
     *         the set.
     * @throws NullPointerException if {@code fromElement} or
     *         {@code toElement} is null and this set does
     *         not permit null elements
     * @throws IllegalArgumentException if {@code fromElement} is
     *         greater than {@code toElement}; or if this set itself
     *         has a restricted range, and {@code fromElement} or
     *         {@code toElement} lies outside the bounds of the range.
     */
    NavigableSet<E> subSet(E fromElement, boolean fromInclusive,
                           E toElement,   boolean toInclusive);

    /**
     * Returns a view of the portion of this set whose elements are less than
     * (or equal to, if {@code inclusive} is true) {@code toElement}.  The
     * returned set is backed by this set, so changes in the returned set are
     * reflected in this set, and vice-versa.  The returned set supports all
     * optional set operations that this set supports.
     *
     * <p>The returned set will throw an {@code IllegalArgumentException}
     * on an attempt to insert an element outside its range.
     *
     * @param toElement high endpoint of the returned set
     * @param inclusive true if the high endpoint ({@code toElement}) is
     *        to be included in the returned view
     * @return a view of the portion of this set whose elements are less than
     *         (or equal to, if {@code inclusive} is true) {@code toElement}
     * @throws ClassCastException if {@code toElement} is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if {@code toElement} does not implement {@link Comparable}).
     *         Implementations may, but are not required to, throw this
     *         exception if {@code toElement} cannot be compared to elements
     *         currently in the set.
     * @throws NullPointerException if {@code toElement} is null and
     *         this set does not permit null elements
     * @throws IllegalArgumentException if this set itself has a
     *         restricted range, and {@code toElement} lies outside the
     *         bounds of the range
     */
    NavigableSet<E> headSet(E toElement, boolean inclusive);

    /**
     * Returns a view of the portion of this set whose elements are greater
     * than (or equal to, if {@code inclusive} is true) {@code fromElement}.
     * The returned set is backed by this set, so changes in the returned set
     * are reflected in this set, and vice-versa.  The returned set supports
     * all optional set operations that this set supports.
     *
     * <p>The returned set will throw an {@code IllegalArgumentException}
     * on an attempt to insert an element outside its range.
     *
     * @param fromElement low endpoint of the returned set
     * @param inclusive true if the low endpoint ({@code fromElement}) is
     *        to be included in the returned view
     * @return a view of the portion of this set whose elements are greater
     *         than or equal to {@code fromElement}
     * @throws ClassCastException if {@code fromElement} is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if {@code fromElement} does not implement {@link Comparable}).
     *         Implementations may, but are not required to, throw this
     *         exception if {@code fromElement} cannot be compared to elements
     *         currently in the set.
     * @throws NullPointerException if {@code fromElement} is null
     *         and this set does not permit null elements
     * @throws IllegalArgumentException if this set itself has a
     *         restricted range, and {@code fromElement} lies outside the
     *         bounds of the range
     */
    NavigableSet<E> tailSet(E fromElement, boolean inclusive);
}
