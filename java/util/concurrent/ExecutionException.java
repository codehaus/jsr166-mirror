package java.util.concurrent;

/**
 * Thrown when attempting to retrieve the results of an asychronous
 * task that aborted, failing to produce a result.  
 * @@@brian Why not just use standard exception chaining?  
 */
public class ExecutionException extends Exception {

    /**
     * This field holds the target if the ExecutionException(Throwable
     * target) constructor was used to instantiate the object
     *
     * @serial
     * */
    private Throwable target;
    
    /**
     * Constructs an <code>ExecutionException</code> with
     * <code>null</code> as the target exception.
     */
    protected ExecutionException() {
        super((Throwable)null);  // Disallow initCause
    }

    /**
     * Constructs a ExecutionException with a target exception.
     *
     * @param target the target exception
     */
    public ExecutionException(Throwable target) {
        super((Throwable)null);  // Disallow initCause
        this.target = target;
    }

    /**
     * Constructs a ExecutionException with a target exception
     * and a detail message.
     *
     * @param target the target exception
     * @param s      the detail message
     */
    public ExecutionException(Throwable target, String s) {
        super(s, null);  // Disallow initCause
        this.target = target;
    }

    /**
     * Get the thrown target exception.
     *
     * <p>This method predates the general-purpose exception chaining
     * facility.  The {@link Throwable#getCause()} method is now the
     * preferred means of obtaining this information.
     *
     * @return the thrown target exception (cause of this exception).  
     */
    public Throwable getTargetException() {
        return target;
    }

    /**
     * Returns the the cause of this exception (the thrown target exception,
     * which may be <tt>null</tt>).
     *
     * @return  the cause of this exception.
     * @since   1.4
     */
    public Throwable getCause() {
        return target;
    }
}
