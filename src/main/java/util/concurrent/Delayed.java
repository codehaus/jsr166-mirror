/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;

/**
 * A mix-in style interface for representing actions, events, etc
 * that should be executed, accessed or acted upon only after
 * a given delay.
 * @since 1.5
 * @author Doug Lea
 */
public interface Delayed extends Comparable {

    /**
     * Get the delay associated with this object, in the given time unit.
     * @param unit the time unit
     * @return the delay; zero or negative values indicate that the
     * delay has already elapsed
     */
    long getDelay(TimeUnit unit);
}
