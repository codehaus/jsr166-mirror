package java.util.concurrent.atomic;
/**
 * An AtomicStampedReference maintains an object reference along with an
 * integer "stamp", that can be updated atomically.
 **/

public class AtomicStampedReference {
  private class ReferenceIntegerPair {
    private final Object reference;
    private final int integer;
    ReferenceIntegerPair(Object r, int i) { 
      reference = r; integer = i;
    }
  } 

  private final AtomicReference atomicRef;

  public AtomicStampedReference(Object r, int i) {
    atomicRef = new AtomicReference(new ReferenceIntegerPair(r, i));
  }

  public Object getReference() {
    return ((ReferenceIntegerPair)(atomicRef.get())).reference;
  }
  public int getStamp() {
    return ((ReferenceIntegerPair)(atomicRef.get())).integer;
  }

  public Object get(int[] stampHolder) {
    ReferenceIntegerPair p = (ReferenceIntegerPair)(atomicRef.get());
    stampHolder[0] = p.integer;
    return p.reference;
  }

  public boolean attemptUpdate(Object expectedReference, 
                               Object newReference,
                               int    expectedStamp, 
                               int    newStamp) {
    ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
    return  expectedReference == current.reference && 
      expectedStamp == current.integer &&
      ((newReference == current.reference && newStamp == current.integer) ||
       atomicRef.attemptUpdate(current, 
                               new ReferenceIntegerPair(newReference, 
                                                        newStamp)));
  }

  public void set(Object r, int stamp) {
    ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
    if (r != current.reference || stamp != current.integer)
      atomicRef.set(new ReferenceIntegerPair(r, stamp));
  }

  public boolean attemptStamp(Object expectedReference, int newStamp) {
    ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
    return  expectedReference == current.reference && 
      (newStamp == current.integer ||
       atomicRef.attemptUpdate(current, 
                               new ReferenceIntegerPair(expectedReference, 
                                                        newStamp)));
  }


}

