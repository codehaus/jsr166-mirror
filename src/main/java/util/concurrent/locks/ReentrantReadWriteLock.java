/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * An implementation of {@link ReadWriteLock} supporting similar
 * semantics to {@link ReentrantLock}.
 * <p>This class has the following properties:
 *
 * <ul>
 * <li><b>Acquisition order</b>
 *
 * <p> This class does not impose a reader or writer preference
 * ordering for lock access.  However, it does support an optional
 * <em>fairness</em> policy.  When constructed as fair, threads
 * contend for entry using an approximately arrival-order policy. When
 * the write lock is released either the longest-waiting single writer
 * will be assigned the write lock, or if there is a reader waiting
 * longer than any writer, the set of readers will be assigned the
 * read lock.  When constructed as non-fair, the order of entry to the
 * lock need not be in arrival order.  In either case, if readers are
 * active and a writer enters the lock then no subsequent readers will
 * be granted the read lock until after that writer has acquired and
 * released the write lock.
 * 
 * <li><b>Reentrancy</b>
 * <p>This lock allows both readers and writers to reacquire read or
 * write locks in the style of a {@link ReentrantLock}. Readers are not
 * allowed until all write locks held by the writing thread have been
 * released.  
 * <p>Additionally, a writer can acquire the read lock - but not vice-versa.
 * Among other applications, reentrancy can be useful when
 * write locks are held during calls or callbacks to methods that
 * perform reads under read locks. 
 * If a reader tries to acquire the write lock it will never succeed.
 * 
 * <li><b>Lock downgrading</b>
 * <p>Reentrancy also allows downgrading from the write lock to a read lock,
 * by acquiring the write lock, then the read lock and then releasing the
 * write lock. However, upgrading from a read lock to the write lock, is
 * <b>not</b> possible.
 *
 * <li><b>Interruption of lock acquisition</b>
 * <p>The read lock and write lock both support interruption during lock
 * acquisition.
 * And both view the
 * interruptible lock methods as explicit interruption points and give
 * preference to responding to the interrupt over normal or reentrant 
 * acquisition of the lock, or over reporting the elapse of the waiting
 * time, as applicable.

 *
 * <li><b>{@link Condition} support</b>
 * <p>The write lock provides a {@link Condition} implementation that
 * behaves in the same way, with respect to the write lock, as the 
 * {@link Condition} implementation provided by
 * {@link ReentrantLock#newCondition} does for {@link ReentrantLock}.
 * This {@link Condition} can, of course, only be used with the write lock.
 * <p>The read lock does not support a {@link Condition} and
 * <tt>readLock().newCondition()</tt> throws 
 * <tt>UnsupportedOperationException</tt>.
 *
 * </ul>
 *
 * <p><b>Sample usage</b>. Here is a code sketch showing how to exploit
 * reentrancy to perform lock downgrading after updating a cache (exception
 * handling is elided for simplicity):
 * <pre>
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *        // upgrade lock manually
 *        rwl.readLock().unlock();   // must unlock first to obtain writelock
 *        rwl.writeLock().lock();
 *        if (!cacheValid) { // recheck
 *          data = ...
 *          cacheValid = true;
 *        }
 *        // downgrade lock
 *        rwl.readLock().lock();  // reacquire read without giving up write lock
 *        rwl.writeLock().unlock(); // unlock write, still hold read
 *     }
 *
 *     use(data);
 *     rwl.readLock().unlock();
 *   }
 * }
 * </pre>
 * <h3>Implementation Considerations</h3>
 * <p>In addition to the above, this <em>reference implementation</em> has the
 * following property:
 * <ul>
 * <li><b>Ownership</b>
 * <p>While not exposing a means to query the owner, the write lock does
 * internally define an owner and so the write lock can only be released by
 * the thread that acquired it.
 * <p>In contrast, the read lock has no concept of ownership.
 * Consequently, while not a particularly 
 * good practice, it is possible to acquire a read lock in one thread, and 
 * release it in another. This can occasionally be useful.
 * </ul>
 *
 * @since 1.5
 * @author Doug Lea
 *
 */
public class ReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable  {

