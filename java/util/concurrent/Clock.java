package java.util.concurrent;

/**
 * A Clock performs system timing functions at a given unit
 * of granularity. 
 *
 * The class cannot be directly instantiated.
 * Use the <tt>seconds</tt>, <tt>milliseconds</tt>, 
 * <tt>microseconds</tt>, and <tt>nanoseconds</tt> static singletons.
 **/

public abstract class Clock implements java.io.Serializable {
    /**
     * The (negative of) power of ten of granularity.
     * 0 for sec, 3 for msec, 6 for usec, 9 for nsec.
     **/
    final int powerOfTen;

    /** package-private constructor */
    Clock(int power) { powerOfTen = power; }

    /** Time unit for one-second granularities */
    public static final Clock SECONDS = new SecondClock();

    /** Time unit for one-millisecond granularities */
    public static final Clock MILLISECONDS = new MillisecondClock();

    /** Time unit for one-microsecond granularities */
    public static final Clock MICROSECONDS = new MicrosecondClock();

    /** Time unit for one-nanosecond granularities */
    public static final Clock NANOSECONDS = new NanosecondClock();

    /**
     * Return the current time in the current unit. The result is an
     * offset from the "epoch", midnight, January 1, 1970 UTC.  The time
     * returned by any two clocks of different granularities may differ
     * by one unit of the coarsest of the two.
     **/
    public abstract long currentTime();

    /**
     * Return the time elapsed since the given time, in the current unit.
     * The result may be unreliable if more than 2^63 time units have
     * elapsed.
     **/
    public long since(long time) {
        return currentTime() - time;
    }

    /**
     * Return the minimum ideally possible non-zero difference in values
     * between two successive calls to current for this clock. For
     * example, on a system with a 10-microsecond  timer,
     * <tt>Clock.nanooseconds.bestResolution()<tt> would return <tt>10000</tt>,
     * <tt>Clock.microseconds.bestResolution()<tt> would return <tt>10</tt>,
     * <tt>Clock.milliseconds.bestResolution()<tt> would return <tt>1</tt>, and
     * <tt>Clock.seconds.bestResolution()<tt> would return <tt>1</tt>.
     *
     * <p>
     * Note that this method does <em>NOT</em> imply any properties
     * about the minimum duration of timed waits or sleeps.
     **/
    public abstract long bestResolution();

    /**
     * Convert the given time value to the current granularity.
     * This conversion may overflow or lose precision.
     * @param time the time in given unit
     * @param granularity the unit of the time argument
     * @return the converted time.
     **/
    public long convert(long time, Clock granularity) {
        int d = granularity.powerOfTen - this.powerOfTen;
        // Note: if non-multiple-of-3 powers are ever used, this must change.
        while (d > 0) {
            d -= 3;
            time *= 1000;
        }
        while (d < 0) {
            d += 3;
            time /= 1000;
        }
        return time;
    }


    /**
     * Perform a timed Object.wait in current units.
     * @param monitor the object to wait on
     * @param time the maximum time to wait
     * @throws InterruptedException if interrupted while waiting.
     **/
    public abstract void timedWait(Object monitor, long time)
        throws InterruptedException;

    /**
     * Perform a timed Thread.join in current units.
     * @param monitor the thread to wait for
     * @param time the maximum time to wait
     * @throws InterruptedException if interrupted while waiting.
     **/
    public abstract void timedJoin(Thread thread, long time)
        throws InterruptedException;

    /**
     * Perform a timed sleep in current units.
     * @param time the maximum time to wait
     * @throws InterruptedException if interrupted while sleeping.
     **/
    public abstract void sleep(long time) throws InterruptedException;


    static final class SecondClock extends Clock {
        private SecondClock() { super(0); }
        public long currentTime() {
            return System.currentTimeMillis() / 1000;
        }

        public long bestResolution() {
            return 1;
        }

        public void timedWait(Object monitor, long time) throws InterruptedException {
            monitor.wait(time * 1000);
        }

        public void timedJoin(Thread thread, long time) throws InterruptedException {
            thread.join(time * 1000);
        }

        public void sleep(long time) throws InterruptedException {
            Thread.sleep(time * 1000);
        }
    }

    static final class MillisecondClock extends Clock {
        private MillisecondClock() { super(3); }
        public long currentTime() {
            return System.currentTimeMillis();
        }

        public long bestResolution() {
            return 1;
        }

        public void timedWait(Object monitor, long time) throws InterruptedException {
            monitor.wait(time);
        }

        public void timedJoin(Thread thread, long time) throws InterruptedException {
            thread.join(time);
        }

        public void sleep(long time) throws InterruptedException {
            Thread.sleep(time);
        }
    }

    static final class MicrosecondClock extends Clock {
        private MicrosecondClock() { super(6); }
        public long currentTime() {
            return System.currentTimeMillis() * 1000;
        }

        public long bestResolution() {
            return 1000;
        }

        public void timedWait(Object monitor, long time) throws InterruptedException {
            long ms = time / 1000;
            int ns = (int)((time - ms * 1000) / 1000);
            monitor.wait(ms, ns);
        }

        public void timedJoin(Thread thread, long time) throws InterruptedException {
            long ms = time / 1000;
            int ns = (int)((time - ms * 1000) / 1000);
            thread.join(ms, ns);
        }

        public void sleep(long time) throws InterruptedException {
            long ms = time / 1000;
            int ns = (int)((time - ms * 1000) / 1000);
            Thread.sleep(ms, ns);
        }
    }


    static final class NanosecondClock extends Clock {
        private NanosecondClock() { super(9); }
        public long currentTime() {
            return System.currentTimeMillis() * 1000000;
        }

        public long bestResolution() {
            return 1000000;
        }

        public void timedWait(Object monitor, long time) throws InterruptedException {
            long ms = time / 1000000;
            int ns = (int)(time - ms * 1000000);
            monitor.wait(ms, ns);
        }

        public void timedJoin(Thread thread, long time) throws InterruptedException {
            long ms = time / 1000000;
            int ns = (int)(time - ms * 1000000);
            thread.join(ms, ns);
        }

        public void sleep(long time) throws InterruptedException {
            long ms = time / 1000000;
            int ns = (int)(time - ms * 1000000);
            Thread.sleep(ms, ns);
        }
    }

}
