/*
 * @(#)ScheduledExecutor.java
 */

package java.util.concurrent;
import java.util.*;

/**
 * An <tt>Executor</tt> that can schedule tasks to run after a given delay,
 * or to execute periodically. This class is preferable to
 * <tt>java.util.Timer</tt> when multiple worker threads are needed,
 * or when the additional flexibility or capabilities of
 * <tt>ThreadExecutor</tt> are required.  Tasks submitted using the
 * <tt>execute</tt> method are scheduled as if they had a requested
 * delay of zero. 
 *
 * @since 1.5
 * @see Executors
 *
 * @spec JSR-166
 */
public class ScheduledExecutor extends ThreadPoolExecutor {

    private static class DelayedWorkQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {
        private DelayQueue<Runnable> dq = new DelayQueue<Runnable>();

        DelayQueue<Runnable> getDelayQueue() { return dq; }

        public Runnable take() throws InterruptedException {
            return dq.take().get();
        }

        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            DelayEntry<Runnable> e = dq.poll(timeout, unit);
            return (e == null) ? null : e.get();
        }

        public Runnable poll() {
            DelayEntry<Runnable> e = dq.poll();
            return (e == null) ? null : e.get();
        }

        public Runnable peek() {
            DelayEntry<Runnable> e = dq.peek();
            return (e == null) ? null : e.get();
        }

        public int size() { return dq.size(); }
        public int remainingCapacity() { return dq.remainingCapacity(); }
        public void put(Runnable r) {
            assert false;
        }
        public boolean offer(Runnable r) {
            assert false;
            return false;
        }
        public boolean offer(Runnable r, long time, TimeUnit unit) {
            assert false;
            return false;
        }
        public Iterator<Runnable> iterator() {
            assert false;
            return null; // for now
        }

        public boolean remove(Object x) {
            assert false;
            return false; // for now
        }

        public Object[] toArray() {
            assert false;
            return null; // for now
        }
        public <T> T[] toArray(T[] a) {
            assert false;
            return null; // for now
        }
    }


    /**
     * Creates a new ScheduledExecutor with the given initial parameters.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     */
    public ScheduledExecutor(int corePoolSize) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue());
    }

    public void execute(DelayEntry<Runnable> r) {
        if (isShutdown()) {
            getRejectedExecutionHandler().rejectedExecution(r.get(), this);
            return;
        }

        addIfUnderCorePoolSize(null);
        ((DelayedWorkQueue)getQueue()).getDelayQueue().put(r);
    }

    public void execute(Runnable r) {
        execute(new DelayEntry(r, 0, TimeUnit.NANOSECONDS));
    }

}
