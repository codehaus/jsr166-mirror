/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * An object that creates new threads on demand.  Using thread factories
 * removes hardwiring of calls to {@link Thread#Thread(Runnable) new Thread},
 * enabling applications to use special thread subclasses, priorities, etc.
 *
 * @since 1.5
 *
 * @spec JSR-166
 * @revised $Date: 2003/06/24 14:34:49 $
 * @editor $Author: dl $
 * @author Doug Lea
 */
public interface ThreadFactory { 

    /**
     * Constructs a new <tt>Thread</tt>.  Implementations may also initialize
     * priority, name, daemon status, <tt>ThreadGroup</tt>, etc.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread
     */
    Thread newThread(Runnable r);
}
