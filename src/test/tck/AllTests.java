/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;

/**
 *  Runs all JSR166 tests
 */
public class AllTests {
    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    
    public static Test suite ( ) {
        TestSuite suite = new TestSuite("JSR166 Unit Tests");
        
        suite.addTest(new TestSuite(ArrayBlockingQueueTest.class));
        suite.addTest(new TestSuite(AtomicBooleanTest.class)); 
        suite.addTest(new TestSuite(AtomicIntegerArrayTest.class)); 
        suite.addTest(new TestSuite(AtomicIntegerFieldUpdaterTest.class)); 
        suite.addTest(new TestSuite(AtomicIntegerTest.class)); 
        suite.addTest(new TestSuite(AtomicLongArrayTest.class)); 
        suite.addTest(new TestSuite(AtomicLongFieldUpdaterTest.class)); 
        suite.addTest(new TestSuite(AtomicLongTest.class)); 
        suite.addTest(new TestSuite(AtomicMarkableReferenceTest.class)); 
        suite.addTest(new TestSuite(AtomicReferenceArrayTest.class)); 
        suite.addTest(new TestSuite(AtomicReferenceFieldUpdaterTest.class)); 
        suite.addTest(new TestSuite(AtomicReferenceTest.class)); 
        suite.addTest(new TestSuite(AtomicStampedReferenceTest.class)); 
        suite.addTest(new TestSuite(CancellableTaskTest.class));
        suite.addTest(new TestSuite(ConcurrentHashMapTest.class));
        suite.addTest(new TestSuite(ConcurrentLinkedQueueTest.class));
        suite.addTest(new TestSuite(CopyOnWriteArrayListTest.class));
        suite.addTest(new TestSuite(CopyOnWriteArraySetTest.class));
        suite.addTest(new TestSuite(CountDownLatchTest.class));
        suite.addTest(new TestSuite(CyclicBarrierTest.class));
        suite.addTest(new TestSuite(DelayQueueTest.class));
        suite.addTest(new TestSuite(ExchangerTest.class));
        suite.addTest(new TestSuite(ExecutorsTest.class));
        suite.addTest(new TestSuite(FairSemaphoreTest.class));
        suite.addTest(new TestSuite(FutureTaskTest.class));
        suite.addTest(new TestSuite(LinkedBlockingQueueTest.class));
        suite.addTest(new TestSuite(LinkedListTest.class));
        suite.addTest(new TestSuite(LockSupportTest.class));
        suite.addTest(new TestSuite(PriorityBlockingQueueTest.class));
        suite.addTest(new TestSuite(PriorityQueueTest.class));
        suite.addTest(new TestSuite(ReentrantLockTest.class));
        suite.addTest(new TestSuite(ReentrantReadWriteLockTest.class));
        suite.addTest(new TestSuite(ScheduledExecutorTest.class));
        suite.addTest(new TestSuite(SemaphoreTest.class));
        suite.addTest(new TestSuite(SynchronousQueueTest.class));
        suite.addTest(new TestSuite(SystemTest.class));
        suite.addTest(new TestSuite(ThreadLocalTest.class));
        suite.addTest(new TestSuite(ThreadPoolExecutorTest.class));
        suite.addTest(new TestSuite(ThreadTest.class));
        suite.addTest(new TestSuite(TimeUnitTest.class));
		
        return suite;
    }
}
