/*
 * Demo the use of JNI in Ant
 */
import java.util.*;

public class JniDemo {
    
    static {
        System.loadLibrary("JniDemo");
    }
    
    public native int getCount();
    public native void setCount(int count);
    
    public static void main(String[] args) {
        Map<String, Integer> m = new HashMap<String, Integer>();
        for (String s : args) {
            if (!m.containsKey(s)) m.put(s, 0);
            m.put(s, m.get(s)+1);
        }
        for (String s : m.keySet()) {
            System.out.println(""+m.get(s)+" "+s);
        }
        
        JniDemo demo = new JniDemo();
        int count = demo.getCount();
        System.out.println("Count starts at "+count);
        demo.setCount(4);
        System.out.println("Count set to 4");
        count = demo.getCount();
        System.out.println("Count is actually "+count);
    }
}