package java.util.concurrent.atomic;

/**
 * An AtomicLong maintains a <tt>long</tt> value that is updated atomically.
 **/

public final class AtomicLong implements java.io.Serializable { 

  /**
   * Create a new AtomicLong with the given initial value;
   **/
  public AtomicLong(long initialValue) {
    // for now
  }
  
  /**
   * Get the current value
   **/
  public long get() {
    return 0; // for now
  }
  
  
  /**
   * Set to the given value
   **/
  public void set(long newValue) {
    // for now
  }
  
  /**
   * Set to the give value and return the old value
   **/
  public long getAndSet(long newValue) {
    return 0; // for now
  }
  
  
  /**
   * Atomically increment the current value.
   * @return the new value;
   **/
  public long increment() {
    return 0; // for now
  }
  
  
  /**
   * Atomically decrement the current value.
   * @return the new value;
   **/
  public long decrement() {
    return 0; // for now
  }
  
  
  /**
   * Atomically add the given value to current value.
   * @return the new value;
   **/
  public long add(long x) {
    return 0; // for now
  }
  
  
  /**
   * Atomically set the value to the given updated value 
   * if the current value is equal to the expected value.
   * @return true if successful. 
   **/
  public boolean attemptUpdate(long expect, long update) {
    return false; // for now
  }
  
  
}
