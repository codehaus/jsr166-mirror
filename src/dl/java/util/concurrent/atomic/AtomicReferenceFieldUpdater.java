package java.util.concurrent.atomic;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * An AtomicReferenceFieldUpdater is a reflection-based utility that
 * enables atomic updates to designated reference fields of designated
 * classes.  It is designed for use in atomic data structures in which
 * several reference fields of the same node are independently subject
 * to atomic updates. For example, a tree node might be declared as
 *
 * <pre>
 * class Node {
 *   private volatile Node left, right;
 *
 *   private static final AtomicReferenceFieldUpdater leftUpdater =
 *     new AtomicReferenceFieldUpdater(Node.class, "left");
 *   private static AtomicReferenceFieldUpdater rightUpdater =
 *     new AtomicReferenceFieldUpdater(Node.class, "right");
 *
 *   Node getLeft() { return left;  }
 *   boolean compareAndSetLeft(Node expect, Node update) {
 *     return leftUpdater.compareAndSet(this, expect, update);
 *   }
 *   // ... and so on
 * }
 * </pre>
 *
 * <p> Note the weaker guarantees of the <code>compareAndSet<code>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity and volatile
 * semantics only with respect to other invocations of
 * <code>compareAndSet<code>.
 */

public class  AtomicReferenceFieldUpdater<T, V> {
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;

    /**
     * Create an updater for objects with the given field.
     * @param field the field to be updated.
     * @throws IllegalArgumentException if the field is not a volatile reference type.
     **/
    public AtomicReferenceFieldUpdater(T[] ta, V[] fa, String fieldName) {
        Field field = null;
        Class fclass = null;
        Class fieldClass = null;
        try {
            Class tclass = ta.getClass().getComponentType();
            field = tclass.getDeclaredField(fieldName);
            fclass = fa.getClass().getComponentType();
            fieldClass = field.getType();
        }
        catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        if (fclass != fieldClass)
            throw new ClassCastException();

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
     **/

    public final boolean compareAndSet(T obj, V expect, V update) {
        return unsafe.compareAndSwapObject(obj, offset, expect, update);
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
     * of the class possessing the field established in the constructor, or
     * if the <tt>update</tt> argument is not of the type of this field.
     **/

    public final boolean weakCompareAndSet(T obj, V expect, V update) {
        return unsafe.compareAndSwapObject(obj, offset, expect, update);
    }


    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>, but only as
     * a non-volatile store otherwise.
     */
    public final void set(T obj, V newValue) {
        // Unsafe puts do not know about barriers, so manually apply
        unsafe.storeStoreBarrier();
        unsafe.putObject(obj, offset, newValue);
        unsafe.storeLoadBarrier();
    }
}

