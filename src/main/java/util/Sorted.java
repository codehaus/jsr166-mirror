 package java.util;

/**
 * Mixin interface used by collections, maps, and similar objects to
 * indicate that their elements or mappings are stored in sorted
 * order.  This allows consumers of collections to determine if the collection
 * is sorted, and if so, retrieve the sort comparator.
 * @since 1.5
 * @author Josh Bloch
 */

public interface Sorted {
    /**
     * Returns the comparator used to order this collection, or <tt>null</tt>
     * if this collection is sorted according to its elements natural ordering
     * (using <tt>Comparable</tt>.)  
     *
     * @return the comparator used to order this collection, or <tt>null</tt>
     * if this collection is sorted according to its elements natural ordering.
     */
    Comparator comparator();
}
