package java.util.concurrent.atomic;
/**
 * An AtomicStampedReference maintains an object reference along with an
 * integer "stamp", that can be updated atomically.
 **/

public class AtomicStampedReference<V> {
  private class ReferenceIntegerPair {
    private final V reference;
    private final int integer;
    ReferenceIntegerPair(V r, int i) {
      reference = r; integer = i;
    }
  }

  private final AtomicReference<ReferenceIntegerPair> atomicRef;

  public AtomicStampedReference(V r, int i) {
    atomicRef = new AtomicReference<ReferenceIntegerPair>(new ReferenceIntegerPair(r, i));
  }

  public V getReference() {
    return ((ReferenceIntegerPair)(atomicRef.get())).reference;
  }
  public int getStamp() {
    return ((ReferenceIntegerPair)(atomicRef.get())).integer;
  }

  public V get(int[] stampHolder) {
    ReferenceIntegerPair p = (ReferenceIntegerPair)(atomicRef.get());
    stampHolder[0] = p.integer;
    return p.reference;
  }

  public boolean attemptUpdate(V      expectedReference,
                               V      newReference,
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

  public void set(V r, int stamp) {
    ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
    if (r != current.reference || stamp != current.integer)
      atomicRef.set(new ReferenceIntegerPair(r, stamp));
  }

  public boolean attemptStamp(V expectedReference, int newStamp) {
    ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
    return  expectedReference == current.reference &&
      (newStamp == current.integer ||
       atomicRef.attemptUpdate(current,
                               new ReferenceIntegerPair(expectedReference,
                                                        newStamp)));
  }


}

