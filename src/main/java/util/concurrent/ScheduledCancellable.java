/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A delayed or periodic action that can be cancelled.
 * Usually a scheduled cancellable is the result of scheduling
 * a task with a {@link ScheduledExecutor}.
 *
 * @since 1.5
 *
 * @spec JSR-166
 * @revised $Date: 2003/08/19 15:04:57 $
 * @editor $Author: tim $
 * @see ScheduledExecutor
 * @author Doug Lea
 */
public interface ScheduledCancellable extends Delayed, Cancellable {
}
