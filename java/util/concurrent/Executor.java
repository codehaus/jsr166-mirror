package java.util.concurrent;

import java.util.List;

/**
 * An Executor asynchronously executes commands. This interface
 * provides a way of decoupling asynchronous task execution from 
 * the explicit construction of new Threads. 
 *
 * <p> An Executor can be shut down, which will cause it to stop
 * processing submitted tasks. After being shut down, it will normally
 * eventually reach a quiescent state in which no tasks are executing
 * and no new ones will be processed.
 **/
public interface Executor {

    /**
     * Execute the given command sometime in the future
     **/
    public void execute(Runnable command);

    /**
     * Cause tasks submitted in subsequent calls to <tt>execute</tt> not
     * to be processed. However, all previously submitted tasks will
     * complete.  The exact fate of tasks submitted in subsequent calls
     * to <tt>execute</tt> is left unspecified in this
     * interface. Implementations may provide different options, such as
     * ignoring them, or causing <tt>execute</tt> to throw an
     * (unchecked) <tt>IllegalStateException</tt>.
     **/
    public void shutdown();

    /**
     * Attempt to stop processing all actively executing tasks, never
     * start processing previously submitted tasks that have not yet
     * commenced execution, and cause subsequently submitted tasks
     * not to be processed.  The exact fate of tasks submitted in
     * subsequent calls to <tt>execute</tt> is left unspecified in this
     * interface. Implementations may provide different options, such as
     * ignoring them, or causing <tt>execute</tt> to throw an
     * (unchecked) <tt>IllegalStateException</tt>.  Similarly, there are
     * no guarantees beyond best-effort attempts to stop processing
     * actively executing tasks.  For example typical thread-based
     * Executors will cancel via <tt>Thread.interrupt</tt>, so if any
     * tasks mask or fail to respond to interrupts, they might never
     * terminate.
     * @return a list of all tasks that never commenced execution.
     **/
    public List shutdownNow();

    /**
     * Return true if the Executor has been shut down.
     **/
    public boolean isShutdown();
    
}
