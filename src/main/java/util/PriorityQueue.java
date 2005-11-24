/*
 * @(#)PriorityQueue.java	1.8 05/08/27
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util;
import java.util.*; // for javadoc (till 6280605 is fixed)

/**
 * An unbounded priority {@linkplain Queue queue} based on a priority
 * heap.  The elements of the priority queue are ordered according to
 * their {@linkplain Comparable natural ordering}, or by a {@link
 * Comparator} provided at queue construction time, depending on which
 * constructor is used.  A priority queue does not permit
 * <tt>null</tt> elements.  A priority queue relying on natural
 * ordering also does not permit insertion of non-comparable objects
 * (doing so may result in <tt>ClassCastException</tt>).
 *
 * <p>The <em>head</em> of this queue is the <em>least</em> element
 * with respect to the specified ordering.  If multiple elements are
 * tied for least value, the head is one of those elements -- ties are
 * broken arbitrarily.  The queue retrieval operations <tt>poll</tt>,
 * <tt>remove</tt>, <tt>peek</tt>, and <tt>element</tt> access the
 * element at the head of the queue.
 *
 * <p>A priority queue is unbounded, but has an internal
 * <i>capacity</i> governing the size of an array used to store the
 * elements on the queue.  It is always at least as large as the queue
 * size.  As elements are added to a priority queue, its capacity
 * grows automatically.  The details of the growth policy are not
 * specified.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.  The Iterator provided in method {@link
 * #iterator()} is <em>not</em> guaranteed to traverse the elements of
 * the priority queue in any particular order. If you need ordered
 * traversal, consider using <tt>Arrays.sort(pq.toArray())</tt>.
 *
 * <p> <strong>Note that this implementation is not synchronized.</strong>
 * Multiple threads should not access a <tt>PriorityQueue</tt>
 * instance concurrently if any of the threads modifies the list
 * structurally. Instead, use the thread-safe {@link
 * java.util.concurrent.PriorityBlockingQueue} class.
 *
 * <p>Implementation note: this implementation provides O(log(n)) time
 * for the insertion methods (<tt>offer</tt>, <tt>poll</tt>,
 * <tt>remove()</tt> and <tt>add</tt>) methods; linear time for the
 * <tt>remove(Object)</tt> and <tt>contains(Object)</tt> methods; and
 * constant time for the retrieval methods (<tt>peek</tt>,
 * <tt>element</tt>, and <tt>size</tt>).
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 * @since 1.5
 * @version 1.8, 08/27/05
 * @author Josh Bloch
 * @param <E> the type of elements held in this collection
 */