    private static final long serialVersionUID = -6992448646407690164L;

    /*
     * FIXME: This code should be refactored to share mechanics with
     * ReentrantLock class, most of which are very similar.
     */


    /** Node status value to indicate thread has cancelled */
    static final int CANCELLED =  1;
    /** Node status value to indicate successor needs unparking */
    static final int SIGNAL    = -1;
    /** Node status value to indicate thread is waiting on condition */
    static final int CONDITION = -2;
    /** Node class for waiting threads */
    static class Node {
        volatile int status;
        volatile Node prev;
        volatile Node next;
        Thread thread;
        final boolean reader;
        Node(Thread t, boolean r) { thread = t; reader = r; }
    }

    /** 
     * Read/write hold status is kept in a separate AtomicInteger. The
     * low bit (1) is set when there is a writer. The rest of the word
     * holds the number of readers, so the value increments/decrements
     * by 2 per lock/unlock.
     */
    private final AtomicInteger count = new AtomicInteger(0);
    /** Current writing thread */
    private transient Thread owner;
    /**  Head of the wait queue, lazily initialized.  */
    private transient volatile Node head;
    /**  Tail of the wait queue, lazily initialized.  */
    private transient volatile Node tail;
    /** Number of recursive write locks. Note: total holds = recursions+1 */
    private transient int recursions;
    /** true if barging disabled */
    private final boolean fair;

    /** Inner class providing readlock */
    private final Lock readerLock = new ReadLock();
    /** Inner class providing writelock */
    private final Lock writerLock = new WriteLock();


    // Atomic update support

    private static final 
        AtomicReferenceFieldUpdater<ReentrantReadWriteLock, Node> tailUpdater = 
        AtomicReferenceFieldUpdater.<ReentrantReadWriteLock, Node>newUpdater 
        (ReentrantReadWriteLock.class, Node.class, "tail");
    private static final 
        AtomicReferenceFieldUpdater<ReentrantReadWriteLock, Node> headUpdater = 
        AtomicReferenceFieldUpdater.<ReentrantReadWriteLock, Node>newUpdater 
        (ReentrantReadWriteLock.class,  Node.class, "head");
    private static final 
        AtomicIntegerFieldUpdater<Node> statusUpdater = 
        AtomicIntegerFieldUpdater.<Node>newUpdater 
        (Node.class, "status");

    /*
     * Mode words are used to handle all of the combinations of r/w
     * interrupt, timeout, etc for lock methods.  These are OR'ed
     * together as appropriate for arguments and results.
     */

    /** As arg, lock in write mode */
    static final int WRITER        =  0;
    /** As arg, lock in read mode */
    static final int READER        =  1;
    /** As arg, don't interrupt; as result, was not interrupted */
    static final int UNINTERRUPTED =  0;
    /** As arg, allow interrupt; as result, was interrupted */ 
    static final int INTERRUPT     =  2;
    /** As arg, allow timeouts; as result, did time out */
    static final int TIMEOUT       =  4;
    /** As arg, reset interrupt status upon return */
    static final int REINTERRUPT   =  8;

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * default ordering properties.
     */
    public ReentrantReadWriteLock() {
        fair = false;
    }

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * the given fairness policy.
     *
     * @param fair true if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock(boolean fair) {
        this.fair = fair;
    }

    public Lock writeLock() { 
        return writerLock; 
    }

    public Lock readLock() { 
        return readerLock; 
    }

    /**
     * The Reader lock
     */
    private class ReadLock implements Lock, java.io.Serializable  {
        private static final long serialVersionUID = -5992448646407690164L;

        public void lock() {
            if (!fastTryReadLock())
                dolock(READER | UNINTERRUPTED, 0);
        }

        public void lockInterruptibly() throws InterruptedException {
            if (dolock(READER | INTERRUPT, 0) == INTERRUPT)
                throw new InterruptedException();
        }

        public  boolean tryLock() {
            return dotry(READER, Thread.currentThread());
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            int stat = dolock(READER | INTERRUPT | TIMEOUT, 
                              unit.toNanos(timeout));
            if (stat == INTERRUPT)
                throw new InterruptedException();
            return (stat == UNINTERRUPTED);
        }

