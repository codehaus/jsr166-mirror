/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads.
 *
 * <p>Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 *
 * <p>This class is designed to ge highly tunable. 
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility hooks.  For
 * example, it can be configured to create a new thread for each task,
 * or even to execute tasks sequentially in a single thread, in
 * addition to its most common configuration, which reuses a pool of
 * threads.  However, programmers are urged to use the more convenient
 * factory methods <tt>newCachedThreadPool</tt> (unbounded thread
 * pool, with automatic thread reclamation),
 * <tt>newFixedThreadPool</tt> (fixed size thread pool),
 * <tt>newSingleThreadPoolExecutor</tt> (single background thread for
 * execution of tasks), and <tt>newThreadPerTaskExeceutor</tt>
 * (execute each task in a new thread), that preconfigure settings for
 * the most common usage scenarios.
 *
 * <p>Each <tt>ThreadPoolExecutor</tt> also maintains some basic
 * statistics, such as the number of completed tasks, that may be
 * useful for monitoring and tuning executors.
 *
 * <h3>Tuning guide</h3>
 * <dl>
 *
 * <dt>Core and maximum pool size</dt>
 *
 * <dd>A ThreadPoolExecutor will automatically adjust the pool size
 * according to the bounds set by corePoolSize and maximumPoolSize.
 * When a new task is submitted, and fewer than corePoolSize threads
 * are running, a new thread is created to handle the request, even if
 * other worker threads are idle.  If there are more than the
 * corePoolSize but less than maximumPoolSize threads running, a new
 * thread will be created only if the queue is full.  By setting
 * corePoolSize and maximumPoolSize the same, you create a fixed-size
 * thread pool. By default, even core threads are only created and
 * started when needed by new tasks, but this can be overridden
 * dynamically using method <tt>prestartCoreThread</tt>.
 * </dd>
 *
 * <dt>Keep-alive</dt>
 *
 * <dd>The keepAliveTime determines what happens to idle threads.  If
 * the pool currently has more than the core number of threads, excess
 * threads will be terminated if they have been idle for more than the
 * keepAliveTime.</dd>
 *
 * <dt>Queueing</dt>
 *
 * <dd>You are free to specify the queuing mechanism used to handle
 * submitted tasks.  A good default is to use a zero-capacity
 * <tt>SynchronousQueue</tt> to to hand off work to threads.  This is
 * a safe, conservative policy that avoids lockups when handling sets
 * of requests that might have internal dependencies.  Using an
 * unbounded queue (for example a <tt>LinkedBlockingQueue</tt>) will
 * cause new tasks to be queued in cases where all corePoolSize
 * threads are busy, so no more than corePoolSize threads will be
 * craated.  This may be appropriate when each task is completely
 * independent of others, so tasks cannot affect each others
 * execution. For example, in a web page server.  When given a choice,
 * this pool always prefers adding a new thread rather than queueing
 * if there are currently fewer than the current getCorePoolSize
 * threads running, but otherwise always prefers queuing a request
 * rather than adding a new thread.
 *
 * <p>While queuing can be useful in smoothing out transient bursts of
 * requests, especially in socket-based services, it is not very well
 * behaved when commands continue to arrive on average faster than
 * they can be processed.  Queue sizes and maximum pool sizes can
 * often be traded off for each other. Using large queues and small
 * pools minimizes CPU usage, OS resources, and context-switching
 * overhead, but can lead to artifically low throughput.  If tasks
 * frequently block (for example if they are I/O bound), a system may
 * be able to schedule time for more threads than you otherwise
 * allow. Use of small queues or queueless handoffs generally requires
 * larger pool sizes, which keeps CPUs busier but may encounter
 * unacceptable scheduling overhead, which also decreases throughput.
 * </dd>
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>New threads are created using a ThreadFactory.  By default,
 * threads are created simply with the new Thread(Runnable)
 * constructor, but by supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc.  </dd>
 *
 * <dt>Before and after intercepts</dt>
 *
 * <dd>This class has overridable methods that which are called before
 * and after execution of each task.  These can be used to manipulate
 * the execution environment (for example, reinitializing
 * ThreadLocals), gather statistics, or perform logging.  </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd>There are a number of factors which can bound the number of
 * tasks which can execute at once, including the maximum pool size
 * and the queuing mechanism used.  If the executor determines that a
 * task cannot be executed because it has been refused by the queue
 * and no threads are available, or because the executor has been shut
 * down, the RejectedExecutionHandler's rejectedExecution method is
 * invoked. The default (<tt>AbortPolicy</tt>) handler throws a runtime
 * {@link RejectedExecutionException} upon rejection.  </dd>
 *
 * <dt>Termination</dt>
 *
 * <dd>ThreadPoolExecutor supports two shutdown options, immediate and
 * graceful.  In an immediate shutdown, any threads currently
 * executing are interrupted, and any tasks not yet begun are returned
 * from the shutdownNow call.  In a graceful shutdown, all queued
 * tasks are allowed to run, but new tasks may not be submitted.
 * </dd>
 *
 * </dl>
 *
 * @since 1.5
 * @see RejectedExecutionHandler
 * @see Executors
 * @see ThreadFactory
 *
 * @spec JSR-166
 * @revised $Date: 2003/08/24 23:32:25 $
 * @editor $Author: dl $
 * @author Doug Lea
 */
