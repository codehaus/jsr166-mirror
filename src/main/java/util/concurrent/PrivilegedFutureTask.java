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
 * A {@link FutureTask} that executes with the given (or current, if
 * not specified) access control context and/or the given (or current)
 * context class loader.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class PrivilegedFutureTask<T> extends FutureTask<T> {

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


    public void run() {
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() { 
                runPrivileged(); 
                return null; 
            }
        }, acc);
    }


    private void runPrivileged() {
        ClassLoader saved = null;
        try {
            ClassLoader current = Thread.currentThread().getContextClassLoader();
            if (ccl != current) {
                Thread.currentThread().setContextClassLoader(ccl);
                saved = current;
            }
            super.run();
        }
        finally {
            if (saved != null)
                Thread.currentThread().setContextClassLoader(saved);
        }
    }

    private final ClassLoader ccl;
    private final AccessControlContext acc;
}

