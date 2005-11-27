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
     * Priority queue represented as a balanced binary heap: the two
     * children of queue[n] are queue[2*n+1] and queue[2*(n+1)].  The
     * priority queue is ordered by comparator, or by the elements'
     * natural ordering, if comparator is null: For each node n in the
     * heap and each descendant d of n, n <= d.  The element with the
     * lowest value is in queue[0], assuming the queue is nonempty.
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
        // Note: This restriction of at least one is not actually needed,
        // but continues for 1.5 compatibility
        if (initialCapacity < 1)
            throw new IllegalArgumentException();
        this.queue = new Object[initialCapacity];
        this.comparator = comparator;
    }
    
    /**
     * Creates a <tt>PriorityQueue</tt> containing the elements in the
     * specified collection.   If the specified collection is an
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
        initFromCollection(c);
        if (c instanceof SortedSet) 
            comparator = (Comparator<? super E>)
                ((SortedSet<? extends E>)c).comparator();
        else if (c instanceof PriorityQueue) 
            comparator = (Comparator<? super E>)
                ((PriorityQueue<? extends E>)c).comparator();
        else {
            comparator = null;
            heapify();
        }
    }

    /**
     * Creates a <tt>PriorityQueue</tt> containing the elements in the
     * specified priority queue.  This priority queue will be
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
        comparator = (Comparator<? super E>)c.comparator();
        initFromCollection(c);
    }

    /**
     * Creates a <tt>PriorityQueue</tt> containing the elements in the
     * specified sorted set.  This priority queue will be ordered
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
        comparator = (Comparator<? super E>)c.comparator();
        initFromCollection(c);
    }

    /**
     * Initialize queue array with elements from the given Collection.
     * @param c the collection
     */
    private void initFromCollection(Collection<? extends E> c) {
        Object[] a = c.toArray();
        // If c.toArray incorrectly doesn't return Object[], copy it.
        if (a.getClass() != Object[].class)
            a = Arrays.copyOf(a, a.length, Object[].class);
        queue = a;
        size = a.length;
    }        

    /**
     * Increases the capacity of the array.
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
	int oldCapacity = queue.length;
        // Double size if small; else grow by 50%
        int newCapacity = ((oldCapacity < 64)?
                           ((oldCapacity + 1) * 2):
                           ((oldCapacity * 3) / 2));
        if (newCapacity < minCapacity)
            newCapacity = minCapacity;
        queue = Arrays.copyOf(queue, newCapacity);
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
        int i = size;
        if (i >= queue.length)
            grow(i + 1);
        size = i + 1;
        if (i == 0)
            queue[0] = e;
        else
            siftUp(i, e);
        return true;
    }

    public E peek() {
        if (size == 0)
            return null;
        return (E) queue[0];
    }

    private int indexOf(Object o) {
	if (o != null) {
            for (int i = 0; i < size; i++)
                if (o.equals(queue[i]))
                    return i;
        }
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
     * Version of remove using reference equality, not equals.
     * Needed by iterator.remove
     * 
     * @param o element to be removed from this queue, if present
     * @return <tt>true</tt> if removed.
     */
    boolean removeEq(Object o) {
	for (int i = 0; i < size; i++) {
	    if (o == queue[i]) {
                removeAt(i);
                return true;
            }
        }
        return false;
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
        return Arrays.copyOf(queue, size);
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
            return (T[]) Arrays.copyOf(queue, size, a.getClass());
	System.arraycopy(queue, 0, a, 0, size);
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

    private final class Itr implements Iterator<E> {
        /**
         * Index (into queue array) of element to be returned by
         * subsequent call to next.
         */
        private int cursor = 0;

        /**
         * Index of element returned by most recent call to next,
         * unless that element came from the forgetMeNot list.
         * Set to -1 if element is deleted by a call to remove.
         */
        private int lastRet = -1;

        /**
         * A queue of elements that were moved from the unvisited portion of
         * the heap into the visited portion as a result of "unlucky" element
         * removals during the iteration.  (Unlucky element removals are those
         * that require a siftup instead of a siftdown.)  We must visit all of
         * the elements in this list to complete the iteration.  We do this
         * after we've completed the "normal" iteration.
         *
         * We expect that most iterations, even those involving removals,
         * will not use need to store elements in this field.
         */
        private ArrayDeque<E> forgetMeNot = null;

        /**
         * Element returned by the most recent call to next iff that
         * element was drawn from the forgetMeNot list.
         */
        private E lastRetElt = null;

        /**
         * The modCount value that the iterator believes that the backing
         * List should have.  If this expectation is violated, the iterator
         * has detected concurrent modification.
         */
        private int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor < size || 
                (forgetMeNot != null && !forgetMeNot.isEmpty());
        }

        public E next() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (cursor < size) 
                return (E) queue[lastRet = cursor++];
            if (forgetMeNot != null) {
                lastRet = -1;
                lastRetElt = forgetMeNot.poll();
                if (lastRetElt != null) 
                    return lastRetElt;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (lastRet == -1 && lastRetElt == null)
                throw new IllegalStateException();
            if (lastRet != -1) {
                E moved = PriorityQueue.this.removeAt(lastRet);
                lastRet = -1;
                if (moved == null) 
                    cursor--;
                else {
                    if (forgetMeNot == null)
                        forgetMeNot = new ArrayDeque<E>();
                    forgetMeNot.add(moved);
                }            
            } else {
                PriorityQueue.this.removeEq(lastRetElt);
                lastRetElt = null;
            } 
            expectedModCount = modCount;
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
        for (int i = 0; i < size; i++)
            queue[i] = null;
        size = 0;
    }

    public E poll() {
        if (size == 0)
            return null;
        int s = --size;
        modCount++;
        E result = (E)queue[0];
        E x = (E)queue[s];
        queue[s] = null;
        if (s != 0)
            siftDown(0, x);
        return result;
    }

    /**
     * Removes the ith element from queue.
     *
     * Normally this method leaves the elements at up to i-1,
     * inclusive, untouched.  Under these circumstances, it returns
     * null.  Occasionally, in order to maintain the heap invariant,
     * it must swap a later element of the list with one earlier than
     * i.  Under these circumstances, this method returns the element
     * that was previously at the end of the list and is now at some
     * position before i. This fact is used by iterator.remove so as to
     * avoid missing traverseing elements.
     */
    private E removeAt(int i) {
        assert i >= 0 && i < size;
        modCount++;
        int s = --size;
        if (s == i) // removed last element
            queue[i] = null;
        else {
            E moved = (E) queue[s];
            queue[s] = null;  
            siftDown(i, moved);
            if (queue[i] == moved) {
                siftUp(i, moved);
                if (queue[i] != moved)
                    return moved;
            }
        }
        return null;
    }

    /**
     * Inserts item x at position k, maintaining heap invariant by
     * promoting x up the tree until it is greater than or equal to
     * its parent, or is the root.
     *
     * To simplify and speed up coercions and comparisons. the
     * Comparable and Comparator versions are separated into different
     * methods that are otherwise identical. (Similarly for siftDown.)
     * 
     * @param k the position to fill
     * @param x the item to insert
     */
    private void siftUp(int k, E x) {
        if (comparator != null) 
            siftUpUsingComparator(k, x);
        else
            siftUpComparable(k, x);
    }

    private void siftUpComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>) x;
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = queue[parent];
            if (key.compareTo((E)e) >= 0) 
                break;
            queue[k] = e;
            k = parent;
        }
        queue[k] = key;
    }

    private void siftUpUsingComparator(int k, E x) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = queue[parent];
            if (comparator.compare(x, (E)e) >= 0) 
                break;
            queue[k] = e;
            k = parent;
        }
        queue[k] = x;
    }

    /**
     * Inserts item x at position k, maintaining heap invariant by
     * demoting x down the tree repeatedly until it is less than or
     * equal to its children or is a leaf.
     *
     * @param k the position to fill
     * @param x the item to insert
     */
    private void siftDown(int k, E x) {
        if (comparator != null) 
            siftDownUsingComparator(k, x);
        else
            siftDownComparable(k, x);
    }

    private void siftDownComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>)x;
        int half = size >>> 1;        // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            Object c = queue[child];
            int right = child + 1;
            if (right < size &&
                ((Comparable<? super E>)c).compareTo((E)queue[right]) > 0)
                c = queue[child = right];
            if (key.compareTo((E)c) <= 0)
                break;
            queue[k] = c;
            k = child;
        }
        queue[k] = key;
    }

    private void siftDownUsingComparator(int k, E x) {
        int half = size >>> 1;
        while (k < half) {
            int child = (k << 1) + 1;
            Object c = queue[child];
            int right = child + 1;
            if (right < size &&
                comparator.compare((E)c, (E)queue[right]) > 0)
                c = queue[child = right]; 
            if (comparator.compare(x, (E)c) <= 0) 
                break;
            queue[k] = c;
            k = child;
        }
        queue[k] = x;
    }

    /**
     * Establishes the heap invariant (described above) in the entire tree,
     * assuming nothing about the order of the elements prior to the call.
     */
    private void heapify() {
        for (int i = (size >>> 1) - 1; i >= 0; i--) 
            siftDown(i, (E)queue[i]);
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
        // For compatibility with 1.5 version, must be at least 2.
        s.writeInt(Math.max(2, queue.length));

        // Write out all elements in the proper order.
        for (int i=0; i<size; i++)
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
        for (int i=0; i<size; i++)
            queue[i] = (E) s.readObject();
    }

}
