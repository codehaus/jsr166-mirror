/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

/**
 * A variant of a cyclic barrier that is advanced upon explicit
 * signals representing event occurrences.
 */
final class PoolBarrier {
    /**
     * Wait nodes for Treiber stack representing wait queue.
     */
    static final class QNode {
        QNode next;
        volatile ForkJoinWorkerThread thread; // nulled to cancel wait
        final long count;
        QNode(ForkJoinWorkerThread t, long c) {
            thread = t;
            count = c;
        }
    }

    /**
     * Head of Treiber stack. Even though this variable is very
     * busy, it is not usually heavily contended because of
     * signal/wait/release policies.
     */
    final AtomicReference<QNode> head = new AtomicReference<QNode>();

    /**
     * The event count
     */
    final AtomicLong counter = new AtomicLong();

    /**
     * Returns the current event count
     */
    long getCount() {
        return counter.get();
    }

    /**
     * Waits until event count advances from count, or some
     * other thread arrives or is already waiting with a different
     * count.
     * @param count previous value returned by sync (or 0)
     * @return current event count
     */
    long sync(ForkJoinWorkerThread thread, long count) {
        long current = counter.get();
        if (current == count) 
            enqAndWait(thread, count);
        if (head.get() != null)
            releaseAll();
        return current;
    }

    /**
     * Ensures that event count on exit is greater than event
     * count on entry, and that at least one thread waiting for
     * count to change is signalled, (It will then in turn
     * propagate other wakeups.) This lessens stalls by signallers
     * when they want to be doing something more productive.
     * However, on contention to release, wakes up all threads to
     * help forestall further contention.  Note that the counter
     * is not necessarily incremented by caller.  If attempted CAS
     * fails, then some other thread already advanced from
     * incoming value;
     */
    void signal() {
        final AtomicReference<QNode> head = this.head;
        final AtomicLong counter = this.counter;
        long c = counter.get();
        counter.compareAndSet(c, c+1); 
        QNode h = head.get();
        if (h != null) {
            ForkJoinWorkerThread t;
            if (head.compareAndSet(h, h.next) && (t = h.thread) != null) {
                h.thread = null;
                LockSupport.unpark(t);
            }
            else if (head.get() != null)
                releaseAll();
        }
    }

    /**
     * Version of signal called from pool. Forces increment and
     * releases all. It is OK if this is called by worker threads,
     * but it is heavier than necessary for them.
     */
    void poolSignal() {
        counter.incrementAndGet();
        releaseAll();
    }

    /**
     * Enqueues node and waits unless aborted or signalled.
     */
    private void enqAndWait(ForkJoinWorkerThread thread, long count) {
        QNode node = new QNode(thread, count);
        final AtomicReference<QNode> head = this.head;
        final AtomicLong counter = this.counter;
        for (;;) {
            QNode h = head.get();
            node.next = h;
            if ((h != null && h.count != count) || counter.get() != count)
                break;
            if (head.compareAndSet(h, node)) {
                while (!thread.isInterrupted() &&
                       node.thread != null &&
                       counter.get() == count)
                    LockSupport.park();
                node.thread = null;
                break;
            }
        }
    }

    /**
     * Release all waiting threads. Called on exit from sync, as
     * well as on contention in signal. Regardless of why sync'ing
     * threads exit, other waiting threads must also recheck for
     * tasks or completions before resync. Release by chopping off
     * entire list, and then signalling. This both lessens
     * contention and avoids unbounded enq/deq races.
     */
    private void releaseAll() {
        final AtomicReference<QNode> head = this.head;
        QNode p;
        while ( (p = head.get()) != null) {
            if (head.compareAndSet(p, null)) {
                do {
                    ForkJoinWorkerThread t = p.thread;
                    if (t != null) {
                        p.thread = null;
                        LockSupport.unpark(t);
                    }
                } while ((p = p.next) != null);
                break;
            }
        }
    }
}
