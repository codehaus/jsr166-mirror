/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.atomic;
import java.lang.reflect.*;

/**
 * A set of methods providing fine-grained control over happens-before
 * and synchronization order relations (as defined in The Java
 * Language Specification section 17.4.4) among accesses to variables.
 *
 * <p> This class is designed for use in uncommon situations where
 * declaring variables <code>volatile</code> or <code>final</code>,
 * using instances of atomic classes, using <code>synchronized</code>
 * blocks or methods, or using other synchronization facilities are
 * not possible or do not provide the desired control over ordering.
 * Fences should be used only when these alternatives do not
 * apply. Even in such cases, usage is typically restricted to the
 * control of strictly internal implementation matters inside a class
 * or package, and must avoid any consequent violations of ordering or
 * safety properties expected by users of a class employing them.
 *
 * <p>There are three kinds of fences for controlling the ordering
 * relations among memory accesses: A <code>postLoadFence</code> is
 * generally used to enforce order between two reads, a
 * <code>preStoreFence</code> between two writes, and a
 * <code>postStorePreLoadFence</code> between a write and a read.
 * These Fence methods specify relationships among accesses, not the
 * accesses themselves. Invocations of Fence methods must be placed,
 * in sometimes nonintuitive ways, <em>between</em> accesses that load
 * or store variables (i.e., those typically performed in expression
 * evaluations and assignment statements). While fences control the
 * orderings of preceding versus subsequent accesses appearing in
 * program order, they do not control any other properties of those
 * accesses themselves, which in some usage scenarios may be unknown
 * to you, so correctness may be context dependent. Fence methods
 * provide platform-independent ordering guarantees that are honored
 * by all levels of a platform (compilers, systems, processors).  The
 * use of Fence methods may result in the suppression of otherwise
 * valid compiler transformations and optimizations that could visibly
 * violate the specified orderings, and may or may not entail the use
 * of processor-level "memory barrier" instructions.  Note that the
 * provided Fence methods are not quite symmetrical in effect, and
 * that some methods that you might expect to be defined are not,
 * because their effects are entailed by those provided.
 * 
 * <p>Additionally, method <code>reachabilityFence</code> establishes
 * an ordering for strong reachability (as defined in the {@link
 * java.lang.ref} package specification) with respect to garbage
 * collection.  Method <code>reachabilityFence</code> differs from the
 * others in that it controls relations that are otherwise only
 * implicit in a program -- the reachability conditions triggering
 * garbage collection. As illustrated in the sample usages below, this
 * method is applicable only when reclamation may have visible
 * effects, which is possible for objects with finalizers (see Section
 * 12.6 of the Java Language Specification) that are implemented in
 * ways that rely on ordering control for correctness.
 * 
 * <p>Each fence method requires an Object <code>ref</code> argument
 * controlling the <em>scope</em> of the fence. Effects are ensured
 * only within that scope -- accesses to <code>ref</code> itself, as
 * well as those accesses that, in accord with the rules in chapter 17
 * of The Java Language Specification for any other accesses in the
 * program, are guaranteed to be directly reachable using
 * <code>ref</code> at the point of invocation of the fence
 * method. This includes accesses of the fields of the object referred
 * to, or if an array, its elements. The supplied <code>ref</code>
 * argument should designate the narrowest applicable scope.  A fence
 * used inside a method of a class accessing its own fields will
 * normally have the scope of <code>this</code>.  For arrays, the
 * scope is normally the array object, and for static fields, the
 * {@link java.lang.Class} object of the class declaring the field.
 *
 * <p><b>Sample Usages.</b> 
 *
 * <p><b>Emulating <code>final</code>.</b> With care, a fence may be
 * used to obtain the memory safety effects of <code>final</code> for
 * a field that cannot be declared as <code>final</code>, because its
 * primary initialization cannot be performed in a constructor, in
 * turn because it is used in a framework requiring that all classes
 * have a no-argument constructor. For example:
 * 
 * <pre>
 * class WidgetHolder {
 *   private Widget widget;
 *   public WidgetHolder() {}
 *   public static WidgetHolder newWidgetHolder(Params params) {
 *     WidgetHolder h = new WidgetHolder();
 *     h.widget = new Widget(params);
 *     Fences.preStoreFence(h);
 *     return h;
 *  } 
 *  // ...
 * }
 * </pre>
 * 
 * Here, the fence ensures that the effects of the widget assignment
 * are ordered before those of any (unknown) subsequent stores of
 * <code>h</code> in other variables that make <code>h</code>
 * available for use by other objects. Notice that this method
 * observes the care required for final fields: It does not internally
 * "leak" the reference by using it as an argument to a callback
 * method or adding it to a static data structure. If such
 * functionality were required, it may be possible to cope using more
 * extensive sets of fences, or as a normally better choice, using
 * synchronization (locking).  Notice also that because
 * <code>final</code> could not be used here, the compiler and JVM
 * cannot help you ensure that the field is set correctly
 * across all usages.
 *
 * <p>An alternative approach is to place similar mechanics in the
 * (sole) method that makes such objects available for use by others.
 * Here is a stripped-down example illustrating the essentials. (In
 * practice, among other changes, you would use access methods instead
 * of a public field).
 * 
 * <pre>
 * class AnotherWidgetHolder {
 *   public Widget widget;
 *   void publish(Widget w) {
 *     Fences.preStoreFence(w);
 *     this.widget = w;
 *   }
 *   // ...
 * }
 * </pre>
 *
 * In this case, the fence occurs before the store making the object
 * available. Correctness again relies on ensuring that there are no
 * leaks prior to invoking this method, and that it really is the
 * <em>only</em> means of accessing the published object.  This
 * approach is not often applicable -- normally you would publish
 * objects using a thread-safe collection that itself guarantees the
 * expected ordering relations. However, it may come into play in the
 * construction of such classes themselves.
 * 
 * <p><b>Emulating <code>volatile</code> access.</b> Suppose there is an
 * accessible variable that should have been declared as
 * <code>volatile</code> but wasn't:
 * 
 * <pre>
 * class C { int data;  ...  } 
 * class App {
 *   int getData(C c) {
 *      Fences.postLoadFence(c);
 *      int currentValue = c.data;
 *      return currentValue;
 *   }
 *
 *   void setData(C c) {
 *      int newValue = ...;
 *      Fences.preStoreFence(c);
 *      c.data = newValue;
 *      Fences.postStorePreLoadFence(c);
 *   }
 *   // ...
 * }
 * </pre>
 * 
 * Method getData provides a faithful emulation of volatile reads of
 * (non-long/double) fields through references: issue a postLoadFence
 * to ensure that the read of C obtained as an argument is ordered
 * before subsequent reads using this reference, and then perform the
 * read of its field. Method setData provides a similarly faithful
 * emulation of volatile writes: Issue a fence ensuring that all other
 * relevant writes have completed, then perform the assignment, and
 * then issue a fence ensuring that the write is ordered before any
 * other access.  These techniques may apply even when fields are not
 * directly accessible, in which case the fences would surround calls
 * to methods such as c.getData().  However, these techniques cannot
 * be applied to <code>long</code> or <code>double</code> fields
 * because reads and writes of fields of these types are not
 * guaranteed to be atomic. Additionally, correctness may require that
 * all accesses of such data use these kinds of wrapper methods, which
 * you would need to manually ensure.
 *
 * <p><b>Acquire/Release management of threadsafe objects</b>. It may
 * be possible to use weaker conventions for volatile-like variables
 * when they are used to keep track of objects that fully manage their
 * own thread-safety and synchronization.  Here, an "acquire"
 * operation remains the same as a volatile-read, but a "release"
 * differs by virtue of not itself ensuring an ordering of its write
 * with subsequent reads, because the required effects are already
 * ensured by the referenced objects.  For example:
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
 *      Item x = item;
 *      Fences.postLoadFence(this);
 *      return x;
 *   }
 *
 *   void releaseItem(Item x) {
 *      Fences.preStoreFence(x);
 *      item = x;
 *   }
 *   // ...
 * }
 * </pre>
 *
 * As is the case with most applications of Fences, correctness relies
 * on the usage context -- here, the thread safety of Items, as well
 * as the lack of need for full volatile semantics inside this class
 * itself. However, the second concern means that can be difficult to
 * extend the ItemHolder class in this example to be more useful.
 *
 * <p><b>Avoiding premature finalization.</b> Finalization may occur
 * whenever a Java Virtual Machine detects that no reference to an
 * object will ever be stored in the heap: A garbage collector may
 * reclaim an object even if the fields of that object are still in
 * use, so long as the object has otherwise become unreachable. This
 * may have surprising and undesirable effects in cases such as the
 * following example in which the bookkeeping associated with a class
 * is managed through array indices. Here, method <code>action</code>
 * uses a <code>reachabilityFence</code> to ensure that the Resource
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
 * <code>reachabilityFence</code>.  It is placed <em>after</em> the
 * call to <code>update</code>, to ensure that the array slot is not
 * nulled out by {@link Object#finalize} before the update, even if
 * the call to <code>action</code> was last use of this object. This
 * might be the case if for example a usage in a user program had the
 * form <code>new Resource().action();</code> which retains no other
 * reference to this Resource.  While probably overkill here,
 * <code>reachabilityFence</code> is placed in a <code>finally</code>
 * block to ensure that it is invoked across all paths in the method.
 * In a method with more complex control paths, you might need further
 * precautions to ensure that <code>reachabilityFence</code> is
 * encountered along all of them.
 *
 * <p>It is sometimes possible to better encapsulate use of
 * <code>reachabilityFence</code>. Continuing the above example, if it
 * were OK for the call to method update to proceed even if the
 * finalizer had already executed (nulling out slot), then you could
 * localize use of <code>reachabilityFence</code>:
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
 * of <code>reachabilityFence</code> entirely if it were OK to skip
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
 * <p>Additionally, method <code>reachabilityFence</code> is not
 * required in constructions that themselves ensure reachability. For
 * example, because objects that are locked cannot in general be
 * reclaimed, it would suffice if all accesses of the object, in all
 * methods of class Resource (including <code>finalize</code>) were
 * enclosed in <code>synchronized(this)</code> blocks. (Further, such
 * blocks must not include infinite loops, or themselves be
 * unreachable, which fall into the corner case exceptions to the "in
 * general" disclaimer.) Method <code>reachabilityFence</code> remains
 * an option in cases where this approach is not desirable or
 * possible; for example because it would encounter deadlock.
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
     * Ensures that, within the scope of the given reference, any read
     * prior to the invocation of this method in program order
     * happens-before and is in synchronization order with, as defined
     * in The Java Language Specification section 17.4.4, any access
     * subsequent to the invocation of this method
     * See the class-level documentation for further explanation
     * and usage examples.
     * @param ref the (nonnull) reference. If null, the effects
     * of the method are undefined.
     */
    public static void postLoadFence(Object ref) {
        int ignore = theVolatile;
    }

    /**
     * Ensures that, within the scope of the given reference, any
     * access prior to the invocation of this method in program order
     * happens-before and is in synchronization order with, as
     * defined in The Java Language Specification section 17.4.4, any
     * write subsequent to the invocation of this method.
     * See the class-level documentation for further explanation
     * and usage examples.
     * @param ref the (nonnull) reference. If null, the effects
     * of the method are undefined.
     */
    public static void preStoreFence(Object ref) {
        theVolatile = 0;
    }

    /**
     * Ensures that, within the scope of the given reference, any
     * write prior to the invocation of this method in program order
     * happens-before and is in synchronization order with, as defined
     * in The Java Language Specification section 17.4.4, any access
     * subsequent to the invocation of this method.
     * See the class-level documentation for further explanation
     * and usage examples.
     * @param ref the (nonnull) reference. If null, the effects
     * of the method are undefined.
     */
    public static void postStorePreLoadFence(Object ref) {
        theVolatile = 0;
    }

    /**
     * Ensures that the object referenced by the given reference
     * remains <em>strongly reachable</em> (as defined in the {@link
     * java.lang.ref} package documentation), regardless of any prior
     * actions of the program that might otherwise cause the object to
     * become unreachable; thus, the referenced object is not
     * reclaimable by garbage collection at least until after the
     * invocation of this method. Invocation of this method does not
     * itself initiate garbage collection or finalization. See the
     * class-level documentation for further explanation and usage
     * examples.
     * 
     * @param ref the (nonnull) reference. If null, the effects
     * of the method are undefined.
     */
    public static void reachabilityFence(Object ref) {
        if (ref != null) {
            synchronized(ref) {}
        }
    }
}
