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
 */

public class  AtomicLongFieldUpdater<T> { 
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;

    /**
     * Create an updater for objects with the given field.  The odd
     * nature of the constructor arguments are a result of needing
     * sufficient information to check that reflective types and
     * generic types match.
     * @param ta an array (normally of length 0) of type T (the class
     * of the objects holding the field).
     * @param fieldName the name of the field to be updated.
     * @throws IllegalArgumentException if the field is not a
     * volatile long type.
     * @throws RuntimeException with an nested reflection-based
     * exception if the class does not hold field or is the wrong type.
     **/
    public AtomicLongFieldUpdater(T[] ta, String fieldName) {
        Field field = null;
        try {
            Class tclass = ta.getClass().getComponentType();
            field = tclass.getDeclaredField(fieldName);
        }
        catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        Class fieldt = field.getType();
        if (fieldt != long.class) 
            throw new IllegalArgumentException("Must be long type");

        if (!Modifier.isVolatile(field.getModifiers()))
            throw new IllegalArgumentException("Must be volatile type");

        offset = unsafe.objectFieldOffset(field);
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

    public final boolean compareAndSet(T obj, long expect, long update) {
        return unsafe.compareAndSwapLong(obj, offset, expect, update);
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
        return unsafe.compareAndSwapLong(obj, offset, expect, update);
    }

}

