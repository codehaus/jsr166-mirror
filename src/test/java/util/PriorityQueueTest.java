package java.util;

import junit.framework.TestCase;

/**
 * Tests the PriorityQueue implementation.
 */
public class PriorityQueueTest extends TestCase {

    public void testNaturalOrder () {

        PriorityQueue<Integer> pq = new PriorityQueue<Integer>();

        assertEquals("new pqueue should be empty", 0, pq.size());

        for (Iterator<Integer> it = unsorted.iterator(); it.hasNext(); ) {
            pq.add(it.next());
        }

        assertEquals("new pqueue should have N elements", N, pq.size());

        List<Integer> pqorder = new ArrayList<Integer>(N);
        for (Integer k; (k = pq.poll()) != null; ) pqorder.add(k);

        Collections.sort(expected);
        assertEquals("pqueue should return elements in sorted order",
                     expected,
                     pqorder);
    }

    public void testComparatorOrder () {

        Comparator<Integer> rev = new Comparator<Integer>() {
            public int compare (Integer a, Integer b) {
                return b.compareTo(a);
            }
        };
        PriorityQueue<Integer> pq = new PriorityQueue<Integer>(N, rev);
        for (Iterator<Integer> it = unsorted.iterator(); it.hasNext(); ) {
            pq.add(it.next());
        }

        List<Integer> pqorder = new ArrayList<Integer>(N);
        for (Integer k; (k = pq.poll()) != null; ) pqorder.add(k);

        Collections.sort(expected, rev);
        assertEquals("pqueue should return elements in sorted order",
                     expected,
                     pqorder);
    }

    public void testConstructFromCollection () {

        PriorityQueue<Integer> pq = new PriorityQueue<Integer>(unsorted);

        assertEquals("new pqueue should have some element count as unsorted",
                     unsorted.size(),
                     pq.size());

        List<Integer> pqorder = new ArrayList<Integer>(N);
        for (Integer k; (k = pq.poll()) != null; ) pqorder.add(k);

        Collections.sort(expected);
        assertEquals("pqueue should return elements in sorted order",
                     expected,
                     pqorder);
    }


    private static final long SEED = 37L;
    private static final int N = 100000;

    private final Random random = new Random(SEED);
    private final List<Integer> unsorted = new ArrayList<Integer>(N);
    {
        for (int i = 0; i < N; ++i) {
            unsorted.add(new Integer(random.nextInt(N)));
        }
    }
    private final List<Integer> expected = new ArrayList<Integer>(unsorted);

}