        public  void unlock() {
            runlock();
        }

        public WriterConditionObject newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The writer lock
     */
    private class WriteLock implements Lock, java.io.Serializable  {
        private static final long serialVersionUID = -4992448646407690164L;

        public void lock() {
            if (!fastTryWriteLock())
                dolock(WRITER | UNINTERRUPTED, 0);
        }

        public void lockInterruptibly() throws InterruptedException {
            if (dolock(WRITER | INTERRUPT, 0) == INTERRUPT)
                throw new InterruptedException();
        }

        public boolean tryLock( ) {
            return dotry(WRITER, Thread.currentThread());
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            int stat = dolock(WRITER | INTERRUPT | TIMEOUT, 
                              unit.toNanos(timeout));
            if (stat == INTERRUPT)
                throw new InterruptedException();
            return (stat == UNINTERRUPTED);
        }
        
        public void unlock() {
            wunlock();
        }

        public Condition newCondition() { 
            return new WriterConditionObject(ReentrantReadWriteLock.this);
        }

    }
    
    // Internal methods

    /**
     * Insert node into queue, initializing head and tail if necessary.
     * @param node the node to insert
     */
    private void enq(Node node) {
        Node t = tail;
        if (t == null) {         // Must initialize first
            Node h = new Node(null, false);
            while ((t = tail) == null) {     
                if (headUpdater.compareAndSet(this, null, h)) 
                    tail = h;
            }
        }

        for (;;) {
            node.prev = t;      // Prev field must be valid before/upon CAS
            if (tailUpdater.compareAndSet(this, t, node)) {
                t.next = node;  // Next field assignment lags CAS
                return;
            }
            t = tail;
        } 
    }

    /**
     * Unblock the successor of node, if one exists
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        statusUpdater.compareAndSet(node, SIGNAL, 0);
        Node s = node.next;
        if (s == null || s.status == CANCELLED) {
            s = tail;
            if (s != null && s != node) {
                Node p = s.prev;
                while (p != null && p != node) {
                    if (p.status != CANCELLED) 
                        s = p; 
                    p = p.prev;
                }
            }
        }
        if (s != null && s != node) 
            LockSupport.unpark(s.thread);
    }


    /**
     * Main trylock mechanics
     * @param mode mode: only READER or WRITER used
     * @param current current thread
     * @return true if successful
     */
    private boolean dotry(int mode, Thread current) {
        for (;;) {
            int c = count.get();
            if ((mode & READER) != 0) {
                if ((c & 1) != 0 && current != owner)
                    return false;
                if (count.compareAndSet(c, c+2))
                    return true;
            }
            else {
                if (c != 0) {
                    if (current != owner)
                        return false;
                    ++recursions;
                    return true;
                }
                else if (count.compareAndSet(c, 1)) {
                    owner = current;
                    return true;
                }
            }
        }
    }

    /**
     * Fast path for read locks
     * @return true if successful
     */
    final boolean fastTryReadLock() {
        if (!fair || head == tail) {
            int c = count.get();
            if ((c & 1) == 0 && count.compareAndSet(c, c+2))
                return true;
        }
        return false;
    }

    /**
     * Fast path for write locks
     * @return true if successful
     */
    final boolean fastTryWriteLock() {
        if (!fair || head == tail) {
            int c = count.get();
            if (c == 0 && count.compareAndSet(0, 1)) {
                owner = Thread.currentThread();
                return true;
            }
        }
        return false;
    }

    /**
     * Main mechanics for writeLock().unlock();
     */
    final void wunlock() {
        Thread current = Thread.currentThread();
        int c = count.get();
        if ((c & 1) == 0 || current != owner) 
            throw new IllegalMonitorStateException();
        else if (recursions > 0) 
            --recursions;
        else {
            owner = null;
            while (!count.compareAndSet(c, c-1))
                c = count.get();
            Node h = head;
            if (h != null  && h.status < 0)
                unparkSuccessor(h);
        }
    }

