package java.util.concurrent;


/**
 * Utility operations for built-in synchronization and Lock classes.
 **/
public class Locks {
  private Locks() {} // uninstantiable.

  /**
   * Perform the given runnable within a block synchronized
   * on the given object only if its lock is currently free.
   *
   * @param lock the Object to synchronize on.
   * @param runnable the code to run
   * @return true if the runnable was executed.
   **/
  public static boolean attempt(Object lock, Runnable runnable) {
    return false; // for now;
  }

  /**
   * Perform the given runnable within a block synchronized
   * on the given object only if its lock is currently free
   * or becomes free within the given wait time.
   * @param lock the Object to synchronize on.
   * @param runnable the code to run
   * @param time the maximum time to wait
   * @param granularity the time unit of the time argument.
   * @return true if the runnable was executed.
   * @throws InterruptedException if interrupted during wait for lock. 
   **/
  public static boolean attempt(Object lock, Runnable runnable, long time, Clock granularity) 
    throws InterruptedException {
    return false; // for now;
  }

  /**
   * Perform the given runnable within a block synchronized
   * on all of the given objects only if the locks are currently free.
   * <p>
   * @param locks the Objects to synchronize on.
   * @param runnable the code to run
   * @return true if the runnable was executed.
   **/
  public static boolean attempt(Object[] locks, Runnable runnable) {
    return false; // for now;
  }

  /**
   * Perform the given runnable within a block acquiring
   * on all of the given lock objects only if the locks are currently free.
   * <p>
   * @param locks the Lock  to synchronize on.
   * @param runnable the code to run
   * @return true if the runnable was executed.
   **/
  public static boolean attempt(Lock[] locks, Runnable runnable) {
    return false; // for now;
  }


  /**
   * Construct a new Condition using the given object as its
   * mutual exclusion lock.
   * <p>
   * @param locks the Lock  to synchronize on.
   * @return a Condition 
   **/
  public static Condition newConditionFor(Object lock) {
    return null; // for now;
  }

}
