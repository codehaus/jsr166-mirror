 package java.util;

/**
 * An unbounded priority queue based on a priority heap.  This queue orders
 * elements according to the order specified at creation time.  This order is
 * specified as for {@link TreeSet} and {@link TreeMap}: Elements are ordered
 * either according to their <i>natural order</i> (see {@link Comparable}), or
 * according to a {@link Comparator}, depending on which constructor is used.
 * The {@link #peek}, {@link #poll}, and {@link #remove} methods return the
 * minimal element with respect to the specified ordering.  If multiple
 * these elements are tied for least value, no guarantees are made as to
 * which of elements is returned.
 *
 * <p>Each priority queue has a <i>capacity</i>.  The capacity is the size of
 * the array used to store the elements on the queue.  It is always at least
 * as large as the queue size.  As elements are added to a priority list,
 * its capacity grows automatically.  The details of the growth policy are not
 * specified.
 *
 *<p>Implementation note: this implementation provides O(log(n)) time for
 * the <tt>offer</tt>, <tt>poll</tt>, <tt>remove()</tt> and <tt>add</tt>
 * methods; linear time for the <tt>remove(Object)</tt> and
 * <tt>contains</tt> methods; and constant time for the <tt>peek</tt>,
 * <tt>element</tt>, and <tt>size</tt> methods.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 */
