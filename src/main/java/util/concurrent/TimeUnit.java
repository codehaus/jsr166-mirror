/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A <tt>TimeUnit</tt> represents time durations at a given unit of
 * granularity and provides utility methods to convert across units,
 * and to perform timing and delay operations in these units.
 * <tt>TimeUnit</tt> is a &quot;featherweight&quot; class.
 * It does not maintain time information, but only helps organize and
 * use time representations that may be maintained separately across
 * various contexts.
 *
 * <p>This class cannot be directly instantiated.  Use the {@link
 * #SECONDS}, {@link #MILLISECONDS}, {@link #MICROSECONDS}, and {@link
 * #NANOSECONDS} static instances that provide predefined units of
 * precision. If you use these frequently, consider statically
 * importing this class.
 *
 * <p>A <tt>TimeUnit</tt> is mainly used to inform blocking methods which
 * can timeout, how the timeout parameter should be interpreted. For example,
 * the following code will timeout in 50 milliseconds if the {@link java.util.concurrent.locks.Lock lock}
 * is not available:
 * <pre>  Lock lock = ...;
 *  if ( lock.tryLock(50L, TimeUnit.MILLISECONDS) ) ...
 * </pre>
 * while this code will timeout in 50 seconds:
 * <pre>
 *  Lock lock = ...;
 *  if ( lock.tryLock(50L, TimeUnit.SECONDS) ) ...
 * </pre>
 * Note however, that there is no guarantee that a particular lock, in this
 * case, will be able to notice the passage of time at the same granularity
 * as the given <tt>TimeUnit</tt>.
 *
 * @since 1.5
 * @author Doug Lea
 */
public final class TimeUnit implements java.io.Serializable {

    /**
     * Perform the conversion based on given index representing the
     * difference between units
     * @param delta the difference in index values of source and target units
     * @param duration the duration
     * @return converted duration or saturated value
     */
    private static long doConvert(int delta, long duration) {
        if (delta == 0)
            return duration;
        if (delta < 0) 
            return duration / multipliers[-delta];
        if (duration > overflows[delta])
            return Long.MAX_VALUE;
        if (duration < -overflows[delta])
            return Long.MIN_VALUE;
        return duration * multipliers[delta];
    }

    /**
     * Convert the given time duration in the given unit to the
     * current unit.  Conversions from finer to coarser granulaties
     * truncate, so lose precision. For example converting
     * <tt>999</tt> milliseconds to seconds results in
     * <tt>0</tt>. Conversions from coarser to finer granularities
     * with arguments that would numerically overflow saturate to
     * <tt>Long.MIN_VALUE</tt> if negative or <tt>Long.MAX_VALUE</tt>
     * if positive.
     *
     * @param duration the time duration in the given <tt>unit</tt>
     * @param unit the unit of the <tt>duration</tt> argument
     * @return the converted duration in the current unit,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     */
    public long convert(long duration, TimeUnit unit) {
        return doConvert(unit.index - index, duration);
    }

    /**
     * Equivalent to <tt>NANOSECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     */
    public long toNanos(long duration) {
        return doConvert(index, duration);
    }

    /**
     * Equivalent to <tt>MICROSECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     */
    public long toMicros(long duration) {
        return doConvert(index - US, duration);
    }

    /**
     * Equivalent to <tt>MILLISECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     */
    public long toMillis(long duration) {
        return doConvert(index - MS, duration);
    }

    /**
     * Equivalent to <tt>SECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration.
     * @see #convert
     */
    public long toSeconds(long duration) {
        return doConvert(index - S, duration);
    }

    /**
     * Perform a timed <tt>Object.wait</tt> using the current time unit.
     * This is a convenience method that converts timeout arguments into the
     * form required by the <tt>Object.wait</tt> method. 
     * <p>For example, you could implement a blocking <tt>poll</tt> method (see
     * {@link BlockingQueue#poll BlockingQueue.poll} using:
     * <pre>  public synchronized  Object poll(long timeout, TimeUnit unit) throws InterruptedException {
     *    while (empty) {
     *      unit.timedWait(this, timeout);
     *      ...
     *    }
     *  }</pre>
     * @param obj the object to wait on
     * @param timeout the maximum time to wait. 
     * @throws InterruptedException if interrupted while waiting.
     * @see Object#wait(long, int)
     */
    public void timedWait(Object obj, long timeout)
        throws InterruptedException {
        if (timeout > 0) {
            long ms = MILLISECONDS.convert(timeout, this);
            int ns = excessNanos(timeout, ms);
            obj.wait(ms, ns);
        }
    }

    /**
     * Perform a timed <tt>Thread.join</tt> using the current time unit.
     * This is a convenience method that converts time arguments into the
     * form required by the <tt>Thread.join</tt> method.
     * @param thread the thread to wait for
     * @param timeout the maximum time to wait
     * @throws InterruptedException if interrupted while waiting.
     * @see Thread#join(long, int)
     */
    public void timedJoin(Thread thread, long timeout)
        throws InterruptedException {
        if (timeout > 0) {
            long ms = MILLISECONDS.convert(timeout, this);
            int ns = excessNanos(timeout, ms);
            thread.join(ms, ns);
        }
    }

    /**
     * Perform a <tt>Thread.sleep</tt> using the current time unit.
     * This is a convenience method that converts time arguments into the
     * form required by the <tt>Thread.sleep</tt> method.
     * @param timeout the minimum time to sleep
     * @throws InterruptedException if interrupted while sleeping.
     * @see Thread#sleep
     */
    public void sleep(long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = MILLISECONDS.convert(timeout, this);
            int ns = excessNanos(timeout, ms);
            Thread.sleep(ms, ns);
        }
    }

    /**
     * Return the common name for this unit.
     */
    public String toString() {
        return unitName;
    }


    /**
     * Returns true if the given object is a TimeUnit representing
     * the same unit as this TimeUnit.
     * @param x the object to compare
     * @return true if equal
     */
    public boolean equals(Object x) {
        if (!(x instanceof TimeUnit))
            return false;
        return ((TimeUnit)x).index == index;
    }

    /**
     * Returns a hash code for this TimeUnit.
     * @return the hash code
     */
    public int hashCode() {
        return unitName.hashCode();
    }

    private final String unitName;

    /* ordered indices for each time unit */
    private static final int NS = 0;
    private static final int US = 1;
    private static final int MS = 2;
    private static final int S  = 3;

    /** quick lookup table for conversion factors */
    static final int[] multipliers = { 1, 
                                       1000, 
                                       1000*1000, 
                                       1000*1000*1000 };

    /** lookup table to check saturation */
    static final long[] overflows = { 
        // Note that because we are dividing these down anyway, 
        // we don't have to deal with asymmetry of MIN/MAX values.
        0, // unused
        Long.MAX_VALUE / 1000,
        Long.MAX_VALUE / (1000 * 1000),
        Long.MAX_VALUE / (1000 * 1000 * 1000) };

    /** the index of this unit */
    int index;

    /** private constructor */
    TimeUnit(int index, String name) { 
        this.index = index; 
        this.unitName = name;
    }

    /**
     * Utility method to compute the excess-nanosecond argument to
     * wait, sleep, join. The results may overflow, so public methods
     * invoking this should document possible overflow unless
     * overflow is known not to be possible for the given arguments.
     */
    private int excessNanos(long time, long ms) {
        if (index == NS)
            return (int) (time  - (ms * multipliers[MS-NS]));
        else if (index == US)
            return (int) ((time * multipliers[US-NS]) - (ms * multipliers[MS-NS]));
        else
            return 0;
    }

    /** Unit for one-second granularities. */
    public static final TimeUnit SECONDS = new TimeUnit(S, "seconds");

    /** Unit for one-millisecond granularities. */
    public static final TimeUnit MILLISECONDS = new TimeUnit(MS, "milliseconds");

    /** Unit for one-microsecond granularities. */
    public static final TimeUnit MICROSECONDS = new TimeUnit(US, "microseconds");

    /** Unit for one-nanosecond granularities. */
    public static final TimeUnit NANOSECONDS = new TimeUnit(NS, "nanoseconds");

}
