/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e.extra;
import jsr166e.*;
import java.util.*;

/**
 * A class with the same API and array-based characteristics as {@link
 * java.util.Vector} but with reduced contention and improved
 * throughput when invocations of read-only methods by multiple
 * threads are most common.  Instances of this class may have
 * relatively poorer performance in other contexts.
 *
 * <p> The iterators returned by this class's {@link #iterator()
 * iterator} and {@link #listIterator(int) listIterator} methods are
 * best-effort in the presence of concurrent modifications, and do
 * <em>NOT</em> throw {@link ConcurrentModificationException}.  An
 * iterator's {@code next()} method returns consecutive elements as
 * they appear in the underlying array upon each access.
 *
 * <p>Otherwise, this class supports all methods, under the same
 * documented specifications, as {@code Vector}.  Consult {@link
 * java.util.Vector} for detailed specifications.  Additionally, this
 * class provides methods {@link #addIfAbsent} and {@link
 * #addAllAbsent}.
 *
 * @author Doug Lea
 */
public class ReadMostlyVector<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 8673264195747942595L;

    /*
     * This class exists mainly as a vehicle to exercise various
     * constructions using SequenceLocks, which are not yet explained
     * well here.
     */

    /**
     * The maximum size of array to allocate.
     * See CopyOnWriteArrayList for explanation.
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    // fields are non-private to simpify nested class access
    Object[] array;
    final SequenceLock lock;
    int count;
    final int capacityIncrement;

    /**
     * Creates an empty vector with the given initial capacity and
     * capacity increment.
     *
     * @param initialCapacity the initial capacity of the underlying array
     * @param capacityIncrement if non-zero, the number to
     * add when resizing to accommodate additional elements.
     * If zero, the array size is doubled when resized.
     *
     * @throws IllegalArgumentException if initial capacity is negative
     */
    public ReadMostlyVector(int initialCapacity, int capacityIncrement) {
        super();
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        this.array = new Object[initialCapacity];
        this.capacityIncrement = capacityIncrement;
        this.lock = new SequenceLock();
    }

    /**
     * Creates an empty vector with the given initial capacity.
     *
     * @param initialCapacity the initial capacity of the underlying array
     *
     * @throws IllegalArgumentException if initial capacity is negative
     */
    public ReadMostlyVector(int initialCapacity) {
        this(initialCapacity, 0);
    }

    /**
     * Creates an empty vector with an underlying array of size {@code 10}.
     */
    public ReadMostlyVector() {
        this(10, 0);
    }

    /**
     * Creates a vector containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param c the collection of initially held elements
     * @throws NullPointerException if the specified collection is null
     */
    public ReadMostlyVector(Collection<? extends E> c) {
        Object[] elements = c.toArray();
        // c.toArray might (incorrectly) not return Object[] (see 6260652)
        if (elements.getClass() != Object[].class)
            elements = Arrays.copyOf(elements, elements.length, Object[].class);
        this.array = elements;
        this.count = elements.length;
        this.capacityIncrement = 0;
        this.lock = new SequenceLock();
    }

    // internal constructor for clone
    ReadMostlyVector(Object[] array, int count, int capacityIncrement) {
        this.array = array;
        this.count = count;
        this.capacityIncrement = capacityIncrement;
        this.lock = new SequenceLock();
    }

    // For explanation, see CopyOnWriteArrayList
    final void grow(int minCapacity) {
        int oldCapacity = array.length;
        int newCapacity = oldCapacity + ((capacityIncrement > 0) ?
                                         capacityIncrement : oldCapacity);
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        array = Arrays.copyOf(array, newCapacity);
    }

    static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        return (minCapacity > MAX_ARRAY_SIZE) ?
            Integer.MAX_VALUE :
            MAX_ARRAY_SIZE;
    }

    /*
     * Internal versions of most base functionality, wrapped
     * in different ways from public methods from this class
     * as well as sublist and iterator classes.
     */

    static int internalIndexOf(Object o, Object[] items,
                               int index, int fence) {
        for (int i = index; i < fence; ++i) {
            Object x = items[i];
            if (o == null? x == null : (x != null && o.equals(x)))
                return i;
        }
        return -1;
    }

    static int internalLastIndexOf(Object o, Object[] items,
                                   int index, int origin) {
        for (int i = index; i >= origin; --i) {
            Object x = items[i];
            if (o == null? x == null : (x != null && o.equals(x)))
                return i;
        }
        return -1;
    }

    final void internalAdd(E e) {
        int c = count;
        if (c >= array.length)
            grow(c + 1);
        array[c] = e;
        count = c + 1;
    }

    final void internalAddAt(int index, E e) {
        int c = count;
        if (index > c)
            throw new ArrayIndexOutOfBoundsException(index);
        if (c >= array.length)
            grow(c + 1);
        System.arraycopy(array, index, array, index + 1, c - index);
        array[index] = e;
        count = c + 1;
    }

    final boolean internalAddAllAt(int index, Object[] elements) {
        int c = count;
        if (index < 0 || index > c)
            throw new ArrayIndexOutOfBoundsException(index);
        int len = elements.length;
        if (len == 0)
            return false;
        int newCount = c + len;
        if (newCount >= array.length)
            grow(newCount);
        int mv = count - index;
        if (mv > 0)
            System.arraycopy(array, index, array, index + len, mv);
        System.arraycopy(elements, 0, array, index, len);
        count = newCount;
        return true;
    }

    final boolean internalRemoveAt(int index) {
        int c = count - 1;
        if (index < 0 || index > c)
            return false;
        int mv = c - index;
        if (mv > 0)
            System.arraycopy(array, index + 1, array, index, mv);
        array[c] = null;
        count = c;
        return true;
    }

    /**
     * Internal version of removeAll for lists and sublists. In this
     * and other similar methods below, the span argument is, if
     * non-negative, the purported size of a list/sublist, or is left
     * negative if the size should be determined via count field under
     * lock.
     */
    final boolean internalRemoveAll(Collection<?> c, int origin, int span) {
        SequenceLock lock = this.lock;
        boolean removed = false;
        lock.lock();
        try {
            int fence = count;
            if (span >= 0 && origin + span < fence)
                fence = origin + span;
            if (origin >= 0 && origin < fence) {
                for (Object x : c) {
                    while (internalRemoveAt(internalIndexOf(x, array,
                                                            origin, fence)))
                        removed = true;
                }
            }
        } finally {
            lock.unlock();
        }
        return removed;
    }

    final boolean internalRetainAll(Collection<?> c, int origin, int span) {
        SequenceLock lock = this.lock;
        boolean removed = false;
        if (c != this) {
            lock.lock();
            try {
                int i = origin;
                int fence = count;
                if (span >= 0 && origin + span < fence)
                    fence = origin + span;
                while (i < fence) {
                    if (c.contains(array[i]))
                        ++i;
                    else {
                        --fence;
                        int mv = --count - i;
                        if (mv > 0)
                            System.arraycopy(array, i + 1, array, i, mv);
                        removed = true;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        return removed;
    }

    final void internalClear(int origin, int span) {
        int c = count;
        int fence = c;
        if (span >= 0 && origin + span < fence)
            fence = origin + span;
        if (origin >= 0 && origin < fence) {
            int removed = fence - origin;
            int newCount = c - removed;
            int mv = c - (origin + removed);
            if (mv > 0)
                System.arraycopy(array, origin + removed, array, origin, mv);
            for (int i = c; i < newCount; ++i)
                array[i] = null;
            count = newCount;
        }
    }

    final boolean internalContainsAll(Collection<?> coll, int origin, int span) {
        SequenceLock lock = this.lock;
        boolean contained;
        boolean locked = false;
        try {
            for (;;) {
                long seq = lock.awaitAvailability();
                Object[] items = array;
                int len = items.length;
                int c = count;
                if (c > len)
                    continue;
                int fence = c;
                if (span >= 0 && origin + span < fence)
                    fence = origin + span;
                if (origin < 0 || fence > c)
                    contained = false;
                else {
                    contained = true;
                    for (Object e : coll) {
                        if (internalIndexOf(e, items, origin, fence) < 0) {
                            contained = false;
                            break;
                        }
                    }
                }
                if (lock.getSequence() == seq)
                    break;
                lock.lock();
                locked = true;
            }
        } finally {
            if (locked)
                lock.unlock();
        }
        return contained;
    }

    final boolean internalEquals(List<?> list, int origin, int span) {
        SequenceLock lock = this.lock;
        boolean equal;
        boolean locked = false;
        try {
            for (;;) {
                equal = true;
                long seq = lock.awaitAvailability();
                Object[] items = array;
                int len = items.length;
                int c = count;
                if (c > len)
                    continue;
                int fence = c;
                if (span >= 0 && origin + span < fence)
                    fence = origin + span;
                if (origin < 0 || fence > c)
                    equal = false;
                else {
                    Iterator<?> it = list.iterator();
                    for (int i = origin; i < fence; ++i) {
                        if (!it.hasNext()) {
                            equal = false;
                            break;
                        }
                        Object x = it.next();
                        Object y = items[i];
                        if (x == null? y != null : (y == null || !x.equals(y))) {
                            equal = false;
                            break;
                        }
                    }
                    if (equal && it.hasNext())
                        equal = false;
                }
                if (lock.getSequence() == seq)
                    break;
                lock.lock();
                locked = true;
            }
        } finally {
            if (locked)
                lock.unlock();
        }
        return equal;
    }

    final int internalHashCode(int origin, int span) {
        SequenceLock lock = this.lock;
        int hash;
        boolean locked = false;
        try {
            for (;;) {
                hash = 1;
                long seq = lock.awaitAvailability();
                Object[] items = array;
                int len = items.length;
                int c = count;
                if (c > len)
                    continue;
                int fence = c;
                if (span >= 0 && origin + span < fence)
                    fence = origin + span;
                if (origin >= 0 && fence <= c) {
                    for (int i = origin; i < fence; ++i) {
                        Object e = items[i];
                        hash = 31*hash + (e == null ? 0 : e.hashCode());
                    }
                }
                if (lock.getSequence() == seq)
                    break;
                lock.lock();
                locked = true;
            }
        } finally {
            if (locked)
                lock.unlock();
        }
        return hash;
    }

    final String internalToString(int origin, int span) {
        SequenceLock lock = this.lock;
        String ret;
        boolean locked = false;
        try {
            for (;;) {
                long seq = lock.awaitAvailability();
                Object[] items = array;
                int len = items.length;
                int c = count;
                if (c > len)
                    continue;
                int fence = c;
                if (span >= 0 && origin + span < fence)
                    fence = origin + span;
                if (origin >= 0 && fence <= c) {
                    if (origin == fence)
                        ret = "[]";
                    else {
                        StringBuilder sb = new StringBuilder();
                        sb.append('[');
                        for (int i = origin;;) {
                            Object e = items[i];
                            sb.append(e == this ? "(this Collection)" : e);
                            if (++i < fence)
                                sb.append(',').append(' ');
                            else {
                                ret = sb.append(']').toString();
                                break;
                            }
                        }
                    }
                    if (lock.getSequence() == seq)
                        break;
                }
                lock.lock();
                locked = true;
            }
        } finally {
            if (locked)
                lock.unlock();
        }
        return ret;
    }

    final Object[] internalToArray(int origin, int span) {
        Object[] result;
        SequenceLock lock = this.lock;
        boolean locked = false;
        try {
            for (;;) {
                result = null;
                long seq = lock.awaitAvailability();
                Object[] items = array;
                int len = items.length;
                int c = count;
                int fence = c;
                if (span >= 0 && origin + span < fence)
                    fence = origin + span;
                if (c <= len && fence <= len) {
                    result = Arrays.copyOfRange(items, origin, fence,
                                                Object[].class);
                    if (lock.getSequence() == seq)
                        break;
                }
                lock.lock();
                locked = true;
            }
        } finally {
            if (locked)
                lock.unlock();
        }
        return result;
    }

    final <T> T[] internalToArray(T[] a, int origin, int span) {
        T[] result;
        SequenceLock lock = this.lock;
        boolean locked = false;
        try {
            for (;;) {
                long seq = lock.awaitAvailability();
                Object[] items = array;
                int len = items.length;
                int c = count;
                int fence = c;
                if (span >= 0 && origin + span < fence)
                    fence = origin + span;
                if (c <= len && fence <= len) {
                    if (a.length < count)
                        result = (T[]) Arrays.copyOfRange(array, origin,
                                                          fence, a.getClass());
                    else {
                        int n = fence - origin;
                        System.arraycopy(array, 0, a, origin, fence - origin);
                        if (a.length > n)
                            a[n] = null;
                        result = a;
                    }
                    if (lock.getSequence() == seq)
                        break;
                }
                lock.lock();
                locked = true;
            }
        } finally {
            if (locked)
                lock.unlock();
        }
        return result;
    }

    // public List methods

    public boolean add(E e) {
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            internalAdd(e);
        } finally {
            lock.unlock();
        }
        return true;
    }

    public void add(int index, E element) {
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            internalAddAt(index, element);
        } finally {
            lock.unlock();
        }
    }

    public boolean addAll(Collection<? extends E> c) {
        Object[] elements = c.toArray();
        int len = elements.length;
        if (len == 0)
            return false;
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            int newCount = count + len;
            if (newCount >= array.length)
                grow(newCount);
            System.arraycopy(elements, 0, array, count, len);
            count = newCount;
        } finally {
            lock.unlock();
        }
        return true;
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        SequenceLock lock = this.lock;
        boolean ret;
        Object[] elements = c.toArray();
        lock.lock();
        try {
            ret = internalAddAllAt(index, elements);
        } finally {
            lock.unlock();
        }
        return ret;
    }

    public void clear() {
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            for (int i = 0; i < count; i++)
                array[i] = null;
            count = 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean contains(Object o) {
        return indexOf(o, 0) >= 0;
    }

    public boolean containsAll(Collection<?> c) {
        return internalContainsAll(c, 0, -1);
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;
        return internalEquals((List<?>)(o), 0, -1);
    }

    public E get(int index) {
        SequenceLock lock = this.lock;
        for (;;) {
            long seq = lock.awaitAvailability();
            Object[] items = array;
            int len = items.length;
            int c = count;
            if (c > len)
                continue;
            E e; boolean ex;
            if (index < 0 || index >= c) {
                e = null;
                ex = true;
            }
            else {
                e = (E)items[index];
                ex = false;
            }
            if (lock.getSequence() == seq) {
                if (ex)
                    throw new ArrayIndexOutOfBoundsException(index);
                else
                    return e;
            }
        }
    }

    public int hashCode() {
        return internalHashCode(0, -1);
    }

    public int indexOf(Object o) {
        SequenceLock lock = this.lock;
        long seq = lock.awaitAvailability();
        Object[] items = array;
        int c = count;
        if (c <= items.length) {
            int idx = internalIndexOf(o, items, 0, c);
            if (lock.getSequence() == seq)
                return idx;
        }
        lock.lock();
        try {
            return internalIndexOf(o, array, 0, count);
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        long ignore = lock.getSequence();
        return count == 0;
    }

    public Iterator<E> iterator() {
        return new Itr(this, 0);
    }

    public int lastIndexOf(Object o) {
        SequenceLock lock = this.lock;
        long seq = lock.awaitAvailability();
        Object[] items = array;
        int c = count;
        if (c <= items.length) {
            int idx = internalLastIndexOf(o, items, c - 1, 0);
            if (lock.getSequence() == seq)
                return idx;
        }
        lock.lock();
        try {
            return internalLastIndexOf(o, array, count-1, 0);
        } finally {
            lock.unlock();
        }
    }

    public ListIterator<E> listIterator() {
        return new Itr(this, 0);
    }

    public ListIterator<E> listIterator(int index) {
        return new Itr(this, index);
    }

    public E remove(int index) {
        SequenceLock lock = this.lock;
        E oldValue;
        lock.lock();
        try {
            if (index < 0 || index >= count)
                throw new ArrayIndexOutOfBoundsException(index);
            oldValue = (E)array[index];
            internalRemoveAt(index);
        } finally {
            lock.unlock();
        }
        return oldValue;
    }

    public boolean remove(Object o) {
        SequenceLock lock = this.lock;
        boolean removed;
        lock.lock();
        try {
            removed = internalRemoveAt(internalIndexOf(o, array, 0, count));
        } finally {
            lock.unlock();
        }
        return removed;
    }

    public boolean removeAll(Collection<?> c) {
        return internalRemoveAll(c, 0, -1);
    }

    public boolean retainAll(Collection<?> c) {
        return internalRetainAll(c, 0, -1);
    }

    public E set(int index, E element) {
        E oldValue;
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            if (index < 0 || index >= count)
                throw new ArrayIndexOutOfBoundsException(index);
            oldValue = (E)array[index];
            array[index] = element;
        } finally {
            lock.unlock();
        }
        return oldValue;
    }

    public int size() {
        long ignore = lock.getSequence();
        return count;
    }

    public List<E> subList(int fromIndex, int toIndex) {
        int c = size();
        int ssize = toIndex - fromIndex;
        if (fromIndex < 0 || toIndex > c || ssize < 0)
            throw new IndexOutOfBoundsException();
        return new ReadMostlyVectorSublist(this, fromIndex, ssize);
    }

    public Object[] toArray() {
        return internalToArray(0, -1);
    }

    public <T> T[] toArray(T[] a) {
        return internalToArray(a, 0, -1);
    }

    public String toString() {
        return internalToString(0, -1);
    }

    // ReadMostlyVector-only methods

    /**
     * Append the element if not present.
     *
     * @param e element to be added to this list, if absent
     * @return <tt>true</tt> if the element was added
     */
    public boolean addIfAbsent(E e) {
        boolean added;
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            if (internalIndexOf(e, array, 0, count) < 0) {
                internalAdd(e);
                added = true;
            }
            else
                added = false;
        } finally {
            lock.unlock();
        }
        return added;
    }

    /**
     * Appends all of the elements in the specified collection that
     * are not already contained in this list, to the end of
     * this list, in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param c collection containing elements to be added to this list
     * @return the number of elements added
     * @throws NullPointerException if the specified collection is null
     * @see #addIfAbsent(Object)
     */
    public int addAllAbsent(Collection<? extends E> c) {
        int added = 0;
        Object[] cs = c.toArray();
        int clen = cs.length;
        if (clen != 0) {
            lock.lock();
            try {
                for (int i = 0; i < clen; ++i) {
                    Object e = cs[i];
                    if (internalIndexOf(e, array, 0, count) < 0) {
                        internalAdd((E)e);
                        ++added;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        return added;
    }

    // Vector-only methods

    /** See {@link Vector#firstElement} */
    public E firstElement() {
        SequenceLock lock = this.lock;
        for (;;) {
            long seq = lock.awaitAvailability();
            Object[] items = array;
            int len = items.length;
            int c = count;
            if (c > len || c < 0)
                continue;
            E e; boolean ex;
            if (c == 0) {
                e = null;
                ex = true;
            }
            else {
                e = (E)items[0];
                ex = false;
            }
            if (lock.getSequence() == seq) {
                if (ex)
                    throw new NoSuchElementException();
                else
                    return e;
            }
        }
    }

    /** See {@link Vector#lastElement} */
    public E lastElement() {
        SequenceLock lock = this.lock;
        for (;;) {
            long seq = lock.awaitAvailability();
            Object[] items = array;
            int len = items.length;
            int c = count;
            if (c > len || c < 0)
                continue;
            E e; boolean ex;
            if (c == 0) {
                e = null;
                ex = true;
            }
            else {
                e = (E)items[c - 1];
                ex = false;
            }
            if (lock.getSequence() == seq) {
                if (ex)
                    throw new NoSuchElementException();
                else
                    return e;
            }
        }
    }

    /** See {@link Vector#indexOf(Object, int)} */
    public int indexOf(Object o, int index) {
        SequenceLock lock = this.lock;
        int idx = 0;
        boolean ex = false;
        long seq = lock.awaitAvailability();
        Object[] items = array;
        int c = count;
        boolean retry = false;
        if (c > items.length)
            retry = true;
        else if (index < 0)
            ex = true;
        else
            idx = internalIndexOf(o, items, index, c);
        if (retry || lock.getSequence() != seq) {
            lock.lock();
            try {
                if (index < 0)
                    ex = true;
                else
                    idx = internalIndexOf(o, array, 0, count);
            } finally {
                lock.unlock();
            }
        }
        if (ex)
            throw new ArrayIndexOutOfBoundsException(index);
        return idx;
    }

    /** See {@link Vector#lastIndexOf(Object, int)} */
    public int lastIndexOf(Object o, int index) {
        SequenceLock lock = this.lock;
        int idx = 0;
        boolean ex = false;
        long seq = lock.awaitAvailability();
        Object[] items = array;
        int c = count;
        boolean retry = false;
        if (c > items.length)
            retry = true;
        else if (index >= c)
            ex = true;
        else
            idx = internalLastIndexOf(o, items, index, 0);
        if (retry || lock.getSequence() != seq) {
            lock.lock();
            try {
                if (index >= count)
                    ex = true;
                else
                    idx = internalLastIndexOf(o, array, index, 0);
            } finally {
                lock.unlock();
            }
        }
        if (ex)
            throw new ArrayIndexOutOfBoundsException(index);
        return idx;
    }

    /** See {@link Vector#setSize} */
    public void setSize(int newSize) {
        if (newSize < 0)
            throw new ArrayIndexOutOfBoundsException(newSize);
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            int c = count;
            if (newSize > c)
                grow(newSize);
            else {
                for (int i = newSize ; i < c ; i++)
                    array[i] = null;
            }
            count = newSize;
        } finally {
            lock.unlock();
        }
    }

    /** See {@link Vector#copyInto} */
    public void copyInto(Object[] anArray) {
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            System.arraycopy(array, 0, anArray, 0, count);
        } finally {
            lock.unlock();
        }
    }

    /** See {@link Vector#trimToSize} */
    public void trimToSize() {
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            if (count < array.length)
                array = Arrays.copyOf(array, count);
        } finally {
            lock.unlock();
        }
    }

    /** See {@link Vector#ensureCapacity} */
    public void ensureCapacity(int minCapacity) {
        if (minCapacity > 0) {
            SequenceLock lock = this.lock;
            lock.lock();
            try {
                if (minCapacity - array.length > 0)
                    grow(minCapacity);
            } finally {
                lock.unlock();
            }
        }
    }

    /** See {@link Vector#elements} */
    public Enumeration<E> elements() {
        return new Itr(this, 0);
    }

    /** See {@link Vector#capacity} */
    public int capacity() {
        long ignore = lock.getSequence();
        return array.length;
    }

    /** See {@link Vector#elementAt} */
    public E elementAt(int index) {
        return get(index);
    }

    /** See {@link Vector#setElementAt} */
    public void setElementAt(E obj, int index) {
        set(index, obj);
    }

    /** See {@link Vector#removeElementAt} */
    public void removeElementAt(int index) {
        remove(index);
    }

    /** See {@link Vector#insertElementAt} */
    public void insertElementAt(E obj, int index) {
        add(index, obj);
    }

    /** See {@link Vector#addElement} */
    public void addElement(E obj) {
        add(obj);
    }

    /** See {@link Vector#removeElement} */
    public boolean removeElement(Object obj) {
        return remove(obj);
    }

    /** See {@link Vector#removeAllElements} */
    public void removeAllElements() {
        clear();
    }

    // other methods

    public Object clone() {
        SequenceLock lock = this.lock;
        Object[] a = null;
        int c;
        boolean retry = false;
        long seq = lock.awaitAvailability();
        Object[] items = array;
        c = count;
        if (c <= items.length)
            a = Arrays.copyOf(items, c);
        else
            retry = true;
        if (retry || lock.getSequence() != seq) {
            lock.lock();
            try {
                c = count;
                a = Arrays.copyOf(array, c);
            } finally {
                lock.unlock();
            }
        }
        return new ReadMostlyVector(a, c, capacityIncrement);
    }

    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        SequenceLock lock = this.lock;
        lock.lock();
        try {
            s.defaultWriteObject();
        } finally {
            lock.unlock();
        }
    }

    static final class Itr<E> implements ListIterator<E>, Enumeration<E>  {
        final ReadMostlyVector<E> list;
        final SequenceLock lock;
        Object[] items;
        Object next, prev;
        long seq;
        int cursor;
        int fence;
        int lastRet;
        boolean haveNext, havePrev;

        Itr(ReadMostlyVector<E> list, int index) {
            this.list = list;
            this.lock = list.lock;
            this.cursor = index;
            this.lastRet = -1;
            refresh();
            if (index < 0 || index > fence)
                throw new ArrayIndexOutOfBoundsException(index);
        }

        private void refresh() {
            do {
                seq = lock.awaitAvailability();
                items = list.array;
                fence = list.count;
            } while (lock.getSequence() != seq);
        }

        public boolean hasNext() {
            int i = cursor;
            while (i < fence && i >= 0) {
                if (lock.getSequence() == seq) {
                    next = items[i];
                    return haveNext = true;
                }
                refresh();
            }
            return false;
        }

        public boolean hasPrevious() {
            int i = cursor;
            while (i <= fence && i > 0) {
                if (lock.getSequence() == seq) {
                    prev = items[i - 1];
                    return havePrev = true;
                }
                refresh();
            }
            return false;
        }

        public E next() {
            if (!haveNext && !hasNext())
                throw new NoSuchElementException();
            haveNext = false;
            lastRet = cursor++;
            return (E) next;
        }

        public E previous() {
            if (!havePrev && !hasPrevious())
                throw new NoSuchElementException();
            havePrev = false;
            lastRet = cursor--;
            return (E) prev;
        }

        public void remove() {
            int i = lastRet;
            if (i < 0)
                throw new IllegalStateException();
            lock.lock();
            try {
                if (i < list.count)
                    list.remove(i);
            } finally {
                lock.unlock();
            }
            cursor = i;
            lastRet = -1;
            refresh();
        }

        public void set(E e) {
            int i = lastRet;
            if (i < 0)
                throw new IllegalStateException();
            lock.lock();
            try {
                if (i < list.count)
                    list.set(i, e);
            } finally {
                lock.unlock();
            }
            refresh();
        }

        public void add(E e) {
            int i = cursor;
            if (i < 0)
                throw new IllegalStateException();
            lock.lock();
            try {
                if (i <= list.count)
                    list.add(i, e);
            } finally {
                lock.unlock();
            }
            cursor = i + 1;
            lastRet = -1;
            refresh();
        }

        public boolean hasMoreElements() { return hasNext(); }
        public E nextElement() { return next(); }
        public int nextIndex() { return cursor; }
        public int previousIndex() { return cursor - 1; }
    }

    static final class ReadMostlyVectorSublist<E> implements List<E>, RandomAccess, java.io.Serializable {
        final ReadMostlyVector<E> list;
        final int offset;
        volatile int size;

        ReadMostlyVectorSublist(ReadMostlyVector<E> list, int offset, int size) {
            this.list = list;
            this.offset = offset;
            this.size = size;
        }

        private void rangeCheck(int index) {
            if (index < 0 || index >= size)
                throw new ArrayIndexOutOfBoundsException(index);
        }

        public boolean add(E element) {
            SequenceLock lock = list.lock;
            lock.lock();
            try {
                int c = size;
                list.internalAddAt(c + offset, element);
                size = c + 1;
            } finally {
                lock.unlock();
            }
            return true;
        }

        public void add(int index, E element) {
            SequenceLock lock = list.lock;
            lock.lock();
            try {
                if (index < 0 || index > size)
                    throw new ArrayIndexOutOfBoundsException(index);
                list.internalAddAt(index + offset, element);
                ++size;
            } finally {
                lock.unlock();
            }
        }

        public boolean addAll(Collection<? extends E> c) {
            Object[] elements = c.toArray();
            int added;
            SequenceLock lock = list.lock;
            lock.lock();
            try {
                int s = size;
                int pc = list.count;
                list.internalAddAllAt(offset + s, elements);
                added = list.count - pc;
                size = s + added;
            } finally {
                lock.unlock();
            }
            return added != 0;
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            Object[] elements = c.toArray();
            int added;
            SequenceLock lock = list.lock;
            lock.lock();
            try {
                int s = size;
                if (index < 0 || index > s)
                    throw new ArrayIndexOutOfBoundsException(index);
                int pc = list.count;
                list.internalAddAllAt(index + offset, elements);
                added = list.count - pc;
                size = s + added;
            } finally {
                lock.unlock();
            }
            return added != 0;
        }

        public void clear() {
            SequenceLock lock = list.lock;
            lock.lock();
            try {
                list.internalClear(offset, size);
                size = 0;
            } finally {
                lock.unlock();
            }
        }

        public boolean contains(Object o) {
            return indexOf(o) >= 0;
        }

        public boolean containsAll(Collection<?> c) {
            return list.internalContainsAll(c, offset, size);
        }

        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof List))
                return false;
            return list.internalEquals((List<?>)(o), offset, size);
        }

        public E get(int index) {
            if (index < 0 || index >= size)
                throw new ArrayIndexOutOfBoundsException(index);
            return list.get(index + offset);
        }

        public int hashCode() {
            return list.internalHashCode(offset, size);
        }

        public int indexOf(Object o) {
            SequenceLock lock = list.lock;
            long seq = lock.awaitAvailability();
            Object[] items = list.array;
            int c = list.count;
            if (c <= items.length) {
                int idx = internalIndexOf(o, items, offset, offset+size);
                if (lock.getSequence() == seq)
                    return idx < 0 ? -1 : idx - offset;
            }
            lock.lock();
            try {
                int idx = internalIndexOf(o, list.array, offset, offset+size);
                return idx < 0 ? -1 : idx - offset;
            } finally {
                lock.unlock();
            }
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public Iterator<E> iterator() {
            return new SubItr(this, offset);
        }

        public int lastIndexOf(Object o) {
            SequenceLock lock = list.lock;
            long seq = lock.awaitAvailability();
            Object[] items = list.array;
            int c = list.count;
            if (c <= items.length) {
                int idx = internalLastIndexOf(o, items, offset+size-1, offset);
                if (lock.getSequence() == seq)
                    return idx < 0 ? -1 : idx - offset;
            }
            lock.lock();
            try {
                int idx = internalLastIndexOf(o, list.array, offset+size-1,
                                              offset);
                return idx < 0 ? -1 : idx - offset;
            } finally {
                lock.unlock();
            }
        }

        public ListIterator<E> listIterator() {
            return new SubItr(this, offset);
        }

        public ListIterator<E> listIterator(int index) {
            return new SubItr(this, index + offset);
        }

        public E remove(int index) {
            E result;
            SequenceLock lock = list.lock;
            lock.lock();
            try {
                if (index < 0 || index >= size)
                    throw new ArrayIndexOutOfBoundsException(index);
                int i = index + offset;
                result = (E)list.array[i];
                list.internalRemoveAt(i);
                size--;
            } finally {
                lock.unlock();
            }
            return result;
        }

        public boolean remove(Object o) {
            boolean removed = false;
            SequenceLock lock = list.lock;
            lock.lock();
            try {
                if (list.internalRemoveAt(internalIndexOf(o, list.array, offset,
                                                          offset+size))) {
                    removed = true;
                    --size;
                }
            } finally {
                lock.unlock();
            }
            return removed;
        }

        public boolean removeAll(Collection<?> c) {
            return list.internalRemoveAll(c, offset, size);
        }

        public boolean retainAll(Collection<?> c) {
            return list.internalRetainAll(c, offset, size);
        }

        public E set(int index, E element) {
            if (index < 0 || index >= size)
                throw new ArrayIndexOutOfBoundsException(index);
            return list.set(index+offset, element);
        }

        public int size() {
            return size;
        }

        public List<E> subList(int fromIndex, int toIndex) {
            int c = size;
            int ssize = toIndex - fromIndex;
            if (fromIndex < 0 || toIndex > c || ssize < 0)
                throw new IndexOutOfBoundsException();
            return new ReadMostlyVectorSublist(list, offset+fromIndex, ssize);
        }

        public Object[] toArray() {
            return list.internalToArray(offset, size);
        }

        public <T> T[] toArray(T[] a) {
            return list.internalToArray(a, offset, size);
        }

        public String toString() {
            return list.internalToString(offset, size);
        }

    }

    static final class SubItr<E> implements ListIterator<E> {
        final ReadMostlyVectorSublist<E> sublist;
        final ReadMostlyVector<E> list;
        final SequenceLock lock;
        Object[] items;
        Object next, prev;
        long seq;
        int cursor;
        int fence;
        int lastRet;
        boolean haveNext, havePrev;

        SubItr(ReadMostlyVectorSublist<E> sublist, int index) {
            this.sublist = sublist;
            this.list = sublist.list;
            this.lock = list.lock;
            this.cursor = index;
            this.lastRet = -1;
            refresh();
            if (index < 0 || index > fence)
                throw new ArrayIndexOutOfBoundsException(index);
        }

        private void refresh() {
            do {
                seq = lock.awaitAvailability();
                items = list.array;
                int c = list.count;
                int b = sublist.offset + sublist.size;
                fence = b < c ? b : c;
            } while (lock.getSequence() != seq);
        }

        public boolean hasNext() {
            int i = cursor;
            while (i < fence && i >= 0) {
                if (lock.getSequence() == seq) {
                    next = items[i];
                    return haveNext = true;
                }
                refresh();
            }
            return false;
        }

        public boolean hasPrevious() {
            int i = cursor;
            while (i <= fence && i > 0) {
                if (lock.getSequence() == seq) {
                    prev = items[i - 1];
                    return havePrev = true;
                }
                refresh();
            }
            return false;
        }

        public E next() {
            if (!haveNext && !hasNext())
                throw new NoSuchElementException();
            haveNext = false;
            lastRet = cursor++;
            return (E) next;
        }

        public E previous() {
            if (!havePrev && !hasPrevious())
                throw new NoSuchElementException();
            havePrev = false;
            lastRet = cursor--;
            return (E) prev;
        }

        public int nextIndex() {
            return cursor - sublist.offset;
        }

        public int previousIndex() {
            return cursor - 1 - sublist.offset;
        }

        public void remove() {
            int i = lastRet;
            if (i < 0)
                throw new IllegalStateException();
            cursor = i;
            lastRet = -1;
            lock.lock();
            try {
                if (i < list.count) {
                    list.remove(i);
                    --sublist.size;
                }
            } finally {
                lock.unlock();
            }
            refresh();
        }

        public void set(E e) {
            int i = lastRet;
            if (i < 0)
                throw new IllegalStateException();
            lock.lock();
            try {
                if (i < list.count)
                    list.set(i, e);
            } finally {
                lock.unlock();
            }
            refresh();
        }

        public void add(E e) {
            int i = cursor;
            if (i < 0)
                throw new IllegalStateException();
            cursor = i + 1;
            lastRet = -1;
            lock.lock();
            try {
                if (i <= list.count) {
                    list.add(i, e);
                    ++sublist.size;
                }
            } finally {
                lock.unlock();
            }
            refresh();
        }

    }
}

