/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A delayed result-bearing action that can be cancelled.
 * Usually a scheduled future is the result of scheduling
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
public interface ScheduledFuture<V> extends Delayed, Future<V> {
}
