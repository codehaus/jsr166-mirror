 package java.util;

/**
 * Mixin interface used by collections, maps, and similar objects to
 * indicate that their elements or mappings are stored in sorted
 * order.
 */

public interface Sorted {
    /**
     * Returns the comparator used to order this collection, or <tt>null</tt>
     * if this collection is sorted according to its elements natural ordering.
     *
     * @return the comparator used to order this collection, or <tt>null</tt>
     * if this collection is sorted according to its elements natural ordering.
     */
    Comparator comparator();
}
