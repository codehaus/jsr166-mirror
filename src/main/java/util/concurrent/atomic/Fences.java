/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.atomic;
import java.lang.reflect.*;

/**
 * A set of methods providing fine-grained control over
 * happens-before and synchronization order relations (as defined in
 * The Java Language Specification, chapter 17) among accesses
 * (i.e., reads or writes) to variables.
 *
 * <p> This class is designed for use in uncommon situations where
 * declaring variables {@code volatile} or {@code final}, using
 * instances of atomic classes, using {@code synchronized} blocks or
 * methods, or using other synchronization facilities are not possible
 * or do not provide the desired control over ordering.  Fence methods
 * should be used only when these alternatives do not apply.  Usages
 * almost always take one of the forms illustrated in examples below,
 * arranging some of the ordering properties associated with {@code
 * volatile}, etc.
 *
 * <p>There are three methods for controlling ordering relations among
 * memory accesses: Method {@code orderReads} is used to enforce order
 * between two reads. Method {@code orderWrites} is normally used
 * between two writes, and {@code orderAccesses} between a write and a
 * read.  These methods specify relationships among accesses, not the
 * accesses themselves. Invocations must be placed <em>between</em>
 * accesses that load or store variables (i.e., those typically
 * performed in expression evaluations and assignment statements) to
 * control the orderings of preceding versus subsequent accesses
 * appearing in program order. Fence methods do not control any other
 * properties of those accesses themselves, which in some usage
 * scenarios may be unknown to you, so correctness may be context
 * dependent. The methods provide platform-independent ordering
 * guarantees that are honored by all levels of a platform (compilers,
 * systems, processors).  The use of these methods may result in the
 * suppression of otherwise valid compiler transformations and
 * optimizations that could visibly violate the specified orderings,
 * and may or may not entail the use of processor-level "memory
 * barrier" instructions.
 *
 * <p>Each method accepts and returns a {@code ref} argument
 * controlling the <em>scope</em> of ordering guarantees.  The scope
 * of {@code ref} includes accesses to {@code ref} itself, as well as
 * any field (or if an array, array element) directly reachable using
 * ref at the point of invocation of the method. More formally, the
 * scope corresponds to any access <em>a</em> obeying the The Java
 * Language Specification section 17.5.1 relation
 * <em>dereferences(ref, a)</em>, where the <em>dereferences</em>
 * relation is defined as: For a read action r, and an action a that
 * reads or writes object o in thread t, dereferences(r, a) holds iff
 * t did not initialize o, and a accesses the object (o) returned by
 * r.
 *
 * <p>It is good practice for scope arguments to designate the
 * narrowest applicable scopes. An invocation inside a method of a
 * class accessing its own fields will normally have the scope of
 * {@code this}. For arrays, the scope is normally the array
 * object. Usages should be restricted to the control of strictly
 * internal implementation matters inside a class or package, and must
 * avoid any consequent violations of ordering or safety properties
 * expected by users of a class employing them.
 *
 * <p>Additionally, method {@code reachabilityFence} establishes
 * an ordering for strong reachability (as defined in the {@link
 * java.lang.ref} package specification) with respect to garbage
 * collection.  Method {@code reachabilityFence} differs from the
 * others in that it controls relations that are otherwise only
 * implicit in a program -- the reachability conditions triggering
 * garbage collection. As illustrated in the sample usages below, this
 * method is applicable only when reclamation may have visible
 * effects, which is possible for objects with finalizers (see Section
 * 12.6 of the Java Language Specification) that are implemented in
 * ways that rely on ordering control for correctness.
 *
 * <p><b>Sample Usages.</b>
 *
 * <p><b>Emulating {@code final}.</b> With care, method {@code
 * orderWrites} may be used to obtain the memory safety effects of
 * {@code final} for a field that cannot be declared as {@code final},
 * because its primary initialization cannot be performed in a
 * constructor, in turn because it is used in a framework requiring
 * that all classes have a no-argument constructor; as in:
 *
 * <pre>
 * class WidgetHolder {
 *   private Widget widget;
 *   public WidgetHolder() {}
 *   public static WidgetHolder newWidgetHolder(Params params) {
 *     WidgetHolder h = new WidgetHolder();
 *     h.widget = new Widget(params);
 *     return Fences.orderWrites(h);
 *  }
 * }
 * </pre>
 *
 * Here, the invocation of {@code orderWrites} makes sure that the
 * effects of the widget assignment are ordered before those of any
 * (unknown) subsequent stores of {@code h} in other variables that
 * make {@code h} available for use by other objects. Notice that this
 * method observes the care required for final fields: It does not
 * internally "leak" the reference by using it as an argument to a
 * callback method or adding it to a static data structure. If such
 * functionality were required, it may be possible to cope using more
 * extensive sets of fences, or as a normally better choice, using
 * synchronization (locking).  Notice also that because {@code final}
 * could not be used here, the compiler and JVM cannot help you ensure
 * that the field is set correctly across all usages.
 *
 * <p>An alternative approach is to place similar mechanics in the
 * (sole) method that makes such objects available for use by others.
 * Here is a stripped-down example illustrating the essentials. In
 * practice, among other changes, you would use access methods instead
 * of a public field.
 *
 * <pre>
 * class AnotherWidgetHolder {
 *   public Widget widget;
 *   void publish(Widget w) {
 *     this.widget = Femces.orderWrites(w);
 *   }
 *   // ...
 * }
 * </pre>
 *
 * In this case, the {@code orderWrites} occurs before the store
 * making the object available. Correctness again relies on ensuring
 * that there are no leaks prior to invoking this method, and that it
 * really is the <em>only</em> means of accessing the published
 * object.  This approach is not often applicable -- normally you
 * would publish objects using a thread-safe collection that itself
 * guarantees the expected ordering relations. However, it may come
 * into play in the construction of such classes themselves.
 *
 * <p><b>Emulating {@code volatile} access.</b> Suppose there is an
 * accessible variable that should have been declared as
 * {@code volatile} but wasn't:
 *
 * <pre>
 * class C { Object data;  ...  }
 * class App {
 *   Object getData(C c) {
 *      return Fences.orderReads(c).data;
 *   }
 *
 *   void setData(C c) {
 *      Object newValue = ...;
 *      c.data = Fences.orderWrites(newValue);
 *      Fences.orderAccesses(c);
 *   }
 *   // ...
 * }
 * </pre>
 *
 * Method {@code getData} provides a faithful emulation of volatile
 * reads of (non-long/double) fields by ensuring that the read of
 * {@code c} obtained as an argument is ordered before subsequent
 * reads using this reference, and then performs the read of its
 * field. Method {@code setData} provides a similarly faithful
 * emulation of volatile writes, ensuring that all other relevant
 * writes have completed, then performing the assignment, and then
 * ensuring that the write is ordered before any other access.  These
 * techniques may apply even when fields are not directly accessible,
 * in which case calls to fence methods would surround calls to
 * methods such as {@code c.getData()}.  However, these techniques
 * cannot be applied to {@code long} or {@code double} fields because
 * reads and writes of fields of these types are not guaranteed to be
 * atomic. Additionally, correctness may require that all accesses of
 * such data use these kinds of wrapper methods, which you would need
 * to manually ensure.
 *
 * <p><b>Acquire/Release management of threadsafe objects</b>. It may
 * be possible to use weaker conventions for volatile-like variables
 * when they are used to keep track of objects that fully manage their
 * own thread-safety and synchronization.  Here, a {@code
 * memoryAcquire} operation remains the same as a volatile-read, but a
 * {@code memoryRelease} differs by virtue of not itself ensuring an
 * ordering of its write with subsequent reads, because the required
 * effects are already ensured by the referenced objects.  For
 * example:
 *
 * <pre>
 * class Item {
 *    synchronized f(); // ALL methods are synchronized
 *    // ...
 * }
 *
 * class ItemHolder {
 *   Item item;
 *   Item acquireItem() {
 *      return Fences.orderReads(item);
 *   }
 *
 *   void releaseItem(Item x) {
 *      item = Fences.orderWrites(x);
 *   }
 *   
 *   // ...
 * }
 * </pre>
 *
 * As is the case with most applications of fence methods, correctness
 * relies on the usage context -- here, the thread safety of {@code
 * Item}, as well as the lack of need for full volatile semantics
 * inside this class itself. However, the second concern means that
 * can be difficult to extend the {@code ItemHolder} class in this
 * example to be more useful.
 *
 * <p><b>Avoiding premature finalization.</b> Finalization may occur
 * whenever a Java Virtual Machine detects that no reference to an
 * object will ever be stored in the heap: A garbage collector may
 * reclaim an object even if the fields of that object are still in
 * use, so long as the object has otherwise become unreachable. This
 * may have surprising and undesirable effects in cases such as the
 * following example in which the bookkeeping associated with a class
 * is managed through array indices. Here, method {@code action}
 * uses a {@code reachabilityFence} to ensure that the Resource
 * object is not reclaimed before bookkeeping on an associated
 * ExternalResource has been performed; in particular here, to ensure
 * that the array slot holding the ExternalResource is not nulled out
 * in method {@link Object#finalize}, which may otherwise run
 * concurrently.
 *
 * <pre>
 * class Resource {
 *   private static ExternalResource[] externalResourceArray = ...
 *
 *   int myIndex;
 *   Resource(...) {
 *     myIndex = ...
 *     externalResourceArray[myIndex] = ...;
 *     ...
 *   }
 *   protected void finalize() {
 *     externalResourceArray[myIndex] = null;
 *     ...
 *   }
 *   public void action() {
 *     try {
 *       // ...
 *       int i = myIndex;
 *       Resource.update(externalResourceArray[i]);
 *     } finally {
 *       Fences.reachabilityFence(this);
 *     }
 *   }
 *   private static void update(ExternalResource ext) {
 *     ext.status = ...;
 *   }
 * }
 * </pre>
 *
 * Notice the nonintuitive placement of the call to
 * {@code reachabilityFence}.  It is placed <em>after</em> the
 * call to {@code update}, to ensure that the array slot is not
 * nulled out by {@link Object#finalize} before the update, even if
 * the call to {@code action} was last use of this object. This
 * might be the case if for example a usage in a user program had the
 * form {@code new Resource().action();} which retains no other
 * reference to this Resource.  While probably overkill here,
 * {@code reachabilityFence} is placed in a {@code finally}
 * block to ensure that it is invoked across all paths in the method.
 * In a method with more complex control paths, you might need further
 * precautions to ensure that {@code reachabilityFence} is
 * encountered along all of them.
 *
 * <p>It is sometimes possible to better encapsulate use of
 * {@code reachabilityFence}. Continuing the above example, if it
 * were OK for the call to method update to proceed even if the
 * finalizer had already executed (nulling out slot), then you could
 * localize use of {@code reachabilityFence}:
 *
 * <pre>
 *   public void action2() {
 *     // ...
 *     Resource.update(getExternalResource());
 *   }
 *   private ExternalResource getExternalResource() {
 *     ExternalResource extRes = externalResourceArray[myIndex];
 *     Fences.reachabilityFence(this);
 *     return ext;
 *   }
 * </pre>
 *
 * <p>Further continuing this example, you might be able to avoid use
 * of {@code reachabilityFence} entirely if it were OK to skip
 * updates in ExternalResources for Resource objects that have become
 * reclaimed (hence have null slots) by the time operations on them
 * are invoked:
 *
 * <pre>
 *   public void action3() {
 *     // ...
 *     Resource.update3(externalResourceArray[myIndex]);
 *   }
 *   private static void update3(ExternalResource ext) {
 *     if (ext != null) ...
 *   }
 * </pre>
 *
 * However this sort of simplification may require added complexity in
 * methods that allocate and reallocate array slots for
 * ExternalResources, to cope with the fact that one associated with
 * an apparently null slot might still be in use, and vice versa.
 *
 * <p>Additionally, method {@code reachabilityFence} is not
 * required in constructions that themselves ensure reachability. For
 * example, because objects that are locked cannot in general be
 * reclaimed, it would suffice if all accesses of the object, in all
 * methods of class Resource (including {@code finalize}) were
 * enclosed in {@code synchronized(this)} blocks. (Further, such
 * blocks must not include infinite loops, or themselves be
 * unreachable, which fall into the corner case exceptions to the "in
 * general" disclaimer.) Method {@code reachabilityFence} remains
 * an option in cases where this approach is not desirable or
 * possible; for example because it would encounter deadlock.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class Fences {
    private Fences() {} // Non-instantiable

    /*
     * The methods of this class are intended to be intrinisified by a
     * JVM. However, we provide correct but inefficient Java-level
     * code that simply reads and writes a static volatile
     * variable. Without JVM support, the consistency effects are
     * stronger than necessary, and the memory contention effects can
     * be a serious performance issue.
     */
    private static volatile int theVolatile;

    /**
     * Informally: Ensures that, within the scope of the given
     * reference, reads prior to the invocation of this method
     * occur before subsequent reads.
     *
     * <p>More formally, using the terminology of the Java Language
     * Specification, given a write <em>w</em>, a read <em>r1</em>
     * such that <em>dereferences(ref, r1)</em> and <em>r1</em> sees
     * the write <em>w</em>, and an invocation of {@code
     * orderReads(ref)} <em>f</em> such that <em>r1 happens-before
     * f</em>, and read <em>r2</em> such that <em>f happens-before
     * r2</em> and <em>dereferences(ref, r1)</em>, then the value of
     * <em>r2</em> is constrained by <em>w happens-before r2</em>.
     *
     * @param ref the reference. If null, this method has no effect.
     * @return a reference to the same object as ref
     */
    public static <T> T orderReads(T ref) {
        int ignore = theVolatile;
        return ref;
    }

    /**
     * Informally: Ensures that, within the scope of the given
     * reference, accesses prior to the invocation of this method occur
     * before subsequent writes.
     *
     * <p>More formally, using the terminology of the Java Language
     * Specification (especially section 17.5.1), given an access
     * <em>b</em> such that <em>dereferences(ref, b)</em>, an
     * invocation of {@code orderWrites(ref)} <em>f</em> such that
     * <em>b happens-before f</em>, an access <em>a</em> such that
     * <em>f happens-before a</em>, a read <em>r1</em> in the
     * <em>memory chain</em> <em>mc(a, r1)</em>, and a read
     * <em>r2</em> such that <em>dereferences(r1, r2)</em> (where r1
     * and r2 may be the same), then the value for <em>r2</em> is
     * constrained by the relation <em>b happens-before r2</em>.
     *
     * @param ref the (non-null) reference. If null, the effects
     * of the method are undefined.
     * @return a reference to the same object as ref
     */
    public static <T> T orderWrites(T ref) {
        theVolatile = 0;
        return ref;
    }

    /**
     * Informally: Ensures that, within the scope of the given
     * reference, accesses (reads or writes) prior to the invocation
     * of this method occur before subsequent accesses.
     * 
     * <p>More formally, using the terminology of the Java Language
     * Specification (see especially section 17.4.4), given a write
     * <em>w</em> such that <em>dereferences(ref, w)</em>, an
     * invocation of {@code orderAccesses(ref)} <em>f</em> such that
     * <em>w happens-before f</em>, and a read <em>r</em> such that
     * such that <em>f happens-before r</em>, and
     * <em>dereferences(ref, r)</em>, then <em>w synchronizes-with
     * r</em>.
     * 
     * @param ref the reference. If null, this method has no effect.
     * @return a reference to the same object as ref
     */
    public static <T> T orderAccesses(T ref) {
        theVolatile = 0;
        return ref;
    }

    /**
     * Ensures that the object referenced by the given reference
     * remains <em>strongly reachable</em> (as defined in the {@link
     * java.lang.ref} package documentation), regardless of any prior
     * actions of the program that might otherwise cause the object to
     * become unreachable; thus, the referenced object is not
     * reclaimable by garbage collection at least until after the
     * invocation of this method. Invocation of this method does not
     * itself initiate garbage collection or finalization.
     *
     * <p>See the class-level documentation for further explanation
     * and usage examples.
     *
     * @param ref the reference. If null, this method has no effect.
     */
    public static void reachabilityFence(Object ref) {
        if (ref != null) {
            synchronized (ref) {}
        }
    }
}
