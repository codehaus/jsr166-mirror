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
        this(task,
             Thread.currentThread().getContextClassLoader(),
             AccessController.getContext());
    }
    
    /**
     * Constructs a <tt>PrivilegedFutureTask</tt> that will, upon running,
     * execute the given <tt>Callable</tt> under the current access control 
     * context, with the given class loader as the context class loader, 
     *
     * @throws AccessControlException if <tt>ccl</tt> is non-null and 
     *         the current access control context does not have permission
     *         to both set and get context class loader.
     */
    public PrivilegedFutureTask(Callable<T> task, ClassLoader ccl) {
        this(task, ccl, AccessController.getContext());
    }
    
    /**
     * Constructs a <tt>PrivilegedFutureTask</tt> that will, upon running,
     * execute the given <tt>Callable</tt> with the current context class 
     * loader as the context class loader and the given access control 
     * context as the current access control context.
     *
     * @throws AccessControlException if <tt>acc</tt>is non-null and 
     *         <tt>acc</tt> does not have permission to both set and get 
     *         context class loader.
     */
    public PrivilegedFutureTask(Callable<T> task, AccessControlContext acc) {
        this(task, Thread.currentThread().getContextClassLoader(), acc);
    }
    
    /**
     * Constructs a <tt>PrivilegedFutureTask</tt> that will, upon running,
     * execute the given <tt>Callable</tt> with the given class loader as
     * the context class loader and the given access control context as the
     * current access control context.
     *
     * @throws AccessControlException if both <tt>ccl</tt> and <tt>acc</tt>
     *         arguments are non-null and <tt>acc</tt> does not have permission
     *         to both set and get context class loader.
     */
    public PrivilegedFutureTask(Callable<T> task, ClassLoader ccl, AccessControlContext acc) {
        super(task);
        if (ccl != null && acc != null) {
            acc.checkPermission(new RuntimePermission("getContextClassLoader"));
            acc.checkPermission(new RuntimePermission("setContextClassLoader"));
        }
        this.ccl = ccl;
        this.acc = acc;
    }


    public void run() {
        if (acc != null)
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() { 
                    runPrivileged(); 
                    return null; 
                }
            }, acc);
        else
            runUnprivileged();
    }


    private void runPrivileged() {
        ClassLoader saved = null;
        if (ccl != null) {
            ClassLoader current = Thread.currentThread().getContextClassLoader();
            if (ccl != current) {
                Thread.currentThread().setContextClassLoader(ccl);
                saved = current;
            }
        }

        try {
            super.run();
        }
        finally {
            if (saved != null)
                Thread.currentThread().setContextClassLoader(saved);
        }
    }

    private void runUnprivileged() {
        ClassLoader saved = null;
        if (ccl != null) {
            ClassLoader current = null;
            try {
                current = Thread.currentThread().getContextClassLoader();
            }
            catch (AccessControlException e) {}

            if (current != null && ccl != current) {
                try {
                    Thread.currentThread().setContextClassLoader(ccl);
                    // we only get here if we successfully set a CCL
                    // different from the current CCL
                    saved = current;
                }
                catch (AccessControlException e) {}
            }
        }
        try {
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

