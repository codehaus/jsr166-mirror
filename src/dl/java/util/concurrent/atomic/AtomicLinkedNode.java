package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicLinkedNode maintains item and next fields that can each be updated atomically.
 **/
public class AtomicLinkedNode implements java.io.Serializable {
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final long itemOffset;
    private static final long nextOffset;

    static {
      try {
        itemOffset =
          unsafe.objectFieldOffset(AtomicLinkedNode.class.getDeclaredField("item"));
        nextOffset =
          unsafe.objectFieldOffset(AtomicLinkedNode.class.getDeclaredField("next"));

      }
      catch(Exception ex) { throw new Error(ex); }
    }

    private volatile Object item;
    private volatile AtomicLinkedNode next;

    /**
     * Create a new AtomicLinkedNode with the given initial values.
     **/
    public AtomicLinkedNode(Object initialItem, AtomicLinkedNode initialNext) {
        item = initialItem;
        next = initialNext;
    }

    /**
     * Create a new AtomicLinkedNode with null item and next values.
     **/
    public AtomicLinkedNode() { }

    /**
     * Get the current value of item
     **/
    public final Object getItem() {
        return item;
    }

    /**
     * Get the current value of next
     **/
    public final AtomicLinkedNode getNext() {
        return next;
    }

    /**
     * Set to the given item.
     **/
    public final void setItem(Object newItem) {
        item = newItem;
    }

    /**
     * Set to the given next.
     **/
    public final void setNext(AtomicLinkedNode newNext) {
        next = newNext;
    }

    /**
     * Atomically set the item field to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful.
     **/
    public final boolean attemptUpdateItem(Object expect, Object update) {
      return unsafe.compareAndSwapObject(this, itemOffset, expect, update);
    }

    /**
     * Atomically set the next field to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @return true if successful.
     **/
    public final boolean attemptUpdateNext(AtomicLinkedNode expect,
                                           AtomicLinkedNode update) {
      return unsafe.compareAndSwapObject(this, nextOffset, expect, update);
    }
}
