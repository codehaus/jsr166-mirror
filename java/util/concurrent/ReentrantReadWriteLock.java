package java.util.concurrent;

/**
 * This class provides the most commonly useful implementation of
 * ReadWriteLocks. 
 *
 * <p> This class does not impose an reader or writer preference
 * ordering for lock access. Instead, threads contend using an
 * approximately arrival-order policy.  As a byproduct, the
 * <tt>attempt</tt> methods may return <tt>false</tt> even when the
 * lock might logically be available, but, due to contention, cannot
 * be accessed within the given time bound.  <p>
 * 
 * This lock allows both readers and writers to reacquire read or
 * write locks in the style of a ReentrantLock.  Readers are not
 * allowed until all write locks held by the writing thread have been
 * released.  Among other applications, reentrancy can be useful when
 * write locks are held during calls or callbacks to methods that
 * perform reads under read locks. Reentrancy also allows downgrading
 * (from write to read) but NOT upgrading (from read to write).
 * Additionally, while not a particularly good practice, it is
 * possible to acquire a read-lock (but not a write-lock) in one
 * thread, and release it in another.  <p>
 *
 * <b>Sample usage</b>. Here is a code sketch showing how to exploit
 * reentrancy to perform lock downgrading after updating a cache:
 * <pre>
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   ReentrantReadWriteLock rwl = ...
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *
 *        // upgrade lock:
 *        rwl.readLock().unlock();   // must unlock first to obtain writelock
 *        rwl.writeLock().lock();
 *        if (!cacheValid) { // recheck
 *          data = ...
 *          cacheValid = true;
 *        }
 *        // downgrade lock
 *        rwl.readLock().lock();  // reacquire read without giving up lock
 *        rwl.writeLock().unlock(); // unlock write, still hold read
 *     }
 *
 *     use(data);
 *     rwl.readLock().unlock();
 *   }
 * }
 * </pre>
 *
**/
public class ReentrantReadWriteLock implements ReadWriteLock {
    public Lock readLock() {
        return null;
    }
    public Lock writeLock() {
        return null;
    }
}
