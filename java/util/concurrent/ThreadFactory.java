package java.util.concurrent;

/**
 * Interface describing any class that can generate
 * new <tt>Thread</tt> objects. Using thread factories removes
 * hardwiring of calls to <tt>new Thread</tt>, enabling
 * applications to use special thread subclasses, default
 * priorities, etc.
 */
public interface ThreadFactory { 

    /**
     * Construct a new <tt>Thread</tt>, possibly also initializing priority,
     * name, daemon status, <tt>ThreadGroup</tt>, etc.
     * @param r the runnable that the thread will run upon <tt>start</tt>.
     * @return the constructed thread.
     */
    Thread newThread(Runnable r);
}
