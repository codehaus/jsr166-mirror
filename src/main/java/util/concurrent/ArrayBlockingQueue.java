/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;

/**
 * A bounded blocking queue based on an array.  The implementation is
 * a classic "bounded buffer", in which a fixed-sized array holds
 * elements inserted by propducers and extracted by
 * consumers. Array-based queues typically have more predictable
 * performance than linked queues but lower throughput in most
 * concurrent applications.
 **/
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    private transient final E[] items; 
    private transient int takeIndex;           
    private transient int putIndex; 
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

    private final FairReentrantLock lock = new FairReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull =  lock.newCondition();

    // Internal helper methods

    /**
     * Circularly increment i.
     */
    int inc(int i) {
        return (++i == items.length)? 0 : i;
    }

    /**
     * Insert element at current put position and advance.
     */
    private void insert(E x) {
        items[putIndex] = x;
        putIndex = inc(putIndex);
        ++count;
    }
    
    /**
     * Extract element at current take position and advance.
     */
    private  E extract() {
        E x = items[takeIndex];
        items[takeIndex] = null;
        takeIndex = inc(takeIndex);
        --count;
        return x;
    }

    /**
     * Utility for remove and iterator.remove: Delete item at position
     * i by sliding over all others up through putIndex.
     */
    void removeAt(int i) {
        for (;;) {
            int nexti = inc(i);
            items[i] = items[nexti];
            if (nexti != putIndex) 
                i = nexti;
            else {
                items[nexti] = null;
                putIndex = i;
                --count;
                return;
            }
        }
    }

    /**
     * Internal constructor also used by readResolve. 
     * Sets all final fields, plus count.
     * @param cap the maximumSize 
     * @param array the array to use or null if should create new one
     * @param count the number of items in the array, where indices 0
     * to count-1 hold items.
     */
    private ArrayBlockingQueue(int cap, E[] array, int count) {
        if (cap <= 0) 
            throw new IllegalArgumentException();
        if (array == null)
            this.items = new E[cap];
        else
            this.items = array;
        this.putIndex = count;
        this.count = count;
    }
    
    /**
     * Creates a new ArrayBlockingQueue with the given (fixed) capacity.
     * @param maximumSize the capacity
     */
    public ArrayBlockingQueue(int maximumSize) {
        this(maximumSize, null, 0);
    }

    /**
     * Creates a new ArrayBlockingQueue with the given (fixed)
     * capacity, and initially contianing the given elements, added in
     * iterator traversal order.
     * @param maximumSize the capacity
     * @param initialElements the items to hold initially.
     * @throws IllegalArgumentException if the given capacity is
     * less than the number of initialElements.
     */
    public ArrayBlockingQueue(int maximumSize, Collection<E> initialElements) {
        this(maximumSize, null, 0);
        int n = 0;
        for (Iterator<E> it = initialElements.iterator(); it.hasNext();) {
            if (++n >= items.length)
                throw new IllegalArgumentException();
            items[n] = it.next();
        }
        putIndex = count = n;
    }

    public int size() {
        lock.lock();
        try {
            return count;
        }
        finally {
            lock.unlock();
        }
    }

    public int remainingCapacity() {
        lock.lock();
        try {
            return items.length - count;
        }
        finally {
            lock.unlock();
        }
    }
        

    public void put(E x) throws InterruptedException {
        if (x == null) throw new IllegalArgumentException();
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
            insert(x);
            notEmpty.signal();
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
            notFull.signal();
            return x;
        }
        finally {
            lock.unlock();
        }
    }

    public boolean offer(E x) {
        if (x == null) throw new IllegalArgumentException();
        lock.lock();
        try {
            if (count == items.length) 
                return false;
            else {
                insert(x);
                notEmpty.signal();
                return true;
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
            notFull.signal();
            return x;
        }
        finally {
            lock.unlock(); 
        }
    }

    public boolean offer(E x, long timeout, TimeUnit unit) throws InterruptedException {
        if (x == null) throw new IllegalArgumentException();
        lock.lockInterruptibly();
        long nanos = unit.toNanos(timeout);
        try {
            for (;;) {
                if (count != items.length) {
                    insert(x);
                    notEmpty.signal();
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

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        long nanos = unit.toNanos(timeout);
        try {
            for (;;) {
                if (count != 0) {
                    E x = extract();
                    notFull.signal();
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

        
    public E peek() {
        lock.lock();
        try {
            return (count == 0)? null : items[takeIndex];
        }
        finally {
            lock.unlock();
        }
    }
    
    
    public boolean remove(Object x) {
        lock.lock();
        try {
            int i = takeIndex;
            int k = 0;
            for (;;) {
                if (k++ >= count)
                    return false;
                if (x.equals(items[i])) {
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
    
    
    public boolean contains(Object x) {
        lock.lock();
        try {
            int i = takeIndex;
            int k = 0;
            while (k++ < count) {
                if (x.equals(items[i]))
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
            E[] a = new E[count];
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
                a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), count);
            
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
    
    public Iterator<E> iterator() {
        lock.lock();
        try {
            return new Itr();
        }
        finally {
            lock.unlock();
        }
    }
    
    private class Itr implements Iterator<E> {
        /**
         * Index of element to be returned by next,
         * or a negative number if no such.
         */
        int nextIndex;

        /** 
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         **/
        E nextItem;

        /**
         * Index of element returned by most recent call to next.
         * Reset to -1 if this element is deleted by a call to remove.
         */
        int lastRet;
        
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
         * Check whether nextIndex is valied; if so setting nextItem.
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
                
                nextIndex = i;   // back up cursor
                removeAt(i);
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
     * Reconstitute the Queue instance from a stream (that is,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in size, and any hidden stuff
        s.defaultReadObject();
        int size = count;
        
        // Read in array length and allocate array
        int arrayLength = s.readInt();
        
        // We use deserializedItems here because "items" is final
        deserializedItems = new E[arrayLength];
        
        // Read in all elements in the proper order into deserializedItems
        for (int i = 0; i < size; i++)
            deserializedItems[i] = (E)s.readObject();
    }
    
    /**
     * Throw away the object created with readObject, and replace it
     * with a usable ArrayBlockingQueue.
     */
    private Object readResolve() throws java.io.ObjectStreamException {
        E[] array = deserializedItems;
        deserializedItems = null;
        return new ArrayBlockingQueue(array.length, array, count);
    }
}
