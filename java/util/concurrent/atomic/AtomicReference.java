package java.util.concurrent.atomic;
/**
 * An AtomicReference maintains an object reference that is updated atomically.
 **/

public class AtomicReference implements java.io.Serializable { 
  private volatile Object value;

  /**
   * Create a new AtomicReference with the given initial value;
   **/
  public AtomicReference(Object initialValue) {
    value = initialValue;
  }
  
  /**
   * Get the current value
   **/
  public final Object get() {
    return value;
  }
  
  /**
   * Set to the given value
   **/
  public final void set(Object newValue) {
    value = newValue;
  }
  
  /**
   * Atomically set the value to the given updated value 
   * if the current value <tt>==</tt> the expected value.
   * @return true if successful. 
   **/
  public final boolean attemptUpdate(Object expect, Object update) {
    return false;
  }

  /**
   * Set to the given value and return the old value
   **/
  public final Object getAndSet(Object newValue) {
    for (;;) {
      Object x = get();
      if (attemptUpdate(x, newValue))
        return x;
    }
  }
  
}
