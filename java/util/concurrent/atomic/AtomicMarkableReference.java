package java.util.concurrent.atomic;
/**
 * An AtomicMarkableReference maintains an object reference along with a
 * mark bit, that can be updated atomically.
 **/
public class AtomicMarkableReference<V> {
  private class ReferenceBooleanPair {
    private final V reference;
    private final boolean bit;
    ReferenceBooleanPair(V r, boolean i) {
      reference = r; bit = i;
    }
  }

  private final AtomicReference<ReferenceBooleanPair> atomicRef;

  public AtomicMarkableReference(V r, boolean bit) {
    atomicRef = new AtomicReference<ReferenceBooleanPair>(new ReferenceBooleanPair(r, bit));
  }

  public V getReference() {
    return ((ReferenceBooleanPair)(atomicRef.get())).reference;
  }
  public boolean isMarked() {
    return ((ReferenceBooleanPair)(atomicRef.get())).bit;
  }

  public V get(boolean[] markHolder) {
    ReferenceBooleanPair p = (ReferenceBooleanPair)(atomicRef.get());
    markHolder[0] = p.bit;
    return p.reference;
  }

  public boolean attemptUpdate(V       expectedReference,
                               V       newReference,
                               boolean expectedMark,
                               boolean newMark) {
    ReferenceBooleanPair current = (ReferenceBooleanPair)(atomicRef.get());
    return  expectedReference == current.reference &&
      expectedMark == current.bit &&
      ((newReference == current.reference && newMark == current.bit) ||
       atomicRef.attemptUpdate(current,
                               new ReferenceBooleanPair(newReference,
                                                        newMark)));
  }

  public void set(V r, boolean mark) {
    ReferenceBooleanPair current = (ReferenceBooleanPair)(atomicRef.get());
    if (r != current.reference || mark != current.bit)
      atomicRef.set(new ReferenceBooleanPair(r, mark));
  }

  public boolean attemptMark(V expectedReference, boolean newMark) {
    ReferenceBooleanPair current = (ReferenceBooleanPair)(atomicRef.get());
    return  expectedReference == current.reference &&
      (newMark == current.bit ||
       atomicRef.attemptUpdate(current,
                               new ReferenceBooleanPair(expectedReference,
                                                        newMark)));
  }
}
