package java.util.concurrent;

import java.util.*;

/**
 * Class containing methods that are invoked during various points of
 * execution of an Executor, allowing fine-grained control and
 * monitoring.  All methods have default implementions; subclasses can
 * selectively override only those methods of interest.
 **/
public class ExecutorIntercepts implements ThreadFactory { 

    /**
     * Method allowing initialization of priorities, names, daemon
     * status, ThreadGroups, etc of worker threads.  This method is
     * called whenever a thread is added.  Default
     * implementation returns <tt>new Thread(r)</tt>.
     * @param r the runnable that the thread will run upon <tt>start</tt>.
     * @param e the Executor.
     * @return the constructed thread.
     **/
    public Thread newThread(Runnable r, Executor e) {
        return new Thread(r);
    }

    /**
     * Method invoked prior to executing the given Runnable in given
     * thread.  This method may be used to re-initialize ThreadLocals,
     * or to perform logging.  Default implementation does nothing.
     * @param t the thread that will run task r.
     * @param r the task that will be executed.
     * @param e the Executor
     **/
    public void beforeExecute(Thread t, Runnable r, Executor e) { }

    /**
     * Method invoked upon completion of execution of the given
     * Runnable.  If non-null, the Throwable is the uncaught exception
     * that caused execution to terminate abruptly.  Default
     * implementation does nothing.
     * @param r the runnable that has completed.
     * @param t the exception that cause termination, or null if
     * execution completed normally.
     * @param e the Executor
     **/
    public void afterExecute(Runnable r, Throwable t, Executor e) { }

    /**
     * Method invoked when <tt>execute</tt> cannot execute a
     * task. This may occur when no more threads or queue slots are
     * available because their bounds would be exceeded, or upon
     * shutdown of the Executor. You can distinguish among these cases
     * by querying the Executor.  The default implementation runs the
     * command in the current thread, unless <tt>p.isShutdown</tt>, in
     * which case it does nothing.
     * @param r the runnable task requested to be executed.
     * @param e the Executor
     * @return false if <tt>execute</tt> should be retried, else true.
     * You may alternatively throw an unchecked exception, which
     * will be propagated to the caller of <tt>execute</tt>.
     **/
    public boolean cannotExecute(Runnable r, Executor e) {
        if (!e.isShutdown()) {
            r.run();
        }
        return true;
    }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing.
     * @param p the Executor
     **/
    public void terminated(Executor e) { }
    
}

