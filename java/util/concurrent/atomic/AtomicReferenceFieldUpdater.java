package java.util.concurrent.atomic;
import java.lang.reflect.*;
/* import commented out so can compile for JSR166 spec - dl
   import sun.misc.Unsafe;
*/

/**
 * AtomicReferenceFieldUpdater is a reflection-based utility that
 * enables atomic updates to designated reference fields of designated
 * classes.  It is designed for use in atomic data structures in which
 * several reference fields of the same node are independently subject
 * to atomic updates. For example, a linked node might be declared as
 *
 * <pre>
 * class Node {
 *   private volatile Node left;
 *   private volatile Node right;
 *
 *   private static AtomicReferenceFieldUpdater leftUpdater;
 *   private static AtomicReferenceFieldUpdater rightUpdater;
 *   static {
 *     try {
 *      leftUpdater = new AtomicReferenceFieldUpdater(
 *         Node.class.getDeclaredField("left"));
 *      rightUpdater = new AtomicReferenceFieldUpdater(
 *         Node.class.getDeclaredField("right"));
 *     }
 *     catch(Exception cannotHappen) { throw new Error(cannotHappen); }
 *   }
 *
 *   Node getLeft() { return left;  }
 *   boolean attemptUpdateLeft(Node expect, Node update) {
 *     return leftUpdater.attemptUpdate(this, expect, update);
 *   }
 *   // ... and so on
 * }
 * </pre>
 *
 * <p> Note the weaker guarantees of the <code>attemptUpdate<code>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity only with
 * respect to other invocations of <code>attemptUpdate</code>.
 */

public class  AtomicReferenceFieldUpdater { 
/*   implementation commented out so can compile - dl
     private static final Unsafe unsafe =  Unsafe.getUnsafe();
     private final long offset;
     private final Class receiverType;
     private final Class argType;
*/

    /**
     * Create an updater for objects with the given field.
     * @param field the field to be updated.
     * @throws IllegalArgumentException if the field is not a reference type.
     **/
    public AtomicReferenceFieldUpdater(Field field) {
/*   implementation commented out so can compile - dl
     offset = unsafe.objectFieldOffset(field);
     receiverType = field.getDeclaringClass();
     argType = field.getType();
     if (argType.isPrimitive()) 
     throw new IllegalArgumentException("Must be reference type");
*/
    }

  
    /**
     * Atomically set the value of the field of the given object
     * managed by this Updater to the given updated value if the
     * current value <tt>==</tt> the expected value. This method is
     * guaranteed to be atomic with respect to other calls to
     * <attemptUpdate</tt>, but not necessarily with respect to other
     * changes in the field.  Any given invocation of this operation
     * may fail (return <code>false</code>) spuriously, but repeated
     * invocation when the current calue holds the expected value and
     * no other thread is also attempting to set the value will
     * eventually succeed.
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor, or
     * if the <tt>update</tt> argument is not of the type of this field.
     **/

    public final boolean attemptUpdate(Object obj, Object expect, Object update) {
/*   implementation commented out so can compile - dl
     if (receiverType.isInstance(obj) &&
     (update == null || argType.isInstance(update)))
     return unsafe.compareAndSwapObject(obj, offset, expect, update);
     else
*/
        throw new ClassCastException();
    }
}

