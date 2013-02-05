/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;

public class ForkJoinPool8Test extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(ForkJoinPool8Test.class);
    }

    /**
     * Common pool exists and has expected parallelism.
     */
    public void testCommonPoolParallelism() {
        assertEquals(ForkJoinPool.getCommonPoolParallelism(),
                     ForkJoinPool.commonPool().getParallelism());
    }
}