public class PriorityQueue<E> extends AbstractQueue<E>
    implements java.io.Serializable {

    private static final long serialVersionUID = -7720805057305804111L;

    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    /**
     * Priority queue represented as a balanced binary heap: the two children
     * of queue[n] are queue[2*n] and queue[2*n + 1].  The priority queue is
     * ordered by comparator, or by the elements' natural ordering, if
     * comparator is null:  For each node n in the heap and each descendant d
     * of n, n <= d.
     *
     * The element with the lowest value is in queue[1], assuming the queue is
     * nonempty.  (A one-based array is used in preference to the traditional
     * zero-based array to simplify parent and child calculations.)
     *
     * queue.length must be >= 2, even if size == 0.
     */
    private transient Object[] queue;

    /**
     * The number of elements in the priority queue.
     */
    private int size = 0;

    /**
     * The comparator, or null if priority queue uses elements'
     * natural ordering.
     */
    private final Comparator<? super E> comparator;

    /**
     * The number of times this priority queue has been
     * <i>structurally modified</i>.  See AbstractList for gory details.
     */
    private transient int modCount = 0;

    /**
     * Creates a <tt>PriorityQueue</tt> with the default initial
     * capacity (11) that orders its elements according to their
     * {@linkplain Comparable natural ordering}.
     */
    public PriorityQueue() {
        this(DEFAULT_INITIAL_CAPACITY, null);
    }

    /**
     * Creates a <tt>PriorityQueue</tt> with the specified initial
     * capacity that orders its elements according to their
     * {@linkplain Comparable natural ordering}.
     *
     * @param initialCapacity the initial capacity for this priority queue
     * @throws IllegalArgumentException if <tt>initialCapacity</tt> is less
     * than 1
     */
    public PriorityQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    /**
     * Creates a <tt>PriorityQueue</tt> with the specified initial capacity
     * that orders its elements according to the specified comparator.
     *
     * @param  initialCapacity the initial capacity for this priority queue
     * @param  comparator the comparator that will be used to order
     *         this priority queue.  If <tt>null</tt>, the <i>natural
     *         ordering</i> of the elements will be used.
     * @throws IllegalArgumentException if <tt>initialCapacity</tt> is
     *         less than 1
     */
    public PriorityQueue(int initialCapacity,
                         Comparator<? super E> comparator) {
        if (initialCapacity < 1)
            throw new IllegalArgumentException();
        this.queue = new Object[initialCapacity + 1];
        this.comparator = comparator;
    }

    /**
     * Common code to initialize underlying queue array across
     * constructors below.
     */
    private void initializeArray(Collection<? extends E> c) {
        int sz = c.size();
        int initialCapacity = (int)Math.min((sz * 110L) / 100,
                                            Integer.MAX_VALUE - 1);
        if (initialCapacity < 1)
            initialCapacity = 1;

        this.queue = new Object[initialCapacity + 1];
    }

    /**
     * Initially fill elements of the queue array under the
     * knowledge that it is sorted or is another PQ, in which
     * case we can just place the elements in the order presented.
     */
    private void fillFromSorted(Collection<? extends E> c) {
        for (Iterator<? extends E> i = c.iterator(); i.hasNext(); ) {
            int k = ++size;
            if (k >= queue.length)
                grow(k);
            queue[k] = i.next();
        }
    }

    /**
     * Initially fill elements of the queue array that is not to our knowledge
     * sorted, so we must rearrange the elements to guarantee the heap
     * invariant.
     */
    private void fillFromUnsorted(Collection<? extends E> c) {
        for (Iterator<? extends E> i = c.iterator(); i.hasNext(); ) {
            int k = ++size;
            if (k >= queue.length)
                grow(k);
            queue[k] = i.next();
        }
        heapify();
    }

    /**
     * Creates a <tt>PriorityQueue</tt> containing the elements in the
     * specified collection.  The priority queue has an initial
     * capacity of 110% of the size of the specified collection or 1
     * if the collection is empty.  If the specified collection is an
     * instance of a {@link java.util.SortedSet} or is another
     * <tt>PriorityQueue</tt>, the priority queue will be ordered
     * according to the same ordering.  Otherwise, this priority queue
     * will be ordered according to the natural ordering of its elements.
     *
     * @param  c the collection whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of the specified collection
     *         cannot be compared to one another according to the priority
     *         queue's ordering
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public PriorityQueue(Collection<? extends E> c) {
        initializeArray(c);
        if (c instanceof SortedSet) {
            SortedSet<? extends E> s = (SortedSet<? extends E>)c;
            comparator = (Comparator<? super E>)s.comparator();
            fillFromSorted(s);
        } else if (c instanceof PriorityQueue) {
            PriorityQueue<? extends E> s = (PriorityQueue<? extends E>) c;
            comparator = (Comparator<? super E>)s.comparator();
            fillFromSorted(s);
        } else {
            comparator = null;
            fillFromUnsorted(c);
        }
    }

    /**
     * Creates a <tt>PriorityQueue</tt> containing the elements in the
     * specified priority queue.  The priority queue has an initial
     * capacity of 110% of the size of the specified priority queue or
     * 1 if the priority queue is empty.  This priority queue will be
     * ordered according to the same ordering as the given priority
     * queue.
     *
     * @param  c the priority queue whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of <tt>c</tt> cannot be
     *         compared to one another according to <tt>c</tt>'s
     *         ordering
     * @throws NullPointerException if the specified priority queue or any
     *         of its elements are null
     */
    public PriorityQueue(PriorityQueue<? extends E> c) {
        initializeArray(c);
        comparator = (Comparator<? super E>)c.comparator();
        fillFromSorted(c);
    }

    /**
     * Creates a <tt>PriorityQueue</tt> containing the elements in the
     * specified sorted set.  The priority queue has an initial
     * capacity of 110% of the size of the specified sorted set or 1
     * if the sorted set is empty.  This priority queue will be ordered
     * according to the same ordering as the given sorted set.
     *
     * @param  c the sorted set whose elements are to be placed
     *         into this priority queue.
     * @throws ClassCastException if elements of the specified sorted
     *         set cannot be compared to one another according to the
     *         sorted set's ordering
     * @throws NullPointerException if the specified sorted set or any
     *         of its elements are null
     */
    public PriorityQueue(SortedSet<? extends E> c) {
        initializeArray(c);
        comparator = (Comparator<? super E>)c.comparator();
        fillFromSorted(c);
    }

    /**
     * Resize array, if necessary, to be able to hold given index.
     */
    private void grow(int index) {
        int newlen = queue.length;
        if (index < newlen) // don't need to grow
            return;
        if (index == Integer.MAX_VALUE)
            throw new OutOfMemoryError();
        while (newlen <= index) {
            if (newlen >= Integer.MAX_VALUE / 2)  // avoid overflow
                newlen = Integer.MAX_VALUE;
            else
                newlen <<= 1;
        }
        queue = Arrays.copyOf(queue, newlen);
    }

    /**
     * Inserts the specified element into this priority queue.
     *
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     * @throws ClassCastException if the specified element cannot be
     *         compared with elements currently in this priority queue
     *         according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this priority queue.
     *
     * @return <tt>true</tt> (as specified by {@link Queue#offer})
     * @throws ClassCastException if the specified element cannot be
     *         compared with elements currently in this priority queue
     *         according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        modCount++;
        ++size;

        // Grow backing store if necessary
        if (size >= queue.length)
            grow(size);

        queue[size] = e;
        fixUp(size);
        return true;
    }

    public E peek() {
        if (size == 0)
            return null;
        return (E) queue[1];
    }

    private int indexOf(Object o) {
	if (o == null)
	    return -1;
	for (int i = 1; i <= size; i++)
	    if (o.equals(queue[i]))
		return i;
        return -1;
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element <tt>e</tt> such
     * that <tt>o.equals(e)</tt>, if this queue contains one or more such
     * elements.  Returns true if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return <tt>true</tt> if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
	int i = indexOf(o);
	if (i == -1)
	    return false;
	else {
	    removeAt(i);
	    return true;
	}
    }

    /**
     * Returns <tt>true</tt> if this queue contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this queue contains
     * at least one element <tt>e</tt> such that <tt>o.equals(e)</tt>.
     *
     * @param o object to be checked for containment in this queue
     * @return <tt>true</tt> if this queue contains the specified element
     */
    public boolean contains(Object o) {
	return indexOf(o) != -1;
    }

    /**
     * Returns an array containing all of the elements in this queue,
     * The elements are in no particular order.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this list.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * @return an array containing all of the elements in this queue.
     */
    public Object[] toArray() {
        return Arrays.copyOfRange(queue, 1, size+1);
    }

    /**
     * Returns an array containing all of the elements in this queue.
     * The elements are in no particular order.  The runtime type of
     * the returned array is that of the specified array.  If the queue
     * fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of
     * the specified array and the size of this queue.
     *
     * <p>If the queue fits in the specified array with room to spare
     * (i.e., the array has more elements than the queue), the element in
     * the array immediately following the end of the collection is set to
     * <tt>null</tt>.  (This is useful in determining the length of the
     * queue <i>only</i> if the caller knows that the queue does not contain
     * any null elements.)
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        if (a.length < size)
            // Make a new array of a's runtime type, but my contents:
            return (T[]) Arrays.copyOfRange(queue, 1, size+1, a.getClass());
	System.arraycopy(queue, 1, a, 0, size);
        if (a.length > size)
            a[size] = null;
        return a;
    }

    /**
     * Returns an iterator over the elements in this queue. The iterator
     * does not return the elements in any particular order.
     *
     * @return an iterator over the elements in this queue
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {

        /**
         * Index (into queue array) of element to be returned by
         * subsequent call to next.
         */
        private int cursor = 1;

        /**
         * Index of element returned by most recent call to next,
         * unless that element came from the forgetMeNot list.
         * Reset to 0 if element is deleted by a call to remove.
         */
        private int lastRet = 0;

        /**
         * The modCount value that the iterator believes that the backing
         * List should have.  If this expectation is violated, the iterator
         * has detected concurrent modification.
         */
        private int expectedModCount = modCount;

        /**
         * A list of elements that were moved from the unvisited portion of
         * the heap into the visited portion as a result of "unlucky" element
         * removals during the iteration.  (Unlucky element removals are those
         * that require a fixup instead of a fixdown.)  We must visit all of
         * the elements in this list to complete the iteration.  We do this
         * after we've completed the "normal" iteration.
         *
         * We expect that most iterations, even those involving removals,
         * will not use need to store elements in this field.
         */
        private ArrayList<E> forgetMeNot = null;

        /**
         * Element returned by the most recent call to next iff that
         * element was drawn from the forgetMeNot list.
         */
        private Object lastRetElt = null;

        public boolean hasNext() {
            return cursor <= size || forgetMeNot != null;
        }

        public E next() {
            checkForComodification();
            E result;
            if (cursor <= size) {
                result = (E) queue[cursor];
                lastRet = cursor++;
            }
            else if (forgetMeNot == null)
                throw new NoSuchElementException();
            else {
                int remaining = forgetMeNot.size();
                result = forgetMeNot.remove(remaining - 1);
                if (remaining == 1)
                    forgetMeNot = null;
                lastRet = 0;
                lastRetElt = result;
            }
            return result;
        }

        public void remove() {
            checkForComodification();

            if (lastRet != 0) {
                E moved = PriorityQueue.this.removeAt(lastRet);
                lastRet = 0;
                if (moved == null) {
                    cursor--;
                } else {
                    if (forgetMeNot == null)
                        forgetMeNot = new ArrayList<E>();
                    forgetMeNot.add(moved);
                }
            } else if (lastRetElt != null) {
                PriorityQueue.this.remove(lastRetElt);
                lastRetElt = null;
            } else {
                throw new IllegalStateException();
            }

            expectedModCount = modCount;
        }

        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    public int size() {
        return size;
    }

    /**
     * Removes all of the elements from this priority queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        modCount++;

        // Null out element references to prevent memory leak
        for (int i=1; i<=size; i++)
            queue[i] = null;

        size = 0;
    }

    public E poll() {
        if (size == 0)
            return null;
        modCount++;

        E result = (E) queue[1];
        queue[1] = queue[size];
        queue[size--] = null;  // Drop extra ref to prevent memory leak
        if (size > 1)
            fixDown(1);

        return result;
    }

    /**
     * Removes and returns the ith element from queue.  (Recall that queue
     * is one-based, so 1 <= i <= size.)
     *
     * Normally this method leaves the elements at positions from 1 up to i-1,
     * inclusive, untouched.  Under these circumstances, it returns null.
     * Occasionally, in order to maintain the heap invariant, it must move
     * the last element of the list to some index in the range [2, i-1],
     * and move the element previously at position (i/2) to position i.
     * Under these circumstances, this method returns the element that was
     * previously at the end of the list and is now at some position between
     * 2 and i-1 inclusive.
     */
    private E removeAt(int i) {
        assert i > 0 && i <= size;
        modCount++;

        E moved = (E) queue[size];
        queue[i] = moved;
        queue[size--] = null;  // Drop extra ref to prevent memory leak
        if (i <= size) {
            fixDown(i);
            if (queue[i] == moved) {
                fixUp(i);
                if (queue[i] != moved)
                    return moved;
            }
        }
        return null;
    }

    /**
     * Establishes the heap invariant (described above) assuming the heap
     * satisfies the invariant except possibly for the leaf-node indexed by k
     * (which may have a nextExecutionTime less than its parent's).
     *
     * This method functions by "promoting" queue[k] up the hierarchy
     * (by swapping it with its parent) repeatedly until queue[k]
     * is greater than or equal to its parent.
     */
    private void fixUp(int k) {
        if (comparator == null) {
            while (k > 1) {
                int j = k >> 1;
                if (((Comparable<? super E>)queue[j]).compareTo((E)queue[k]) <= 0)
                    break;
                Object tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
                k = j;
            }
        } else {
            while (k > 1) {
                int j = k >>> 1;
                if (comparator.compare((E)queue[j], (E)queue[k]) <= 0)
                    break;
                Object tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
                k = j;
            }
        }
    }

    /**
     * Establishes the heap invariant (described above) in the subtree
     * rooted at k, which is assumed to satisfy the heap invariant except
     * possibly for node k itself (which may be greater than its children).
     *
     * This method functions by "demoting" queue[k] down the hierarchy
     * (by swapping it with its smaller child) repeatedly until queue[k]
     * is less than or equal to its children.
     */
    private void fixDown(int k) {
        int j;
        if (comparator == null) {
            while ((j = k << 1) <= size && (j > 0)) {
                if (j<size &&
                    ((Comparable<? super E>)queue[j]).compareTo((E)queue[j+1]) > 0)
                    j++; // j indexes smallest kid

                if (((Comparable<? super E>)queue[k]).compareTo((E)queue[j]) <= 0)
                    break;
                Object tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
                k = j;
            }
        } else {
            while ((j = k << 1) <= size && (j > 0)) {
                if (j<size &&
                    comparator.compare((E)queue[j], (E)queue[j+1]) > 0)
                    j++; // j indexes smallest kid
                if (comparator.compare((E)queue[k], (E)queue[j]) <= 0)
                    break;
                Object tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
                k = j;
            }
        }
    }

    /**
     * Establishes the heap invariant (described above) in the entire tree,
     * assuming nothing about the order of the elements prior to the call.
     */
    private void heapify() {
        for (int i = size/2; i >= 1; i--)
            fixDown(i);
    }

    /**
     * Returns the comparator used to order the elements in this
     * queue, or <tt>null</tt> if this queue is sorted according to
     * the {@linkplain Comparable natural ordering} of its elements.
     *
     * @return the comparator used to order this queue, or
     *         <tt>null</tt> if this queue is sorted according to the
     *         natural ordering of its elements.
     */
    public Comparator<? super E> comparator() {
        return comparator;
    }

    /**
     * Save the state of the instance to a stream (that
     * is, serialize it).
     *
     * @serialData The length of the array backing the instance is
     * emitted (int), followed by all of its elements (each an
     * <tt>Object</tt>) in the proper order.
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException{
        // Write out element count, and any hidden stuff
        s.defaultWriteObject();

        // Write out array length
        s.writeInt(queue.length);

        // Write out all elements in the proper order.
        for (int i=1; i<=size; i++)
            s.writeObject(queue[i]);
    }

    /**
     * Reconstitute the <tt>PriorityQueue</tt> instance from a stream
     * (that is, deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in array length and allocate array
        int arrayLength = s.readInt();
        queue = new Object[arrayLength];

        // Read in all elements in the proper order.
        for (int i=1; i<=size; i++)
            queue[i] = (E) s.readObject();
    }

}
