import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.util.concurrent.CountDownLatch;

public class Test {

    private CountDownLatch started = new CountDownLatch(1);
    private CountDownLatch done = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        new Test().go();
    }

    private void go() throws InterruptedException {
        new UIThread().start();
        started.await();
        done.await();
    }

    private class UIThread extends Thread {
        UIThread() {
            setDaemon(true);
        }

        public void run() {
            Display display = new Display();
            Shell shell = new Shell(display, SWT.SHELL_TRIM);
            shell.setText("Test");
            shell.setSize(800, 500);
            shell.open();

            started.countDown();
            while (!shell.isDisposed ()) {
                if (!display.readAndDispatch ()) display.sleep ();
            }
            display.dispose();
            done.countDown();
        }
    }

}