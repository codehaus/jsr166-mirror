package java.util;

import static java.lang.Math.*;

import junit.framework.TestCase;

/**
 * Tests the JSR-201 syntax features.
 */
public class SyntaxTest extends TestCase {

    public void testArrayForeach () {

        int[] ints = IARRAY;
        int total = 0;
        for (int i : ints) total += i;

        assertEquals("expecting total of 10", 10, total);
    }


    public void testCollectionForeach () {

        List<Integer> ints = new ArrayList<Integer>();
        for (int i : IARRAY) ints.add(i);
        int total = 0;
        for (Integer i : ints) total += i.intValue();
        assertEquals("expecting total of 10", 10, total);
    }


    public void testPriorityQueueForeach () {

        Queue<Integer> ints = new PriorityQueue<Integer>();
        for (int i : IARRAY) ints.add(i);
        int total = 0;
        for (Integer i : ints) total += i.intValue();
        assertEquals("expecting total of 10", 10, total);
    }


    public void testEnumerations () {

        enum Color {
            red(1), green(2), blue(3), yellow(4), magenta(5), cyan(6);

            Color(int ival) { this.ival = ival; }

            public int intValue() { return ival; }

            private final int ival;
        };

        List<Color> colors = Color.red.family();

        assertEquals("should get expected list of colors",
                     "[red, green, blue, yellow, magenta, cyan]",
                     colors.toString());

        int total = 0;
        for (Color c : colors) {
            switch (c) {
            case Color.red:
            case Color.yellow:
                System.out.println("skipping " + c);
                continue;
            default:
                total += c.intValue();
            }
        }

        assertEquals("wrong total int values of colors", 16, total);
    }


    public void testStaticImport () {
        int[] iarray = new int[] { 1, -2, 3, -4 };
        int total = 0;
        for (int i : iarray) total += abs(i);

        assertEquals("should get total of 10", 10, total);
    }


    public void testVarargs () {

        int count = argCount(1, 2, 3, 4);
        assertEquals("wrong # of args", 4, count);

        int total = argTotal(1, 2, 3, 4);
        assertEquals("wrong total", 10, total);

        total = totalAll(new int[] { 1, 2 }, new int[] { 3, 4 }, new int[] { 5 });
        assertEquals("wrong totalAll", 15, total);
    }

    private int argCount (Integer[] args ...) {
        return args.length;
    }

    private int argTotal (Integer[] args ...) {
        int total = 0;
        for (Integer i : args) total += i.intValue();
        return total;
    }

    private int totalAll (int[][] args ...) {
        int total = 0;
        for (int[] ia : args) {
            for (int i : ia) {
                total += i;
            }
        }
        return total;
    }


    private static final int[] IARRAY = new int[] { 1, 2, 3, 4 };
}
