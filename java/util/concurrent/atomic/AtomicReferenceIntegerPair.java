package java.util.concurrent.atomic;

/**
 * An AtomicReferenceIntegerPair maintains a pair of values
 * that can be updated atomically.
 */
public class AtomicReferenceIntegerPair {
  public AtomicReferenceIntegerPair(Object r, int i) {}

  // simple reads
  public Object getReference() {
    return null;
  }
  public int    getInteger() {
    return 0;
  }

  // paired reads
  public Object get(int[] holder) {
    return null;
  }

  // CAS
  public boolean attemptUpdate(Object oldr, Object newr,
                               int    oldi, int    newi) {
    return false;
  }

  // unconditional writes (always paired)
  public void set(Object r, int i) {}
}