    /**
     * Main mechanics for readLock().unlock();
     */
    final void runlock() {
        for (;;) {
            int c = count.get();
            int nextc = c-2;
            if (nextc < 0)
                throw new IllegalMonitorStateException();
            if (count.compareAndSet(c, nextc)) {
                if (nextc <= 1) {
                    Node h = head;
                    if (h != null  && h.status < 0)
                        unparkSuccessor(h);
                }
                return;
            }
        }
    }
        

    /**
     * Main locking code.
     * @param mode mode representing r/w, interrupt, timeout control
     * @param nanos timeout time
     * @return UNINTERRUPTED on success, INTERRUPT on interrupt,
     * TIMEOUT on timeout
     */
    final int dolock(int mode, long nanos) {
        if ((mode & INTERRUPT) != 0 && Thread.interrupted())
            return INTERRUPT;
        Thread current = Thread.currentThread();
        if ((!fair || head == tail) && dotry(mode, current)) 
            return UNINTERRUPTED;
        long lastTime = ((mode & TIMEOUT) == 0)? 0 : System.nanoTime();
        Node node = new Node(current, false);
        if (!fair && dotry(mode, current)) 
            return UNINTERRUPTED;
        enq(node);

        for (;;) {
            Node p = node.prev; 
            if (p == head) {
                if (dotry(mode, current)) {
                    p.next = null; 
                    node.thread = null;
                    node.prev = null; 
                    head = node;
                    if ((mode & READER) != 0 && node.status < 0) {
                        // wake up other readers
                        Node s = node.next;
                        if (s == null || s.reader)
                            unparkSuccessor(node);
                    }

                    if ((mode & REINTERRUPT) != 0)
                        current.interrupt();
                    return UNINTERRUPTED;
                }
            }

            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p.prev;
            else { 
                assert (status == SIGNAL);
                if ((mode & TIMEOUT) != 0) {
                    if (nanos > 0) {
                        long now = System.nanoTime();
                        nanos -= now - lastTime;
                        lastTime = now;
                    }
                    if (nanos <= 0) {
                        node.thread = null;      
                        node.status = CANCELLED;
                        unparkSuccessor(node);
                        return TIMEOUT;
                    }
                    else
                        LockSupport.parkNanos(nanos);
                }
                else
                    LockSupport.park();

                if (Thread.interrupted()) {
                    if ((mode & INTERRUPT) != 0)  {  
                        node.thread = null;      
                        node.status = CANCELLED;
                        unparkSuccessor(node);
                        return INTERRUPT;
                    }
                    else
                        mode |= REINTERRUPT;
                }
            }
        }
    }

    // Instrumentation methods

    /**
     * Return true if this lock has fairness set true.
     * @return true if this lock has fairness set true.
     */
    public boolean isFair() {
        return fair;
    }

    /**
     * Queries the number of read locks held for this lock. This method is
     * designed for use in monitoring of the system state, 
     * not for synchronization control.
     * @return the number of read locks held.
     */
    public int getReadLocks() {
        return count.get() >>> 1;
    }


    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring of the system state, 
     * not for synchronization control.
     * @return <tt>true</tt> if any thread holds write lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isWriteLocked() {
        return (count.get() & 1) != 0;
    }