public class PriorityQueue<E> extends AbstractQueue<E>
                              implements Queue<E>
{
    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    /**
     * Priority queue represented as a balanced binary heap: the two children
     * of queue[n] are queue[2*n] and queue[2*n + 1].  The priority queue is
     * ordered by comparator, or by the elements' natural ordering, if
     * comparator is null:  For each node n in the heap, and each descendant
     * of n, d, n <= d.
     *
     * The element with the lowest value is in queue[1] (assuming the queue is
     * nonempty). A one-based array is used in preference to the traditional
     * zero-based array to simplify parent and child calculations.
     *
     * queue.length must be >= 2, even if size == 0.
     */
    private transient E[] queue;

    /**
     * The number of elements in the priority queue.
     */
    private int size = 0;

    /**
     * The comparator, or null if priority queue uses elements'
     * natural ordering.
     */
    private final Comparator<E> comparator;

    /**
     * The number of times this priority queue has been
     * <i>structurally modified</i>.  See AbstractList for gory details.
     */
    private transient int modCount = 0;

    /**
     * Create a new priority queue with the default initial capacity (11)
     * that orders its elements according to their natural ordering.
     */
    public PriorityQueue() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Create a new priority queue with the specified initial capacity
     * that orders its elements according to their natural ordering.
     *
     * @param initialCapacity the initial capacity for this priority queue.
     */
    public PriorityQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    /**
     * Create a new priority queue with the specified initial capacity (11)
     * that orders its elements according to the specified comparator.
     *
     * @param initialCapacity the initial capacity for this priority queue.
     * @param comparator the comparator used to order this priority queue.
     */
    public PriorityQueue(int initialCapacity, Comparator<E> comparator) {
        if (initialCapacity < 1)
            initialCapacity = 1;
        queue = new E[initialCapacity + 1];
        this.comparator = comparator;
    }

    /**
     * Create a new priority queue containing the elements in the specified
     * collection.  The priority queue has an initial capacity of 110% of the
     * size of the specified collection. If the specified collection
     * implements the {@link Sorted} interface, the priority queue will be
     * sorted according to the same comparator, or according to its elements'
     * natural order if the collection is sorted according to its elements'
     * natural order.  If the specified collection does not implement the
     * <tt>Sorted</tt> interface, the priority queue is ordered according to
     * its elements' natural order.
     *
     * @param initialElements the collection whose elements are to be placed
     *        into this priority queue.
     * @throws ClassCastException if elements of the specified collection
     *         cannot be compared to one another according to the priority
     *         queue's ordering.
     * @throws NullPointerException if the specified collection or an
     *         element of the specified collection is <tt>null</tt>.
     */
    public PriorityQueue(Collection<E> initialElements) {
        int sz = initialElements.size();
        int initialCapacity = (int)Math.min((sz * 110L) / 100,
                                            Integer.MAX_VALUE - 1);
        if (initialCapacity < 1)
            initialCapacity = 1;
        queue = new E[initialCapacity + 1];

        /* Commented out to compile with generics compiler

        if (initialElements instanceof Sorted) {
            comparator = ((Sorted)initialElements).comparator();
            for (Iterator<E> i = initialElements.iterator(); i.hasNext(); )
                queue[++size] = i.next();
        } else {
        */
        {
            comparator = null;
            for (Iterator<E> i = initialElements.iterator(); i.hasNext(); )
                add(i.next());
        }
    }

    // Queue Methods

    /**
     * Remove and return the minimal element from this priority queue if
     * it contains one or more elements, otherwise <tt>null</tt>.  The term
     * <i>minimal</i> is defined according to this priority queue's order.
     *
     * @return the minimal element from this priority queue if it contains
     *         one or more elements, otherwise <tt>null</tt>.
     */
    public E poll() {
        if (size == 0)
            return null;
        return remove(1);
    }

    /**
     * Return, but do not remove, the minimal element from the priority queue,
     * or <tt>null</tt> if the queue is empty.  The term <i>minimal</i> is
     * defined according to this priority queue's order.  This method returns
     * the same object reference that would be returned by by the
     * <tt>poll</tt> method.  The two methods differ in that this method 
     * does not remove the element from the priority queue.
     *
     * @return the minimal element from this priority queue if it contains
     *         one or more elements, otherwise <tt>null</tt>.
     */
    public E peek() {
        return queue[1];
    }

    // Collection Methods

    /**
     * Removes a single instance of the specified element from this priority
     * queue, if it is present.  Returns true if this collection contained the
     * specified element (or equivalently, if this collection changed as a
     * result of the call).
     *
     * @param o element to be removed from this collection, if present.
     * @return <tt>true</tt> if this collection changed as a result of the
     *         call
     * @throws ClassCastException if the specified element cannot be compared
     * 		  with elements currently in the priority queue according
     *            to the priority queue's ordering.
     * @throws NullPointerException if the specified element is null.
     */
    public boolean remove(Object element) {
        if (element == null)
            throw new NullPointerException();

        if (comparator == null) {
            for (int i = 1; i <= size; i++) {
                if (((Comparable)queue[i]).compareTo(element) == 0) {
                    remove(i);
                    return true;
                }
            }
        } else {
            for (int i = 1; i <= size; i++) {
                if (comparator.compare(queue[i], (E) element) == 0) {
                    remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns an iterator over the elements in this priority queue.  The
     * first element returned by this iterator is the same element that 
     * would be returned by a call to <tt>peek</tt>.
     * 
     * @return an <tt>Iterator</tt> over the elements in this priority queue.
     */
    public Iterator<E> iterator() {
	return new Itr();
    }

    private class Itr implements Iterator<E> {
	/**
	 * Index (into queue array) of element to be returned by
         * subsequent call to next.
	 */
	int cursor = 1;

	/**
	 * Index of element returned by most recent call to next or
	 * previous.  Reset to 0 if this element is deleted by a call
	 * to remove.
	 */
	int lastRet = 0;

	/**
	 * The modCount value that the iterator believes that the backing
	 * List should have.  If this expectation is violated, the iterator
	 * has detected concurrent modification.
	 */
	int expectedModCount = modCount;

	public boolean hasNext() {
	    return cursor <= size;
	}

	public E next() {
            checkForComodification();
            if (cursor > size)
		throw new NoSuchElementException();
            E result = queue[cursor];
            lastRet = cursor++;
            return result;
	}

	public void remove() {
	    if (lastRet == 0)
		throw new IllegalStateException();
            checkForComodification();

            PriorityQueue.this.remove(lastRet);
            if (lastRet < cursor)
                cursor--;
            lastRet = 0;
            expectedModCount = modCount;
	}

	final void checkForComodification() {
	    if (modCount != expectedModCount)
		throw new ConcurrentModificationException();
	}
    }

    /**
     * Returns the number of elements in this priority queue.
     * 
     * @return the number of elements in this priority queue.
     */
    public int size() {
        return size;
    }

    /**
     * Add the specified element to this priority queue.
     *
     * @param element the element to add.
     * @return true
     * @throws ClassCastException if the specified element cannot be compared
     * 		  with elements currently in the priority queue according
     *            to the priority queue's ordering.
     * @throws NullPointerException if the specified element is null.
     */
    public boolean offer(E element) {
        if (element == null)
            throw new NullPointerException();
        modCount++;

        // Grow backing store if necessary
        if (++size == queue.length) {
            E[] newQueue = new E[2 * queue.length];
            System.arraycopy(queue, 0, newQueue, 0, size);
            queue = newQueue;
        }

        queue[size] = element;
        fixUp(size);
        return true;
    }

    /**
     * Remove all elements from the priority queue.
     */
    public void clear() {
        modCount++;

        // Null out element references to prevent memory leak
        for (int i=1; i<=size; i++)
            queue[i] = null;

        size = 0;
    }

    /**
     * Removes and returns the ith element from queue.  Recall
     * that queue is one-based, so 1 <= i <= size.
     *
     * XXX: Could further special-case i==size, but is it worth it?
     * XXX: Could special-case i==0, but is it worth it?
     */
    private E remove(int i) {
        assert i <= size;
        modCount++;

        E result = queue[i];
        queue[i] = queue[size];
        queue[size--] = null;  // Drop extra ref to prevent memory leak
        if (i <= size)
            fixDown(i);
        return result;
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
                if (((Comparable)queue[j]).compareTo(queue[k]) <= 0)
                    break;
                E tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
                k = j;
            }
        } else {
            while (k > 1) {
                int j = k >> 1;
                if (comparator.compare(queue[j], queue[k]) <= 0)
                    break;
                E tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
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
            while ((j = k << 1) <= size) {
                if (j<size && ((Comparable)queue[j]).compareTo(queue[j+1]) > 0)
                    j++; // j indexes smallest kid
                if (((Comparable)queue[k]).compareTo(queue[j]) <= 0)
                    break;
                E tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
                k = j;
            }
        } else {
            while ((j = k << 1) <= size) {
                if (j < size && comparator.compare(queue[j], queue[j+1]) > 0)
                    j++; // j indexes smallest kid
                if (comparator.compare(queue[k], queue[j]) <= 0)
                    break;
                E tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
                k = j;
            }
        }
    }

    /**
     * Returns the comparator associated with this priority queue, or
     * <tt>null</tt> if it uses its elements' natural ordering.
     *
     * @return the comparator associated with this priority queue, or
     * 	       <tt>null</tt> if it uses its elements' natural ordering.
     */
    Comparator comparator() {
        return comparator;
    }

    /**
     * Save the state of the instance to a stream (that
     * is, serialize it).
     *
     * @serialData The length of the array backing the instance is
     * emitted (int), followed by all of its elements (each an
     * <tt>Object</tt>) in the proper order.
     */
    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException{
	// Write out element count, and any hidden stuff
	s.defaultWriteObject();

        // Write out array length
        s.writeInt(queue.length);

	// Write out all elements in the proper order.
	for (int i=0; i<size; i++)
            s.writeObject(queue[i]);
    }

    /**
     * Reconstitute the <tt>ArrayList</tt> instance from a stream (that is,
     * deserialize it).
     */
    private synchronized void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
	// Read in size, and any hidden stuff
	s.defaultReadObject();

        // Read in array length and allocate array
        int arrayLength = s.readInt();
        queue = new E[arrayLength];

	// Read in all elements in the proper order.
	for (int i=0; i<size; i++)
            queue[i] = (E)s.readObject();
    }

}
