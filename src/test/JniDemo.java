/*
 * Demo the use of JNI in Ant
 */
 
public class JniDemo {
    
    static {
        System.loadLibrary("JniDemo");
    }
    
    public native int getCount();
    public native void setCount(int count);
    
    public static void main(String[] args) {
        JniDemo demo = new JniDemo();
        int count = demo.getCount();
        System.out.println("Count starts at "+count);
        demo.setCount(4);
        System.out.println("Count set to 4");
        count = demo.getCount();
        System.out.println("Count is actually "+count);
    }
}