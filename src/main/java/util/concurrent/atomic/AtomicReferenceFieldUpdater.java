/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

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
 *   private static final AtomicReferenceFieldUpdater&lt;Node, Node&gt; leftUpdater =
 *     AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "left");
 *   private static AtomicReferenceFieldUpdater&lt;Node, Node&gt; rightUpdater =
 *     AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "right");
 *
 *   Node getLeft() { return left;  }
 *   boolean compareAndSetLeft(Node expect, Node update) {
 *     return leftUpdater.compareAndSet(this, expect, update);
 *   }
 *   // ... and so on
 * }
 * </pre>
 *
 * <p> Note the weaker guarantees of the <tt>compareAndSet</tt>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity and volatile
 * semantics only with respect to other invocations of
 * <tt>compareAndSet</tt> and <tt>set</tt>.
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AtomicReferenceFieldUpdater<T, V>  {
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;

    // Standin for upcoming synthesized version
    static class AtomicReferenceFieldUpdaterImpl<T,V> extends AtomicReferenceFieldUpdater<T,V> {
        AtomicReferenceFieldUpdaterImpl(Class<T> tclass, Class<V> vclass, String fieldName) {
            super(tclass, vclass, fieldName);
        }
    }

    /**
     * Create an updater for objects with the given field.
     * The Class constructor arguments are needed to check
     * that reflective types and generic types match.
     * @param tclass the class of the objects holding the field.
     * @param vclass the class of the field
     * @param fieldName the name of the field to be updated.
     * @return the updater
     * @throws IllegalArgumentException if the field is not a volatile reference type.
     * @throws RuntimeException with an nested reflection-based
     * exception if the class does not hold field or is the wrong type.
     */
    public static <U, W> AtomicReferenceFieldUpdater<U,W> newUpdater(Class<U> tclass, Class<W> vclass, String fieldName) {
        return new AtomicReferenceFieldUpdaterImpl<U,W>(tclass, vclass, fieldName);
    }

    AtomicReferenceFieldUpdater(Class<T> tclass, Class<V> vclass, String fieldName) {
        Field field = null;
        Class fieldClass = null;
        try {
            field = tclass.getDeclaredField(fieldName);
            fieldClass = field.getType();
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        if (vclass != fieldClass)
            throw new ClassCastException();

        if (!Modifier.isVolatile(field.getModifiers()))
            throw new IllegalArgumentException("Must be volatile type");

        offset = unsafe.objectFieldOffset(field);
    }

    final boolean doCas(Object obj, Object expect, Object update) {
        return unsafe.compareAndSwapObject(obj, offset, expect, update);
    }

    final void doSet(Object obj, Object newValue) {
        unsafe.putObjectVolatile(obj, offset, newValue);
    }

    final Object doGet(Object obj) {
        return unsafe.getObjectVolatile(obj, offset);
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
     */

    public boolean compareAndSet(T obj, V expect, V update) {
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
     */
    public boolean weakCompareAndSet(T obj, V expect, V update) {
        return compareAndSet(obj, expect, update);
    }


    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>.
     * @param obj An object whose field to set
     * @param newValue the new value
     */
    public void set(T obj, V newValue) {
        doSet(obj, newValue);
    }

    /**
     * Get the current value held in the field by the given object.
     * @param obj An object whose field to get
     * @return the current value
     */
    public V get(T obj) {
        return (V)doGet(obj);
    }

    /**
     * Set to the given value and return the old value.
     *
     * @param obj An object whose field to get and set
     * @param newValue the new value
     * @return the previous value
     */
    public V getAndSet(T obj, V newValue) {
        for (;;) {
            V current = get(obj);
            if (compareAndSet(obj, current, newValue))
                return current;
        }
    }


}

