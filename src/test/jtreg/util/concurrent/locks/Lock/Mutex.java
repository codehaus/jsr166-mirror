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
        public boolean isLocked() { return getState() == 1; }

        public boolean tryAcquireExclusiveState(boolean isQueued, int acquires) {
            assert acquires == 1; // Does not use multiple acquires
            return compareAndSetState(0, 1);
        }
            
        public boolean releaseExclusiveState(int releases) {
            setState(0);
            return true;
        }
        public void checkConditionAccess(Thread thread, boolean waiting) {
            if (!isLocked()) throw new IllegalMonitorStateException();
        }
            
        Condition newCondition() { return new ConditionObject(); }
            
        private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }
        
    private final Sync sync = new Sync();
    public void lock() { 
        sync.acquireExclusiveUninterruptibly(1);  
    }
    public boolean tryLock() { 
        return sync.tryAcquireExclusiveState(false, 1);
    }
    public void lockInterruptibly() throws InterruptedException { 
        sync.acquireExclusiveInterruptibly(1);
    }
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.acquireExclusiveTimed(1, unit.toNanos(timeout));
    }
    public void unlock() { sync.releaseExclusive(1); }
    public Condition newCondition() { return sync.newCondition(); }
    public boolean isLocked() { return sync.isLocked(); }
    public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
}
