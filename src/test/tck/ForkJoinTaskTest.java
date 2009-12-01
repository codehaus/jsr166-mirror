/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */
import junit.framework.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

public class ForkJoinTaskTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(ForkJoinTaskTest.class);
    }

    /**
     * Testing coverage notes:
     *
     * To test extension methods and overrides, most tests use
     * BinaryAsyncAction extension class that processes joins
     * differently than supplied Recursive forms.
     */

    static final ForkJoinPool mainPool = new ForkJoinPool();
    static final ForkJoinPool singletonPool = new ForkJoinPool(1);
    static final ForkJoinPool asyncSingletonPool = new ForkJoinPool(1);
    static {
        asyncSingletonPool.setAsyncMode(true);
    }

    static final class FJException extends RuntimeException {
        FJException() { super(); }
    }

    static abstract class BinaryAsyncAction extends ForkJoinTask<Void> {
        private volatile int controlState;

        static final AtomicIntegerFieldUpdater<BinaryAsyncAction> controlStateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(BinaryAsyncAction.class,
                                                 "controlState");

        private BinaryAsyncAction parent;

        private BinaryAsyncAction sibling;

        protected BinaryAsyncAction() {
        }

        public final Void getRawResult() { return null; }
        protected final void setRawResult(Void mustBeNull) { }

        public final void linkSubtasks(BinaryAsyncAction x, BinaryAsyncAction y) {
            x.parent = y.parent = this;
            x.sibling = y;
            y.sibling = x;
        }

        protected void onComplete(BinaryAsyncAction x, BinaryAsyncAction y) {
        }

        protected boolean onException() {
            return true;
        }

        public void linkAndForkSubtasks(BinaryAsyncAction x, BinaryAsyncAction y) {
            linkSubtasks(x, y);
            y.fork();
            x.fork();
        }

        private void completeThis() {
            super.complete(null);
        }

        private void completeThisExceptionally(Throwable ex) {
            super.completeExceptionally(ex);
        }

        public final void complete() {
            BinaryAsyncAction a = this;
            for (;;) {
                BinaryAsyncAction s = a.sibling;
                BinaryAsyncAction p = a.parent;
                a.sibling = null;
                a.parent = null;
                a.completeThis();
                if (p == null || p.compareAndSetControlState(0, 1))
                    break;
                try {
                    p.onComplete(a, s);
                } catch (Throwable rex) {
                    p.completeExceptionally(rex);
                    return;
                }
                a = p;
            }
        }

        public final void completeExceptionally(Throwable ex) {
            BinaryAsyncAction a = this;
            while (!a.isCompletedAbnormally()) {
                a.completeThisExceptionally(ex);
                BinaryAsyncAction s = a.sibling;
                if (s != null)
                    s.cancel(false);
                if (!a.onException() || (a = a.parent) == null)
                    break;
            }
        }

        public final BinaryAsyncAction getParent() {
            return parent;
        }

        public BinaryAsyncAction getSibling() {
            return sibling;
        }

        public void reinitialize() {
            parent = sibling = null;
            super.reinitialize();
        }

        protected final int getControlState() {
            return controlState;
        }

        protected final boolean compareAndSetControlState(int expect,
                                                          int update) {
            return controlStateUpdater.compareAndSet(this, expect, update);
        }

        protected final void setControlState(int value) {
            controlState = value;
        }

        protected final void incrementControlState() {
            controlStateUpdater.incrementAndGet(this);
        }

        protected final void decrementControlState() {
            controlStateUpdater.decrementAndGet(this);
        }

    }

    static final class AsyncFib extends BinaryAsyncAction {
        int number;
        public AsyncFib(int n) {
            this.number = n;
        }

        public final boolean exec() {
            AsyncFib f = this;
            int n = f.number;
            if (n > 1) {
                while (n > 1) {
                    AsyncFib p = f;
                    AsyncFib r = new AsyncFib(n - 2);
                    f = new AsyncFib(--n);
                    p.linkSubtasks(r, f);
                    r.fork();
                }
                f.number = n;
            }
            f.complete();
            return false;
        }

        protected void onComplete(BinaryAsyncAction x, BinaryAsyncAction y) {
            number = ((AsyncFib)x).number + ((AsyncFib)y).number;
        }
    }


    static final class FailingAsyncFib extends BinaryAsyncAction {
        int number;
        public FailingAsyncFib(int n) {
            this.number = n;
        }

        public final boolean exec() {
            FailingAsyncFib f = this;
            int n = f.number;
            if (n > 1) {
                while (n > 1) {
                    FailingAsyncFib p = f;
                    FailingAsyncFib r = new FailingAsyncFib(n - 2);
                    f = new FailingAsyncFib(--n);
                    p.linkSubtasks(r, f);
                    r.fork();
                }
                f.number = n;
            }
            f.complete();
            return false;
        }

        protected void onComplete(BinaryAsyncAction x, BinaryAsyncAction y) {
            completeExceptionally(new FJException());
        }
    }

    /**
     * invoke returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks. getRawResult of a RecursiveAction returns null;
     *
     */
    public void testInvoke() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.invoke();
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(f.isCancelled());
                    threadAssertFalse(f.isCompletedAbnormally());
                    threadAssertTrue(f.getRawResult() == null);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.quietlyInvoke();
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(f.isCancelled());
                    threadAssertFalse(f.isCompletedAbnormally());
                    threadAssertTrue(f.getRawResult() == null);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    f.join();
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.getRawResult() == null);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGet() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.fork();
                        f.get();
                        threadAssertTrue(f.number == 21);
                        threadAssertTrue(f.isDone());
                    } catch (Exception ex) {
                        unexpectedException(ex);
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGet() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.fork();
                        f.get(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                        threadAssertTrue(f.number == 21);
                        threadAssertTrue(f.isDone());
                    } catch (Exception ex) {
                        unexpectedException(ex);
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPE() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.fork();
                        f.get(5L, null);
                        shouldThrow();
                    } catch (NullPointerException success) {
                    } catch (Exception ex) {
                        unexpectedException(ex);
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * helpJoin of a forked task returns when task completes
     */
    public void testForkHelpJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    f.helpJoin();
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(f.isDone());
                }
            };
        mainPool.invoke(a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    f.quietlyJoin();
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(f.isDone());
                }
            };
        mainPool.invoke(a);
    }


    /**
     * quietlyHelpJoin of a forked task returns when task completes
     */
    public void testForkQuietlyHelpJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    f.quietlyHelpJoin();
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(f.isDone());
                }
            };
        mainPool.invoke(a);
    }


    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesce() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    f.helpQuiesce();
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(getQueuedTaskCount() == 0);
                }
            };
        mainPool.invoke(a);
    }


    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvoke() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        FailingAsyncFib f = new FailingAsyncFib(8);
                        f.invoke();
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvoke() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    FailingAsyncFib f = new FailingAsyncFib(8);
                    f.quietlyInvoke();
                    threadAssertTrue(f.isDone());
                }
            };
        mainPool.invoke(a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        FailingAsyncFib f = new FailingAsyncFib(8);
                        f.fork();
                        f.join();
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGet() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        FailingAsyncFib f = new FailingAsyncFib(8);
                        f.fork();
                        f.get();
                        shouldThrow();
                    } catch (ExecutionException success) {
                    } catch (Exception ex) {
                        unexpectedException(ex);
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGet() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        FailingAsyncFib f = new FailingAsyncFib(8);
                        f.fork();
                        f.get(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                        shouldThrow();
                    } catch (ExecutionException success) {
                    } catch (Exception ex) {
                        unexpectedException(ex);
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkHelpJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        FailingAsyncFib f = new FailingAsyncFib(8);
                        f.fork();
                        f.helpJoin();
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * quietlyHelpJoin of a forked task returns when task completes abnormally.
     * getException of failed task returns its exception.
     * isCompletedAbnormally of a failed task returns true.
     * isCancelled of a failed uncancelled task returns false
     */
    public void testAbnormalForkQuietlyHelpJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    FailingAsyncFib f = new FailingAsyncFib(8);
                    f.fork();
                    f.quietlyHelpJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertFalse(f.isCancelled());
                    threadAssertTrue(f.getException() instanceof FJException);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    FailingAsyncFib f = new FailingAsyncFib(8);
                    f.fork();
                    f.quietlyJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertTrue(f.getException() instanceof FJException);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvoke() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.cancel(true);
                        f.invoke();
                        shouldThrow();
                    } catch (CancellationException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.cancel(true);
                        f.fork();
                        f.join();
                        shouldThrow();
                    } catch (CancellationException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGet() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.cancel(true);
                        f.fork();
                        f.get();
                        shouldThrow();
                    } catch (CancellationException success) {
                    } catch (Exception ex) {
                        unexpectedException(ex);
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGet() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.cancel(true);
                        f.fork();
                        f.get(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                        shouldThrow();
                    } catch (CancellationException success) {
                    } catch (Exception ex) {
                        unexpectedException(ex);
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkHelpJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.cancel(true);
                        f.fork();
                        f.helpJoin();
                        shouldThrow();
                    } catch (CancellationException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * quietlyHelpJoin of a forked task returns when task cancelled.
     * getException of cancelled task returns its exception.
     * isCompletedAbnormally of a cancelled task returns true.
     * isCancelled of a cancelled task returns true
     */
    public void testCancelledForkQuietlyHelpJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.cancel(true);
                    f.fork();
                    f.quietlyHelpJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertTrue(f.isCancelled());
                    threadAssertTrue(f.getException() instanceof CancellationException);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoin() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    f.cancel(true);
                    f.fork();
                    f.quietlyJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertTrue(f.getException() instanceof CancellationException);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * getPool of executing task returns its pool
     */
    public void testGetPool() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    threadAssertTrue(getPool() == mainPool);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * getPool of non-FJ task returns null
     */
    public void testGetPool2() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    threadAssertTrue(getPool() == null);
                }
            };
        a.invoke();
    }

    /**
     * inForkJoinPool of executing task returns true
     */
    public void testInForkJoinPool() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    threadAssertTrue(inForkJoinPool());
                }
            };
        mainPool.invoke(a);
    }

    /**
     * inForkJoinPool of non-FJ task returns false
     */
    public void testInForkJoinPool2() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    threadAssertTrue(!inForkJoinPool());
                }
            };
        a.invoke();
    }

    /**
     * setRawResult(null) succeeds
     */
    public void testSetRawResult() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    setRawResult(null);
                }
            };
        a.invoke();
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionally() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        f.completeExceptionally(new FJException());
                        f.invoke();
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    AsyncFib g = new AsyncFib(9);
                    invokeAll(f, g);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(g.isDone());
                    threadAssertTrue(g.number == 34);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    invokeAll(f);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.number == 21);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    AsyncFib g = new AsyncFib(9);
                    AsyncFib h = new AsyncFib(7);
                    invokeAll(f, g, h);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(g.isDone());
                    threadAssertTrue(g.number == 34);
                    threadAssertTrue(h.isDone());
                    threadAssertTrue(h.number == 13);
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollection() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib f = new AsyncFib(8);
                    AsyncFib g = new AsyncFib(9);
                    AsyncFib h = new AsyncFib(7);
                    HashSet set = new HashSet();
                    set.add(f);
                    set.add(g);
                    set.add(h);
                    invokeAll(set);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.number == 21);
                    threadAssertTrue(g.isDone());
                    threadAssertTrue(g.number == 34);
                    threadAssertTrue(h.isDone());
                    threadAssertTrue(h.number == 13);
                }
            };
        mainPool.invoke(a);
    }


    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPE() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        AsyncFib g = new AsyncFib(9);
                        AsyncFib h = null;
                        invokeAll(f, g, h);
                        shouldThrow();
                    } catch (NullPointerException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        FailingAsyncFib g = new FailingAsyncFib(9);
                        invokeAll(f, g);
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        FailingAsyncFib g = new FailingAsyncFib(9);
                        invokeAll(g);
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        AsyncFib f = new AsyncFib(8);
                        FailingAsyncFib g = new FailingAsyncFib(9);
                        AsyncFib h = new AsyncFib(7);
                        invokeAll(f, g, h);
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * invokeAll(collection)  throws exception if any task does
     */
    public void testAbnormalInvokeAllCollection() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    try {
                        FailingAsyncFib f = new FailingAsyncFib(8);
                        AsyncFib g = new AsyncFib(9);
                        AsyncFib h = new AsyncFib(7);
                        HashSet set = new HashSet();
                        set.add(f);
                        set.add(g);
                        set.add(h);
                        invokeAll(set);
                        shouldThrow();
                    } catch (FJException success) {
                    }
                }
            };
        mainPool.invoke(a);
    }

    /**
     * tryUnfork returns true for most recent unexecuted task,
     * and suppresses execution
     */
    public void testTryUnfork() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(f.tryUnfork());
                    helpQuiesce();
                    threadAssertFalse(f.isDone());
                    threadAssertTrue(g.isDone());
                }
            };
        singletonPool.invoke(a);
    }

    /**
     * getSurplusQueuedTaskCount returns > 0 when
     * there are more tasks than threads
     */
    public void testGetSurplusQueuedTaskCount() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib h = new AsyncFib(7);
                    h.fork();
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(getSurplusQueuedTaskCount() > 0);
                    helpQuiesce();
                }
            };
        singletonPool.invoke(a);
    }

    /**
     * peekNextLocalTask returns most recent unexecuted task.
     */
    public void testPeekNextLocalTask() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(peekNextLocalTask() == f);
                    f.join();
                    threadAssertTrue(f.isDone());
                    helpQuiesce();
                }
            };
        singletonPool.invoke(a);
    }

    /**
     * pollNextLocalTask returns most recent unexecuted task
     * without executing it
     */
    public void testPollNextLocalTask() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(pollNextLocalTask() == f);
                    helpQuiesce();
                    threadAssertFalse(f.isDone());
                }
            };
        singletonPool.invoke(a);
    }

    /**
     * pollTask returns an unexecuted task
     * without executing it
     */
    public void testPollTask() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(pollTask() == f);
                    helpQuiesce();
                    threadAssertFalse(f.isDone());
                    threadAssertTrue(g.isDone());
                }
            };
        singletonPool.invoke(a);
    }

    /**
     * peekNextLocalTask returns least recent unexecuted task in async mode
     */
    public void testPeekNextLocalTaskAsync() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(peekNextLocalTask() == g);
                    f.join();
                    helpQuiesce();
                    threadAssertTrue(f.isDone());
                }
            };
        asyncSingletonPool.invoke(a);
    }

    /**
     * pollNextLocalTask returns least recent unexecuted task
     * without executing it, in async mode
     */
    public void testPollNextLocalTaskAsync() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(pollNextLocalTask() == g);
                    helpQuiesce();
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(g.isDone());
                }
            };
        asyncSingletonPool.invoke(a);
    }

    /**
     * pollTask returns an unexecuted task
     * without executing it, in async mode
     */
    public void testPollTaskAsync() {
        RecursiveAction a = new RecursiveAction() {
                public void compute() {
                    AsyncFib g = new AsyncFib(9);
                    g.fork();
                    AsyncFib f = new AsyncFib(8);
                    f.fork();
                    threadAssertTrue(pollTask() == g);
                    helpQuiesce();
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(g.isDone());
                }
            };
        asyncSingletonPool.invoke(a);
    }
}
