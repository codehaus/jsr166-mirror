/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.atomic;
import java.lang.reflect.*;

/**
 * A set of methods providing fine-grained control over happens-before
 * and synchronization order relations among reads and/or writes to
 * variables.
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
 * <p><b>Memory Ordering.</b> There are three methods for controlling
 * ordering relations among memory accesses (i.e., reads and
 * writes). Method {@code orderWrites} is normally used to enforce
 * order between two writes, and {@code orderAccesses} between a write
 * and a read.  Method {@code orderReads} is used to enforce order
 * between two reads with respect to other {@code orderWrites} and/or
 * {@code orderAccesses} invocations.  Each method accepts and returns
 * a {@code ref} argument.  Effects are specified in terms of accesses
 * to any field (or if an array, array element) of the denoted object
 * using {@code ref}. These methods specify relationships among
 * accesses, not the accesses themselves.  Invocations must be placed
 * <em>between</em> accesses performed in expression evaluations and
 * assignment statements to control the orderings of prior versus
 * subsequent accesses appearing in program order. (As illustrated
 * below, these methods return their arguments to simplify correct
 * usage in these contexts.)  The methods provide platform-independent
 * ordering guarantees that are honored by all levels of a platform
 * (compilers, systems, processors).  The use of these methods may
 * result in the suppression of otherwise valid compiler
 * transformations and optimizations that could visibly violate the
 * specified orderings, and may or may not entail the use of
 * processor-level "memory barrier" instructions.
 *
 * <p><b>Ordering Semantics.</b> Informal descriptions of ordering
 * methods are provided in method specifications and the usage
 * examples below.  More formally, using the terminology of The Java
 * Language Specification chapter 17, the two rules governing their
 * semantics are as follows:
 *
 * <ol>
 *
 *   <li>Every invocation of {@code orderAccesses} is an element of the
 *   <em>synchronization order</em>.
 *
 *   <li>Given:
 *
 *   <ul COMPACT>
 *     
 *     <li><em>ref</em>, a reference to an object,
 *
 *     <li><em>fw</em>, an invocation of {@code orderWrites(ref)} or
 *       {@code orderAccesses(ref)}, 
 *
 *     <li><em>w</em>, a write to a field or element of the object denoted
 *       by <em>ref</em>, where <em>fw</em> precedes <em>w</em> in program
 *       order, 
 *
 *     <li> <em>r</em>, a read that sees write <em>w</em>,
 *
 *     <li> <em>fr</em>, taking either of two forms:
 *
 *      <ul COMPACT> 
 *
 *       <li> an invocation of {@code orderReads(ref)} or {@code
 *        orderAccesses(ref)} following <em>r</em> in program order, or
 *
 *       <li> if <em>r</em> is the initial read of ref by thread
 *        <em>t</em>, a program point following <em>r</em> but prior to
 *        any other read by <em>t</em>.
 *
 *      </ul>
 *
 *   </ul> 
 *
 *    then:
 *
 *   <ul COMPACT> 
 *
 *     <li> <em>fw happens-before fr</em> and,
 *
 *     <li> <em>fw</em> precedes <em>fr</em> in the 
 *          <em>synchronization order</em>.
 *
 *   </ul>
 *
 * </ol>
 *
 * <p><b>Reachability.</b> Method {@code reachabilityFence}
 * establishes an ordering for strong reachability (as defined in the
 * {@link java.lang.ref} package specification) with respect to
 * garbage collection.  Method {@code reachabilityFence} differs from
 * the others in that it controls relations that are otherwise only
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
 * synchronization (locking).  
 *
 * <p>Notice that because {@code final} could not be used here, the
 * compiler and JVM cannot help you ensure that the field is set
 * correctly across all usages.  Initialization sequences using {@code
 * orderWrites} may require more care than those involving {@code
 * final} fields: You must fully initialize objects <em>before</em>
 * the {@code orderWrites} invocation that makes references to them
 * safe to assign to accessible variables.
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
 *     this.widget = Fences.orderWrites(w);
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
 * 
 *
 * <p><b>Emulating {@code volatile} access.</b> Outside of the
 * initialization idioms illustrated above, Fence methods ordering
 * writes must be paired with those ordering reads.  Suppose there is
 * an accessible variable that should have been declared as {@code
 * volatile} but wasn't:
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
 * own thread-safety and synchronization.  Here, an acquiring read
 * operation remains the same as a volatile-read, but a releasing
 * write differs by virtue of not itself ensuring an ordering of its
 * write with subsequent reads, because the required effects are
 * already ensured by the referenced objects.  
 * For example:
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
 * Because this construction avoids use of {@code orderAccesses},
 * which is typically more costly than the other fence methods, it may
 * result in better performance than using {@code volatile} or its
 * emulation. However, as is the case with most applications of fence
 * methods, correctness relies on the usage context -- here, the
 * thread safety of {@code Item}, as well as the lack of need for full
 * volatile semantics inside this class itself. However, the second
 * concern means that can be difficult to extend the {@code
 * ItemHolder} class in this example to be more useful.
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
 *     ExternalResource ext = externalResourceArray[myIndex];
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
     * Informally: Ensures that reads prior to the invocation of this
     * method occur before subsequent reads, relative to other
     * invocations of {@link #orderWrites} and/or {@link
     * #orderAccesses} for the given reference. For details, see the
     * class documentation for this class.
     *
     * @param ref the reference. If null, this method has no effect.
     * @return a reference to the same object as ref
     */
    public static <T> T orderReads(T ref) {
        int ignore = theVolatile;
        return ref;
    }

    /**
     * Informally: Ensures that accesses (reads or writes) of the
     * fields (or if an array, elements) of the denoted object using
     * the given reference prior to the invocation of this method
     * occur before subsequent writes. For details, see the class
     * documentation for this class.
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
     * Informally: Ensures that accesses (reads or writes) of the
     * fields (or if an array, elements) of the denoted object using
     * the given reference prior to the invocation of this method
     * occur before subsequent accesses.  For details, see the class
     * documentation for this class.
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
