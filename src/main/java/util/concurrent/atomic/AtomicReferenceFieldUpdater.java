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
 *   private static final AtomicReferenceFieldUpdater leftUpdater =
 *     new AtomicReferenceFieldUpdater(new Node[0], new Node[0], "left");
 *   private static AtomicReferenceFieldUpdater rightUpdater =
 *     new AtomicReferenceFieldUpdater(new Node[0], new Node[0], "right");
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
 * <code>compareAndSet<code> and <tt>set</tt>.
 */

public class  AtomicReferenceFieldUpdater<T, V> { 
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;

    /**
     * Create an updater for objects with the given field.  The odd
     * nature of the constructor arguments are a result of needing
     * sufficient information to check that reflective types and
     * generic types match.
     * @param ta an array (normally of length 0) of type T (the class
     * of the objects holding the field).
     * @param va an array (normally of length 0) of type V (the class
     * of the field).
     * @param fieldName the name of the field to be updated.
     * @throws IllegalArgumentException if the field is not a volatile reference type.
     * @throws RuntimeException with an nested reflection-based
     * exception if the class does not hold field or is the wrong type.
     **/
    public AtomicReferenceFieldUpdater(T[] ta, V[] va, String fieldName) {
        Field field = null;
        Class vclass = null;
        Class fieldClass = null;
        try {
            Class tclass = ta.getClass().getComponentType();
            field = tclass.getDeclaredField(fieldName);
            vclass = va.getClass().getComponentType();
            fieldClass = field.getType();
        }
        catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        if (vclass != fieldClass) 
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
     * to subsequent invocations of <tt>compareAndSet</tt>.
     */
    public final void set(T obj, V newValue) {
        // Unsafe puts do not know about barriers, so manually apply
        unsafe.storeStoreBarrier();
        unsafe.putObject(obj, offset, newValue); 
        unsafe.storeLoadBarrier();
    }

    /**
     * Get the current value held in the field by the given object.
     */
    public final V get(T obj) {
        // Unsafe gets do not know about barriers, so manually apply
        V v = (V)unsafe.getObject(obj, offset); 
        unsafe.loadLoadBarrier();
        return v;
    }

    /**
     * Set to the given value and return the old value
     **/
    public V getAndSet(T obj, V newValue) {
        for (;;) {
            V current = get(obj);
            if (compareAndSet(obj, current, newValue))
                return current;
        }
    }


}