public class ThreadPoolExecutor implements ExecutorService {
    /**
     * Queue used for holding tasks and handing off to worker threads.
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on updates to poolSize, corePoolSize, maximumPoolSize, and
     * workers set.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Set containing all worker threads in pool.
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Timeout in nanosecods for idle threads waiting for work.
     * Threads use this timeout only when there are more than
     * corePoolSize present. Otherwise they wait forever for new work.
     */
    private volatile long  keepAliveTime;

    /**
     * Core pool size, updated only while holding mainLock,
     * but volatile to allow concurrent readability even
     * during updates.
     */
    private volatile int   corePoolSize;

    /**
     * Maximum pool size, updated only while holding mainLock
     * but volatile to allow concurrent readability even
     * during updates.
     */
    private volatile int   maximumPoolSize;

    /**
     * Current pool size, updated only while holding mainLock
     * but volatile to allow concurrent readability even
     * during updates.
     */
    private volatile int   poolSize;

    /**
     * Lifecycle state
     */
    private volatile int runState;

    // Special values for runState
    /** Normal, not-shutdown mode */
    private static final int RUNNING    = 0;
    /** Controlled shutdown mode */
    private static final int SHUTDOWN   = 1;
    /** Immediate shutdown mode */
    private static final int STOP       = 2;
    /** Final state */
    private static final int TERMINATED = 3;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    private volatile RejectedExecutionHandler handler = defaultHandler;

    /**
     * Factory for new threads.
     */
    private volatile ThreadFactory threadFactory = defaultThreadFactory;

    /**
     * Tracks largest attained pool size.
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads.
     */
    private long completedTaskCount;

    /**
     * The default thread factory
     */
    private static final ThreadFactory defaultThreadFactory =
        new ThreadFactory() {
            public Thread newThread(Runnable r) {
                return new Thread(r);
            }
        };

    /**
     * The default rejectect execution handler
     */
    private static final RejectedExecutionHandler defaultHandler =
        new AbortPolicy();

    /**
     * Invoke the rejected execution handler for the given command.
     */
    void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Create and return a new thread running firstTask as its first
     * task. Call only while holding mainLock
     * @param firstTask the task the new thread should run first (or
     * null if none)
     * @return the new thread
     */
    private Thread addThread(Runnable firstTask) {
        Worker w = new Worker(firstTask);
        Thread t = threadFactory.newThread(w);
        w.thread = t;
        workers.add(w);
        int nt = ++poolSize;
        if (nt > largestPoolSize)
            largestPoolSize = nt;
        return t;
    }



