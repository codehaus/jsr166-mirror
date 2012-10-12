/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * A capability-based lock with three modes for controlling read/write
 * access.  The state of a StampedLock consists of a version and
 * mode. Lock acquisition methods return a stamp that represents and
 * controls access with respect to a lock state; "try" versions of
 * these methods may instead return the special value zero to
 * represent failure to acquire access. Lock release and conversion
 * methods require stamps as arguments, and fail if they do not match
 * the state of the lock. The three modes are:
 *
 * <ul>
 *
 *  <li><b>Writing.</b> Method {@link #writeLock} possibly blocks
 *   waiting for exclusive access, returning a stamp that can be used
 *   in method {@link #unlockWrite} to release the lock. Untimed and
 *   timed versions of {@code tryWriteLock} are also provided. When
 *   the lock is held in write mode, no read locks may be obtained,
 *   and all observer validations will fail.  </li>
 *
 *  <li><b>Reading.</b> Method {@link #readLock} possibly blocks
 *   waiting for non-exclusive access, returning a stamp that can be
 *   used in method {@link #unlockRead} to release the lock. Untimed
 *   and timed versions of {@code tryReadLock} are also provided. </li>
 *
 *  <li><b>Optimistic Reading.</b> Method {@link #tryOptimisticRead}
 *   returns a non-zero stamp only if the lock is not currently held
 *   in write mode. Method {@link #validate} returns true if the lock
 *   has not since been acquired in write mode. This mode can be
 *   thought of as an extremely weak version of a read-lock, that can
 *   be broken by a writer at any time.  The use of optimistic mode
 *   for short read-only code segments often reduces contention and
 *   improves throughput.  However, its use is inherently fragile.
 *   Optimistic read sections should only read fields and hold them in
 *   local variables for later use after validation. Fields read while
 *   in optimistic mode may be wildly inconsistent, so usage applies
 *   only when you are familiar enough with data representations to
 *   check consistency and/or repeatedly invoke method {@code
 *   validate()}.  For example, such steps are typically required when
 *   first reading an object or array reference, and then accessing
 *   one of its fields, elements or methods. </li>
 *
 * </ul>
 *
 * <p>This class also supports methods that conditionally provide
 * conversions across the three modes. For example, method {@link
 * #tryConvertToWriteLock} attempts to "upgrade" a mode, returning
 * valid write stamp if (1) already in writing mode (2) in reading
 * mode and there are no other readers or (3) in optimistic mode and
 * the lock is available. The forms of these methods are designed to
 * help reduce some of the code bloat that otherwise occurs in
 * retry-based designs.
 *
 * <p>StampedLocks are designed for use in a different (and generally
 * narrower) range of contexts than most other locks: They are not
 * reentrant, so locked bodies should not call other unknown methods
 * that may try to re-acquire locks (although you may pass a stamp to
 * other methods that can use or convert it). Unvalidated optimistic
 * read sections should further not call methods that are not known to
 * tolerate potential inconsistencies.  Stamps use finite
 * representations, and are not cryptographically secure (i.e., a
 * valid stamp may be guessable). Stamp values may recycle after (no
 * sooner than) one year of continuous operation. A stamp held without
 * use or validation for longer than this period may fail to validate
 * correctly.  StampedLocks are serializable, but always deserialize
 * into initial unlocked state, so they are not useful for remote
 * locking.
 *
 * <p>The scheduling policy of StampedLock does not consistently prefer
 * readers over writers or vice versa.
 *
 * <p><b>Sample Usage.</b> The following illustrates some usage idioms
 * in a class that maintains simple two-dimensional points. The sample
 * code illustrates some try/catch conventions even though they are
 * not strictly needed here because no exceptions can occur in their
 * bodies.<br>
 *
 *  <pre>{@code
 * class Point {
 *   private volatile double x, y;
 *   private final StampedLock sl = new StampedLock();
 *
 *   void move(double deltaX, double deltaY) { // an exclusively locked method
 *     long stamp = sl.writeLock();
 *     try {
 *       x += deltaX;
 *       y += deltaY;
 *     } finally {
 *       sl.unlockWrite(stamp);
 *     }
 *   }
 *
 *   double distanceFromOriginV1() { // A read-only method
 *     long stamp;
 *     if ((stamp = sl.tryOptimisticRead()) != 0L) { // optimistic
 *       double currentX = x;
 *       double currentY = y;
 *       if (sl.validate(stamp))
 *         return Math.sqrt(currentX * currentX + currentY * currentY);
 *     }
 *     stamp = sl.readLock(); // fall back to read lock
 *     try {
 *       double currentX = x;
 *       double currentY = y;
 *         return Math.sqrt(currentX * currentX + currentY * currentY);
 *     } finally {
 *       sl.unlockRead(stamp);
 *     }
 *   }
 *
 *   double distanceFromOriginV2() { // combines code paths
 *     for (long stamp = sl.optimisticRead(); ; stamp = sl.readLock()) {
 *       double currentX, currentY;
 *       try {
 *         currentX = x;
 *         currentY = y;
 *       } finally {
 *         if (sl.tryConvertToOptimisticRead(stamp) != 0L) // unlock or validate
 *           return Math.sqrt(currentX * currentX + currentY * currentY);
 *       }
 *     }
 *   }
 *
 *   void moveIfAtOrigin(double newX, double newY) { // upgrade
 *     // Could instead start with optimistic, not read mode
 *     long stamp = sl.readLock();
 *     try {
 *       while (x == 0.0 && y == 0.0) {
 *         long ws = tryConvertToWriteLock(stamp);
 *         if (ws != 0L) {
 *           stamp = ws;
 *           x = newX;
 *           y = newY;
 *           break;
 *         }
 *         else {
 *           sl.unlockRead(stamp);
 *           stamp = sl.writeLock();
 *         }
 *       }
 *     } finally {
 *        sl.unlock(stamp);
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.8
 * @author Doug Lea
 */
public class StampedLock implements java.io.Serializable {
    /*
     * Algorithmic notes:
     *
     * The design employs elements of Sequence locks
     * (as used in linux kernels; see Lameter's
     * http://www.lameter.com/gelato2005.pdf
     * and elsewhere; see
     * Boehm's http://www.hpl.hp.com/techreports/2012/HPL-2012-68.html)
     * Ordered RW locks (see Shirako et al
     * http://dl.acm.org/citation.cfm?id=2312015)
     * and Phase-Fair locks (see Brandenburg & Anderson, especially
     * http://www.cs.unc.edu/~bbb/diss/).
     *
     * Conceptually, the primary state of the lock includes a sequence
     * number that is odd when write-locked and even otherwise.
     * However, this is offset by a reader count that is non-zero when
     * read-locked.  The read count is ignored when validating
     * "optimistic" seqlock-reader-style stamps.  Because we must use
     * a small finite number of bits (currently 7) for readers, a
     * supplementatry reader overflow word is used when then number of
     * readers exceeds the count field. We do this by treating the max
     * reader count value (RBITS) as a spinlock protecting overflow
     * updates.
     *
     * Waiting readers and writers use different queues. The writer
     * queue is a modified form of CLH lock.  (For discussion of CLH,
     * see the internal documentation of AbstractQueuedSynchronizer.)
     * The reader "queue" is a form of Treiber stack, that supports
     * simpler/faster operations because order within a queue doesn't
     * matter and all are signalled at once.  However the sequence of
     * threads within the queue vs the current stamp does matter (see
     * Shirako et al) so each carries its incoming stamp value.
     * Waiting writers never need to track sequence values, so they
     * don't.
     *
     * These queue mechanics hardwire the scheduling policy.  Ignoring
     * trylocks, cancellation, and spinning, they implement Phase-Fair
     * preferences:
     *   1. Unlocked writers prefer to signal waiting readers
     *   2. Fully unlocked readers prefer to signal waiting writers
     *   3. When read-locked and a waiting writer exists, the writer
     *      is preferred to incoming readers
     *
     * These rules apply to threads actually queued. All tryLock forms
     * opportunistically try to acquire locks regardless of preference
     * rules, and so may "barge" their way in.  Additionally, initial
     * phases of the await* methods (invoked from readLock() and
     * writeLock()) use controlled spins that have similar effect.
     * Phase-fair preferences may also be broken on cancellations due
     * to timeouts and interrupts.  Rule #3 (incoming readers when a
     * waiting writer) is approximated with varying precision in
     * different contexts -- some checks do not account for
     * in-progress spins/signals, and others do not account for
     * cancellations.
     *
     * As noted in Boehm's paper (above), sequence validation (mainly
     * method validate()) requires stricter ordering rules than apply
     * to normal volatile reads (of "state").  In the absence of (but
     * continual hope for) explicit JVM support of intrinsics with
     * double-sided reordering prohibition, or corresponding fence
     * intrinsics, we for now uncomfortably rely on the fact that the
     * Unsafe.getXVolatile intrinsic must have this property
     * (syntactic volatile reads do not) for internal purposes anyway,
     * even though it is not documented.
     *
     * The memory layout keeps lock state and queue pointers together
     * (normally on the same cache line). This usually works well for
     * read-mostly loads. In most other cases, the natural tendency of
     * adaptive-spin CLH locks to reduce memory contention lessens
     * motivation to further spread out contended locations, but might
     * be subject to future improvements.
     */

    /** Number of processors, for spin control */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /** Maximum number of retries before blocking on acquisition */
    private static final int SPINS = NCPU > 1 ? 1 << 6 : 1;

    /** Maximum number of retries before re-blocking on write lock */
    private static final int MAX_HEAD_SPINS = NCPU > 1 ? 1 << 12 : 1;

    /** The period for yielding when waiting for overflow spinlock */
    private static final int OVERFLOW_YIELD_RATE = 7; // must be power 2 - 1

    /** The number of bits to use for reader count before overflowing */
    private static final int  LG_READERS = 7;

    // Values for lock state and stamp operations
    private static final long RUNIT = 1L;
    private static final long WBIT  = 1L << LG_READERS;
    private static final long RBITS = WBIT - 1L;
    private static final long RFULL = RBITS - 1L;
    private static final long ABITS = RBITS | WBIT;
    private static final long SBITS = ~RBITS; // note overlap with ABITS

    // Initial value for lock state; avoid failure value zero
    private static final long ORIGIN = WBIT << 1;

    // Special value from cancelled await methods so caller can throw IE
    private static final long INTERRUPTED = 1L;

    // Values for writer status; order matters
    private static final int WAITING   = -1;
    private static final int CANCELLED =  1;

    /** Wait nodes for readers */
    static final class RNode {
        final long seq;         // stamp value upon enqueue
        volatile Thread waiter; // null if no longer waiting
        volatile RNode next;
        RNode(long s, Thread w) { seq = s; waiter = w; }
    }

    /** Wait nodes for writers */
    static final class WNode {
        volatile int status;   // 0, WAITING, or CANCELLED
        volatile WNode prev;
        volatile WNode next;
        volatile Thread thread;
        WNode(Thread t, WNode p) { thread = t; prev = p; }
    }

    /** Head of writer CLH queue */
    private transient volatile WNode whead;
    /** Tail (last) of writer CLH queue */
    private transient volatile WNode wtail;
    /** Head of read queue  */
    private transient volatile RNode rhead;
    /** The state of the lock -- high bits hold sequence, low bits read count */
    private transient volatile long state;
    /** extra reader count when state read count saturated */
    private transient int readerOverflow;

    /**
     * Creates a new lock initially in unlocked state.
     */
    public StampedLock() {
        state = ORIGIN;
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available.
     *
     * @return a stamp that can be used to unlock or convert mode.
     */
    public long writeLock() {
        long s, next;
        if (((s = state) & ABITS) == 0L &&
            U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
            return next;
        return awaitWrite(false, 0L);
    }

    /**
     * Exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available.
     */
    public long tryWriteLock() {
        long s, next;
        if (((s = state) & ABITS) == 0L &&
            U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
            return next;
        return 0L;
    }

    /**
     * Exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available.
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock.
     */
    public long tryWriteLock(long time, TimeUnit unit)
        throws InterruptedException {
        long nanos =  unit.toNanos(time);
        if (!Thread.interrupted()) {
            long s, next, deadline;
            if (((s = state) & ABITS) == 0L &&
                U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                return next;
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = awaitWrite(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     *
     * @return a stamp that can be used to unlock or convert mode.
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock.
     */
    public long writeLockInterruptibly() throws InterruptedException {
        if (!Thread.interrupted()) {
            long s, next;
            if (((s = state) & ABITS) == 0L &&
                U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                return next;
            if ((next = awaitWrite(true, 0L)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available.
     *
     * @return a stamp that can be used to unlock or convert mode.
     */
    public long readLock() {
        for (;;) {
            long s, m, next;
            if ((m = (s = state) & ABITS) == 0L ||
                (m < WBIT && whead == wtail)) {
                if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            else
                return awaitRead(s, false, 0L);
        }
    }

    /**
     * Non-exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available.
     */
    public long tryReadLock() {
        for (;;) {
            long s, m, next;
            if ((m = (s = state) & ABITS) == WBIT)
                return 0L;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                    return next;
            }
            else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
    }

    /**
     * Non-exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available.
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock.
     */
    public long tryReadLock(long time, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            for (;;) {
                long s, m, next, deadline;
                if ((m = (s = state) & ABITS) == WBIT ||
                    (m != 0L && whead != wtail)) {
                    if (nanos <= 0L)
                        return 0L;
                    if ((deadline = System.nanoTime() + nanos) == 0L)
                        deadline = 1L;
                    if ((next = awaitRead(s, true, deadline)) != INTERRUPTED)
                        return next;
                    break;
                }
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
        }
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     *
     * @return a stamp that can be used to unlock or convert mode.
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock.
     */
    public long readLockInterruptibly() throws InterruptedException {
        if (!Thread.interrupted()) {
            for (;;) {
                long s, next, m;
                if ((m = (s = state) & ABITS) == WBIT ||
                    (m != 0L && whead != wtail)) {
                    if ((next = awaitRead(s, true, 0L)) != INTERRUPTED)
                        return next;
                    break;
                }
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
        }
        throw new InterruptedException();
    }

    /**
     * Returns a stamp that can later be validated, or zero
     * if exclusively locked.
     *
     * @return a stamp, or zero if exclusively locked
     */
    public long tryOptimisticRead() {
        long s;
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * Returns true if the lock has not been exclusively held since
     * issuance of the given stamp. Always returns false if the stamp
     * is zero. Always returns true if the stamp represents a
     * currently held lock.
     *
     * @return true if the lock has not been exclusively held since
     * issuance of the given stamp; else false
     */
    public boolean validate(long stamp) {
        return (stamp & SBITS) == (U.getLongVolatile(this, STATE) & SBITS);
    }

    /**
     * If the lock state matches the given stamp, releases the
     * exclusive lock.
     *
     * @param stamp a stamp returned by a write-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock.
     */
    public void unlockWrite(long stamp) {
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        state = (stamp += WBIT) == 0L ? ORIGIN : stamp;
        readerPrefSignal();
    }

    /**
     * If the lock state matches the given stamp, releases
     * non-exclusive lock.
     *
     * @param stamp a stamp returned by a read-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock.
     */
    public void unlockRead(long stamp) {
        long s, m;
        if ((stamp & RBITS) != 0L) {
            while (((s = state) & SBITS) == (stamp & SBITS)) {
                if ((m = s & ABITS) == 0L)
                    break;
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                        if (m == RUNIT)
                            writerPrefSignal();
                        return;
                    }
                }
                else if (m >= WBIT)
                    break;
                else if (tryDecReaderOverflow(s) != 0L)
                    return;
            }
        }
        throw new IllegalMonitorStateException();
    }

    /**
     * If the lock state matches the given stamp, releases the
     * corresponding mode of the lock.
     *
     * @param stamp a stamp returned by a lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock.
     */
    public void unlock(long stamp) {
        long a = stamp & ABITS, m, s;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L)
                break;
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = (s += WBIT) == 0L ? ORIGIN : s;
                readerPrefSignal();
                return;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT)
                        writerPrefSignal();
                    return;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    /**
     * If the lock state matches the given stamp then performs one of
     * the following actions. If the stamp represents holding a write
     * lock, returns it. Or, if a read lock, if the write lock is
     * available, releases the read and returns a write stamp. Or, if
     * an optimistic read, returns a write stamp only if immediately
     * available. This method returns zero in all other cases.
     *
     * @param stamp a stamp
     * @return a valid write stamp, or zero on failure
     */
    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                return stamp;
            }
            else if (m == RUNIT && a != 0L && a < WBIT) {
                if (U.compareAndSwapLong(this, STATE, s,
                                         next = s - RUNIT + WBIT))
                    return next;
            }
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp then performs one of
     * the following actions. If the stamp represents holding a write
     * lock, releases it and obtains a read lock.  Or, if a read lock,
     * returns it. Or, if an optimistic read, acquires a read lock and
     * returns a read stamp only if immediately available. This method
     * returns zero in all other cases.
     *
     * @param stamp a stamp
     * @return a valid read stamp, or zero on failure
     */
    public long tryConvertToReadLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                next = state = s + (WBIT + RUNIT);
                readerPrefSignal();
                return next;
            }
            else if (a != 0L && a < WBIT)
                return stamp;
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp then, if the stamp
     * represents holding a lock, releases it and returns an
     * observation stamp.  Or, if an optimistic read, returns it if
     * validated. This method returns zero in all other cases, and so
     * may be useful as a form of "tryUnlock".
     *
     * @param stamp a stamp
     * @return a valid optimistic read stamp, or zero on failure
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = U.getLongVolatile(this, STATE)) &
                SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                return s;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                next = state = (s += WBIT) == 0L ? ORIGIN : s;
                readerPrefSignal();
                return next;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT)) {
                    if (m == RUNIT)
                        writerPrefSignal();
                    return next & SBITS;
                }
            }
            else if ((next = tryDecReaderOverflow(s)) != 0L)
                return next & SBITS;
        }
        return 0L;
    }

    /**
     * Releases the write lock if it is held, without requiring a
     * stamp value. This method may be useful for recovery after
     * errors.
     *
     * @return true if the lock was held, else false.
     */
    public boolean tryUnlockWrite() {
        long s;
        if (((s = state) & WBIT) != 0L) {
            state = (s += WBIT) == 0L ? ORIGIN : s;
            readerPrefSignal();
            return true;
        }
        return false;
    }

    /**
     * Releases one hold of the read lock if it is held, without
     * requiring a stamp value. This method may be useful for recovery
     * after errors.
     *
     * @return true if the read lock was held, else false.
     */
    public boolean tryUnlockRead() {
        long s, m;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT)
                        writerPrefSignal();
                    return true;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    /**
     * Returns true if the lock is currently held exclusively.
     *
     * @return true if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * Returns true if the lock is currently held non-exclusively.
     *
     * @return true if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        long m;
        return (m = state & ABITS) > 0L && m < WBIT;
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }

    // internals

    /**
     * Tries to increment readerOverflow by first setting state
     * access bits value to RBITS, indicating hold of spinlock,
     * then updating, then releasing.
     * @param stamp, assumed that (stamp & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryIncReaderOverflow(long s) {
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                ++readerOverflow;
                state = s;
                return s;
            }
        }
        else if ((ThreadLocalRandom.current().nextInt() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * Tries to decrement readerOverflow.
     * @param stamp, assumed that (stamp & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryDecReaderOverflow(long s) {
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                int r; long next;
                if ((r = readerOverflow) > 0) {
                    readerOverflow = r - 1;
                    next = s;
                }
                else
                    next = s - RUNIT;
                 state = next;
                 return next;
            }
        }
        else if ((ThreadLocalRandom.current().nextInt() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /*
     * The two versions of signal implement the phase-fair policy.
     * They include almost the same code, but repacked in different
     * ways.  Integrating the policy with the mechanics eliminates
     * state rechecks that would be needed with separate reader and
     * writer signal methods.  Both methods assume that they are
     * called when the lock is last known to be available, and
     * continue until the lock is unavailable, or at least one thread
     * is signalled, or there are no more waiting threads.  Signalling
     * a reader entails popping (CASing) from rhead and unparking
     * unless the thread already cancelled (indicated by a null waiter
     * field). Signalling a writer requires finding the first node,
     * i.e., the successor of whead. This is normally just head.next,
     * but may require traversal from wtail if next pointers are
     * lagging. These methods may fail to wake up an acquiring thread
     * when one or more have been cancelled, but the cancel methods
     * themselves provide extra safeguards to ensure liveness.
     */

    private void readerPrefSignal() {
        boolean readers = false;
        RNode p; WNode h, q; long s; Thread w;
        while ((p = rhead) != null) {
            if (((s = state) & WBIT) != 0L)
                return;
            if (p.seq == (s & SBITS))
                break;
            readers = true;
            if (U.compareAndSwapObject(this, RHEAD, p, p.next) &&
                (w = p.waiter) != null &&
                U.compareAndSwapObject(p, WAITER, w, null))
                U.unpark(w);
        }
        if (!readers && (state & ABITS) == 0L &&
            (h = whead) != null && h.status != 0) {
            U.compareAndSwapInt(h, STATUS, WAITING, 0);
            if ((q = h.next) == null || q.status == CANCELLED) {
                q = null;
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (q != null && (w = q.thread) != null)
                U.unpark(w);
        }
    }

    private void writerPrefSignal() {
        RNode p; WNode h, q; long s; Thread w;
        if ((h = whead) != null && h.status != 0) {
            U.compareAndSwapInt(h, STATUS, WAITING, 0);
            if ((q = h.next) == null || q.status == CANCELLED) {
                q = null;
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (q != null && (w = q.thread) != null)
                U.unpark(w);
        }
        else {
            while ((p = rhead) != null && ((s = state) & WBIT) == 0L &&
                   p.seq != (s & SBITS)) {
                if (U.compareAndSwapObject(this, RHEAD, p, p.next) &&
                    (w = p.waiter) != null &&
                    U.compareAndSwapObject(p, WAITER, w, null))
                    U.unpark(w);
            }
        }
    }

    /**
     * RNG for local spins. The first call from await{Read,Write}
     * produces a thread-local value. Unless zero, subsequent calls
     * use an xorShift to further reduce memory traffic.  Both await
     * methods use a similar spin strategy: If associated queue
     * appears to be empty, then the thread spin-waits up to SPINS
     * times before enqueing, and then, if the first thread to be
     * enqueued, spins again up to SPINS times before blocking. If,
     * upon wakening it fails to obtain lock, and is still (or
     * becomes) the first waiting thread (which indicates that some
     * other thread barged and obtained lock), it escalates spins (up
     * to MAX_HEAD_SPINS) to reduce the likelihood of continually
     * losing to barging threads.
     */
    private static int nextRandom(int r) {
        if (r == 0)
            return ThreadLocalRandom.current().nextInt();
        r ^= r << 1; // xorshift
        r ^= r >>> 3;
        r ^= r << 10;
        return r;
    }

    /**
     * Possibly spins trying to obtain write lock, then enqueues and
     * blocks while not head of write queue or cannot aquire lock,
     * possibly spinning when at head; cancelling on timeout or
     * interrupt.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero).
     */
    private long awaitWrite(boolean interruptible, long deadline) {
        WNode node = null;
        for (int r = 0, spins = -1;;) {
            WNode p; long s, next;
            if (((s = state) & ABITS) == 0L) {
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                    return next;
            }
            else if (spins < 0)
                spins = whead == wtail ? SPINS : 0;
            else if (spins > 0) {
                if ((r = nextRandom(r)) >= 0)
                    --spins;
            }
            else if ((p = wtail) == null) { // initialize queue
                if (U.compareAndSwapObject(this, WHEAD, null,
                                           new WNode(null, null)))
                    wtail = whead;
            }
            else if (node == null)
                node = new WNode(Thread.currentThread(), p);
            else if (node.prev != p)
                node.prev = p;
            else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                p.next = node;
                for (int headSpins = SPINS;;) {
                    WNode np; int ps;
                    if ((np = node.prev) != p && np != null)
                        (p = np).next = node; // stale
                    if (p == whead) {
                        for (int k = headSpins;;) {
                            if (((s = state) & ABITS) == 0L) {
                                if (U.compareAndSwapLong(this, STATE,
                                                         s, next = s + WBIT)) {
                                    whead = node;
                                    node.thread = null;
                                    node.prev = null;
                                    return next;
                                }
                                break;
                            }
                            if ((r = nextRandom(r)) >= 0 && --k <= 0)
                                break;
                        }
                        if (headSpins < MAX_HEAD_SPINS)
                            headSpins <<= 1;
                    }
                    if ((ps = p.status) == 0)
                        U.compareAndSwapInt(p, STATUS, 0, WAITING);
                    else if (ps == CANCELLED)
                        node.prev = p.prev;
                    else {
                        long time; // 0 argument to park means no timeout
                        if (deadline == 0L)
                            time = 0L;
                        else if ((time = deadline - System.nanoTime()) <= 0L)
                            return cancelWriter(node, false);
                        if (node.prev == p && p.status == WAITING &&
                            (p != whead || (state & WBIT) != 0L)) { // recheck
                            U.park(false, time);
                            if (interruptible && Thread.interrupted())
                                return cancelWriter(node, true);
                        }
                    }
                }
            }
        }
    }

    /**
     * If node non-null, forces cancel status and unsplices from queue
     * if possible. This is a streamlined variant of cancellation
     * methods in AbstractQueuedSynchronizer that includes a detailed
     * explanation.
     */
    private long cancelWriter(WNode node, boolean interrupted) {
        WNode pred;
        if (node != null && (pred = node.prev) != null) {
            WNode pp;
            node.thread = null;
            while (pred.status == CANCELLED && (pp = pred.prev) != null)
                pred = node.prev = pp;
            WNode predNext = pred.next;
            node.status = CANCELLED;
            if (predNext != null) {
                Thread w = null;
                WNode succ = node.next;
                while (succ != null && succ.status == CANCELLED)
                    succ = succ.next;
                if (succ != null)
                    w = succ.thread;
                else if (node == wtail)
                    U.compareAndSwapObject(this, WTAIL, node, pred);
                U.compareAndSwapObject(pred, WNEXT, predNext, succ);
                if (w != null)
                    U.unpark(w);
            }
        }
        writerPrefSignal();
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    /*
     * Waits for read lock or timeout or interrupt. The form of
     * awaitRead differs from awaitWrite mainly because it must
     * restart (with a new wait node) if the thread was unqueued and
     * unparked but could not the obtain lock.  We also need to help
     * with preference rules by not trying to acquire the lock before
     * enqueuing if there is a known waiting writer, but also helping
     * to release those threads that are still queued from the last
     * release.
     */
    private long awaitRead(long stamp, boolean interruptible, long deadline) {
        long seq = stamp & SBITS;
        RNode node = null;
        boolean queued = false;
        for (int r = 0, headSpins = SPINS, spins = -1;;) {
            long s, m, next; RNode p; WNode wh; Thread w;
            if ((m = (s = state) & ABITS) != WBIT &&
                ((s & SBITS) != seq || (wh = whead) == null ||
                 wh.status == 0)) {
                if (m < RFULL ?
                    U.compareAndSwapLong(this, STATE, s, next = s + RUNIT) :
                    (next = tryIncReaderOverflow(s)) != 0L) {
                    if (node != null && (w = node.waiter) != null)
                        U.compareAndSwapObject(node, WAITER, w, null);
                    if ((p = rhead) != null && (s & SBITS) != p.seq &&
                        U.compareAndSwapObject(this, RHEAD, p, p.next) &&
                        (w = p.waiter) != null &&
                        U.compareAndSwapObject(p, WAITER, w, null))
                        U.unpark(w); // help signal other waiters
                    return next;
                }
            }
            else if (m != WBIT && (p = rhead) != null &&
                     (s & SBITS) != p.seq) { // help release old readers
                if (U.compareAndSwapObject(this, RHEAD, p, p.next) &&
                    (w = p.waiter) != null &&
                    U.compareAndSwapObject(p, WAITER, w, null))
                    U.unpark(w);
            }
            else if (queued && node != null && node.waiter == null) {
                node = null;    // restart
                queued = false;
                spins = -1;
            }
            else if (spins < 0) {
                if (rhead != node)
                    spins = 0;
                else if ((spins = headSpins) < MAX_HEAD_SPINS && node != null)
                    headSpins <<= 1;
            }
            else if (spins > 0) {
                if ((r = nextRandom(r)) >= 0)
                    --spins;
            }
            else if (node == null)
                node = new RNode(seq, Thread.currentThread());
            else if (!queued) {
                if (queued = U.compareAndSwapObject(this, RHEAD,
                                                    node.next = rhead, node))
                    spins = -1;
            }
            else {
                long time;
                if (deadline == 0L)
                    time = 0L;
                else if ((time = deadline - System.nanoTime()) <= 0L)
                    return cancelReader(node, false);
                if ((state & WBIT) != 0L && node.waiter != null) { // recheck
                    U.park(false, time);
                    if (interruptible && Thread.interrupted())
                        return cancelReader(node, true);
                }
            }
        }
    }

    /**
     * If node non-null, forces cancel status and unsplices from queue
     * if possible, by traversing entire queue looking for cancelled
     * nodes, cleaning out all at head, but stopping upon first
     * encounter otherwise.
     */
    private long cancelReader(RNode node, boolean interrupted) {
        Thread w;
        if (node != null && (w = node.waiter) != null &&
            U.compareAndSwapObject(node, WAITER, w, null)) {
            for (RNode pred = null, p = rhead; p != null;) {
                RNode q = p.next;
                if (p.waiter == null) {
                    if (pred == null) {
                        U.compareAndSwapObject(this, RHEAD, p, q);
                        p = rhead;
                    }
                    else {
                        U.compareAndSwapObject(pred, RNEXT, p, q);
                        break;
                    }
                }
                else {
                    pred = p;
                    p = q;
                }
            }
        }
        readerPrefSignal();
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long STATE;
    private static final long RHEAD;
    private static final long WHEAD;
    private static final long WTAIL;
    private static final long RNEXT;
    private static final long WNEXT;
    private static final long WPREV;
    private static final long WAITER;
    private static final long STATUS;

    static {
        try {
            U = getUnsafe();
            Class<?> k = StampedLock.class;
            Class<?> rk = RNode.class;
            Class<?> wk = WNode.class;
            STATE = U.objectFieldOffset
                (k.getDeclaredField("state"));
            RHEAD = U.objectFieldOffset
                (k.getDeclaredField("rhead"));
            WHEAD = U.objectFieldOffset
                (k.getDeclaredField("whead"));
            WTAIL = U.objectFieldOffset
                (k.getDeclaredField("wtail"));
            RNEXT = U.objectFieldOffset
                (rk.getDeclaredField("next"));
            WAITER = U.objectFieldOffset
                (rk.getDeclaredField("waiter"));
            STATUS = U.objectFieldOffset
                (wk.getDeclaredField("status"));
            WNEXT = U.objectFieldOffset
                (wk.getDeclaredField("next"));
            WPREV = U.objectFieldOffset
                (wk.getDeclaredField("prev"));

        } catch (Exception e) {
            throw new Error(e);
        }
    }

    /**
     * Returns a sun.misc.Unsafe.  Suitable for use in a 3rd party package.
     * Replace with a simple call to Unsafe.getUnsafe when integrating
     * into a jdk.
     *
     * @return a sun.misc.Unsafe
     */
    private static sun.misc.Unsafe getUnsafe() {
        try {
            return sun.misc.Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                return java.security.AccessController.doPrivileged
                    (new java.security
                     .PrivilegedExceptionAction<sun.misc.Unsafe>() {
                        public sun.misc.Unsafe run() throws Exception {
                            java.lang.reflect.Field f = sun.misc
                                .Unsafe.class.getDeclaredField("theUnsafe");
                            f.setAccessible(true);
                            return (sun.misc.Unsafe) f.get(null);
                        }});
            } catch (java.security.PrivilegedActionException e) {
                throw new RuntimeException("Could not initialize intrinsics",
                                           e.getCause());
            }
        }
    }

}
