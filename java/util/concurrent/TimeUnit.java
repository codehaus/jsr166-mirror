package java.util.concurrent;

/**
 * A TimeUnit computes time durations at a given unit of granularity,
 * provides utility methods to convert across units, and to perform
 * timing and delay operations in these units. TimeUnits are
 * "featherweight" classes. They do not themselves maintain time
 * information, they only help organize and use time representations
 * that may be maintained separately across various contexts.
 *
 * <p>
 * The base TimeUnit class cannot be directly instantiated.  Use the
 * <tt>SECONDS</tt>, <tt>MILLISECONDS</tt>, <tt>MICROSECONDS</tt>, and
 * <tt>NANOSECONDS</tt> static singletons that provide predefined
 * units of precision. If you use these frequently, consider
 * statically importing this class.
 **/

public abstract class TimeUnit implements java.io.Serializable {

    /**
     * Return a representation of the current time that can later be
     * used to obtain an elapsed duration via method
     * <code>elapsed</code>.  Values returned from <code>now</code>
     * have no numerical significance, and may be completely unrelated
     * to and uncoordinated with those from
     * <code>System.currentTimeMillis</code> or those used by class
     * <code>Date</code>. Returned values are useful only as
     * arguments to subsequent calls to <code>elapsed</code>, using
     * the same unit, called from within the same Isolate.
     * @return an arbitrary representation of the current time, in
     * the current unit, that can later be used as an argument to
     * <code>elapsed</code>.
     **/
    public long now() {
        return systemTimeNanos() * nanosPerUnit;
    }

    /**
     * Return an estimate of the time elapsed since the given previous
     * time, in the current unit.  The returned value is a best-effort
     * <em>estimate</em> of elapsed time that may reflect the effects
     * of hardware clock drift, system-level clock resets,
     * multiprocessor clock coordination, and network time
     * synchronization protocols that lead to inaccuracies and
     * discontinuities, even those that cause estimated durations of
     * brief intervals to be negative.  The estimates returned from
     * this method have the same <em>accuracy</em>, regardless of the
     * <em>precision</em> of the current <code>TimeUnit</code>.  The
     * returned value may be wrong or unreliable when the interval is
     * greater than <code>Long.MAX_VALUE</code> nanoseconds
     * (approximately 292 years).
     *
     * <p> For example to measure how long some code takes to execute, 
     * with nanosecond precision:
     * <pre>
     * long startTime = TimeUnit.NANOSECONDS.now();
     * // ... the code being measured ...
     * long estimatedTime = TimeUnit.NANOSECONDS.elapsed(startTime);
     * </pre>
     *
     * @param previous a value returned by or derived from a previous
     * call to <code>now</code> with the same time unit, by the same Isolate.
     * @return an estimate of the time elapsed since <code>previous</code>.
     **/
    public long elapsed(long previous) {
        return now() - previous;
    }

    /**
     * Convert the given duration value in the given unit to the
     * current unit.  Conversions from finer to coarser granulaties
     * truncate, so lose precision. Conversions from coarser to finer
     * granularities may numerically overflow.
     * @param time the duration in given unit
     * @param unit the unit of the duration argument
     * @return the converted duration.
     **/
    public long convert(long duration, TimeUnit unit) {
        long outUnits = nanosPerUnit;
        long inUnits = unit.nanosPerUnit;
        if (outUnits >= inUnits)  // minimize overflow and underflow
            return duration * (outUnits / inUnits);
        else
            return (duration * outUnits) / inUnits;
    }
    
    /**
     * Equivalent to <code>NANOOSECONDS.convert(duration, this)</code>.
     * This conversion may overflow.
     * @param duration the duration
     * @return the converted duration.
     **/
    public long toNanos(long duration) {
        return duration * nanosPerUnit;
    }
    
    /**
     * Equivalent to <code>convert(duration, NANOSECONDS)</code>.
     * This conversion may lose precision.
     * @param nanos the duration in nanoseconds.
     * @return the converted duration.
     **/
    public long fromNanos(long nanos) {
        return nanos / nanosPerUnit;
    }

    /**
     * Perform a timed <tt>Object.wait</tt> in current units. 
     * This is a convenience method that converts time arguments into the
     * form required by the <tt>Object.wait</tt> method.
     * @param monitor the object to wait on
     * @param timeout the maximum time to wait
     * @throws InterruptedException if interrupted while waiting.
     **/
    public void timedWait(Object monitor, long timeout)
        throws InterruptedException {
        long ms = MILLISECONDS.convert(timeout, this);
        int ns = excessNanos(timeout, ms);
        monitor.wait(ms, ns);
    }

    /**
     * Perform a timed <tt>Thread.join</tt> in current units.
     * This is a convenience method that converts time arguments into the
     * form required by the <tt>Thread.join</tt> method.
     * @param monitor the thread to wait for
     * @param time the maximum time to wait
     * @throws InterruptedException if interrupted while waiting.
     **/
    public void timedJoin(Thread thread, long timeout)
        throws InterruptedException {
        long ms = MILLISECONDS.convert(timeout, this);
        int ns = excessNanos(timeout, ms);
        thread.join(ms, ns);
    }
    
    /**
     * Perform a timed sleep in current units.
     * This is a convenience method that converts time arguments into the
     * form required by the <tt>Thread.sleep</tt> method.
     * @param time the maximum time to wait
     * @throws InterruptedException if interrupted while sleeping.
     **/
    public void sleep(long timeout) throws InterruptedException {
        long ms = MILLISECONDS.convert(timeout, this);
        int ns = excessNanos(timeout, ms);
        Thread.sleep(ms, ns);
    }

    /** package-private conversion factor */
    final int nanosPerUnit;
    /** package-private constructor */
    TimeUnit(int npu) { nanosPerUnit = npu; }

    /** 
     * Utility method to compute the excess-nanosecond argument to
     * wait, sleep, join.
     */
    private int excessNanos(long time, long ms) {
        if (nanosPerUnit < MILLISECONDS.nanosPerUnit) 
            return (int)(toNanos(time) - ms * (1000 * 1000));
        else 
            return 0;
    }

    
    /** Underlying native time call */
    static native long systemTimeNanos();

    /** Unit for one-second granularities */
    public static final TimeUnit SECONDS = new SecondTimeUnit();

    /** Unit for one-millisecond granularities */
    public static final TimeUnit MILLISECONDS = new MillisecondTimeUnit();

    /** Unit for one-microsecond granularities */
    public static final TimeUnit MICROSECONDS = new MicrosecondTimeUnit();

    /** Unit for one-nanosecond granularities */
    public static final TimeUnit NANOSECONDS = new NanosecondTimeUnit();

    static final class SecondTimeUnit extends TimeUnit {
        private SecondTimeUnit() { super(1000 * 1000 * 1000); }
    }
    static final class MillisecondTimeUnit extends TimeUnit {
        private MillisecondTimeUnit() { super(1000 * 1000); }
    }
    static final class MicrosecondTimeUnit extends TimeUnit {
        private MicrosecondTimeUnit() { super(1000); }
    }
    static final class NanosecondTimeUnit extends TimeUnit {
        private NanosecondTimeUnit() { super(1); }
    }

}
