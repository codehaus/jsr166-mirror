package java.util.concurrent.atomic;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * An AtomicLongFieldUpdater is a reflection-based utility that
 * enables atomic updates to designated long fields of designated
 * classes.  It is designed for use in atomic data structures in which
 * several long fields of the same node are independently subject
 * to atomic updates.
 * <p> Note the weaker guarantees of the <code>compareAndSet<code>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity and volatile
 * semantics only with respect to other invocations of
 * <code>compareAndSet<code>.
 */

public class  AtomicLongFieldUpdater {
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;
    private final Class receiverType;

    /**
     * Create an updater for objects with the given field.
     * @param field the field to be updated.
     * @throws IllegalArgumentException if the field is not a volatile long type.
     **/
    public AtomicLongFieldUpdater(Field field) {
        offset = unsafe.objectFieldOffset(field);
        receiverType = field.getDeclaringClass();
        Class fieldt = field.getType();
        if (fieldt != long.class)
            throw new IllegalArgumentException("Must be long type");

// Commented out since hits HS bug
//    if (!Modifier.isVolatile(fieldt.getModifiers()))
//      throw new IllegalArgumentException("Must be volatile type");

    }

    /**
     * Create an updater for objects of the given class with the given field.
     * @param klass the class with the given field
     * @param fieldname the name of the field to be updated.
     * @throws Error if the given class does not have
     * an accessible field of the given name.
     * @throws IllegalArgumentException if the field is not a
     * volatile long type.
     **/
    public AtomicLongFieldUpdater(Class klass, String fieldName) {
        Field field = null;
        try {
            field = klass.getDeclaredField(fieldName);
        }
        catch(Exception ex) {
            throw new Error(ex);
        }
        offset = unsafe.objectFieldOffset(field);
        receiverType = field.getDeclaringClass();
        Class fieldt = field.getType();
        if (fieldt != long.class)
            throw new IllegalArgumentException("Must be long type");

// Commented out since hits HS bug
//    if (!Modifier.isVolatile(fieldt.getModifiers()))
//      throw new IllegalArgumentException("Must be volatile type");


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

    public final boolean compareAndSet(Object obj, long expect, long update) {
        //        if (!receiverType.isInstance(obj))
        //            throw new ClassCastException();
        return unsafe.compareAndSwapLong(obj, offset, expect, update);
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

    public final boolean weakCompareAndSet(Object obj, long expect, long update) {
        //        if (!receiverType.isInstance(obj))
        //            throw new ClassCastException();
        return unsafe.compareAndSwapLong(obj, offset, expect, update);
    }

    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>, but only as
     * a non-volatile store otherwise.
     */
    public final void setForUpdate(Object obj, long newValue) {
        //        if (!receiverType.isInstance(obj))
        //            throw new ClassCastException();
        // We do not need the full StoreLoad barrier semantics of a
        // volatile store.  But we do need to preserve ordering with
        // respect to other stores by surrounding with storeStore
        // barriers.
        unsafe.storeStoreBarrier();
        // Unsafe store call bypasses barriers used in volatile assignment
        unsafe.putLong(obj, offset, newValue);
        unsafe.storeStoreBarrier();
    }
}

