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
public class ArrayBlockingQueue<E> extends AbstractBlockingQueueFromQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    public ArrayBlockingQueue(int maximumSize) {
        super(new CircularBuffer<E>(maximumSize), maximumSize);
    }
    
    public ArrayBlockingQueue(int maximumSize, Collection<E> initialElements) {
        super(new CircularBuffer<E>(maximumSize, initialElements), maximumSize);
    }

    /**
     * A classic circular bounded buffer. The bare unsynchronized
     * version here is practially never useful by itself, so is
     * defined as a private class to be wrapped with concurrency
     * control by AbstractBlockingQueueFromQueue.
     */
    static private class CircularBuffer<E> extends AbstractQueue<E>
        implements Queue<E>, java.io.Serializable {
                                               
        private transient final E[] items; 
        private transient int takePtr;           
        private transient int putPtr; 
        private transient int modCount;
        private int count;
        
        /**
         * An array used only during deserialization, to hold
         * items read back in from the stream, and then used
         * as "items" by readResolve via the private constructor.
         */
        private transient E[] deserializedItems;

        /**
         * Internal constructor also used by readResolve. 
         * Sets all final fields, plus count.
         * @param cap the maximumSize 
         * @param array the array to use or null if should create new one
         * @param count the number of items in the array, where indices 0
         * to count-1 hold items.
         */
        private CircularBuffer(int cap, E[] array, int count) {
            if (cap <= 0) 
                throw new IllegalArgumentException();
            if (array == null)
                this.items = new E[cap];
            else
                this.items = array;
            this.putPtr = count;
            this.count = count;
        }

        public CircularBuffer(int maximumSize) {
            this(maximumSize, null, 0);
        }

        public CircularBuffer(int maximumSize, Collection<E> initialElements) {
            this(maximumSize, null, 0);
            int size = initialElements.size();
            if (size > maximumSize) throw new IllegalArgumentException();
            for (Iterator<E> it = initialElements.iterator(); it.hasNext();) {
                items[putPtr] = it.next();
                putPtr = inc(putPtr);
            }
            count = size;
        }

        public int size() {
            return count;
        }
        
        /**
         * Circularly increment i.
         */
        int inc(int i) {
            return (++i == items.length)? 0 : i;
        }
        
        public boolean offer(E x) {
            if (count >= items.length) 
                return false;
            items[putPtr] = x;
            putPtr = inc(putPtr);
            ++modCount;
            ++count;
            return true;
        }

        public E poll() {
            if (count == 0) 
                return null;
            E x = items[takePtr];
            items[takePtr] = null;
            takePtr = inc(takePtr);
            ++modCount;
            --count;
            return x;
        }

        public E peek() {
            return (count == 0)? null : items[takePtr];
        }

        
        /**
         * Utility for remove and iterator.remove: Delete item at position
         * i by sliding over all others up through putPtr.
         */
        void removeAt(int i) {
            for (;;) {
                int nexti = inc(i);
                items[i] = items[nexti];
                if (nexti != putPtr) 
                    i = nexti;
                else {
                    items[nexti] = null;
                    putPtr = i;
                    ++modCount;
                    --count;
                    return;
                }
            }
        }

        public boolean remove(Object x) {
            int i = takePtr;
            while (i != putPtr && !x.equals(items[i])) 
                i = inc(i);
            if (i == putPtr)
                return false;
            
            removeAt(i);
            return true;
        }


        public boolean contains(Object x) {
            for (int i = takePtr; i != putPtr; i = inc(i)) 
                if (x.equals(items[i]))
                    return true;
            
            return false;
        }

        public Object[] toArray() {
            int size = count;
            E[] a = new E[size];
            for (int k = 0, i = takePtr; i != putPtr; i = inc(i))
                a[k++] = items[i];
            
            return a;
        }

        public <T> T[] toArray(T[] a) {
            int size = count;
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
            
            for (int k = 0, i = takePtr; i != putPtr; i = inc(i))
                a[k++] = (T)items[i];
            if (a.length > size)
                a[size] = null;
            
            return a;
        }

        public Iterator<E> iterator() {
            return new Itr();
        }

        private class Itr<E> implements Iterator<E> {
            /**
             * Index of element to be returned by next,
             * or a negative number if no such.
             */
            int cursor;
            
            /**
             * Index of element returned by most recent call to next.
             * Reset to -1 if this element is deleted by a call to remove.
             */
            int lastRet;
            
            /**
             * The modCount value that the iterator believes that the
             * queue should have.  If this expectation is violated, the
             * iterator has detected concurrent modification.
             */
            int expectedModCount;
            
            Itr() {
                expectedModCount = modCount;
                lastRet = -1;
                cursor = (count > 0)? takePtr : -1;
            }

            public boolean hasNext() {
                return cursor >= 0;
            }

            public E next() {
                if (expectedModCount != modCount)
                    throw new ConcurrentModificationException();
                if (cursor < 0)
                    throw new NoSuchElementException();
                lastRet = cursor;
                cursor = inc(cursor);
                if (cursor == putPtr)
                    cursor = -1;
                return (E)items[lastRet];
            }

            public void remove() {
                int i = lastRet;
                if (i == -1)
                    throw new IllegalStateException();
                lastRet = -1;
                if (expectedModCount != modCount || cursor < 0)
                    throw new ConcurrentModificationException();
                
                cursor = i;   // back up cursor
                removeAt(i);
                expectedModCount = modCount;
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
            for (int i = takePtr; i != putPtr; i = inc(i))
                s.writeObject(items[i]);
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
            for (int i=0; i<size; i++)
                deserializedItems[i] = (E)s.readObject();
        }
        
        /**
         * Throw away the object created with readObject, and replace it
         * with a usable ArrayBlockingQueue.
         */
        private Object readResolve() throws java.io.ObjectStreamException {
            E[] array = deserializedItems;
            deserializedItems = null;
            return new CircularBuffer(array.length, array, count);
        }
    }
}
