package java.util.concurrent.atomic;
/**
 * An AtomicMarkableReference maintains an object reference along with a
 * mark bit, that can be updated atomically.
 **/
public class AtomicMarkableReference {
  private class ReferenceBooleanPair {
    private final Object reference;
    private final boolean bit;
    ReferenceBooleanPair(Object r, boolean i) { 
      reference = r; bit = i;
    }
  } 

  private final AtomicReference atomicRef;

  public AtomicMarkableReference(Object r, boolean bit) {
    atomicRef = new AtomicReference(new ReferenceBooleanPair(r, bit));
  }

  public Object getReference() {
    return ((ReferenceBooleanPair)(atomicRef.get())).reference;
  }
  public boolean isMarked() {
    return ((ReferenceBooleanPair)(atomicRef.get())).bit;
  }

  public Object get(boolean[] markHolder) {
    ReferenceBooleanPair p = (ReferenceBooleanPair)(atomicRef.get());
    markHolder[0] = p.bit;
    return p.reference;
  }

  public boolean attemptUpdate(Object  expectedReference, 
                               Object  newReference,
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

  public void set(Object r, boolean mark) {
    ReferenceBooleanPair current = (ReferenceBooleanPair)(atomicRef.get());
    if (r != current.reference || mark != current.bit)
      atomicRef.set(new ReferenceBooleanPair(r, mark));
  }

  public boolean attemptMark(Object expectedReference, boolean newMark) {
    ReferenceBooleanPair current = (ReferenceBooleanPair)(atomicRef.get());
    return  expectedReference == current.reference && 
      (newMark == current.bit ||
       atomicRef.attemptUpdate(current, 
                               new ReferenceBooleanPair(expectedReference, 
                                                        newMark)));
  }
}