    /**
     * Create and start a new thread running firstTask as its first
     * task, only if less than corePoolSize threads are running.
     * @param firstTask the task the new thread should run first (or
     * null if none)
     * @return true if successful.
     */
    private boolean addIfUnderCorePoolSize(Runnable firstTask) {
        Thread t = null;
        mainLock.lock();
        try {
            if (poolSize < corePoolSize)
                t = addThread(firstTask);
        } finally {
            mainLock.unlock();
        }
        if (t == null)
            return false;
        t.start();
        return true;
    }

    /**
     * Create and start a new thread only if less than maximumPoolSize
     * threads are running.  The new thread runs as its first task the
     * next task in queue, or if there is none, the given task.
     * @param firstTask the task the new thread should run first (or
     * null if none)
     * @return null on failure, else the first task to be run by new thread.
     */
    private Runnable addIfUnderMaximumPoolSize(Runnable firstTask) {
        Thread t = null;
        Runnable next = null;
        mainLock.lock();
        try {
            if (poolSize < maximumPoolSize) {
                next = workQueue.poll();
                if (next == null)
                    next = firstTask;
                t = addThread(next);
            }
        } finally {
            mainLock.unlock();
        }
        if (t == null)
            return null;
        t.start();
        return next;
    }


    /**
     * Get the next task for a worker thread to run.
     * @return the task
     * @throws InterruptedException if interrupted while waiting for task
     */
    private Runnable getTask() throws InterruptedException {
        for (;;) {
            switch(runState) {
            case RUNNING: {
                if (poolSize <= corePoolSize)   // untimed wait if core
                    return workQueue.take();
                
                long timeout = keepAliveTime;
                if (timeout <= 0) // die immediately for 0 timeout
                    return null;
                Runnable r =  workQueue.poll(timeout, TimeUnit.NANOSECONDS);
                if (r != null)
                    return r;
                if (poolSize > corePoolSize) // timed out
                    return null;
                // else, after timeout, pool shrank so shouldn't die, so retry
                break;
            }

            case SHUTDOWN: {
                // Help drain queue 
                Runnable r = workQueue.poll();
                if (r != null)
                    return r;
                    
                // Check if can terminate
                if (workQueue.isEmpty()) {
                    interruptIdleWorkers();
                    return null;
                }

                // There could still be delayed tasks in queue.
                // Wait for one, re-checking state upon interruption
                try {
                    return workQueue.take();
                }
                catch(InterruptedException ignore) {
                }
                break;
            }

            case STOP:
                return null;
            default:
                assert false; 
            }
        }
    }

