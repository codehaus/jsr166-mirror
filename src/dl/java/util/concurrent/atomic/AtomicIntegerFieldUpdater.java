package java.util.concurrent.atomic;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * An AtomicIntegerFieldUpdater is a reflection-based utility that
 * enables atomic updates to designated integer fields of designated
 * classes.  It is designed for use in atomic data structures in which
 * several integer fields of the same node are independently subject
 * to atomic updates.
 * <p> Note the weaker guarantees of the <code>compareAndSet<code>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity and volatile
 * semantics only with respect to other invocations of
 * <code>compareAndSet<code>.
 */

public class  AtomicIntegerFieldUpdater<T> {
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;

    /**
     * Create an updater for objects of the given class with the given field.
     * @param fieldname the name of the field to be updated.
     * @throws Error if the given class does not have
     * an accessible field of the given name.
     * @throws IllegalArgumentException if the field is not a
     * volatile integer type.
     * @throws RuntimeException with an nested reflection-based
     * exception if the class does not hold field or is the wrong type.
     **/
    public AtomicIntegerFieldUpdater(T[] ta, String fieldName) {
        Field field = null;
        try {
            Class tclass = ta.getClass().getComponentType();
            field = tclass.getDeclaredField(fieldName);
        }
        catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        Class fieldt = field.getType();
        if (fieldt != int.class)
            throw new IllegalArgumentException("Must be integer type");

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

    public final boolean compareAndSet(T obj, int expect, int update) {
        //        if (!receiverType.isInstance(obj))
        //            throw new ClassCastException();
        return unsafe.compareAndSwapInt(obj, offset, expect, update);
    }

    /**
     * Atomically set the value of the field of the given object managed
     * by this Updater to the given updated value if the current value
     * <tt>==</tt> the expected value. This method is guaranteed to be
     * atomic with respect to other calls to <tt>compareAndSet</tt> and
     * <tt>setForUpdate</tt>, but not necessarily with respect to other
     * changes in the field.
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor.
     **/

    public final boolean weakCompareAndSet(T obj, int expect, int update) {
        return unsafe.compareAndSwapInt(obj, offset, expect, update);
    }

    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>, but only as
     * a non-volatile store otherwise.
     */
    public final void set(T obj, int newValue) {
        // Unsafe puts do not know about barriers, so manually apply
        unsafe.storeStoreBarrier();
        unsafe.putInt(obj, offset, newValue);
        unsafe.storeLoadBarrier();
    }
}

