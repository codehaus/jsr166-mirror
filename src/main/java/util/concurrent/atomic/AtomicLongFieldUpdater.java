/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * An AtomicLongFieldUpdater is a reflection-based utility that
 * enables atomic updates to designated long fields of designated
 * classes.  It is designed for use in atomic data structures in which
 * several fields of the same node are independently subject
 * to atomic updates.
 * <p> Note the weaker guarantees of the <tt>compareAndSet</tt>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity and volatile
 * semantics only with respect to other invocations of
 * <tt>compareAndSet</tt> and <tt>set</tt>.
 *
 * <p> <em>Development note: This class is currently missing
 * some planned methods </em>
 * @since 1.5
 * @author Doug Lea
 */

public abstract class  AtomicLongFieldUpdater<T>  {
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;

    // Standin for upcoming synthesized version
    static class AtomicLongFieldUpdaterImpl<T> extends AtomicLongFieldUpdater<T> {
        AtomicLongFieldUpdaterImpl(Class<T> tclass, String fieldName) {
            super(tclass, fieldName);
        }
    }


    /**
     * Create an updater for objects with the given field.
     * The Class constructor argument is needed to check
     * that reflective types and generic types match.
     * @param tclass the class of the objects holding the field
     * @param fieldName the name of the field to be updated.
     * @return the updater
     * @throws IllegalArgumentException if the field is not a
     * volatile long type.
     * @throws RuntimeException with an nested reflection-based
     * exception if the class does not hold field or is the wrong type.
     */
    public static <U> AtomicLongFieldUpdater<U> newUpdater(Class<U> tclass, String fieldName) {
        return new AtomicLongFieldUpdaterImpl<U>(tclass, fieldName);
    }


    AtomicLongFieldUpdater(Class<T> tclass, String fieldName) {
        Field field = null;
        try {
            field = tclass.getDeclaredField(fieldName);
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        Class fieldt = field.getType();
        if (fieldt != long.class)
            throw new IllegalArgumentException("Must be long type");

        if (!Modifier.isVolatile(field.getModifiers()))
            throw new IllegalArgumentException("Must be volatile type");

        offset = unsafe.objectFieldOffset(field);
    }

    final boolean doCas(Object obj, long expect, long update) {
        return unsafe.compareAndSwapLong(obj, offset, expect, update);
    }

    final void doSet(Object obj, long newValue) {
        unsafe.putLongVolatile(obj, offset, newValue);
    }

    final long doGet(Object obj) {
        return unsafe.getLongVolatile(obj, offset);
    }

    /**
     * Atomically set the value of the field of the given object managed
     * by this Updater to the given updated value if the current value
     * <tt>==</tt> the expected value. This method is guaranteed to be
     * atomic with respect to other calls to <tt>compareAndSet</tt> and
     * <tt>set</tt>, but not necessarily with respect to other
     * changes in the field.
     * @param obj An object whose field to conditionally set
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor.
     */

    public final boolean compareAndSet(T obj, long expect, long update) {
        return doCas(obj, expect, update);
    }

    /**
     * Atomically set the value of the field of the given object managed
     * by this Updater to the given updated value if the current value
     * <tt>==</tt> the expected value. This method is guaranteed to be
     * atomic with respect to other calls to <tt>compareAndSet</tt> and
     * <tt>set</tt>, but not necessarily with respect to other
     * changes in the field.
     * @param obj An object whose field to conditionally set
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor.
     */

    public final boolean weakCompareAndSet(T obj, long expect, long update) {
        return compareAndSet(obj, expect, update);
    }

    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>.
     * @param obj An object whose field to set
     * @param newValue the new value
     */
    public final void set(T obj, long newValue) {
        doSet(obj, newValue);
    }

    /**
     * Get the current value held in the field by the given object.
     * @param obj An object whose field to get
     * @return the current value
     */
    public final long get(T obj) {
        return doGet(obj);
    }

    /**
     * Set to the given value and return the old value.
     *
     * @param obj An object whose field to get and set
     * @param newValue the new value
     * @return the previous value
     */
    public long getAndSet(T obj, long newValue) {
        for (;;) {
            long current = get(obj);
            if (compareAndSet(obj, current, newValue))
                return current;
        }
    }

    /**
     * Atomically increment the current value.
     * @param obj An object whose field to get and set
     * @return the previous value;
     */
    public long getAndIncrement(T obj) {
        for (;;) {
            long current = get(obj);
            long next = current + 1;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }


    /**
     * Atomically decrement the current value.
     * @param obj An object whose field to get and set
     * @return the previous value;
     */
    public long getAndDecrement(T obj) {
        for (;;) {
            long current = get(obj);
            long next = current - 1;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }


    /**
     * Atomically add the given value to current value.
     * @param obj An object whose field to get and set
     * @param delta the value to add
     * @return the previous value;
     */
    public long getAndAdd(T obj, long delta) {
        for (;;) {
            long current = get(obj);
            long next = current + delta;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }

    /**
     * Atomically increment the current value.
     * @param obj An object whose field to get and set
     * @return the updated value;
     */
    public long incrementAndGet(T obj) {
        for (;;) {
            long current = get(obj);
            long next = current + 1;
            if (compareAndSet(obj, current, next))
                return next;
        }
    }


    /**
     * Atomically decrement the current value.
     * @param obj An object whose field to get and set
     * @return the updated value;
     */
    public long decrementAndGet(T obj) {
        for (;;) {
            long current = get(obj);
            long next = current - 1;
            if (compareAndSet(obj, current, next))
                return next;
        }
    }


    /**
     * Atomically add the given value to current value.
     * @param obj An object whose field to get and set
     * @param delta the value to add
     * @return the updated value;
     */
    public long addAndGet(T obj, long delta) {
        for (;;) {
            long current = get(obj);
            long next = current + delta;
            if (compareAndSet(obj, current, next))
                return next;
        }
    }

}

