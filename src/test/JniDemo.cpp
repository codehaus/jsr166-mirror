/*
 * Native method implementations for JniDemo
 */

#include <JniDemo.h>

class JniDemo {
public:
    static int count;
};

int JniDemo::count = 0;


/*
 * Class:     JniDemo
 * Method:    getCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_JniDemo_getCount
  (JNIEnv *, jobject)
{
    return JniDemo::count;
}

/*
 * Class:     JniDemo
 * Method:    setCount
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_JniDemo_setCount
  (JNIEnv *, jobject, jint count)
{
    JniDemo::count = count;
}