    /**
     * Queries if the write lock is held by the current thread. 
     * @return <tt>true</tt> if current thread holds this lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isWriteLockedByCurrentThread() {
        return (count.get() & 1) != 0 && owner == Thread.currentThread();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  <p>A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on this lock by the current thread,
     * or zero if this lock is not held by the current thread.
     */
    public int getWriteHoldCount() {
        return ((count.get() & 1) != 0 && owner == Thread.currentThread())?
            recursions + 1 :  0;
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * this lock. The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     * @return the estimated number of threads waiting for this lock
     */
    public int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null && p != head; p = p.prev)
            ++n;
        return n;
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * <tt>null</tt> if not owned. Note that the owner may be
     * momentarily <tt>null</tt> even if there are threads trying to
     * acquire the lock but have not yet done so.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the owner, or <tt>null</tt> if not owned.
     */
    protected Thread getWriter() {
        return ((count.get() & 1) != 0)? owner : null;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire this lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.reader) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }


    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.reader) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    // Serialization support

    /**
     * Reconstitute this lock instance from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        // reset to unlocked state
        count.set(0);
    }


    // Helper methods for Conditions

    /**
     * Throw IllegalMonitorStateException if given thread is not owner
     * @param thread the thread
     * @throws IllegalMonitorStateException if thread not owner
     */
    final void checkOwner(Thread thread) {
        if ((count.get() & 1) == 0 || owner != thread) 
            throw new IllegalMonitorStateException();
    }

    /**
     * Variant of unlock used by condition wait. 
     * Fully unlocks, setting recursions to zero.
     * @return current recursion count.
     */
    final int unlockForWait() {
        int recs = recursions;
        recursions = 0;
        wunlock();
        return recs;
    }

    /**
     * Re-acquire lock after a wait, resetting recursion count
     * and propagating interrupt status for reset control. 
     * @param current the waiting thread
     * @param node its node
     * @param recs number of recursive holds on lock before entering wait
     * @param interrupts interrupt control upon acquiring lock
     */
    final void relockAfterWait(Thread current, 
                               Node node, 
                               int recs,
                               int interrupts) {
        for (;;) {
            Node p = node.prev; 
            if (p == head) {
                if (dotry(WRITER, current)) {
                    recursions = recs;
                    p.next = null; 
                    node.thread = null;
                    node.prev = null; 
                    head = node;
                    if (interrupts > 0)
                        current.interrupt();
                    return;
                }
            }

            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p.prev;
            else { 
                LockSupport.park();
                if (Thread.interrupted()) 
                    interrupts = 1;
            }
        }
    }

    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is now on the lock queue.
     * @param node the node
     * @return true if on lock queue
     */
    final boolean isOnLockQueue(Node node) {
        if (node.status == CONDITION || node.prev == null)
            return false;
        // If node has successor, it must be on lock queue
        if (node.next != null) 
            return true;
        /*
         * node.prev can be non-null, but not yet on lock queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        Node t = tail; 
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    } 

    /**
     * Transfer a node from a condition queue onto lock queue. 
     * Return true if successful.
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal).
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change status, the node has been cancelled.
         */
        if (!statusUpdater.compareAndSet(node, CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set status of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set status fails, wake up to resynch (in which
         * case the status can be transiently/harmlessly wrong).
         */
        enq(node);
        Node p = node.prev;
        int c = p.status;
        if (c == CANCELLED || !statusUpdater.compareAndSet(p, c, SIGNAL))
            LockSupport.unpark(node.thread);

        return true;
    }

    /**
     * Re-acquire lock after a cancelled wait. Return true if thread
     * was cancelled before being signalled.
     * @param current the waiting thread
     * @param node its node
     * @param recs number of recursive holds on lock before entering wait
     * @return true if cancelled before the node was signalled.
     */
    final boolean relockAfterCancelledWait(Thread current, 
                                           Node node, 
                                           int recs) {
        boolean isCancelled = statusUpdater.compareAndSet(node, CONDITION, 0);
        if (isCancelled) // place on lock queue
            enq(node);
        else {
            /*
             * If we lost out to a signal(), then we can't proceed
             * until it finishes its enq().  Cancelling during an
             * incomplete transfer is both rare and transient, so just
             * spin.
             */
            while (!isOnLockQueue(node)) 
                Thread.yield();
        }
        relockAfterWait(current, node, recs, 0);
        return isCancelled;
    }

    /**
     * Condition implementation for use with <tt>ReentrantReadWriteLock</tt>.
     * 
     * <p>This class supports the same basic semantics and styles of
     * usage as the {@link Object} monitor methods.  Methods may be
     * invoked only when holding the write lock of the 
     * <tt>ReentrantReadWriteLock</tt> associated with this
     * Condition. Failure to comply results in {@link
     * IllegalMonitorStateException}.
     *
     * <p>In addition to implementing the {@link Condition} interface,
     * this class defines methods <tt>hasWaiters</tt> and
     * <tt>getWaitQueueLength</tt>, as well as some associated
     * <tt>protected</tt> access methods, that may be useful for
     * instrumentation and monitoring.
     */

    public static class WriterConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 2173984872572414699L;

        /**
         * Node class for conditions. Because condition queues are
         * accessed only when locks are already held, we just need a
         * simple linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the lock queue to
         * re-acquire locks.
         */
        static class WriterConditionNode extends Node {
            /** Link to next node waiting on condition. */
            transient WriterConditionNode nextWaiter;
            
            WriterConditionNode(Thread t) { 
                super(t, false); 
                status = CONDITION;
            }
        }

        /** The lock we are serving as a condition for. */
        private final ReentrantReadWriteLock lock;
        /** First node of condition queue. */
        private transient WriterConditionNode firstWaiter;
        /** Last node of condition queue. */
        private transient WriterConditionNode lastWaiter;

        /**
         * Constructor for use by subclasses to create a
         * WriterConditionObject associated with given lock. 
         * @param lock the lock for this condition
         * @throws NullPointerException if lock null
         */
        protected WriterConditionObject(ReentrantReadWriteLock lock) {
            if (lock == null)
                throw new NullPointerException();
            this.lock = lock;
        }

        // Internal methods

        /**
         * Add a new waiter to wait queue
         * @param current the thread that will be waiting
         * @return its new wait node
         */
        private WriterConditionNode addConditionWaiter(Thread current) {
            WriterConditionNode w = new WriterConditionNode(current);
            WriterConditionNode t = lastWaiter;
            if (t == null) 
                firstWaiter = w;
            else 
                t.nextWaiter = w;
            lastWaiter = w;
            return w;
        }

        /**
         * Remove and transfer nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(WriterConditionNode first) {
            do {
                if ( (firstWaiter = first.nextWaiter) == null) 
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!lock.transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Remove and transfer all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(WriterConditionNode first) {
            lastWaiter = firstWaiter  = null;
            do {
                WriterConditionNode next = first.nextWaiter;
                first.nextWaiter = null;
                lock.transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /*
         * Cancellation support for await() etc. When a cancellation
         * (interrupt or timeout) and a notification (signal or
         * signalAll) occur at about the same time, the signaller
         * cannot tell for sure that it should not perform the queue
         * transfer when it actually should instead let the
         * cancellation continue. So the signaller will blindly try
         * the transfer. To compensate for this, a cancelling thread
         * must, according to the (draft) JSR-133 spec, ensure
         * notification of some other thread that was waiting when
         * signal() made this mistake (this kind of mistake is rare
         * but inevitable because of intrinsic races detecting
         * cancellation vs queue transfer). Since we give preference
         * to cancellation over normal returns, a cancelling thread
         * that has already been notified performs the equivalent of a
         * signal(). Because our condition queues are FIFO, the first
         * waiter (if one exists) may be one that had been waiting at
         * the point of the lost signal/cancel race (or not, in which
         * case the wakeup is spurious). But no other thread could
         * have already been waiting if the first one wasn't, so
         * a signal((), as opposed to a signalAll(), suffices.
         *
         * This is done only when the cancelling thread has reacquired
         * the lock, in the following two methods dealing with the two
         * kinds of circumstances in which this race can occur: In
         * method cancelAndRelock, when a thread knows that it has
         * cancelled before reacquiring lock and in method
         * checkInterruptAfterRelock, when it notices that it has been
         * interrupted after reacquiring lock, but hadn't noticed
         * before relocking.
         */

        /**
         * Cancel a wait due to interrupt or timeout: reacquire lock
         * and handle possible cancellation race.
         * @param current the waiting thread
         * @param node its node
         * @param recs number of recursive holds on lock before entering wait
         */
        private void cancelAndRelock(Thread current, 
                                     WriterConditionNode node, 
                                     int recs) {
            if (!lock.relockAfterCancelledWait(current, node, recs)) {
                WriterConditionNode w = firstWaiter;
                if (w != null) 
                    doSignal(w);
            }
        }

        /**
         * Check for interruption upon return from a wait, throwing
         * InterruptedException and handling possible cancellation
         * race if interrupted.
         */
        private void checkInterruptAfterRelock() throws InterruptedException {
            if (Thread.interrupted()) {
                WriterConditionNode w = firstWaiter;
                if (w != null) 
                    doSignal(w);
                throw new InterruptedException();
            }
        }

        // public methods

        /**
         * Wakes up one waiting thread.
         *
         * <p>If any threads are waiting on this condition then one is
         * selected for waking up.  This implementation always chooses
         * to wake up the longest-waiting thread whose wait has not
         * been interrupted or timed out.  That thread must then
         * re-acquire the lock before it returns. The order in which
         * it will do so is the same as that for threads initially
         * acquiring the lock, which is in the default case not
         * specified, but for <em>fair</em> locks favors those threads
         * that have been waiting the longest. Note that an awakened
         * thread can return, at the soonest, only after the current
         * thread releases the lock associated with this Condition.
         * 
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         **/
        public void signal() {
            lock.checkOwner(Thread.currentThread());
            WriterConditionNode w = firstWaiter;
            if (w != null)
                doSignal(w);
        }
         
        /**
         * Wake up all waiting threads.
         *
         * <p>If any threads are waiting on this condition then they
         * are all woken up. Each thread must re-acquire the lock
         * before it returns.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         */
        public void signalAll() {
            lock.checkOwner(Thread.currentThread());
            WriterConditionNode w = firstWaiter;
            if (w != null) 
                doSignalAll(w);
        }

        /*
         * Various flavors of wait. Each almost the same, but
         * annoyingly different.
         */


        /**
         * Causes the current thread to wait until it is signalled.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * three things happens: 
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>; or
         *
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
         *
         * </ul>
         *
         * <p>In all cases, before this method can return, the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread's interrupt status is set when it
         * enters this method, or it is {@link Thread#interrupt
         * interrupted} while waiting, it will continue to wait until
         * signalled. When it finally returns from this method its
         * <em>interrupted status</em> will still be set.
         * 
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         */
        public void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            WriterConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();
            int interrupted = 0;
            while (!lock.isOnLockQueue(w)) {
                LockSupport.park();
                if (Thread.interrupted()) 
                    interrupted = 1;
            }
            lock.relockAfterWait(current, w, recs, interrupted);
        }

        /**
         * Causes the current thread to wait until it is signalled or
         * {@link Thread#interrupt interrupted}.
         *
         * <p>The lock associated with this <tt>Condition</tt> is
         * atomically released and the current thread becomes disabled
         * for thread scheduling purposes and lies dormant until
         * <em>one</em> of four things happens:
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
         *
         * </ul>
         *
         * <p>In all cases, before this method can return the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread has its interrupted status set on
         * entry to this method or is {@link Thread#interrupt
         * interrupted} while waiting, then {@link
         * InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.  This implementation favors
         * responding to an interrupt over normal method return in
         * response to a signal.
         *
         * @throws InterruptedException if the current thread is
         * interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         **/
        public void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            WriterConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();

            for (;;) {
                if (Thread.interrupted()) {
                    cancelAndRelock(current, w, recs);
                    throw new InterruptedException();
                }
                if (lock.isOnLockQueue(w)) {
                    lock.relockAfterWait(current, w, recs, 0);
                    checkInterruptAfterRelock();
                    return;
                }
                LockSupport.park();
            }
        }

        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified waiting time elapses.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * five things happens:
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>The specified waiting time elapses; or
         *
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs.
         *
         * </ul>
         *
         * <p>In all cases, before this method can return the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread has its interrupted status set on
         * entry to this method or is {@link Thread#interrupt
         * interrupted} while waiting, then {@link
         * InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.  This implementation favors
         * responding to an interrupt over normal method return in
         * response to a signal or timeout.
         *
         * <p>The method returns an estimate of the number of nanoseconds
         * remaining to wait given the supplied <tt>nanosTimeout</tt>
         * value upon return, or a value less than or equal to zero if it
         * timed out. This value can be used to determine whether and how
         * long to re-wait in cases where the wait returns but an awaited
         * condition still does not hold. 
         *
         * @param nanosTimeout the maximum time to wait, in nanoseconds
         * @return A value less than or equal to zero if the wait has
         * timed out; otherwise an estimate, that
         * is strictly less than the <tt>nanosTimeout</tt> argument,
         * of the time still remaining when this method returned.
         *
         * @throws InterruptedException if the current thread is
         * interrupted.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         */
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            WriterConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();
            long lastTime = System.nanoTime();

            for (;;) {
                if (Thread.interrupted()) {
                    cancelAndRelock(current, w, recs);
                    throw new InterruptedException();
                }
                if (nanosTimeout <= 0L) {
                    cancelAndRelock(current, w, recs);
                    return nanosTimeout;
                }
                if (lock.isOnLockQueue(w)) {
                    lock.relockAfterWait(current, w, recs, 0);
                    checkInterruptAfterRelock();
                    // We could have sat in lock queue a while, so
                    // recompute time left on way out.
                    return nanosTimeout - (System.nanoTime() - lastTime);
                }
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }
        }


        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified deadline elapses.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * five things happens:
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>The specified deadline elapses; or
         *
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs.
         *
         * </ul>
         *
         * <p>In all cases, before this method can return the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread has its interrupted status set on
         * entry to this method or is {@link Thread#interrupt
         * interrupted} while waiting, then {@link
         * InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.  This implementation favors
         * responding to an interrupt over normal method return in
         * response to a signal or timeout.
         *
         * @param deadline the absolute time to wait until
         * @return <tt>false</tt> if the deadline has
         * elapsed upon return, else <tt>true</tt>.
         *
         * @throws InterruptedException if the current thread is interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         * @throws NullPointerException if deadline is null
         */
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            if (deadline == null)
                throw new NullPointerException();
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            WriterConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();
            long abstime = deadline.getTime();
            
            for (;;) {
                if (Thread.interrupted()) {
                    cancelAndRelock(current, w, recs);
                    throw new InterruptedException();
                }
                if (System.currentTimeMillis() > abstime) {
                    cancelAndRelock(current, w, recs);
                    return false;
                }
                if (lock.isOnLockQueue(w)) {
                    lock.relockAfterWait(current, w, recs, 0);
                    checkInterruptAfterRelock();
                    return true;
                }
                LockSupport.parkUntil(abstime);
            }
        }
        
        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified waiting time elapses. This
         * method is behaviorally equivalent to:<br>
         *
         * <pre>
         *   awaitNanos(unit.toNanos(time)) &gt; 0
         * </pre>
         *
         * @param time the maximum time to wait
         * @param unit the time unit of the <tt>time</tt> argument.
         * @return <tt>false</tt> if the waiting time detectably
         * elapsed before return from the method, else <tt>true</tt>.
         * @throws InterruptedException if the current thread is
         * interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         * @throws NullPointerException if unit is null
         */
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            return awaitNanos(unit.toNanos(time)) > 0L;
        }

        /**
         * Queries whether any threads are waiting on this
         * condition. Note that because timeouts and interrupts may
         * occur at any time, a <tt>true</tt> return does not
         * guarantee that a future <tt>signal</tt> will awaken any
         * threads.  This method is designed primarily for use in
         * monitoring of the system state.
         * @return <tt>true</tt> if there are any waiting threads.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         */ 
        public boolean hasWaiters() {
            lock.checkOwner(Thread.currentThread());
            for (WriterConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition. Note that because timeouts and interrupts
         * may occur at any time, the estimate serves only as an upper
         * bound on the actual number of waiters.  This method is
         * designed for use in monitoring of the system state, not for
         * synchronization control.
         * @return the estimated number of waiting threads.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         */ 
        public int getWaitQueueLength() {
            lock.checkOwner(Thread.currentThread());
            int n = 0;
            for (WriterConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.  Because the actual set of
         * threads may change dynamically while constructing this
         * result, the returned collection is only a best-effort
         * estimate. The elements of the returned collection are in no
         * particular order.  This method is designed to facilitate
         * construction of subclasses that provide more extensive
         * condition monitoring facilities.
         * @return the collection of threads
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         */
        protected Collection<Thread> getWaitingThreads() {
            lock.checkOwner(Thread.currentThread());
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (WriterConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

}

