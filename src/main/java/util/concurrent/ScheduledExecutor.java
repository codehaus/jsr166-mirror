/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
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

    /**
     * Sequence number to break scheduling ties, and in turn to
     * guarantee FIFO order among tied entries.
     */
    private static final AtomicLong sequencer = new AtomicLong(0);

    /**
     * A delayed or periodic action.
     */
    public class DelayedTask extends CancellableTask implements Delayed {
        private final long sequenceNumber;
        private final long time;
        private final long period;

        /**
         * Creates a one-shot action with given nanoTime-based trigger time
         */
        DelayedTask(Runnable r, long ns) {
            super(r);
            this.time = ns;
            this.period = 0;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * Creates a periodic action with given nano time and period
         */
        DelayedTask(Runnable r, long ns,  long period) {
            super(r);
            if (period <= 0)
                throw new IllegalArgumentException();
            this.time = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }


        public long getDelay(TimeUnit unit) {
            return unit.convert(time - TimeUnit.nanoTime(), TimeUnit.NANOSECONDS);
        }

        public int compareTo(Object other) {
            DelayedTask x = (DelayedTask)other;
            long diff = time - x.time;
            if (diff < 0)
                return -1;
            else if (diff > 0)
                return 1;
            else if (sequenceNumber < x.sequenceNumber)
                return -1;
            else
                return 1;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!isDone()) 
                ScheduledExecutor.this.remove(this);
            return super.cancel(mayInterruptIfRunning);
        }

        /**
         * Return true if this is a periodic (not a one-shot) action.
         */
        public boolean isPeriodic() {
            return period > 0;
        }

        /**
         * Returns the period, or zero if non-periodic
         */
        public long getPeriod(TimeUnit unit) {
            return unit.convert(period, TimeUnit.NANOSECONDS);
        }

        /**
         * Return a new DelayedTask that will trigger in the period
         * subsequent to current task, or null if non-periodic
         * or canceled.
         */
        DelayedTask nextTask() {
            if (period <= 0 || isCancelled())
                return null;
            return new DelayedTask(getRunnable(), time+period, period);
        }

    }

    /**
     * A delayed result-bearing action.
     */
    public class DelayedFutureTask<V> extends DelayedTask implements Future<V> {
        /**
         * Creates a Future that may trigger after the given delay.
         */
        DelayedFutureTask(Callable<V> callable, long delay,  TimeUnit unit) {
            // must set after super ctor call to use inner class
            super(null, TimeUnit.nanoTime() + unit.toNanos(delay));
            setRunnable(new InnerCancellableFuture<V>(callable));
        }

        /**
         * Creates a one-shot action that may trigger after the given date.
         */
        DelayedFutureTask(Callable<V> callable, Date date) {
            super(null, 
                  TimeUnit.MILLISECONDS.toNanos(date.getTime() - 
                                                System.currentTimeMillis()));
            setRunnable(new InnerCancellableFuture<V>(callable));
        }
    
        public V get() throws InterruptedException, ExecutionException {
            return ((InnerCancellableFuture<V>)getRunnable()).get();
        }

        public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
            return ((InnerCancellableFuture<V>)getRunnable()).get(timeout, unit);
        }

        protected void set(V v) {
            ((InnerCancellableFuture<V>)getRunnable()).set(v);
        }

        protected void setException(Throwable t) {
            ((InnerCancellableFuture<V>)getRunnable()).setException(t);
        }
    }


    /**
     * An annoying wrapper class to convince generics compiler to
     * use a DelayQueue<DelayedTask> as a BlockingQUeue<Runnable>
     */ 
    private static class DelayedWorkQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {
        private final DelayQueue<DelayedTask> dq = new DelayQueue<DelayedTask>();
        public Runnable poll() { return dq.poll(); }
        public Runnable peek() { return dq.peek(); }
        public Runnable take() throws InterruptedException { return dq.take(); }
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            return dq.poll(timeout, unit);
        }
        public boolean offer(Runnable x) { return dq.offer((DelayedTask)x); }
        public void put(Runnable x) throws InterruptedException {
            dq.put((DelayedTask)x); 
        }
        public boolean offer(Runnable x, long timeout, TimeUnit unit) throws InterruptedException {
            return dq.offer((DelayedTask)x, timeout, unit);
        }
        public int remainingCapacity() { return dq.remainingCapacity(); }
        public boolean remove(Object x) { return dq.remove(x); }
        public boolean contains(Object x) { return dq.contains(x); }
        public int size() { return dq.size(); }
	public boolean isEmpty() { return dq.isEmpty(); }
        public Iterator<Runnable> iterator() { 
            return new Iterator<Runnable>() {
                private Iterator<DelayedTask> it = dq.iterator();
                public boolean hasNext() { return it.hasNext(); }
                public Runnable next() { return it.next(); }
                public void remove() {  it.remove(); }
            };
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

    /**
     * Creates a new ScheduledExecutor with the given initial parameters.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param threadFactory the factory to use when the executor
     * creates a new thread. 
     */
    public ScheduledExecutor(int corePoolSize,
                             ThreadFactory threadFactory) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue(), threadFactory);
    }

    /**
     * Creates a new ScheduledExecutor with the given initial parameters.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached.
     */
    public ScheduledExecutor(int corePoolSize,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue(), handler);
    }

    /**
     * Creates a new ScheduledExecutor with the given initial parameters.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param threadFactory the factory to use when the executor
     * creates a new thread. 
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached.
     */
    public ScheduledExecutor(int corePoolSize,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue(), threadFactory, handler);
    }

    /**
     * Creates and executes a one-shot action that may trigger after
     * the given delay.
     * @param command the task to execute.
     * @param delay the time from now to delay execution.
     * @param unit the time unit of the delay parameter.
     * @return a handle that can be used to cancel the task.
     */

    public DelayedTask schedule(Runnable command, long delay,  TimeUnit unit) {
        DelayedTask t = new DelayedTask(command, TimeUnit.nanoTime() + unit.toNanos(delay));
        super.execute(t);
        return t;
    }

    /**
     * Creates and executes a one-shot action that may trigger after the given date.
     * @param command the task to execute.
     * @param date the time to commence excution.
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public DelayedTask schedule(Runnable command, Date date) {
        DelayedTask t = new DelayedTask
            (command, 
             TimeUnit.MILLISECONDS.toNanos(date.getTime() - 
                                           System.currentTimeMillis()));
        super.execute(t);
        return t;
    }
    
    /**
     * Creates and executes a periodic action that may trigger first
     * after the given initial delay, and subsequently with the given
     * period.
     * @param command the task to execute.
     * @param initialDelay the time to delay first execution.
     * @param period the period between successive executions.
     * @param unit the time unit of the delay and period parameters
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public DelayedTask schedule (Runnable command, long initialDelay,  long period, TimeUnit unit) {
        DelayedTask t = new DelayedTask
            (command, TimeUnit.nanoTime() + unit.toNanos(initialDelay),
             unit.toNanos(period));
        super.execute(t);
        return t;
    }
    
    /**
     * Creates a periodic action that may trigger first after the
     * given date, and subsequently with the given period.
     * @param command the task to execute.
     * @param initialDate the time to delay first execution.
     * @param period the period between successive executions.
     * @param unit the time unit of the  period parameter.
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public DelayedTask schedule(Runnable command, Date initialDate, long period, TimeUnit unit) {
        DelayedTask t = new DelayedTask
            (command, 
             TimeUnit.MILLISECONDS.toNanos(initialDate.getTime() - 
                                           System.currentTimeMillis()),
             unit.toNanos(period));
        super.execute(t);
        return t;
    }


    /**
     * Creates and executes a Future that may trigger after the given delay.
     * @param callable the function to execute.
     * @param delay the time from now to delay execution.
     * @param unit the time unit of the delay parameter.
     * @return a Future that can be used to extract result or cancel.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public <V> DelayedFutureTask<V> schedule(Callable<V> callable, long delay,  TimeUnit unit) {
        DelayedFutureTask<V> t = new DelayedFutureTask<V>
            (callable, delay, unit);
        super.execute(t);
        return t;
    }

    /**
     * Creates and executes a one-shot action that may trigger after
     * the given date.
     * @param callable the function to execute.
     * @param date the time to commence excution.
     * @return a Future that can be used to extract result or cancel.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public <V> DelayedFutureTask<V> schedule(Callable<V> callable, Date date) {
        DelayedFutureTask<V> t = new DelayedFutureTask<V>
            (callable, date);
        super.execute(t);
        return t;
    }

    /**
     * Execute command with zero required delay
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     * <tt>RejectedExecutionHandler</tt>, if task cannot be accepted
     * for execution because the executor has been shut down.
     */
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.NANOSECONDS);
    }

    /**
     * If executed task was periodic, cause the task for the next
     * period to execute.
     */
    protected void afterExecute(Runnable r, Throwable t) { 
        if (isShutdown()) 
            return;
        super.afterExecute(r, t);
        DelayedTask d = (DelayedTask)r;
        DelayedTask next = d.nextTask();
        if (next == null) 
            return;
        try {
            super.execute(next);
        }
        catch(RejectedExecutionException ex) {
            // lost race to detect shutdown; ignore
        }
    }
}


