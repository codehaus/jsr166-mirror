package jsr166.test;

import static java.lang.Math.*;
import java.nio.*;
import java.util.*;

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

    private static final int[] IARRAY = new int[] { 1, 2, 3, 4 };


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


    public void testColor () {
        Color2 c = Color2.red;
        assertEquals("color name", "red", c.toString());
    }

    enum Color2 { red, green, blue;
        static Map<String,Color2> colorMap;
        private static Map<String, Color2> colorMap() {
            synchronized (Color2.class) {
                return (colorMap == null) ? (colorMap = new HashMap<String,Color2>()) : colorMap;
            }
        }
        Color2() {
            colorMap().put(toString(), this);
        }
    }


    public void testIterable () {
        ByteBuffer buf = ByteBuffer.wrap(new byte[] { 0x12, 0x13, 0x14 });
        for (Byte b : Iterables.asIterable(buf)) printByte(b);

        for (Character c : Iterables.asIterable("klmnopq")) printChar(c);
    }

    private static void printByte (byte b) {
        System.out.println("buf.get() returns " + b);
    }

    private static void printChar (char c) {
        System.out.println("charAt returns " + c);
    }

    public static class Iterables {
        public static Iterable<Byte> asIterable(final ByteBuffer buf) {
            return new Iterable<Byte>() {
                public SimpleIterator<Byte> iterator() {
                    return new SimpleIterator<Byte>() {
                        public boolean hasNext() {
                            return buf.hasRemaining();
                        }
                        public Byte next() {
                            return Byte.valueOf(buf.get());
                        }
                    };
                }
            };
        }
        public static Iterable<Character> asIterable(final CharSequence s) {
            return new Iterable<Character>() {
                public SimpleIterator<Character> iterator() {
                    return new SimpleIterator<Character>() {
                        public boolean hasNext() {
                            return index < s.length();
                        }
                        public Character next() {
                            return Character.valueOf(s.charAt(index++));
                        }
                        private int index = 0;
                    };
                }
            };
        }
    }
}
