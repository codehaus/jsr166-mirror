/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * An <tt>Executor</tt> that can schedule commands to run after a given
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
 * <p>While this class inherits from {@link ThreadPoolExecutor}, a few
 * of the inherited tuning methods are not especially useful for
 * it. In particular, because a <tt>ScheduledExecutor</tt> always acts
 * as a fixed-sized pool using <tt>corePoolSize</tt> threads and an
 * unbounded queue, adjustments to <tt>maximumPoolSize</tt> have no
 * useful effect.
 *
 * @since 1.5
 * @see Executors
 *
 * @spec JSR-166
 * @author Doug Lea
 */
public class ScheduledExecutor extends ThreadPoolExecutor {

    /**
     * False if should cancel/suppress periodic tasks on shutdown.
     */
    private volatile boolean continueExistingPeriodicTasksAfterShutdown;

    /**
     * False if should cancel non-periodic tasks on shutdown.
     */
    private volatile boolean executeExistingDelayedTasksAfterShutdown = true;


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
            long d =  unit.convert(time - System.nanoTime(), 
                                TimeUnit.NANOSECONDS);
            return d;
        }

        public int compareTo(Object other) {
            if (other == this) // compare zero ONLY if same object
                return 0;
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
         * Returns the period, or zero if non-periodic.
         *
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
        DelayedFutureTask(Callable<V> callable, long triggerTime) {
            // must set after super ctor call to use inner class
            super(null, triggerTime);
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
    private static class DelayedWorkQueue extends AbstractCollection<Runnable> implements BlockingQueue<Runnable> {
        private final DelayQueue<DelayedTask> dq = new DelayQueue<DelayedTask>();
        public Runnable poll() { return dq.poll(); }
        public Runnable peek() { return dq.peek(); }
        public Runnable take() throws InterruptedException { return dq.take(); }
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            return dq.poll(timeout, unit);
        }

        public boolean add(Runnable x) { return dq.add((DelayedTask)x); }
        public boolean offer(Runnable x) { return dq.offer((DelayedTask)x); }
        public void put(Runnable x) throws InterruptedException {
            dq.put((DelayedTask)x); 
        }
        public boolean offer(Runnable x, long timeout, TimeUnit unit) throws InterruptedException {
            return dq.offer((DelayedTask)x, timeout, unit);
        }

        public Runnable remove() { return dq.remove(); }
        public Runnable element() { return dq.element(); }
        public void clear() { dq.clear(); }

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
     * Specialized variant of ThreadPoolExecutor.execute for delayed tasks.
     */
    private void delayedExecute(Runnable command) {
        if (isShutdown()) {
            reject(command);
            return;
        }
        // Prestart a thread if necessary. We cannot prestart it
        // running the task because the task (probably) shouldn't be
        // run yet, so thread will just idle until delay elapses.
        if (getPoolSize() < getCorePoolSize())
            prestartCoreThread();
            
        getQueue().offer(command);
    }

    /**
     * Creates and executes a one-shot action that becomes enabled after
     * the given delay.
     * @param command the task to execute.
     * @param delay the time from now to delay execution.
     * @param unit the time unit of the delay parameter.
     * @return a handle that can be used to cancel the task.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     */

    public DelayedTask schedule(Runnable command, long delay,  TimeUnit unit) {
        long triggerTime = System.nanoTime() + unit.toNanos(delay);
        DelayedTask t = new DelayedTask(command, triggerTime);
        delayedExecute(t);
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
        long triggerTime = System.nanoTime() + 
            TimeUnit.MILLISECONDS.toNanos(date.getTime() - 
                                          System.currentTimeMillis()); 
        DelayedTask t = new DelayedTask(command, triggerTime);
        delayedExecute(t);
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
        long triggerTime = System.nanoTime() + unit.toNanos(initialDelay);
        DelayedTask t = new DelayedTask(command, 
                                        triggerTime,
                                        unit.toNanos(period), 
                                        true);
        delayedExecute(t);
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
        long triggerTime = System.nanoTime() + 
            TimeUnit.MILLISECONDS.toNanos(initialDate.getTime() - 
                                          System.currentTimeMillis()); 
        DelayedTask t = new DelayedTask(command, 
                                        triggerTime,
                                        unit.toNanos(period), 
                                        true);
        delayedExecute(t);
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
        long triggerTime = System.nanoTime() + unit.toNanos(initialDelay);
        DelayedTask t = new DelayedTask(command, 
                                        triggerTime,
                                        unit.toNanos(delay), 
                                        false);
        delayedExecute(t);
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
        long triggerTime = System.nanoTime() + 
            TimeUnit.MILLISECONDS.toNanos(initialDate.getTime() - 
                                          System.currentTimeMillis()); 
        DelayedTask t = new DelayedTask(command, 
                                        triggerTime,
                                        unit.toNanos(delay), 
                                        false);
        delayedExecute(t);
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
        long triggerTime = System.nanoTime() + unit.toNanos(delay);
        DelayedFutureTask<V> t = new DelayedFutureTask<V>(callable, 
                                                          triggerTime);
        delayedExecute(t);
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
        long triggerTime = System.nanoTime() + 
            TimeUnit.MILLISECONDS.toNanos(date.getTime() - 
                                          System.currentTimeMillis()); 
        DelayedFutureTask<V> t = new DelayedFutureTask<V>(callable, 
                                                          triggerTime);
        delayedExecute(t);
        return t;
    }

    /**
     * Execute command with zero required delay. This has effect
     * equivalent to <tt>schedule(command, 0, anyUnit)</tt>.  Note
     * that inspections of the queue and of the list returned by
     * <tt>shutdownNow</tt> will access the zero-delayed
     * <tt>DelayedTask</tt>, not the <tt>command</tt> itself.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     * <tt>RejectedExecutionHandler</tt>, if task cannot be accepted
     * for execution because the executor has been shut down.
     */
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.NANOSECONDS);
    }


    /**
     * Set policy on whether to continue executing existing periodic
     * tasks even when this executor has been <tt>shutdown</tt>. In
     * this case, these tasks will only terminate upon
     * <tt>shutdownNow</tt>, or after setting the policy to
     * <tt>false</tt> when already shutdown. This value is by default
     * false.
     * @param value if true, continue after shutdown, else don't.
     */
    public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value) {
        continueExistingPeriodicTasksAfterShutdown = value;
        if (!value && isShutdown())
            cancelUnwantedTasks();
    }

    /**
     * Get the policy on whether to continue executing existing
     * periodic tasks even when this executor has been
     * <tt>shutdown</tt>. In this case, these tasks will only
     * terminate upon <tt>shutdownNow</tt> or after setting the policy
     * to <tt>false</tt> when already shutdown. This value is by
     * default false.
     * @return true if will continue after shutdown.
     */
    public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
        return continueExistingPeriodicTasksAfterShutdown;
    }

    /**
     * Set policy on whether to execute existing delayed
     * tasks even when this executor has been <tt>shutdown</tt>. In
     * this case, these tasks will only terminate upon
     * <tt>shutdownNow</tt>, or after setting the policy to
     * <tt>false</tt> when already shutdown. This value is by default
     * true.
     * @param value if true, execute after shutdown, else don't.
     */
    public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value) {
        executeExistingDelayedTasksAfterShutdown = value;
        if (!value && isShutdown())
            cancelUnwantedTasks();
    }

    /**
     * Set policy on whether to execute existing delayed
     * tasks even when this executor has been <tt>shutdown</tt>. In
     * this case, these tasks will only terminate upon
     * <tt>shutdownNow</tt>, or after setting the policy to
     * <tt>false</tt> when already shutdown. This value is by default
     * true.
     * @return true if will execute after shutdown.
     */
    public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
        return executeExistingDelayedTasksAfterShutdown;
    }

    /**
     * Cancel and clear the queue of all tasks that should not be run
     * due to shutdown policy.
     */
    private void cancelUnwantedTasks() {
        boolean keepDelayed = getExecuteExistingDelayedTasksAfterShutdownPolicy();
        boolean keepPeriodic = getContinueExistingPeriodicTasksAfterShutdownPolicy();
        if (!keepDelayed && !keepPeriodic) 
            getQueue().clear();
        else if (keepDelayed || keepPeriodic) {
            Object[] entries = getQueue().toArray();
            for (int i = 0; i < entries.length; ++i) {
                DelayedTask t = (DelayedTask)entries[i];
                if (t.isPeriodic()? !keepPeriodic : !keepDelayed)
                    t.cancel(false);
            }
            entries = null;
            purge();
        }
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted. If the
     * <tt>ExecuteExistingDelayedTasksAfterShutdownPolicy</tt> has
     * been set <tt>false</tt>, existing delayed tasks whose delays
     * have not yet elapsed are cancelled. And unless the
     * <tt>ContinueExistingPeriodicTasksAfterShutdownPolicy>/tt> hase
     * been set <tt>true</tt>, future executions of existing periodic
     * tasks will be cancelled.
     */
    public void shutdown() {
        cancelUnwantedTasks();
        super.shutdown();
    }
            
    /**
     * Removes this task from internal queue if it is present, thus
     * causing it not to be run if it has not already started.  This
     * method may be useful as one part of a cancellation scheme.
     *
     * @param task the task to remove
     * @return true if the task was removed
     */
    public boolean remove(Runnable task) {
        if (task instanceof DelayedTask && getQueue().remove(task))
            return true;

        // The task might actually have been wrapped as a DelayedTask
        // in execute(), in which case we need to maually traverse
        // looking for it.

        DelayedTask wrap = null;
        Object[] entries = getQueue().toArray();
        for (int i = 0; i < entries.length; ++i) {
            DelayedTask t = (DelayedTask)entries[i];
            Runnable r = t.getRunnable();
            if (task.equals(r)) {
                wrap = t;
                break;
            }
        }
        entries = null;
        return wrap != null && getQueue().remove(wrap);
    }

    /**
     * If executed task was periodic, cause the task for the next
     * period to execute.
     * @param r the task (assumed to be a DelayedTask)
     * @param t the exception
     */
    protected void afterExecute(Runnable r, Throwable t) { 
        super.afterExecute(r, t);
        DelayedTask next = ((DelayedTask)r).nextTask();
        if (next != null &&
            (!isShutdown() ||
             (getContinueExistingPeriodicTasksAfterShutdownPolicy() && 
              !isTerminating())))
            getQueue().offer(next);

        // This might have been the final executed delayed task.  Wake
        // up threads to check.
        else if (isShutdown()) 
            interruptIdleWorkers();
    }
}
