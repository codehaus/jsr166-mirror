package java.util.concurrent.atomic;
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
 *   private static final AtomicReferenceFieldUpdater<Node, Node> leftUpdater =
 *     new AtomicReferenceFieldUpdater<Node, Node>(Node.class, Node.class, "left");
 *   private static AtomicReferenceFieldUpdater<Node, Node> rightUpdater =
 *     new AtomicReferenceFieldUpdater<Node, Node>(Node.class, Node.class, "right");
 *
 *   Node getLeft() { return left;  }
 *   boolean compareAndSetLeft(Node expect, Node update) {
 *     return leftUpdater.compareAndSet(this, expect, update);
 *   }
 *   // ... and so on
 * }
 * <p> Note the weaker guarantees of the <tt>compareAndSet</tt>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity and volatile
 * semantics only with respect to other invocations of
 * <tt>compareAndSet</tt> and <tt>set</tt>.
 * @since 1.5
 * @author Doug Lea
 */

public class AtomicReferenceFieldUpdater<T, V> {
    private final Field field;

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
     **/
    public static <U, W> AtomicReferenceFieldUpdater<U,W> newUpdater(Class<U> tclass, Class<W> vclass, String fieldName) {
        return new AtomicReferenceFieldUpdaterImpl<U,W>(tclass, vclass, fieldName);
    }

    AtomicReferenceFieldUpdater(Class<T> tclass, Class<V> vclass, String fieldName) {
        Class fieldClass = null;
        try {
            field = tclass.getDeclaredField(fieldName);
            fieldClass = field.getType();
            field.setAccessible(true);
        }
        catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        if (vclass != fieldClass)
            throw new ClassCastException();

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
     **/

    public synchronized final boolean compareAndSet(T obj, V expect, V update) {
        try {
            V value = (V) field.get(obj);
            if (value == expect) {
                field.set(obj, update);
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
     * of the class possessing the field established in the constructor, or
     * if the <tt>update</tt> argument is not of the type of this field.
     **/

    public final boolean weakCompareAndSet(T obj, V expect, V update) {
        return compareAndSet(obj, expect, update);
    }


    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>.
     */
    public synchronized final void set(T obj, V newValue) {
        try {
            field.set(obj, newValue);
        }
        catch(Exception ex) {
            throw new Error(ex);
        }
    }
}

