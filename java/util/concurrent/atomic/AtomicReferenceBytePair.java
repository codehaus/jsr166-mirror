package java.util.concurrent.atomic;
/*
 * An AtomicByteIntegerPair maintains a pair of values
 * that can be updated atomically.
 */
public class AtomicReferenceBytePair {
  public AtomicReferenceBytePair(Object r, byte i) {}

  // simple reads
  public Object getReference() {
    return null;
  }
  public byte getByte() {
    return 0;
  }

  // paired reads
  public Object get(byte[] holder) {
    return null;
  }

  // CAS
  public boolean attemptUpdate(Object oldr, Object newr,
                               byte oldi, byte    newi) {
    return false;
  }

  // unconditional writes (always paired)
  public void set(Object r, byte i) {}
}
