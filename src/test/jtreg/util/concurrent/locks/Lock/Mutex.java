/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.io.*;

/**
 * A sample user extension of AbstractQueuedSynchronizer. 
 */
public class Mutex implements Lock, java.io.Serializable {
    private static class Sync extends AbstractQueuedSynchronizer {
        public int acquireExclusiveState(boolean isQueued, int acquires, Thread current) {
            assert acquires == 1; // Does not use multiple acquires
            return state().compareAndSet(0, 1)? 0 : -1;
        }
            
        public boolean releaseExclusiveState(int releases) {
            state().set(0);
            return true;
        }
            
        public int acquireSharedState(boolean isQueued, int acquires, Thread current) {
            throw new UnsupportedOperationException();
        }
            
        public boolean releaseSharedState(int releases) {
            throw new UnsupportedOperationException();
        }
            
        public void checkConditionAccess(Thread thread, boolean waiting) {
            if (state().get() == 0) throw new IllegalMonitorStateException();
        }
            
        Condition newCondition() { return new ConditionObject(); }
            
        private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            state().set(0); // reset to unlocked state
        }
    }
        
    private final Sync sync = new Sync();
    public void lock() { 
        if (!tryLock()) sync.acquireExclusiveUninterruptibly(1);  
    }
    public boolean tryLock() { 
        return sync.acquireExclusiveState(false, 1, null) >= 0;
    }
    public void lockInterruptibly() throws InterruptedException { 
        sync.acquireExclusiveInterruptibly(1);
    }
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.acquireExclusiveTimed(1, unit.toNanos(timeout));
    }
    public void unlock() { sync.releaseExclusive(1); }
    public Condition newCondition() { return sync.newCondition(); }
    public boolean isLocked() { return sync.state().get() != 0; }
    public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
}
