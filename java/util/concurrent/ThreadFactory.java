package java.util.concurrent;

/**
 * Interface describing any class that can generate new Thread
 * objects. Using ThreadFactories removes hardwiring of calls to
 * <code>new Thread</code>, enabling applications to use special
 * thread subclasses, default prioritization settings, or create
 * threads with a specific name or in a specific thread group.  
 */
public interface ThreadFactory { 
    /**
     * Construct a new Thread, possibly also initializing priorities,
     * names, daemon status, ThreadGroups, etc.
     * @param r the runnable that the thread will run upon
     * <tt>start</tt>.
     * @return the constructed thread.
     **/
    public Thread newThread(Runnable r);
}
