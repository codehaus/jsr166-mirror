package java.util.concurrent;
import java.util.*;

/**
 * An unbounded queue based on linked nodes.
 **/

public class LinkedBlockingQueue implements BlockingQueue, java.io.Serializable {

  public LinkedBlockingQueue() {}
  public void put(Object x) {
  }
  public boolean offer(Object x, long time, Clock granularity) {
    return false;
  }
  public Object take() throws InterruptedException {
    return null;
  }
  public boolean add(Object x) {
    return false;
  }
  public Object poll() {
    return null;
  }
  public Object poll(long time, Clock granularity) throws InterruptedException {
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
