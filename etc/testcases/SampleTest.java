// root package

import junit.framework.TestCase;

/**
 * A test case to check whether the Ant junit tasks
 * are working. Requires JUnit 1.8 or later.
 */
public class SampleTest extends TestCase {
    
    interface Binop<Op1, Op2, Result>  {
        Result apply (Op1 op1, Op2 op2);
    }
        
    static class Add implements Binop<Integer, Integer, Integer> {
        public Integer apply (Integer a, Integer b) { 
            return new Integer(a.intValue() + b.intValue()); 
        }
    }
    
    public void testSomething () throws Exception {
        Binop<Integer, Integer, Integer> adder = new Add();
        Integer TWO = new Integer(2);
        assertEquals("2 plus 2 should equal 5", 5, adder.apply(TWO, TWO).intValue());
    }
}
