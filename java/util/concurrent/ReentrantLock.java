package java.util.concurrent;

/**
 * A reentrant mutual exclusion lock.  This class exists to be used in
 * the infrequent contexts in which <tt>synchronized</tt> methods and
 * blocks cannot be used to achieve desired effects, in particular
 * when locks must be used in a non-nested fashion.<p>
 *
 * ReentrantLocks are intended to be used primarily in before/after
 * constructions such as:
 *
 * <pre>
 * class X {
 *   ReentrantLock lock;
 *   // ...
 *
 *   public void m() { 
 *     lock.lock();  // block until condition holds
 *     try {
 *       // ... method body
 *     }
 *     finally {
 *       lock.unlock()
 *     }
 *   }
 * }
 * </pre>
 *
 * 
 **/
public class ReentrantLock implements Lock {
  public ReentrantLock() {}
  public void lock() {}
  public void lockInterruptibly() throws InterruptedException { }
  public boolean tryLock() {
    return false;
  }
  public boolean tryLock(long time, Clock granularity) throws InterruptedException {
    return false;
  }
  public void unlock() {}

  /**
   * Return the number of holds of this lock by current Thread,
   * or zero if lock not held by current thread.
   **/
  public int getHoldCount() { 
    return 0;
  }

  /**
   * Return true if current thread holds lock.
   **/
  public boolean isHeldByCurrentThread() { 
    return false;
  }

  public Condition newCondition() {
    return null;
  }

}
