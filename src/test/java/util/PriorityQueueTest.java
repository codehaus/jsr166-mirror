package java.util;

import junit.framework.TestCase;

/**
 * Tests the PriorityQueue implementation.
 */
public class PriorityQueueTest extends TestCase {

    private static final long SEED = 37L;

    private static final int N = 100000;

    private final Random random = new Random(SEED);
    private final List<Integer> unsorted = new ArrayList<Integer>(N);
    {
        for (int i = 0; i < N; ++i) {
            unsorted.add(new Integer(random.nextInt(N)));
        }
    }


    public void testSortedOrder () {
        PriorityQueue<Integer> pq = new PriorityQueue<Integer>();
        List<Integer> expected = new ArrayList<Integer>(unsorted);
        Collections.sort(expected);
        doTest(pq, expected);
    }

    public void testReverseOrder () {
        Comparator<Integer> rev = new Comparator<Integer>() {
            public int compare (Integer a, Integer b) {
                return b.compareTo(a);
            }
        };
        PriorityQueue<Integer> pq = new PriorityQueue<Integer>(N, rev);
        List<Integer> expected = new ArrayList<Integer>(unsorted);
        Collections.sort(expected, rev);
        doTest(pq, expected);
    }

    private void doTest (PriorityQueue<Integer> pq, List<Integer> expected) {

        assertEquals("new pqueue should be empty", 0, pq.size());

        for (Iterator<Integer> it = unsorted.iterator(); it.hasNext(); ) {
            pq.add(it.next());
        }

        assertEquals("new pqueue should have N elements", N, pq.size());

        List<Integer> sorted = new ArrayList<Integer>(N);
        for (Integer k; (k = pq.poll()) != null; ) {
            sorted.add(k);
        }

        assertEquals("pqueue should return elements in sorted order",
                     expected,
                     sorted);

        //System.out.println(unsorted.subList(0, 10));
        //System.out.println(sorted.subList(0, 10));
    }
}
