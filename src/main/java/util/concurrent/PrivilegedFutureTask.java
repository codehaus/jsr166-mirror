/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A {@link FutureTask} that executes, if allowed, with the current
 * access control context and context class loader of the thread
 * creating the task. A new <tt>PrivilegedFutureTask</tt> 
 * can be created within an {@link AccessController#doPrivileged}
 * action to run tasks under the selected permission settings
 * holding within that action.
 * @see Executors
 *
 * @since 1.5
 * @author Doug Lea
 */
public class PrivilegedFutureTask<T> extends FutureTask<T> {
    private final ClassLoader ccl;
    private final AccessControlContext acc;

    /**
     * Constructs a <tt>PrivilegedFutureTask</tt> that will, upon running,
     * execute the given <tt>Callable</tt> under the current access control 
     * context, with the current context class loader as the context class 
     * loader.
     *
     * @throws AccessControlException if the current access control context 
     *         does not have permission to both set and get context class loader.
     */
    public PrivilegedFutureTask(Callable<T> task) {
        super(task);
        this.ccl = Thread.currentThread().getContextClassLoader();
        this.acc = AccessController.getContext();
        acc.checkPermission(new RuntimePermission("getContextClassLoader"));
        acc.checkPermission(new RuntimePermission("setContextClassLoader"));
    }

    /**
     * Executes within the established access control and context
     * class loader if possible, else causes invocations of {@link
     * Future#get} to receive the associated AccessControlException.
     */
    public void run() {
	try {
	    AccessController.doPrivileged(new PrivilegedFutureAction(), acc);
	} catch(Throwable ex) {
	    setException(ex);
	}
    }

    private class PrivilegedFutureAction implements PrivilegedAction {
	public Object run() {
	    ClassLoader saved = null;
	    try {
		ClassLoader current = Thread.currentThread().getContextClassLoader();
		if (ccl != current) {
		    Thread.currentThread().setContextClassLoader(ccl);
		    saved = current;
		}
		PrivilegedFutureTask.super.run();
		return null;
	    } finally {
		if (saved != null)
		    Thread.currentThread().setContextClassLoader(saved);
	    }
	}
    }
}
