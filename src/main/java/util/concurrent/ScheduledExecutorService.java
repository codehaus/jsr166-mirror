/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A {@link ScheduledExecutor} that provides methds to manage termination
 * via {@link ExecutorService}.
 *
 * <p>The {@link Executors} class provides factory methods for the
 * scheduled executor services provided in this package.
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface ScheduledExecutorService extends ScheduledExecutor, ExecutorService {
}
