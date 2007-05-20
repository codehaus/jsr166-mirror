/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.util;

/**
 * This class provides a skeletal implementation of the {@link List}
 * interface to minimize the effort required to implement this interface
 * backed by a "random access" data store (such as an array).  For sequential
 * access data (such as a linked list), {@link AbstractSequentialList} should
 * be used in preference to this class.
 *
 * <p>To implement an unmodifiable list, the programmer needs only to extend
 * this class and provide implementations for the {@link #get(int)} and
 * {@link List#size() size()} methods.
 *
 * <p>To implement a modifiable list, the programmer must additionally
 * override the {@link #set(int, Object) set(int, E)} method (which otherwise
 * throws an {@code UnsupportedOperationException}).  If the list is
 * variable-size the programmer must additionally override the
 * {@link #add(int, Object) add(int, E)} and {@link #remove(int)} methods.
 *
 * <p>The programmer should generally provide a void (no argument) and collection
 * constructor, as per the recommendation in the {@link Collection} interface
 * specification.
 *
 * <p>Unlike the other abstract collection implementations, the programmer does
 * <i>not</i> have to provide an iterator implementation; the iterator and
 * list iterator are implemented by this class, on top of the "random access"
 * methods:
 * {@link #get(int)},
 * {@link #set(int, Object) set(int, E)},
 * {@link #add(int, Object) add(int, E)} and
 * {@link #remove(int)}.
 *
 * <p>The documentation for each non-abstract method in this class describes its
 * implementation in detail.  Each of these methods may be overridden if the
 * collection being implemented admits a more efficient implementation.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @version %I%, %G%
 * @since 1.2
 */

