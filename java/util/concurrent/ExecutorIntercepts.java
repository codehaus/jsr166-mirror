package java.util.concurrent;

import java.util.*;

/**
 * Class containing methods that are invoked during various points of
 * execution of an Executor, allowing fine-grained control and
 * monitoring.  All methods are default-implemented. The intended
 * usage is to create subclasses that selectively override only those
 * methods of interest.
 **/
public class ExecutorIntercepts implements ThreadFactory { 

    /**
     * Method allowing initialization of priorities, names, daemon
     * status, ThreadGroups, etc of worker threads.  This method is
     * called whenever a thread is added.  Default
     * implementation is to return <tt>new Thread(r)</tt>.
     * @param r the runnable that the thread will run upon <tt>start</tt>.
     * @param e the Executor.
     * @return the constructed thread.
     **/
    public Thread newThread(Runnable r, Executor e) {
        return new Thread(r);
    }

    /**
     * Method invoked prior to executing the given Runnable in given thread.
     * This method may for example be used to re-initialize ThreadLocals,
     * or to perform logging.
     * Default implementation is no-op.
     * @param t the thread that will run task r.
     * @param r the task that will be executed.
     * @param e the Executor
     **/
    public void beforeExecute(Thread t, Runnable r, Executor e) { }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * If non-null, the Throwable is the uncaught exception that
     * caused execution to terminate abruptly.
     * Default implementation is no-op.
     * @param r the runnable that has completed.
     * @param t the exception that cause termination, or null if
     * execution completed normally.
     * @param e the Executor
     **/
    public void afterExecute(Runnable r, Throwable t, Executor e) { }

    /**
     * Method invoked upon <tt>execute</tt> when a task cannot be
     * executed. This may occur when no more threads or queue slots
     * are available because their bounds would be exceeded, and also
     * upon shutdown of the executor. You can distinguish among these
     * cases by querying the executor.  Default implementation is to run
     * the command in the current thread unless <tt>p.isShutdown</tt>,
     * in which case to do nothing.
     * @param r the runnable task requested to be executed.
     * @param e the Executor
     * @return false if <tt>execute</tt> should be retried, else true.
     * You may alternatively throw an unchecked exception, which
     * will be propagated to the caller of <tt>execute</tt>.
     **/
    public boolean cannotExecute(Runnable r, Executor e) {
        try {
            if (!e.isShutdown())
                r.run();
        }
        finally {
            return true;
        }
    }

    /**
     * Method invoked when executor has terminated.
     * Default implementation is no-op.
     * @param p the Executor
     **/
    public void terminated(Executor e) { }
    
}

