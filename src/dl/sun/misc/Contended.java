/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package sun.misc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation expressing that objects and/or their fields are
 * expected to encounter memory contention, generally in the form of
 * "false sharing". This annotation serves as a hint that such objects
 * and fields should reside in locations isolated from those of other
 * objects or fields. Susceptibility to memory contention is a
 * property of the intended usages of objects and fields, not their
 * types or qualifiers. The effects of this annotation will nearly
 * always add significant space overhead to objects.  The use of
 * {@code @Contended} is warranted only when the performance impact of
 * this time/space tradeoff is intrinsically worthwhile; for example,
 * in concurrent contexts in which each instance of the annotated
 * object is often accessed by a different thread.
 *
 * <p>A {@code @Contended} field annotation may optionally include a
 * contention group tag. All fields with the same tag are considered
 * as a group with respect to isolation from other groups. A default
 * annotation without a tag indicates contention with all other
 * fields, including other {@code @Contended} ones.

 * <p>When the annotation is used at the class level, all unannotated
 * fields of the object are considered to be in the same default
 * group, separate from any fields that carry their own (possibly
 * tagged) {@code @Contended} annotations.
 *
 * @since 1.8
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface Contended {
    /**
     * The (optional) contention group tag.
     */
    String value() default "";
}
