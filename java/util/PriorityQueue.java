package java.util;
import java.util.*;

/**
 * An unbounded (resizable) priority queue based on a priority
 * heap.The take operation returns the least element with respect to
 * the given ordering. (If more than one element is tied for least
 * value, one of them is arbitrarily chosen to be returned -- no
 * guarantees are made for ordering across ties.) Ordering follows the
 * java.util.Collection conventions: Either the elements must be
 * Comparable, or a Comparator must be supplied. Comparison failures
 * throw ClassCastExceptions during insertions and extractions.
 **/

public class PriorityQueue implements Queue {
  public PriorityQueue(int initialCapacity) {}
  public PriorityQueue(int initialCapacity, Comparator comparator) {}

  public PriorityQueue(int initialCapacity, Collection initialElements) {}

  public PriorityQueue(int initialCapacity, Comparator comparator, Collection initialElements) {}
  
  public boolean add(Object x) {
    return false;
  }
  public Object poll() {
    return null;
  }
  public Object peek() {
    return null;
  }
  public boolean isEmpty() {
    return false;
  }
  public int size() {
    return 0;
  }
  public Object[] toArray() {
    return null;
  }

  public Object[] toArray(Object[] array) {
    return null;
  }

}
