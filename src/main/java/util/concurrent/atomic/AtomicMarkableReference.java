/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;

/**
 * An <tt>AtomicMarkableReference</tt> maintains an object reference
 * along with a mark bit, that can be updated atomically.
 * <p>
 * Implementation note. This implementation maintains marked references
 * by internally "boxing" [reference, boolean] pairs.
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/07/31 16:26:57 $
 * @editor $Author: tim $
 * @author Doug Lea
 */
public class AtomicMarkableReference<V>  {

    private class ReferenceBooleanPair {
        private final V reference;
        private final boolean bit;
        ReferenceBooleanPair(V r, boolean i) {
            reference = r; bit = i;
        }
    }

    private final AtomicReference<ReferenceBooleanPair>  atomicRef;

    /**
     * Creates a new <tt>AtomicMarkableReference</tt> with the given
     * initial values.
     *
     * @param initialRef the intial reference
     * @param initialMark the intial mark
     */
    public AtomicMarkableReference(V initialRef, boolean initialMark) {
        atomicRef = new AtomicReference<ReferenceBooleanPair> (new ReferenceBooleanPair(initialRef, initialMark));
    }

    /**
     * Returns the current value of the reference.
     *
     * @return the current value of the reference
     */
    public V getReference() {
        return atomicRef.get().reference;
    }

    /**
     * Returns the current value of the mark.
     *
     * @return the current value of the mark
     */
    public boolean isMarked() {
        return atomicRef.get().bit;
    }

    /**
     * Returns the current values of both the reference and the mark.
     * Typical usage is <code>boolean[1] holder; ref = v.get(holder); </code>.
     *
     * @param markHolder an array of size of at least one. On return,
     * <tt>markholder[0]</tt> will hold the value of the mark.
     * @return the current value of the reference
     */
    public V get(boolean[] markHolder) {
        ReferenceBooleanPair p = atomicRef.get();
        markHolder[0] = p.bit;
        return p.reference;
    }

    /**
     * Atomically sets the value of both the reference and mark
     * to the given update values if the
     * current reference is <code>==</code> to the expected reference
     * and the current mark is equal to the expected mark.  Any given
     * invocation of this operation may fail (return
     * <code>false</code>) spuriously, but repeated invocation when
     * the current value holds the expected value and no other thread
     * is also attempting to set the value will eventually succeed.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference the new value for the reference
     * @param expectedMark the expected value of the mark
     * @param newMark the new value for the mark
     * @return true if successful
     */
    public boolean compareAndSet(V       expectedReference,
                                 V       newReference,
                                 boolean expectedMark,
                                 boolean newMark) {
        ReferenceBooleanPair current = atomicRef.get();
        return  expectedReference == current.reference &&
            expectedMark == current.bit &&
            ((newReference == current.reference && newMark == current.bit) ||
             atomicRef.compareAndSet(current,
                                     new ReferenceBooleanPair(newReference,
                                                              newMark)));
    }

    /**
     * Unconditionally sets the value of both the reference and mark.
     *
     * @param newReference the new value for the reference
     * @param newMark the new value for the mark
     */
    public void set(V newReference, boolean newMark) {
        ReferenceBooleanPair current = atomicRef.get();
        if (newReference != current.reference || newMark != current.bit)
            atomicRef.set(new ReferenceBooleanPair(newReference, newMark));
    }

    /**
     * Atomically sets the value of the mark to the given update value
     * if the current reference is <code>==</code> to the expected
     * reference.  Any given invocation of this operation may fail
     * (return <code>false</code>) spuriously, but repeated invocation
     * when the current value holds the expected value and no other
     * thread is also attempting to set the value will eventually
     * succeed.
     *
     * @param expectedReference the expected value of the reference
     * @param newMark the new value for the mark
     * @return true if successful
     */
    public boolean attemptMark(V expectedReference, boolean newMark) {
        ReferenceBooleanPair current = atomicRef.get();
        return  expectedReference == current.reference &&
            (newMark == current.bit ||
             atomicRef.compareAndSet
             (current, new ReferenceBooleanPair(expectedReference,
                                                newMark)));
    }
}