public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {
    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected AbstractList() {
    }

    /**
     * Appends the specified element to the end of this list (optional
     * operation).
     *
     * <p>Lists that support this operation may place limitations on what
     * elements may be added to this list.  In particular, some
     * lists will refuse to add null elements, and others will impose
     * restrictions on the type of elements that may be added.  List
     * classes should clearly specify in their documentation any restrictions
     * on what elements may be added.
     *
     * <p>This implementation calls {@code add(size(), e)}.
     *
     * <p>Note that this implementation throws an
     * {@code UnsupportedOperationException} unless
     * {@link #add(int, Object) add(int, E)} is overridden.
     *
     * @param e element to be appended to this list
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws UnsupportedOperationException if the {@code add} operation
     *         is not supported by this list
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this list
     * @throws NullPointerException if the specified element is null and this
     *         list does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this list
     */
    public boolean add(E e) {
	add(size(), e);
	return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    abstract public E get(int index);

    /**
     * {@inheritDoc}
     *
     * <p>This implementation always throws an
     * {@code UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public E set(int index, E element) {
	throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation always throws an
     * {@code UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public void add(int index, E element) {
	throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation always throws an
     * {@code UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public E remove(int index) {
	throw new UnsupportedOperationException();
    }


    // Search Operations

    /**
     * {@inheritDoc}
     *
     * <p>This implementation first gets a list iterator (with
     * {@code listIterator()}).  Then, it iterates over the list until the
     * specified element is found or the end of the list is reached.
     *
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public int indexOf(Object o) {
	ListIterator<E> e = listIterator();
	if (o==null) {
	    while (e.hasNext())
		if (e.next()==null)
		    return e.previousIndex();
	} else {
	    while (e.hasNext())
		if (o.equals(e.next()))
		    return e.previousIndex();
	}
	return -1;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation first gets a list iterator that points to the end
     * of the list (with {@code listIterator(size())}).  Then, it iterates
     * backwards over the list until the specified element is found, or the
     * beginning of the list is reached.
     *
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public int lastIndexOf(Object o) {
	ListIterator<E> e = listIterator(size());
	if (o==null) {
	    while (e.hasPrevious())
		if (e.previous()==null)
		    return e.nextIndex();
	} else {
	    while (e.hasPrevious())
		if (o.equals(e.previous()))
		    return e.nextIndex();
	}
	return -1;
    }


    // Bulk Operations

    /**
     * Removes all of the elements from this list (optional operation).
     * The list will be empty after this call returns.
     *
     * <p>This implementation calls {@code removeRange(0, size())}.
     *
     * <p>Note that this implementation throws an
     * {@code UnsupportedOperationException} unless {@code remove(int
     * index)} or {@code removeRange(int fromIndex, int toIndex)} is
     * overridden.
     *
     * @throws UnsupportedOperationException if the {@code clear} operation
     *         is not supported by this list
     */
    public void clear() {
        removeRange(0, size());
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation gets an iterator over the specified collection
     * and iterates over it, inserting the elements obtained from the
     * iterator into this list at the appropriate position, one at a time,
     * using {@code add(int, E)}.
     * Many implementations will override this method for efficiency.
     *
     * <p>Note that this implementation throws an
     * {@code UnsupportedOperationException} unless
     * {@link #add(int, Object) add(int, E)} is overridden.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public boolean addAll(int index, Collection<? extends E> c) {
	boolean modified = false;
	Iterator<? extends E> e = c.iterator();
	while (e.hasNext()) {
	    add(index++, e.next());
	    modified = true;
	}
	return modified;
    }


    // Iterators

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * <p>This implementation returns a straightforward implementation of the
     * iterator interface, relying on the backing list's {@code size()},
     * {@code get(int)}, and {@code remove(int)} methods.
     *
     * <p>Note that the iterator returned by this method will throw an
     * {@code UnsupportedOperationException} in response to its
     * {@code remove} method unless the list's {@code remove(int)} method is
     * overridden.
     *
     * <p>This implementation can be made to throw runtime exceptions in the
     * face of concurrent modification, as described in the specification
     * for the (protected) {@code modCount} field.
     *
     * @return an iterator over the elements in this list in proper sequence
     *
     * @see #modCount
     */
    public Iterator<E> iterator() {
	return new Itr();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns {@code listIterator(0)}.
     *
     * @see #listIterator(int)
     */
    public ListIterator<E> listIterator() {
	return listIterator(0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns a straightforward implementation of the
     * {@code ListIterator} interface that extends the implementation of the
     * {@code Iterator} interface returned by the {@code iterator()} method.
     * The {@code ListIterator} implementation relies on the backing list's
     * {@code get(int)}, {@code set(int, E)}, {@code add(int, E)}
     * and {@code remove(int)} methods.
     *
     * <p>Note that the list iterator returned by this implementation will
     * throw an {@code UnsupportedOperationException} in response to its
     * {@code remove}, {@code set} and {@code add} methods unless the
     * list's {@code remove(int)}, {@code set(int, E)}, and
     * {@code add(int, E)} methods are overridden.
     *
     * <p>This implementation can be made to throw runtime exceptions in the
     * face of concurrent modification, as described in the specification for
     * the (protected) {@code modCount} field.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     *
     * @see #modCount
     */
    public ListIterator<E> listIterator(final int index) {
	if (index<0 || index>size())
	  throw new IndexOutOfBoundsException("Index: "+index);

	return new ListItr(index);
    }

    private class Itr implements Iterator<E> {
	/**
	 * Index of element to be returned by subsequent call to next.
	 */
	int cursor = 0;

	/**
	 * Index of element returned by most recent call to next or
	 * previous.  Reset to -1 if this element is deleted by a call
	 * to remove.
	 */
	int lastRet = -1;

	/**
	 * The modCount value that the iterator believes that the backing
	 * List should have.  If this expectation is violated, the iterator
	 * has detected concurrent modification.
	 */
	int expectedModCount = modCount;

	public boolean hasNext() {
            return cursor != size();
	}

	public E next() {
            checkForComodification();
	    try {
		E next = get(cursor);
		lastRet = cursor++;
		return next;
	    } catch (IndexOutOfBoundsException e) {
		checkForComodification();
		throw new NoSuchElementException();
	    }
	}

	public void remove() {
	    if (lastRet == -1)
		throw new IllegalStateException();
            checkForComodification();

	    try {
		AbstractList.this.remove(lastRet);
		if (lastRet < cursor)
		    cursor--;
		lastRet = -1;
		expectedModCount = modCount;
	    } catch (IndexOutOfBoundsException e) {
		throw new ConcurrentModificationException();
	    }
	}

	final void checkForComodification() {
	    if (modCount != expectedModCount)
		throw new ConcurrentModificationException();
	}
    }

    private class ListItr extends Itr implements ListIterator<E> {
	ListItr(int index) {
	    cursor = index;
	}

	public boolean hasPrevious() {
	    return cursor != 0;
	}

        public E previous() {
            checkForComodification();
            try {
                int i = cursor - 1;
                E previous = get(i);
                lastRet = cursor = i;
                return previous;
            } catch (IndexOutOfBoundsException e) {
                checkForComodification();
                throw new NoSuchElementException();
            }
        }

	public int nextIndex() {
	    return cursor;
	}

	public int previousIndex() {
	    return cursor-1;
	}

	public void set(E e) {
	    if (lastRet == -1)
		throw new IllegalStateException();
            checkForComodification();

	    try {
		AbstractList.this.set(lastRet, e);
		expectedModCount = modCount;
	    } catch (IndexOutOfBoundsException ex) {
		throw new ConcurrentModificationException();
	    }
	}

	public void add(E e) {
            checkForComodification();

	    try {
                int i = cursor;
		AbstractList.this.add(i, e);
                cursor = i + 1;
		lastRet = -1;
		expectedModCount = modCount;
	    } catch (IndexOutOfBoundsException ex) {
		throw new ConcurrentModificationException();
	    }
	}
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns a list that subclasses
     * {@code AbstractList}.  The subclass stores, in private fields, the
     * offset of the subList within the backing list, the size of the subList
     * (which can change over its lifetime), and the expected
     * {@code modCount} value of the backing list.  There are two variants
     * of the subclass, one of which implements {@code RandomAccess}.
     * If this list implements {@code RandomAccess} the returned list will
     * be an instance of the subclass that implements {@code RandomAccess}.
     *
     * <p>The subclass's {@code set(int, E)}, {@code get(int)},
     * {@code add(int, E)}, {@code remove(int)}, {@code addAll(int,
     * Collection)} and {@code removeRange(int, int)} methods all
     * delegate to the corresponding methods on the backing abstract list,
     * after bounds-checking the index and adjusting for the offset.  The
     * {@code addAll(Collection c)} method merely returns {@code addAll(size,
     * c)}.
     *
     * <p>The {@code listIterator(int)} method returns a "wrapper object"
     * over a list iterator on the backing list, which is created with the
     * corresponding method on the backing list.  The {@code iterator} method
     * merely returns {@code listIterator()}, and the {@code size} method
     * merely returns the subclass's {@code size} field.
     *
     * <p>All methods first check to see if the actual {@code modCount} of
     * the backing list is equal to its expected value, and throw a
     * {@code ConcurrentModificationException} if it is not.
     *
     * @throws IndexOutOfBoundsException endpoint index value out of range
     *         {@code (fromIndex < 0 || toIndex > size)}
     * @throws IllegalArgumentException if the endpoint indices are out of order
     *         {@code (fromIndex > toIndex)}
     */
    public List<E> subList(int fromIndex, int toIndex) {
        return (this instanceof RandomAccess ?
                new RandomAccessSubList(this, this, fromIndex, fromIndex, toIndex) :
                new SubList(this, this, fromIndex, fromIndex, toIndex));
    }

    // Comparison and hashing

    /**
     * Compares the specified object with this list for equality.  Returns
     * {@code true} if and only if the specified object is also a list, both
     * lists have the same size, and all corresponding pairs of elements in
     * the two lists are <i>equal</i>.  (Two elements {@code e1} and
     * {@code e2} are <i>equal</i> if {@code (e1==null ? e2==null :
     * e1.equals(e2))}.)  In other words, two lists are defined to be
     * equal if they contain the same elements in the same order.
     *
     * <p>This implementation first checks if the specified object is this
     * list. If so, it returns {@code true}; if not, it checks if the
     * specified object is a list. If not, it returns {@code false}; if so,
     * it iterates over both lists, comparing corresponding pairs of elements.
     * If any comparison returns {@code false}, this method returns
     * {@code false}.  If either iterator runs out of elements before the
     * other it returns {@code false} (as the lists are of unequal length);
     * otherwise it returns {@code true} when the iterations complete.
     *
     * @param o the object to be compared for equality with this list
     * @return {@code true} if the specified object is equal to this list
     */
    public boolean equals(Object o) {
	if (o == this)
	    return true;
	if (!(o instanceof List))
	    return false;

	ListIterator<E> e1 = listIterator();
	ListIterator e2 = ((List) o).listIterator();
	while(e1.hasNext() && e2.hasNext()) {
	    E o1 = e1.next();
	    Object o2 = e2.next();
	    if (!(o1==null ? o2==null : o1.equals(o2)))
		return false;
	}
	return !(e1.hasNext() || e2.hasNext());
    }

    /**
     * Returns the hash code value for this list.
     *
     * <p>This implementation uses exactly the code that is used to define the
     * list hash function in the documentation for the {@link List#hashCode}
     * method.
     *
     * @return the hash code value for this list
     */
    public int hashCode() {
	int hashCode = 1;
	Iterator<E> i = iterator();
	while (i.hasNext()) {
	    E obj = i.next();
	    hashCode = 31*hashCode + (obj==null ? 0 : obj.hashCode());
	}
	return hashCode;
    }

    /**
     * Removes from this list all of the elements whose index is between
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
     * Shifts any succeeding elements to the left (reduces their index).
     * This call shortens the ArrayList by {@code (toIndex - fromIndex)}
     * elements.  (If {@code toIndex==fromIndex}, this operation has no
     * effect.)
     *
     * <p>This method is called by the {@code clear} operation on this list
     * and its subLists.  Overriding this method to take advantage of
     * the internals of the list implementation can <i>substantially</i>
     * improve the performance of the {@code clear} operation on this list
     * and its subLists.
     *
     * <p>This implementation gets a list iterator positioned before
     * {@code fromIndex}, and repeatedly calls {@code ListIterator.next}
     * followed by {@code ListIterator.remove} until the entire range has
     * been removed.  <b>Note: if {@code ListIterator.remove} requires linear
     * time, this implementation requires quadratic time.</b>
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     */
    protected void removeRange(int fromIndex, int toIndex) {
        ListIterator<E> it = listIterator(fromIndex);
        for (int i=0, n=toIndex-fromIndex; i<n; i++) {
            it.next();
            it.remove();
        }
    }

    /**
     * The number of times this list has been <i>structurally modified</i>.
     * Structural modifications are those that change the size of the
     * list, or otherwise perturb it in such a fashion that iterations in
     * progress may yield incorrect results.
     *
     * <p>This field is used by the iterator and list iterator implementation
     * returned by the {@code iterator} and {@code listIterator} methods.
     * If the value of this field changes unexpectedly, the iterator (or list
     * iterator) will throw a {@code ConcurrentModificationException} in
     * response to the {@code next}, {@code remove}, {@code previous},
     * {@code set} or {@code add} operations.  This provides
     * <i>fail-fast</i> behavior, rather than non-deterministic behavior in
     * the face of concurrent modification during iteration.
     *
     * <p><b>Use of this field by subclasses is optional.</b> If a subclass
     * wishes to provide fail-fast iterators (and list iterators), then it
     * merely has to increment this field in its {@code add(int, E)} and
     * {@code remove(int)} methods (and any other methods that it overrides
     * that result in structural modifications to the list).  A single call to
     * {@code add(int, E)} or {@code remove(int)} must add no more than
     * one to this field, or the iterators (and list iterators) will throw
     * bogus {@code ConcurrentModificationExceptions}.  If an implementation
     * does not wish to provide fail-fast iterators, this field may be
     * ignored.
     */
    protected transient int modCount = 0;
}

/**
 * Generic sublists. Non-nested to enable construction by other
 * classes in this package.
 */
class SubList<E> extends AbstractList<E> {
    /*
     * A SubList has both a "base", the ultimate backing list, as well
     * as a "parent", which is the list or sublist creating this
     * sublist. All methods that may cause structural modifications
     * must propagate through the parent link, with O(k) performance
     * where k is sublist depth. For example in the case of a
     * sub-sub-list, invoking remove(x) will result in a chain of
     * three remove calls. However, all other non-structurally
     * modifying methods can bypass this chain, and relay directly to
     * the base list. In particular, doing so signficantly speeds up
     * the performance of iterators for deeply-nested sublists.
     */
    final AbstractList<E> base;   // Backing list
    final AbstractList<E> parent; // Parent list
    final int baseOffset;         // index wrt base
    final int parentOffset;       // index wrt parent
    int length;                   // Number of elements in this sublist

    SubList(AbstractList<E> base,
            AbstractList<E> parent,
            int baseIndex,
            int fromIndex,
            int toIndex) {
        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > parent.size())
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                                               ") > toIndex(" + toIndex + ")");
        this.base = base;
        this.parent = parent;
        this.baseOffset = baseIndex;
        this.parentOffset = fromIndex;
        this.length = toIndex - fromIndex;
        this.modCount = base.modCount;
    }

    /**
     * Returns an IndexOutOfBoundsException with nicer message
     */
    private IndexOutOfBoundsException indexError(int index) {
        return new IndexOutOfBoundsException("Index: " + index +
                                             ", Size: " + length);
    }

    public E set(int index, E element) {
        if (index < 0 || index >= length)
            throw indexError(index);
        if (base.modCount != modCount)
            throw new ConcurrentModificationException();
        return base.set(index + baseOffset, element);
    }

    public E get(int index) {
        if (index < 0 || index >= length)
            throw indexError(index);
        if (base.modCount != modCount)
            throw new ConcurrentModificationException();
        return base.get(index + baseOffset);
    }

    public int size() {
        if (base.modCount != modCount)
            throw new ConcurrentModificationException();
        return length;
    }

    public void add(int index, E element) {
        if (index < 0 || index>length)
            throw indexError(index);
        if (base.modCount != modCount)
            throw new ConcurrentModificationException();
        parent.add(index + parentOffset, element);
        length++;
        modCount = base.modCount;
    }

    public E remove(int index) {
        if (index < 0 || index >= length)
            throw indexError(index);
        if (base.modCount != modCount)
            throw new ConcurrentModificationException();
        E result = parent.remove(index + parentOffset);
        length--;
        modCount = base.modCount;
        return result;
    }

    protected void removeRange(int fromIndex, int toIndex) {
        if (base.modCount != modCount)
            throw new ConcurrentModificationException();
        parent.removeRange(fromIndex + parentOffset, toIndex + parentOffset);
        length -= (toIndex-fromIndex);
        modCount = base.modCount;
    }

    public boolean addAll(Collection<? extends E> c) {
        return addAll(length, c);
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        if (index < 0 || index > length)
            throw indexError(index);
        int cSize = c.size();
        if (cSize==0)
            return false;

        if (base.modCount != modCount)
            throw new ConcurrentModificationException();
        parent.addAll(parentOffset + index, c);
        length += cSize;
        modCount = base.modCount;
        return true;
    }

    public List<E> subList(int fromIndex, int toIndex) {
        return new SubList(base, this, fromIndex + baseOffset,
                           fromIndex, toIndex);
    }

    public Iterator<E> iterator() {
        return new SubListIterator(this, 0);
    }

    public ListIterator<E> listIterator() {
        return new SubListIterator(this, 0);
    }

    public ListIterator<E> listIterator(int index) {
        if (index < 0 || index>length)
            throw indexError(index);
        return new SubListIterator(this, index);
    }

    /**
     * Generic sublist iterator obeying fastfail semantics via
     * modCount.  The hasNext and next methods locally check for
     * in-range indices before relaying to backing list to get
     * element. If this either encounters an unexpected modCount or
     * fails, the backing list must have been concurrently modified,
     * and is so reported.  The add and remove methods performing
     * structural modifications instead relay them through the
     * sublist.
     */
    private static final class SubListIterator<E> implements ListIterator<E> {
        final SubList<E> outer;       // Sublist creating this iteraor
        final AbstractList<E> base;   // base list
        final int offset;             // Cursor offset wrt base
        int cursor;                   // Current index
        int fence;                    // Upper bound on cursor
        int lastRet;                  // Index of returned element, or -1
        int expectedModCount;         // Expected modCount of base

        SubListIterator(SubList<E> list, int index) {
            this.lastRet = -1;
            this.cursor = index;
            this.outer = list;
            this.offset = list.baseOffset;
            this.fence = list.length;
            this.base = list.base;
            this.expectedModCount = base.modCount;
        }

        public boolean hasNext() {
            return cursor < fence;
        }

        public boolean hasPrevious() {
            return cursor > 0;
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        public E next() {
            int i = cursor;
            if (cursor >= fence)
                throw new NoSuchElementException();
            if (expectedModCount == base.modCount) {
                try {
                    Object next = base.get(i + offset);
                    lastRet = i;
                    cursor = i + 1;
                    return (E)next;
                } catch (IndexOutOfBoundsException fallThrough) {
                }
            }
            throw new ConcurrentModificationException();
        }

        public E previous() {
            int i = cursor - 1;
            if (i < 0)
                throw new NoSuchElementException();
            if (expectedModCount == base.modCount) {
                try {
                    Object prev = base.get(i + offset);
                    lastRet = i;
                    cursor = i;
                    return (E)prev;
                } catch (IndexOutOfBoundsException fallThrough) {
                }
            }
            throw new ConcurrentModificationException();
        }

        public void set(E e) {
            if (lastRet < 0)
                throw new IllegalStateException();
            if (expectedModCount != base.modCount)
                throw new ConcurrentModificationException();
            try {
                outer.set(lastRet, e);
                expectedModCount = base.modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        public void remove() {
            int i = lastRet;
            if (i < 0)
                throw new IllegalStateException();
            if (expectedModCount != base.modCount)
                throw new ConcurrentModificationException();
            try {
                outer.remove(i);
                if (i < cursor)
                    cursor--;
                lastRet = -1;
                fence = outer.length;
                expectedModCount = base.modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        public void add(E e) {
            if (expectedModCount != base.modCount)
                throw new ConcurrentModificationException();
            try {
                int i = cursor;
                outer.add(i, e);
                cursor = i + 1;
                lastRet = -1;
                fence = outer.length;
                expectedModCount = base.modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
    }

}

class RandomAccessSubList<E> extends SubList<E> implements RandomAccess {
    RandomAccessSubList(AbstractList<E> base,
                        AbstractList<E> parent, int baseIndex,
                        int fromIndex, int toIndex) {
        super(base, parent, baseIndex, fromIndex, toIndex);
    }

    public List<E> subList(int fromIndex, int toIndex) {
        return new RandomAccessSubList(base, this, fromIndex + baseOffset,
                                       fromIndex, toIndex);
    }
}
