package java.util.concurrent.atomic;

/**
 * An AtomicInteger maintains an <tt>int</tt> value that is updated
 * atomically.
 **/

public final class AtomicInteger implements java.io.Serializable { 

  /**
   * Create a new AtomicInteger with the given initial value;
   **/
  public AtomicInteger(int initialValue) {
    // for now
  }

  /**
   * Get the current value
   **/
  public int get() {
    return 0; // for now
  }
  
  /**
   * Set to the given value
   **/
  public void set(int newValue) {
    // for now
  }

  /**
   * Set to the give value and return the old value
   **/
  public int getAndSet(int newValue) {
    return 0; // for now
  }

  /**
   * Atomically increment the current value.
   * @return the new value;
   **/
  public int increment() {
    return 0; // for now
  }
  
  
  /**
   * Atomically decrement the current value.
   * @return the new value;
   **/
  public int decrement() {
    return 0; // for now
  }
  
  
  /**
   * Atomically add the given value to current value.
   * @return the new value;
   **/
  public int add(int x) {
    return 0; // for now
  }
  
  
  /**
   * Atomically set the value to the given updated value 
   * if the current value is equal to the expected value.
   * @return true if successful. 
   **/
  public boolean attemptUpdate(int expect, int update) {
    return false; // for now
  }
  
  
}