    /**
     * Wake up all threads that might be waiting for tasks.
     */
    void interruptIdleWorkers() {
        mainLock.lock();
        try {
            for (Iterator<Worker> it = workers.iterator(); it.hasNext(); )
                it.next().interruptIfIdle();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Perform bookkeeping for a terminated worker thread.
     * @param w the worker
     */
    private void workerDone(Worker w) {
        mainLock.lock();
        try {
            completedTaskCount += w.completedTasks;
            workers.remove(w);
            if (--poolSize > 0)
                return;

            // Else, this is the last thread. Deal with potential shutdown.

            int state = runState;
            assert state != TERMINATED;

            if (state != STOP) {
                // If there are queued tasks but no threads, create
                // replacement.
                Runnable r = workQueue.poll();
                if (r != null) {
                    addThread(r).start();
                    return;
                }

                // If there are some (presumably delayed) tasks but
                // none pollable, create an idle replacement to wait.
                if (!workQueue.isEmpty()) { 
                    addThread(null).start();
                    return;
                }

                // Otherwise, we can exit without replacement
                if (state == RUNNING)
                    return;
            }

            // Either state is STOP, or state is SHUTDOWN and there is
            // no work to do. So we can terminate.
            runState = TERMINATED;
            termination.signalAll();
            // fall through to call terminate() outside of lock.
        } finally {
            mainLock.unlock();
        }

        assert runState == TERMINATED;
        terminated(); 
    }

    /**
     *  Worker threads
     */
    private class Worker implements Runnable {

        /**
         * The runLock is acquired and released surrounding each task
         * execution. It mainly protects against interrupts that are
         * intended to cancel the worker thread from instead
         * interrupting the task being run.
         */
        private final ReentrantLock runLock = new ReentrantLock();

        /**
         * Initial task to run before entering run loop
         */
        private Runnable firstTask;

        /**
         * Per thread completed task counter; accumulated
         * into completedTaskCount upon termination.
         */
        volatile long completedTasks;

        /**
         * Thread this worker is running in.  Acts as a final field,
         * but cannot be set until thread is created.
         */
        Thread thread;

        Worker(Runnable firstTask) {
            this.firstTask = firstTask;
        }

        boolean isActive() {
            return runLock.isLocked();
        }

        /**
         * Interrupt thread if not running a task
         */
        void interruptIfIdle() {
            if (runLock.tryLock()) {
                try {
                    thread.interrupt();
                } finally {
                    runLock.unlock();
                }
            }
        }

        /**
         * Cause thread to die even if running a task.
         */
        void interruptNow() {
            thread.interrupt();
        }

        /**
         * Run a single task between before/after methods.
         */
        private void runTask(Runnable task) {
            runLock.lock();
            try {
                // Abort now if immediate cancel.  Otherwise, we have
                // committed to run this task.
                if (runState == STOP)
                    return;

                Thread.interrupted(); // clear interrupt status on entry
                boolean ran = false;
                beforeExecute(thread, task);
                try {
                    task.run();
                    ran = true;
                    afterExecute(task, null);
                    ++completedTasks;
                } catch(RuntimeException ex) {
                    if (!ran)
                        afterExecute(task, ex);
                    // Else the exception occurred within
                    // afterExecute itself in which case we don't
                    // want to call it again.
                    throw ex;
                }
            } finally {
                runLock.unlock();
            }
        }

        /**
         * Main run loop
         */
        public void run() {
            try {
                for (;;) {
                    Runnable task;
                    if (firstTask != null) {
                        task = firstTask;
                        firstTask = null;
                    } else {
                        task = getTask();
                        if (task == null)
                            break;
                    }
                    runTask(task);
                    task = null; // unnecessary but can help GC
                }
            } catch(InterruptedException ie) {
                // fall through
            } finally {
                workerDone(this);
            }
        }
    }

    // Public methods

    /**
     * Creates a new <tt>ThreadPoolExecutor</tt> with the given
     * initial parameters.  It may be more convenient to use one of
     * the {@link @Executors} factory methods instead of this general
     * purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the
     * pool, even if they are idle.
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool.
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the keepAliveTime
     * argument.
     * @param workQueue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @throws IllegalArgumentException if corePoolSize, or
     * keepAliveTime less than zero, or if maximumPoolSize less than or
     * equal to zero, or if corePoolSize greater than maximumPoolSize.
     * @throws NullPointerException if <tt>workQueue</tt> is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             defaultThreadFactory, defaultHandler);
    }

    /**
     * Creates a new <tt>ThreadPoolExecutor</tt> with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the
     * pool, even if they are idle.
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool.
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the keepAliveTime
     * argument.
     * @param workQueue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @param threadFactory the factory to use when the executor
     * creates a new thread.
     * @throws IllegalArgumentException if corePoolSize, or
     * keepAliveTime less than zero, or if maximumPoolSize less than or
     * equal to zero, or if corePoolSize greater than maximumPoolSize.
     * @throws NullPointerException if <tt>workQueue</tt>
     * or <tt>threadFactory</tt> are null.
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {

        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             threadFactory, defaultHandler);
    }

    /**
     * Creates a new <tt>ThreadPoolExecutor</tt> with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the
     * pool, even if they are idle.
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool.
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the keepAliveTime
     * argument.
     * @param workQueue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached.
     * @throws IllegalArgumentException if corePoolSize, or
     * keepAliveTime less than zero, or if maximumPoolSize less than or
     * equal to zero, or if corePoolSize greater than maximumPoolSize.
     * @throws NullPointerException if <tt>workQueue</tt>
     * or  <tt>handler</tt> are null.
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             defaultThreadFactory, handler);
    }

    /**
     * Creates a new <tt>ThreadPoolExecutor</tt> with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the
     * pool, even if they are idle.
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool.
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the keepAliveTime
     * argument.
     * @param workQueue the queue to use for holding tasks before the
     * are executed. This queue will hold only the <tt>Runnable</tt>
     * tasks submitted by the <tt>execute</tt> method.
     * @param threadFactory the factory to use when the executor
     * creates a new thread.
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached.
     * @throws IllegalArgumentException if corePoolSize, or
     * keepAliveTime less than zero, or if maximumPoolSize less than or
     * equal to zero, or if corePoolSize greater than maximumPoolSize.
     * @throws NullPointerException if <tt>workQueue</tt>
     * or <tt>threadFactory</tt> or <tt>handler</tt> are null.
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }


    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current <tt>RejectedExecutionHandler</tt>.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     * <tt>RejectedExecutionHandler</tt>, if task cannot be accepted
     * for execution
     */
    public void execute(Runnable command) {
        for (;;) {
            if (runState != RUNNING) {
                reject(command);
                return;
            }
            if (poolSize < corePoolSize && addIfUnderCorePoolSize(command))
                return;
            if (workQueue.offer(command))
                return;
            Runnable r = addIfUnderMaximumPoolSize(command);
            if (r == command)
                return;
            if (r == null) {
                reject(command);
                return;
            }
            // else retry
        }
    }

    public void shutdown() {
        mainLock.lock();
        try {
            if (runState == RUNNING) // don't override shutdownNow
                runState = SHUTDOWN;
            for (Iterator<Worker> it = workers.iterator(); it.hasNext(); )
                it.next().interruptIfIdle();
        } finally {
            mainLock.unlock();
        }
    }


    public List shutdownNow() {
        mainLock.lock();
        try {
            if (runState != TERMINATED)
                runState = STOP;
            for (Iterator<Worker> it = workers.iterator(); it.hasNext(); )
                it.next().interruptNow();
        } finally {
            mainLock.unlock();
        }
        return Arrays.asList(workQueue.toArray());
    }

    public boolean isShutdown() {
        return runState != RUNNING;
    }

    /** 
     * Return true if this executor is in the process of terminating
     * after <tt>shutdown</tt> or <tt>shutdownNow</tt> but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of <tt>true</tt> reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     * @return true if terminating but not yet terminated.
     */
    public boolean isTerminating() {
        return runState == STOP;
    }

    public boolean isTerminated() {
        return runState == TERMINATED;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        mainLock.lock();
        try {
            return termination.await(timeout, unit);
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes <tt>shutdown</tt> when this executor is no longer
     * referenced.
     */ 
    protected void finalize()  {
        shutdown();
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieveing the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
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
        return getQueue().remove(task);
    }


    /**
     * Tries to remove from the work queue all {@link Cancellable}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove all such tasks in
     * the presence of interference by other threads.
     */

    public void purge() {
        // Fail if we encounter interference during traversal
        try {
            Iterator<Runnable> it = getQueue().iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Cancellable) {
                    Cancellable c = (Cancellable)r;
                    if (c.isCancelled())
                        it.remove();
                }
            }
        }
        catch(ConcurrentModificationException ex) {
            return; 
        }
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if <tt>corePoolSize</tt>
     * less than zero
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        mainLock.lock();
        try {
            int extra = this.corePoolSize - corePoolSize;
            this.corePoolSize = corePoolSize;
            if (extra > 0 && poolSize > corePoolSize) {
                Iterator<Worker> it = workers.iterator();
                while (it.hasNext() &&
                       extra > 0 &&
                       poolSize > corePoolSize &&
                       workQueue.remainingCapacity() == 0) {
                    it.next().interruptIfIdle();
                    --extra;
                }
            }

        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Start a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return <tt>false</tt>
     * if all core threads have already been started.
     * @return true if a thread was started
     */ 
    public boolean prestartCoreThread() {
        return addIfUnderCorePoolSize(null);
    }

    /**
     * Start all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. 
     * @return the number of threads started.
     */ 
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addIfUnderCorePoolSize(null))
            ++n;
        return n;
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if maximumPoolSize less than zero or
     * the {@link #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        mainLock.lock();
        try {
            int extra = this.maximumPoolSize - maximumPoolSize;
            this.maximumPoolSize = maximumPoolSize;
            if (extra > 0 && poolSize > maximumPoolSize) {
                Iterator<Worker> it = workers.iterator();
                while (it.hasNext() &&
                       extra > 0 &&
                       poolSize > maximumPoolSize) {
                    it.next().interruptIfIdle();
                    --extra;
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     * @param time the time to wait.  A time value of zero will cause
     * excess threads to terminate immediately after executing tasks.
     * @param unit  the time unit of the time argument
     * @throws IllegalArgumentException if time less than zero
     * @see #getKeepAliveTime
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        this.keepAliveTime = unit.toNanos(time);
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * which threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        mainLock.lock();
        try {
            int n = 0;
            for (Iterator<Worker> it = workers.iterator(); it.hasNext(); ) {
                if (it.next().isActive())
                    ++n;
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation, but one that does not ever
     * decrease across successive calls.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Iterator<Worker> it = workers.iterator(); it.hasNext(); ) {
                Worker w = it.next();
                n += w.completedTasks;
                if (w.isActive())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Iterator<Worker> it = workers.iterator(); it.hasNext(); )
                n += it.next().completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method may be used to re-initialize
     * ThreadLocals, or to perform logging. Note: To properly nest
     * multiple overridings, subclasses should generally invoke
     * <tt>super.beforeExecute</tt> at the end of this method.
     *
     * @param t the thread that will run task r.
     * @param r the task that will be executed.
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given
     * Runnable.  If non-null, the Throwable is the uncaught exception
     * that caused execution to terminate abruptly. Note: To properly
     * nest multiple overridings, subclasses should generally invoke
     * <tt>super.afterExecute</tt> at the beginning of this method.
     *
     * @param r the runnable that has completed.
     * @param t the exception that cause termination, or null if
     * execution completed normally.
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * <tt>super.terminated</tt> within this method.
     */
    protected void terminated() { }

    /**
     * A handler for unexecutable tasks that runs these tasks directly
     * in the calling thread of the <tt>execute</tt> method.  This is
     * the default <tt>RejectedExecutionHandler</tt>.
     */
   public static class CallerRunsPolicy implements RejectedExecutionHandler {

        /**
         * Constructs a <tt>CallerRunsPolicy</tt>.
         */
        public CallerRunsPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for unexecutable tasks that throws a
     * <tt>RejectedExecutionException</tt>.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {

        /**
         * Constructs a <tt>AbortPolicy</tt>.
         */
        public AbortPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException();
        }
    }

    /**
     * A handler for unexecutable tasks that waits until the task can be
     * submitted for execution.
     */
    public static class WaitPolicy implements RejectedExecutionHandler {
        /**
         * Constructs a <tt>WaitPolicy</tt>.
         */
        public WaitPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                try {
                    e.getQueue().put(r);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RejectedExecutionException(ie);
                }
            }
        }
    }

    /**
     * A handler for unexecutable tasks that silently discards these tasks.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {

        /**
         * Constructs <tt>DiscardPolicy</tt>.
         */
        public DiscardPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for unexecutable tasks that discards the oldest
     * unhandled request.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Constructs a <tt>DiscardOldestPolicy</tt> for the given executor.
         */
        public DiscardOldestPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
