/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
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

public class  AtomicLongFieldUpdater<T> {
    private final Field field;

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
        try {
            field = tclass.getDeclaredField(fieldName);
            field.setAccessible(true);
        }
        catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        Class fieldt = field.getType();
        if (fieldt != long.class)
            throw new IllegalArgumentException("Must be long type");

        if (!Modifier.isVolatile(field.getModifiers()))
            throw new IllegalArgumentException("Must be volatile type");

    }

    /**
     * Atomically set the value of the field of the given object managed
     * by this Updater to the given updated value if the current value
     * <tt>==</tt> the expected value. This method is guaranteed to be
     * atomic with respect to other calls to <tt>compareAndSet</tt> and
     * <tt>set</tt>, but not necessarily with respect to other
     * changes in the field.
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor.
     **/

    public synchronized final boolean compareAndSet(T obj, long expect, long update) {
        try {
            long value = field.getLong(obj);
            if (value == expect) {
                field.setLong(obj, update);
                return true;
            }
            else
                return false;
        }
        catch(Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * Atomically set the value of the field of the given object managed
     * by this Updater to the given updated value if the current value
     * <tt>==</tt> the expected value. This method is guaranteed to be
     * atomic with respect to other calls to <tt>compareAndSet</tt> and
     * <tt>set</tt>, but not necessarily with respect to other
     * changes in the field.
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor.
     **/

    public final boolean weakCompareAndSet(T obj, long expect, long update) {
        return compareAndSet(obj, expect, update);
    }

    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>.
     */
    public final synchronized void set(T obj, long newValue) {
        try {
            field.setLong(obj, newValue);
        }
        catch(Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * Get the current value held in the field by the given object.
     */
    public final long get(T obj) {
        try {
            return field.getLong(obj);
        }
        catch(Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * Set to the given value and return the old value
     **/
    public long getAndSet(T obj, long newValue) {
        for (;;) {
            long current = get(obj);
            if (compareAndSet(obj, current, newValue))
                return current;
        }
    }

    /**
     * Atomically increment the current value.
     * @return the previous value;
     **/
    public long getAndIncrement(T obj) {
        for (;;) {
            long current = get(obj);
            long next = current+1;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }


    /**
     * Atomically decrement the current value.
     * @return the previous value;
     **/
    public long getAndDecrement(T obj) {
        for (;;) {
            long current = get(obj);
            long next = current-1;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }


    /**
     * Atomically add the given value to current value.
     * @return the previous value;
     **/
    public long getAndAdd(T obj, long y) {
        for (;;) {
            long current = get(obj);
            long next = current+y;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }


}
