/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */


package java.util.concurrent;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * A DelayEntry associates a delay with an object.
 * Most typically, the object will be a runnable action.
 */

public class DelayEntry<E> implements Comparable {
    /**
     * Sequence number to break ties, and in turn to guarantee
     * FIFO order among tied entries.
     */
    private static final AtomicLong sequencer = new AtomicLong(0);

    private final long sequenceNumber;
    private final long triggerTime;
    private final E item;

    /**
     * Creates a new DelayEntry for the given object with given delay.
     */
    DelayEntry(E x, long delay, TimeUnit unit) {
        item = x;
        triggerTime = TimeUnit.nanoTime() + unit.toNanos(delay);
        sequenceNumber = sequencer.getAndIncrement();
    }

    /**
     * Equivalent to:
     * <tt> DelayEntry(x, date.getTime() - System.currentTimeMillis(), 
     * TimeUnit.MiLLISECONDS)</tt>
     */ 
    DelayEntry(E x, Date date) {
        this(x, date.getTime() - System.currentTimeMillis(), 
             TimeUnit.MILLISECONDS);
    }

    /**
     * Get the delay, in the given time unit.
     */
    public long getDelay(TimeUnit unit) {
        long d = triggerTime - TimeUnit.nanoTime();
        if (d <= 0)
            return 0;
        else
            return unit.convert(d, TimeUnit.NANOSECONDS);
    }

    /**
     * Get the object.
     */
    public E get() {
        return item;
    }

    public int compareTo(Object other) {
        DelayEntry x = (DelayEntry)other;
        long diff = triggerTime - x.triggerTime;
        if (diff < 0)
            return -1;
        else if (diff > 0)
            return 1;
        else if (sequenceNumber < x.sequenceNumber)
            return -1;
        else
            return 1;
    }

}
