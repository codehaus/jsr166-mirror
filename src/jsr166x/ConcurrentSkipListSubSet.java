/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166x; 

import java.util.*;
import java.util.concurrent.*;

/**
 * Subsets returned by {@link ConcurrentSkipListSet} subset operations
 * represent a subrange of elements of their underlying
 * sets. Instances of this class support all methods of their
 * underlying sets, differing in that elements outside their range are
 * ignored, and attempts to add elements outside their ranges result
 * in {@link IllegalArgumentException}.  Instances of this class are
 * constructed only using the <tt>subSet</tt>, <tt>headSet</tt>, and
 * <tt>tailSet</tt> methods of their underlying sets.
 *
 * @author Doug Lea
 * @param <E> the type of elements maintained by this set
 */
public class ConcurrentSkipListSubSet<E> 
    extends AbstractSet<E> 
    implements SortedSet<E>, Queue<E>, java.io.Serializable {

    private static final long serialVersionUID = -7647078645896651609L;

    /** The underlying submap   */
    private final ConcurrentSkipListSubMap<E,Object> s;
        
    /**
     * Creates a new submap. 
     * @param fromElement inclusive least value, or null if from start
     * @param toElement exclusive upper bound or null if to end
     * @throws IllegalArgumentException if fromElement and toElement
     * nonnull and fromElement greater than toElement
     */
    ConcurrentSkipListSubSet(ConcurrentSkipListMap<E,Object> map, 
                             E fromElement, E toElement) {
        s = new ConcurrentSkipListSubMap<E,Object>(map, fromElement, 
                                                   toElement);
    }

    /**
     * Returns the number of elements in this set.  If this set
     * contains more than <tt>Integer.MAX_VALUE</tt> elements, it
     * returns <tt>Integer.MAX_VALUE</tt>.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these sets, determining the current
     * number of elements requires traversing them all to count them.
     * Additionally, it is possible for the size to change during
     * execution of this method, in which case the returned result
     * will be inaccurate. Thus, this method is typically not very
     * useful in concurrent applications.
     *
     * @return  the number of elements in this set.
     */
    public int size() {
        return s.size();
    }
        
    /**
     * Returns <tt>true</tt> if this set contains no elements.
     * @return <tt>true</tt> if this set contains no elements.
     */
    public boolean isEmpty() {
        return s.isEmpty();
    }
                                           
    /**
     * Returns <tt>true</tt> if this set contains the specified
     * element.
     *
     * @param o the object to be checked for containment in this
     * set.
     * @return <tt>true</tt> if this set contains the specified
     * element.
     *
     * @throws ClassCastException if the specified object cannot
     * be compared with the elements currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>.
     */
    public boolean contains(Object o) {
        return s.containsKey(o);
    }


    /**
     * Adds the specified element to this set if it is not already
     * present.
     *
     * @param o element to be added to this set.
     * @return <tt>true</tt> if the set did not already contain
     * the specified element.
     *
     * @throws ClassCastException if the specified object cannot
     * be compared with the elements currently in the set.
     * @throws IllegalArgumentException if o is outside the range
     * of this subset.
     * @throws NullPointerException if o is <tt>null</tt>.
     */
    public boolean add(E o) {
        return s.put(o, Boolean.TRUE) == null;
    }

    /**
     * Removes the specified element from this set if it is
     * present.
     *
     * @param o object to be removed from this set, if present.
     * @return <tt>true</tt> if the set contained the specified element.
     *
     * @throws ClassCastException if the specified object cannot
     * be compared with the elements currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>.
     */
    public boolean remove(Object o) {
        return s.remove(o) != null;
    }

    /**
     * Removes all of the elements from this set.
     */
    public void clear() {
        s.clear();
    }

    /**
     * Returns an iterator over the elements in this set.  The elements
     * are returned in ascending order.
     *
     * @return an iterator over the elements in this set.
     */
    public Iterator<E> iterator() {
        return s.keySet().iterator();
    }

    /**
     * Returns an element greater than or equal to the given
     * element, or null if there is no such element.
     * 
     * @param o the value to match
     * @return an element greater than or equal to given element, or null
     * if there is no such element.
     * @throws ClassCastException if o cannot be compared with the
     * elements currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>
     */
    public E ceiling(E o) {
        E key = o;
        E least = s.getLeast();
        if (least != null && s.getMap().compare(o, least) < 0)
            key = least;
        E k = s.getMap().ceilingKey(key);
        return (k != null && s.inHalfOpenRange(k))? k : null;
    }

    /**
     * Returns an element strictly less than the given element, or null if
     * there is no such element.
     * 
     * @param o the value to match
     * @return the greatest element less than the given element, or
     * null if there is no such element.
     * @throws ClassCastException if o cannot be compared with the
     * elements currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>.
     */
    public E lower(E o) {
        E k = s.getMap().lowerKey(o);
        return (k != null && s.inHalfOpenRange(k))? k : null;
    }

    /**
     * Returns an element less than or equal to the given element, or null
     * if there is no such element.
     * 
     * @param o the value to match
     * @return the greatest element less than or equal to given
     * element, or null if there is no such element.
     * @throws ClassCastException if o cannot be compared with the
     * elements currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>.
     */
    public E floor(E o) {
        E k = s.getMap().floorKey(o);
        return (k != null && s.inHalfOpenRange(k))? k : null;
    }

    /**
     * Returns an element strictly greater than the given element, or null
     * if there is no such element.
     * 
     * @param o the value to match
     * @return the least element greater than the given element, or
     * null if there is no such element.
     * @throws ClassCastException if o cannot be compared with the
     * elements currently in the set.
     * @throws NullPointerException if o is <tt>null</tt>.
     */
    public E higher(E o) {
        E k;
        E least = s.getLeast();
        if (least != null && s.getMap().compare(o, least) < 0)
            k = s.getMap().ceilingKey(least);
        else
            k = s.getMap().higherKey(o);
        return (k != null && s.inHalfOpenRange(k))? k : null;
    }

    /* ---------------- Queue operations -------------- */

    /**
     * Adds the specified element.
     * @param o the element to insert.
     * @return <tt>true</tt> if it was possible to add the element,
     * else <tt>false</tt>
     */
    public boolean offer(E o) {
        return add(o);
    }

    /**
     * Retrieves and removes the first (lowest) element.
     *
     * @return the least element, or <tt>null</tt> if empty.
     */
    public E poll() {
        for (;;) {
            E k = s.lowestKey();
            if (k == null)
                return null;
            if (remove(k))
                return k;
        }
    }

    /**
     * Retrieves and removes the first (lowest) element.  This method
     * differs from the <tt>poll</tt> method in that it throws an
     * exception if empty.
     *
     * @return the first (lowest) element.
     * @throws NoSuchElementException if empty.
     */
    public E remove() {
        for (;;) {
            E k = s.lowestKey();
            if (k == null)
                throw new NoSuchElementException();
            if (remove(k))
                return k;
        }
    }

    /**
     * Retrieves, but does not remove, the first (lowest) element,
     * returning <tt>null</tt> if empty.
     *
     * @return the first (lowest) element, or <tt>null</tt> if empty.
     */
    public E peek() {
        return s.lowestKey();
    }

    /**
     * Retrieves, but does not remove, the first (lowest) element.  This method
     * differs from the <tt>peek</tt> method only in that it throws an
     * exception if empty.
     *
     * @return the first (lowest) element.
     * @throws NoSuchElementException if empty.
     */
    public E element() {
        return s.firstKey();
    }        

    /**
     * Returns the comparator used to order this set, or <tt>null</tt>
     * if this set uses its elements natural ordering.
     *
     * @return the comparator used to order this set, or <tt>null</tt>
     * if this set uses its elements natural ordering.
     */
    public Comparator<? super E> comparator() {
        return s.comparator();
    }

    /**
     * Returns the first (lowest) element currently in this set.
     *
     * @return the first (lowest) element currently in this set.
     * @throws    NoSuchElementException sorted set is empty.
     */
    public E first() {
        return s.firstKey();
    }

    /**
     * Returns the last (highest) element currently in this set.
     *
     * @return the last (highest) element currently in this set.
     * @throws    NoSuchElementException sorted set is empty.
     */
    public E last() {
        return s.lastKey();
    }

    /**
     * Returns a view of the portion of this set whose elements
     * range from <tt>fromElement</tt>, inclusive, to
     * <tt>toElement</tt>, exclusive.  (If <tt>fromElement</tt>
     * and <tt>toElement</tt> are equal, the returned sorted set
     * is empty.)  The returned sorted set is backed by this set,
     * so changes in the returned sorted set are reflected in this
     * set, and vice-versa.
     * @param fromElement low endpoint (inclusive) of the subSet.
     * @param toElement high endpoint (exclusive) of the subSet.
     * @return a view of the portion of this set whose elements
     * range from <tt>fromElement</tt>, inclusive, to
     * <tt>toElement</tt>, exclusive.
     * @throws ClassCastException if <tt>fromElement</tt> and
     * <tt>toElement</tt> cannot be compared to one another using
     * this set's comparator (or, if the set has no comparator,
     * using natural ordering).
     * @throws IllegalArgumentException if <tt>fromElement</tt> is
     * greater than <tt>toElement</tt> or either key is outside
     * the range of this set.
     * @throws NullPointerException if <tt>fromElement</tt> or
     *	       <tt>toElement</tt> is <tt>null</tt>.
     */
    public ConcurrentSkipListSubSet<E> subSet(E fromElement, 
                                              E toElement) {
        if (!s.inOpenRange(fromElement) || !s.inOpenRange(toElement))
            throw new IllegalArgumentException("element out of range");
        return new ConcurrentSkipListSubSet<E>(s.getMap(), 
                                               fromElement, toElement);
    }

    /**
     * Returns a view of the portion of this set whose elements are
     * strictly less than <tt>toElement</tt>.  The returned sorted set
     * is backed by this set, so changes in the returned sorted set
     * are reflected in this set, and vice-versa.
     * @param toElement high endpoint (exclusive) of the headSet.
     * @return a view of the portion of this set whose elements
     * are strictly less than toElement.
     * @throws ClassCastException if <tt>toElement</tt> is not
     * compatible with this set's comparator (or, if the set has
     * no comparator, if <tt>toElement</tt> does not implement
     * <tt>Comparable</tt>).
     * @throws IllegalArgumentException if <tt>toElement</tt> is 
     * outside the range of this set.
     * @throws NullPointerException if <tt>toElement</tt> is
     * <tt>null</tt>.
     */
    public ConcurrentSkipListSubSet<E> headSet(E toElement) {
        E least = s.getLeast();
        if (!s.inOpenRange(toElement))
            throw new IllegalArgumentException("element out of range");
        return new ConcurrentSkipListSubSet<E>(s.getMap(), 
                                               least, toElement);
    }
        
    /**
     * Returns a view of the portion of this set whose elements
     * are greater than or equal to <tt>fromElement</tt>.  The
     * returned sorted set is backed by this set, so changes in
     * the returned sorted set are reflected in this set, and
     * vice-versa.
     * @param fromElement low endpoint (inclusive) of the tailSet.
     * @return a view of the portion of this set whose elements
     * are greater than or equal to <tt>fromElement</tt>.
     * @throws ClassCastException if <tt>fromElement</tt> is not
     * compatible with this set's comparator (or, if the set has
     * no comparator, if <tt>fromElement</tt> does not implement
     * <tt>Comparable</tt>).
     * @throws IllegalArgumentException if <tt>fromElement</tt> is 
     * outside the range of this set.
     * @throws NullPointerException if <tt>fromElement</tt> is
     * <tt>null</tt>.
     */
    public ConcurrentSkipListSubSet<E> tailSet(E fromElement) {
        E fence = s.getFence();
        if (!s.inOpenRange(fromElement))
            throw new IllegalArgumentException("element out of range");
        return new ConcurrentSkipListSubSet<E>(s.getMap(), 
                                               fromElement, fence);
    }
}
