package java.util.concurrent.atomic;
/**
 * An AtomicFloat maintains a <tt>float</tt> value that is updated atomically.
 **/

public final class AtomicFloat implements java.io.Serializable { 

  /**
   * Create a new AtomicFloat witht he given initial value;
   **/
  public AtomicFloat(float initialValue) {
    // for now;
  }
  
  /**
   * Get the current value
   **/
  public float get() {
    return 0.0f; // for now
  }
  
  /**
   * Set to the given value
   **/
  public void set(float newValue) {
    // for now
  }
  
  /**
   * Set to the give value and return the old value
   **/
  public float getAndSet(float newValue) {
    return 0.0f; // for now
  }
 
  
  /**
   * Atomically set the value to the given updated value
   * if the current value is equal to the expected value.
   * @return true if successful. 
   **/
  public boolean attemptUpdate(float expect, float update) {
    return false; // for now
  }
  
}
