package java.util.concurrent.atomic;
/*
 * An AtomicBooleanIntegerPair maintains a pair of values
 * that can be updated atomically.
 */
public class AtomicReferenceBooleanPair {
  public AtomicReferenceBooleanPair(Object r, boolean i) {}

  // simple reads
  public Object getReference() {
    return null;
  }
  public boolean getBoolean() {
    return false;
  }

  // paired reads
  public Object get(boolean[] holder) {
    return null;
  }

  // CAS
  public boolean attemptUpdate(Object oldr, Object newr,
                               boolean oldi, boolean    newi) {
    return false;
  }

  // unconditional writes (always paired)
  public void set(Object r, boolean i) {}
}
