/*
 * File: SwingWorkerDemo.java
 * 
 * Written by Joseph Bowbeer and released to the public domain,
 * as explained at http://creativecommons.org/licenses/publicdomain
 */

package jsr166.swing;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class SwingWorkerDemo extends javax.swing.JApplet {

  private static final int TIMEOUT = 5000; // 5 seconds
  private javax.swing.JLabel status;
  private javax.swing.JButton start;
  private javax.swing.Timer timer;
  private SwingWorker worker;

  public SwingWorkerDemo() {
    status = new javax.swing.JLabel("Ready");
    status.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    getContentPane().add(status, java.awt.BorderLayout.CENTER);
    start = new javax.swing.JButton("Start");
    getContentPane().add(start, java.awt.BorderLayout.SOUTH);

    start.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        if (start.getText().equals("Start")) {
          start.setText("Stop");
          status.setText("Working...");
          worker = new DemoSwingWorker();
          worker.start();
          timer.start();
        } else if (worker.cancel(true)) {
          status.setText("Cancelled");
        }
      }
    });

    timer = new javax.swing.Timer(TIMEOUT, null);
    timer.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        if (worker.cancel(true)) {
          status.setText("Timed out");
        }
      }
    });
    timer.setRepeats(false);
  }

  private class DemoSwingWorker extends SwingWorker<String> {
    protected String construct() throws InterruptedException {
      // Take a random nap. If we oversleep, timer cancels us.
      Thread.sleep(new java.util.Random().nextInt(2 * TIMEOUT));
      return "Success";
    }
    protected void finished() {
      timer.stop();
      start.setText("Start");
      try {
        status.setText(get());
      } catch (CancellationException ex) {
        // status was assigned when cancelled 
      } catch (ExecutionException ex) {
        status.setText("Exception: " + ex.getCause());
      } catch (InterruptedException ex) {
        // event-dispatch thread won't be interrupted 
        throw new IllegalStateException(ex + "");
      }
    }
  }
}
