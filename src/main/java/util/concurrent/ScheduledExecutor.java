/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * An <tt>Executor</tt> that can schedule command to run after a given
 * delay, or to execute periodically. This class is preferable to
 * <tt>java.util.Timer</tt> when multiple worker threads are needed,
 * or when the additional flexibility or capabilities of
 * <tt>ThreadPoolExecutor</tt> (which this class extends) are
 * required.
 *
 * <p> The <tt>schedule</tt> methods create tasks with various delays
 * and return a task object that can be used to cancel or check
 * execution. The <tt>scheduleAtFixedRate</tt> and
 * <tt>scheduleWithFixedDelay</tt> methods create and execute tasks
 * that run periodically until cancelled.  Commands submitted using
 * the <tt>execute</tt> method are scheduled with a requested delay of
 * zero.
 *
 * <p> Delayed tasks execute no sooner than they are enabled, but
 * without any real-time guarantees about when, after they are enabled
 * they will commence. Tasks tied for the same execution time are
 * enabled in first-in-first-out (FIFO) order of submission. An
 * internal {@link DelayQueue} used for scheduling relies on relative
 * delays, which may drift from absolute times (as returned by
 * <tt>System.currentTimeMillis</tt>) over sufficiently long periods.
 *
 * @since 1.5
 * @see Executors
 *
 * @spec JSR-166
 * @author Doug Lea
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
    public static class DelayedTask extends CancellableTask implements Delayed {
        /** Sequence number to break ties FIFO */
        private final long sequenceNumber;
        /** The time the task is enabled to execute in nanoTime units */
        private final long time;
        /** The delay forllowing next time, or <= 0 if non-periodic */
        private final long period;
        /** true if at fixed rate; false if fixed delay */
        private final boolean rateBased; 

        /**
         * Creates a one-shot action with given nanoTime-based trigger time
         */
        DelayedTask(Runnable r, long ns) {
            super(r);
            this.time = ns;
            this.period = 0;
            rateBased = false;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * Creates a periodic action with given nano time and period
         */
        DelayedTask(Runnable r, long ns,  long period, boolean rateBased) {
            super(r);
            if (period <= 0)
                throw new IllegalArgumentException();
            this.time = ns;
            this.period = period;
            this.rateBased = rateBased;
            this.sequenceNumber = sequencer.getAndIncrement();
        }


        public long getDelay(TimeUnit unit) {
            return unit.convert(time - System.nanoTime(), 
                                TimeUnit.NANOSECONDS);
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

        /**
         * Return true if this is a periodic (not a one-shot) action.
         * @return true if periodic
         */
        public boolean isPeriodic() {
            return period > 0;
        }

        /**
         * Returns the period, or zero if non-periodic
         * @return the period
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
            long nextTime = period + (rateBased ? time : System.nanoTime());
            return new DelayedTask(getRunnable(), nextTime, period, rateBased);
        }

    }

    /**
     * A delayed result-bearing action.
     */
    public static class DelayedFutureTask<V> extends DelayedTask implements Future<V> {
        /**
         * Creates a Future that may trigger after the given delay.
         */
        DelayedFutureTask(Callable<V> callable, long delay,  TimeUnit unit) {
            // must set after super ctor call to use inner class
            super(null, System.nanoTime() + unit.toNanos(delay));
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
     * use a DelayQueue<DelayedTask> as a BlockingQueue<Runnable>
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
     * Creates and executes a one-shot action that becomes enabled after
     * the given delay.
     * @param command the task to execute.
     * @param delay the time from now to delay execution.
     * @param unit the time unit of the delay parameter.
     * @return a handle that can be used to cancel the task.
     */

    public DelayedTask schedule(Runnable command, long delay,  TimeUnit unit) {
        DelayedTask t = new DelayedTask(command, System.nanoTime() + unit.toNanos(delay));
        super.execute(t);
        return t;
    }

    /**
     * Creates and executes a one-shot action that becomes enabled
     * after the given date.
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
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the given
     * period; that is executions will commence after
     * <tt>initialDelay</tt> then <tt>initialDelay+period</tt>, then
     * <tt>initialDelay + 2 * period</tt>, and so on.
     * @param command the task to execute.
     * @param initialDelay the time to delay first execution.
     * @param period the period between successive executions.
     * @param unit the time unit of the delay and period parameters
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public DelayedTask scheduleAtFixedRate(Runnable command, long initialDelay,  long period, TimeUnit unit) {
        DelayedTask t = new DelayedTask
            (command, System.nanoTime() + unit.toNanos(initialDelay),
             unit.toNanos(period), true);
        super.execute(t);
        return t;
    }
    
    /**
     * Creates a periodic action that becomes enabled first after the
     * given date, and subsequently with the given period
     * period; that is executions will commence after
     * <tt>initialDate</tt> then <tt>initialDate+period</tt>, then
     * <tt>initialDate + 2 * period</tt>, and so on.
     * @param command the task to execute.
     * @param initialDate the time to delay first execution.
     * @param period the period between commencement of successive
     * executions.
     * @param unit the time unit of the  period parameter.
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public DelayedTask scheduleAtFixedRate(Runnable command, Date initialDate, long period, TimeUnit unit) {
        DelayedTask t = new DelayedTask
            (command, 
             TimeUnit.MILLISECONDS.toNanos(initialDate.getTime() - 
                                           System.currentTimeMillis()),
             unit.toNanos(period), true);
        super.execute(t);
        return t;
    }

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and and subsequently with the
     * given delay between the termination of one execution and the
     * commencement of the next.
     * @param command the task to execute.
     * @param initialDelay the time to delay first execution.
     * @param delay the delay between the termination of one
     * execution and the commencement of the next.
     * @param unit the time unit of the delay and delay parameters
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public DelayedTask scheduleWithFixedDelay(Runnable command, long initialDelay,  long delay, TimeUnit unit) {
        DelayedTask t = new DelayedTask
            (command, System.nanoTime() + unit.toNanos(initialDelay),
             unit.toNanos(delay), false);
        super.execute(t);
        return t;
    }
    
    /**
     * Creates a periodic action that becomes enabled first after the
     * given date, and subsequently with the given delay between
     * the termination of one execution and the commencement of the
     * next.
     * @param command the task to execute.
     * @param initialDate the time to delay first execution.
     * @param delay the delay between the termination of one
     * execution and the commencement of the next.
     * @param unit the time unit of the  delay parameter.
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */
    public DelayedTask scheduleWithFixedDelay(Runnable command, Date initialDate, long delay, TimeUnit unit) {
        DelayedTask t = new DelayedTask
            (command, 
             TimeUnit.MILLISECONDS.toNanos(initialDate.getTime() - 
                                           System.currentTimeMillis()),
             unit.toNanos(delay), false);
        super.execute(t);
        return t;
    }


    /**
     * Creates and executes a Future that becomes enabled after the
     * given delay.
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
     * Creates and executes a one-shot action that becomes enabled after
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
     * @param r the task (assumed to be a DelayedTask)
     * @param t the exception
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


