/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * A bounded {@linkplain BlockingQueue blocking queue} backed by an array.
 * This queue orders elements FIFO (first-in-first-out).
 * The <em>head</em> of the queue is that element that has been on the
 * queue the longest time.
 * The <em>tail</em> of the queue is that element that has been on the
 * queue the shortest time.
 *
 * <p>This is a classic &quot;bounded buffer&quot;, in which a fixed-sized
 * array holds
 * elements inserted by producers and extracted by consumers.  Once
 * created, the capacity can not be increased.  Attempts to offer an
 * element to a full queue will result in the offer operation
 * blocking; attempts to retrieve an element from an empty queue will
 * similarly block.
 *
 * <p> This class supports an optional fairness policy for ordering
 * threads blocked on an insertion or removal.  By default, this
 * ordering is not guaranteed. However, an <tt>ArrayBlockingQueue</tt>
 * constructed with fairness set to <tt>true</tt> grants blocked
 * threads access in FIFO order. Fairness generally substantially
 * decreases throughput but reduces variablility and avoids
 * starvation.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    /** The queued items */
    private transient final E[] items;
    /** items index for next take, poll or remove */
    private transient int takeIndex;
    /** items index for next put, offer, or add. */
    private transient int putIndex;
    /** Number of items in the queue */
    private int count;

    /**
     * An array used only during deserialization, to hold
     * items read back in from the stream, and then used
     * as "items" by readResolve via the private constructor.
     */
    private transient E[] deserializedItems;

    /*
     * Concurrency control via the classic two-condition algorithm
     * found in any textbook.
     */

    /** Main lock guarding all access */
    private final ReentrantLock lock;
    /** Condition for waiting takes */
    private final Condition notEmpty;
    /** Condition for wiating puts */
    private final Condition notFull;

    // Internal helper methods

    /**
     * Circularly increment i.
     */
    int inc(int i) {
        return (++i == items.length)? 0 : i;
    }

    /**
     * Insert element at current put position, advance, and signal.
     * Call only when holding lock.
     */
    private void insert(E x) {
        items[putIndex] = x;
        putIndex = inc(putIndex);
        ++count;
        notEmpty.signal();
    }

    /**
     * Extract element at current take position, advance, and signal.
     * Call only when holding lock.
     */
    private E extract() {
        E x = items[takeIndex];
        items[takeIndex] = null;
        takeIndex = inc(takeIndex);
        --count;
        notFull.signal();
        return x;
    }

    /**
     * Utility for remove and iterator.remove: Delete item at position i.
     * Call only when holding lock.
     */
    void removeAt(int i) {
        // if removing front item, just advance
        if (i == takeIndex) {
            items[takeIndex] = null;
            takeIndex = inc(takeIndex);
        }
        else {
            // slide over all others up through putIndex.
            for (;;) {
                int nexti = inc(i);
                if (nexti != putIndex) {
                    items[i] = items[nexti];
                    i = nexti;
                }
                else {
                    items[i] = null;
                    putIndex = i;
                    break;
                }
            }
        }
        --count;
        notFull.signal();
    }

    /**
     * Internal constructor also used by readResolve.
     * Sets all final fields, plus count.
     * @param cap the capacity
     * @param array the array to use or null if should create new one
     * @param count the number of items in the array, where indices 0
     * to count-1 hold items.
     * @param lk the lock to use with this queue
     */
    private ArrayBlockingQueue(int cap, E[] array, int count,
                               ReentrantLock lk) {
        if (cap <= 0)
            throw new IllegalArgumentException();
        if (array == null)
            this.items = (E[]) new Object[cap];
        else
            this.items = array;
        this.putIndex = count;
        this.count = count;
        lock = lk;
        notEmpty = lock.newCondition();
        notFull =  lock.newCondition();
    }

    /**
     * Creates an <tt>ArrayBlockingQueue</tt> with the given (fixed)
     * capacity and default access policy.
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if <tt>capacity</tt> is less than 1
     */
    public ArrayBlockingQueue(int capacity) {
        this(capacity, null, 0, new ReentrantLock());
    }

    /**
     * Creates an <tt>ArrayBlockingQueue</tt> with the given (fixed)
     * capacity and the specified access policy.
     * @param capacity the capacity of this queue
     * @param fair if <tt>true</tt> then queue accesses for threads blocked
     * on insertion or removal, are processed in FIFO order; if <tt>false</tt>
     * the access order is unspecified.
     * @throws IllegalArgumentException if <tt>capacity</tt> is less than 1
     */
    public ArrayBlockingQueue(int capacity, boolean fair) {
        this(capacity, null, 0, new ReentrantLock(fair));
    }

    /**
     * Creates an <tt>ArrayBlockingQueue</tt> with the given (fixed)
     * capacity, the specified access policy and initially containing the
     * elements of the given collection,
     * added in traversal order of the collection's iterator.
     * @param capacity the capacity of this queue
     * @param fair if <tt>true</tt> then queue accesses for threads blocked
     * on insertion or removal, are processed in FIFO order; if <tt>false</tt>
     * the access order is unspecified.
     * @param c the collection of elements to initially contain
     * @throws IllegalArgumentException if <tt>capacity</tt> is less than
     * <tt>c.size()</tt>, or less than 1.
     * @throws NullPointerException if <tt>c</tt> or any element within it
     * is <tt>null</tt>
     */
    public ArrayBlockingQueue(int capacity, boolean fair,
                              Collection<? extends E> c) {
        this(capacity, null, 0, new ReentrantLock(fair));

        if (capacity < c.size())
            throw new IllegalArgumentException();

        for (Iterator<? extends E> it = c.iterator(); it.hasNext();)
            add(it.next());
    }

    // Have to override just to update the javadoc

    /**
     * Adds the specified element to the tail of this queue.
     * @return <tt>true</tt> (as per the general contract of
     * <tt>Collection.add</tt>).
     * @throws IllegalStateException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean add(E o) {
        return super.add(o);
    }

    /**
     * Adds all of the elements in the specified collection to this queue.
     * The behavior of this operation is undefined if
     * the specified collection is modified while the operation is in
     * progress.  (This implies that the behavior of this call is undefined if
     * the specified collection is this queue, and this queue is nonempty.)
     * <p>
     * This implementation iterates over the specified collection, and adds
     * each object returned by the iterator to this queue's tail, in turn.
     * @throws IllegalStateException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean addAll(Collection<? extends E> c) {
        return super.addAll(c);
    }

   /**
    * Adds the specified element to the tail of this queue if possible,
    * returning immediately if this queue is full.
    *
    * @throws NullPointerException {@inheritDoc}
    */
    public boolean offer(E o) {
        if (o == null) throw new NullPointerException();
        lock.lock();
        try {
            if (count == items.length)
                return false;
            else {
                insert(o);
                return true;
            }
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Adds the specified element to the tail of this queue, waiting if
     * necessary up to the specified wait time for space to become available.
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E o, long timeout, TimeUnit unit)
        throws InterruptedException {

        if (o == null) throw new NullPointerException();

        lock.lockInterruptibly();
        try {
            long nanos = unit.toNanos(timeout);
            for (;;) {
                if (count != items.length) {
                    insert(o);
                    return true;
                }
                if (nanos <= 0)
                    return false;
                try {
                    nanos = notFull.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notFull.signal(); // propagate to non-interrupted thread
                    throw ie;
                }
            }
        }
        finally {
            lock.unlock();
        }
    }


    public E poll() {
        lock.lock();
        try {
            if (count == 0)
                return null;
            E x = extract();
            return x;
        }
        finally {
            lock.unlock();
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            long nanos = unit.toNanos(timeout);
            for (;;) {
                if (count != 0) {
                    E x = extract();
                    return x;
                }
                if (nanos <= 0)
                    return null;
                try {
                    nanos = notEmpty.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notEmpty.signal(); // propagate to non-interrupted thread
                    throw ie;
                }

            }
        }
        finally {
            lock.unlock();
        }
    }


    /**
     * Removes a single instance of the specified element from this
     * queue, if it is present.  More formally,
     * removes an element <tt>e</tt> such that <tt>(o==null ? e==null :
     * o.equals(e))</tt>, if the queue contains one or more such
     * elements.  Returns <tt>true</tt> if the queue contained the
     * specified element (or equivalently, if the queue changed as a
     * result of the call).
     *
     * <p>This implementation iterates over the queue looking for the
     * specified element.  If it finds the element, it removes the element
     * from the queue using the iterator's remove method.<p>
     *
     */
    public boolean remove(Object o) {
        if (o == null) return false;
        lock.lock();
        try {
            int i = takeIndex;
            int k = 0;
            for (;;) {
                if (k++ >= count)
                    return false;
                if (o.equals(items[i])) {
                    removeAt(i);
                    return true;
                }
                i = inc(i);
            }

        }
        finally {
            lock.unlock();
        }
    }

    public E peek() {
        lock.lock();
        try {
            return (count == 0) ? null : items[takeIndex];
        }
        finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            try {
                while (count == 0)
                    notEmpty.await();
            }
            catch (InterruptedException ie) {
                notEmpty.signal(); // propagate to non-interrupted thread
                throw ie;
            }
            E x = extract();
            return x;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Adds the specified element to the tail of this queue, waiting if
     * necessary for space to become available.
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E o) throws InterruptedException {

        if (o == null) throw new NullPointerException();

        lock.lockInterruptibly();
        try {
            try {
                while (count == items.length)
                    notFull.await();
            }
            catch (InterruptedException ie) {
                notFull.signal(); // propagate to non-interrupted thread
                throw ie;
            }
            insert(o);
        }
        finally {
            lock.unlock();
        }
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * Returns the number of elements in this collection.
     */
    public int size() {
        lock.lock();
        try {
            return count;
        }
        finally {
            lock.unlock();
        }
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of elements that this queue can ideally (in
     * the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current <tt>size</tt> of this queue.
     * <p>Note that you <em>cannot</em> always tell if
     * an attempt to <tt>add</tt> an element will succeed by
     * inspecting <tt>remainingCapacity</tt> because it may be the
     * case that a waiting consumer is ready to <tt>take</tt> an
     * element out of an otherwise full queue.
     */
    public int remainingCapacity() {
        lock.lock();
        try {
            return items.length - count;
        }
        finally {
            lock.unlock();
        }
    }


    public boolean contains(Object o) {
        if (o == null) return false;
        lock.lock();
        try {
            int i = takeIndex;
            int k = 0;
            while (k++ < count) {
                if (o.equals(items[i]))
                    return true;
                i = inc(i);
            }
            return false;
        }
        finally {
            lock.unlock();
        }
    }

    public Object[] toArray() {
        lock.lock();
        try {
            E[] a = (E[]) new Object[count];
            int k = 0;
            int i = takeIndex;
            while (k < count) {
                a[k++] = items[i];
                i = inc(i);
            }
            return a;
        }
        finally {
            lock.unlock();
        }
    }

    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            if (a.length < count)
                a = (T[])java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(),
                    count
                    );

            int k = 0;
            int i = takeIndex;
            while (k < count) {
                a[k++] = (T)items[i];
                i = inc(i);
            }
            if (a.length > count)
                a[count] = null;
            return a;
        }
        finally {
            lock.unlock();
        }
    }

    public String toString() {
        lock.lock();
        try {
            return super.toString();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     *
     * @return an iterator over the elements in this queue in proper sequence.
     */
    public Iterator<E> iterator() {
        lock.lock();
        try {
            return new Itr();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Iterator for ArrayBlockingQueue
     */
    private class Itr implements Iterator<E> {
        /**
         * Index of element to be returned by next,
         * or a negative number if no such.
         */
        private int nextIndex;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         **/
        private E nextItem;

        /**
         * Index of element returned by most recent call to next.
         * Reset to -1 if this element is deleted by a call to remove.
         */
        private int lastRet;

        Itr() {
            lastRet = -1;
            if (count == 0)
                nextIndex = -1;
            else {
                nextIndex = takeIndex;
                nextItem = items[takeIndex];
            }
        }

        public boolean hasNext() {
            /*
             * No sync. We can return true by mistake here
             * only if this iterator passed across threads,
             * which we don't support anyway.
             */
            return nextIndex >= 0;
        }

        /**
         * Check whether nextIndex is valid; if so setting nextItem.
         * Stops iterator when either hits putIndex or sees null item.
         */
        private void checkNext() {
            if (nextIndex == putIndex) {
                nextIndex = -1;
                nextItem = null;
            }
            else {
                nextItem = items[nextIndex];
                if (nextItem == null)
                    nextIndex = -1;
            }
        }

        public E next() {
            lock.lock();
            try {
                if (nextIndex < 0)
                    throw new NoSuchElementException();
                lastRet = nextIndex;
                E x = nextItem;
                nextIndex = inc(nextIndex);
                checkNext();
                return x;
            }
            finally {
                lock.unlock();
            }
        }

        public void remove() {
            lock.lock();
            try {
                int i = lastRet;
                if (i == -1)
                    throw new IllegalStateException();
                lastRet = -1;

                int ti = takeIndex;
                removeAt(i);
                // back up cursor (reset to front if was first element)
                nextIndex = (i == ti) ? takeIndex : i;
                checkNext();
            }
            finally {
                lock.unlock();
            }
        }
    }

    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @serialData The maximumSize is emitted (int), followed by all of
     * its elements (each an <tt>E</tt>) in the proper order.
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        // Write out element count, and any hidden stuff
        s.defaultWriteObject();
        // Write out maximumSize == items length
        s.writeInt(items.length);

        // Write out all elements in the proper order.
        int i = takeIndex;
        int k = 0;
        while (k++ < count) {
            s.writeObject(items[i]);
            i = inc(i);
        }
    }

    /**
     * Reconstitute this queue instance from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in size, and any hidden stuff
        s.defaultReadObject();
        int size = count;

        // Read in array length and allocate array
        int arrayLength = s.readInt();

        // We use deserializedItems here because "items" is final
        deserializedItems = (E[]) new Object[arrayLength];

        // Read in all elements in the proper order into deserializedItems
        for (int i = 0; i < size; i++)
            deserializedItems[i] = (E)s.readObject();
    }

    /**
     * Throw away the object created with readObject, and replace it
     * with a usable ArrayBlockingQueue.
     * @return the ArrayBlockingQueue
     */
    private Object readResolve() throws java.io.ObjectStreamException {
        E[] array = deserializedItems;
        deserializedItems = null;
        return new ArrayBlockingQueue<E>(array.length, array, count, lock);
    }
}
