package java.util.concurrent;

/**
 * This class provides the most commonly useful implementation of a
 * {@link ReadWriteLock}. 
 *
 * <p>This class has the following properties:
 * <ul>
 * <li><b>Acquisition order</b>
 * <p> This class does not impose a reader or writer preference
 * ordering for lock access. Instead, threads contend using an
 * approximately arrival-order policy. The actual order depends on the
 * order in which an internal {@link ReentrantLock} is granted, but
 * essentially when the write lock is released either the single writer
 * at the notional head of the queue will be assigned the write lock, or
 * the set of readers at the head of the queue will be assigned the read lock.
 * <p>If readers are active and a writer arrives then no subsequent readers
 * will be granted the read lock until after that writer has acquired and 
 * released the write lock.
 * <p><b>Note:</b> As all threads, readers and writers, must pass through an
 * internal lock, it is possible for the read lock <tt>tryLock</tt> methods
 * to return <tt>false</tt> even if the lock is logically available to the 
 * thread.
 * 
 * <li><b>Reentrancy</b>
 * <p>This lock allows both readers and writers to reacquire read or
 * write locks in the style of a {@link ReentrantLock}. Readers are not
 * allowed until all write locks held by the writing thread have been
 * released.  
 * <p>Additionally, a writer can acquire the read lock - but not vice-versa.
 * Among other applications, reentrancy can be useful when
 * write locks are held during calls or callbacks to methods that
 * perform reads under read locks. 
 * If a reader tries to acquire the write lock it will never succeed.
 * 
 * <li><b>Lock downgrading</b>
 * <p>Reentrancy also allows downgrading from the write lock to a read lock,
 * by acquiring the write lock, then the read lock and then releasing the
 * write lock. Note that upgrading, from a read lock to the write lock, is
 * <b>not</b> possible.
 *
 * <li><b>Interruption of lock aquisition</b>
 * <p>Both the read lock and write lock support interruption during lock
 * acquisition.
 *
 * <li><b>{@link Condition} support</b>
 * <p>The write lock provides a {@link Condition} implementation that
 * behaves in the same way, with respect to the write lock, as the 
 * {@link Condition} implementation provided by
 * {@link ReentrantLock#newCondition} does for {@link ReentrantLock}.
 * This {@link Condition} can, of course, only be used with the write lock.
 * <p>The read lock does not support a {@link Condition} and
 * <tt>readLock().newCondition()</tt> will return <tt>null</tt>.
 *
 * <li><b>Ownership</b>
 * <p>While not exposing a means to query the owner, the write lock does
 * internally define an owner and so the write lock can only be released by
 * the thread that acquired it.
 * <p>In contrast, the read lock has no concept of ownership, simply a count
 * of the number of active readers. Consequently, while not a particularly 
 * good practice, it is possible to acquire a read lock in one thread, and 
 * release it in another. This can occasionally be useful.
 *
 * </ul>
 *
 * <p><b>Sample usage</b>. Here is a code sketch showing how to exploit
 * reentrancy to perform lock downgrading after updating a cache (exception
 * handling is elided for simplicity):
 * <pre>
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *        // upgrade lock manually
 *        rwl.readLock().unlock();   // must unlock first to obtain writelock
 *        rwl.writeLock().lock();
 *        if (!cacheValid) { // recheck
 *          data = ...
 *          cacheValid = true;
 *        }
 *        // downgrade lock
 *        rwl.readLock().lock();  // reacquire read without giving up write lock
 *        rwl.writeLock().unlock(); // unlock write, still hold read
 *     }
 *
 *     use(data);
 *     rwl.readLock().unlock();
 *   }
 * }
 * </pre>
 *
 * @see ReentrantLock
 * @see Condition
 * @see ReadWriteLock
 * @see Locks
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/05/14 21:30:47 $
 * @editor $Author: tim $
 *
 */
public class ReentrantReadWriteLock implements ReadWriteLock {

    public Lock readLock() {
        return null;
    }

    public Lock writeLock() {
        return null;
    }
}



