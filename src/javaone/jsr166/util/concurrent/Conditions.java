package jsr166.util.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Conditions {

    public interface Guard {
        boolean call();
    }

    public static GuardedAction newGuardedAction(Lock lock, Guard guard) {
        return new GuardedActionImpl(lock, guard);
    }

    public static GuardedCondition newGuardedCondition(final Lock lock, final Guard guard) {
        return new GuardedConditionImpl(lock, guard);
    }

    private static class GuardedConditionImpl implements GuardedCondition {
        public void await() throws InterruptedException {
            while (!guard.call()) condition.await();
        }
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            boolean waitElapsed = false;
            while (!guard.call()) waitElapsed = condition.await(time, unit);
            return waitElapsed;
        }
        public long awaitNanos(long nanos) throws InterruptedException {
            long nanosRemaining = 0L;
            while (!guard.call()) nanosRemaining = condition.awaitNanos(nanos);
            return nanosRemaining;
        }
        public void awaitUninterruptibly() {
            while (!guard.call()) condition.awaitUninterruptibly();
        }
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            boolean deadlineElapsed = false;
            while (!guard.call()) deadlineElapsed = condition.awaitUntil(deadline);
            return deadlineElapsed;
        }
        public void signal() {
            assert guard.call() : "guard does not hold at signal";
            condition.signal();
        }
        public void signalAll() {
            assert guard.call() : "guard does not hold at signalAll";
            condition.signalAll();
        }
        public boolean trySignal() {
            if (!guard.call()) return false;
            condition.signal();
            return true;
        }
        public boolean trySignalAll() {
            if (!guard.call()) return false;
            condition.signalAll();
            return true;
        }

        private GuardedConditionImpl(Lock lock, Guard guard) {
            this.condition = lock.newCondition();
            this.guard = guard;
        }
        private final Condition condition;
        private final Guard guard;
    }

    private static class GuardedActionImpl extends GuardedConditionImpl implements GuardedAction {
        public void when(Runnable action) throws InterruptedException {
            lock.lock();
            try {
                this.await();
                action.run();
            } finally {
                lock.unlock();
            }
        }
        public <V> V when(Callable<V> action) throws InterruptedException {
            lock.lock();
            try {
                await();
                return action.call();
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }

        private GuardedActionImpl(Lock lock, Guard guard) {
            super(lock, guard);
            this.lock = lock;
        }
        private final Lock lock;
    }

    private Conditions() {} // uninstantiable
}
