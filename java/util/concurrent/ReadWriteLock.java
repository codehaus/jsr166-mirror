package java.util.concurrent;
/**
 *  ReadWriteLocks maintain a pair of associated locks. The readLock
 *  may be held simultanously by multiple reader threads, so long as
 *  there are no writers. The writeLock is exclusive. 
 *
 **/
public interface ReadWriteLock {
  /** 
   * Return the lock used for reading.
   **/
  public Lock readLock();

  /** 
   * Return the lock used for writing.
   **/
  public Lock writeLock();
}
