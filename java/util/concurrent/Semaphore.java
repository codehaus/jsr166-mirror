package java.util.concurrent;

/**
 * A counting semaphore.  Conceptually, a semaphore maintains a set of
 * permits.  Each acquire() blocks if necessary until a permit is
 * available, and then takes it.  Each release adds a permit. However,
 * no actual permit objects are used; the Semaphore just keeps a count
 * of the number available and acts accordingly.  <p>
 *
 * This class makes no guarantees about the order in which waiting threads
 * acquire semaphores. If you need deterministic guarantees, consider
 * using <tt>FifoSemaphore</tt>
 *
 * <p>
 * A semaphore initialized to 1 can serve as a mutual exclusion
 * lock. 
 * <p>
 * <b>Sample usage.</b> Here is a class that uses a semaphore to
 * help manage access to a pool of items.
 * <pre>
 * class Pool {
 *   static final MAX_AVAILABLE = 100;
 *   private final Semaphore available = new Semaphore(MAX_AVAILABLE);
 *   
 *   public Object getItem() throws InterruptedException { // no synch
 *     available.acquire();
 *     return getNextAvailableItem();
 *   }
 *
 *   public void putItem(Object x) { // no synch
 *     if (markAsUnused(x))
 *       available.release();
 *   }
 *
 *   // Not a particularly efficient data structure; just for demo
 *
 *   protected Object[] items = ... whatever kinds of items being managed
 *   protected boolean[] used = new boolean[MAX_AVAILABLE];
 *
 *   protected synchronized Object getNextAvailableItem() { 
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (!used[i]) {
 *          used[i] = true;
 *          return items[i];
 *       }
 *     }
 *     return null; // not reached 
 *   }
 *
 *   protected synchronized boolean markAsUnused(Object item) { 
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (item == items[i]) {
 *          if (used[i]) {
 *            used[i] = false;
 *            return true;
 *          }
 *          else
 *            return false;
 *       }
 *     }
 *     return false;
 *   }
 *
 * }
 *</pre>
 * 
 **/
public class Semaphore {
    public Semaphore(long initialCount) {}
    public void acquire()  throws InterruptedException {}

    public boolean tryAcquire() {
        return false;
    }
    public boolean tryAcquire(long timeout, TimeUnit granularity) throws InterruptedException {
        return false;
    }
    public void release() {}

    /**
     * Return the current count value of the semaphore.
     **/
    public long getCount() {
        return 0;
    }
}
