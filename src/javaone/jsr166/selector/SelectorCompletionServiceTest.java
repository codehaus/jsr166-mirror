/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166.selector;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.channels.SelectionKey.*;
import static jsr166.selector.SelectorCompletionService.*;
import static jsr166.selector.SelectorCompletionService.Op.*;

import junit.framework.*;

/**
 * Driver for demo to supplement Birds-of-Feather session.
 */
public class SelectorCompletionServiceTest extends TestCase {
           
    private static final int PORT = 1234;
    private static final int MAX_ITERS = 10;
    
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

    private SelectorCompletionService service;
    
    public void testService() {
        try {
            service = new SelectorCompletionService();
            System.out.println("constructed completion service");
        }
        catch (IOException e) {
            fail("i/o failure constructing service: "+e);
        }

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
            
            service.submit(serverAcceptor(serverSocketChannel));
            
            SocketChannel clientChannel = SocketChannel.open();

            clientChannel.configureBlocking(false);
            clientChannel.connect(new InetSocketAddress(InetAddress.getByName("localhost"), PORT));

            service.submit(clientConnector(clientChannel));

            // Main loop

            Future<String> f;
            for (int i = 0; i < MAX_ITERS; ++i) {
                f = service.poll();
                if (f != null) {
                    String s = f.get();
                    if (s != null)
                        System.out.println("result is "+s);
                }
                else {
                    System.out.println("nothing to do, sleeping");
                    Thread.sleep(1000);
                }
            }
        }
        catch (InterruptedException e) {
            System.err.println("interrupted: "+e);
        }
        catch (ExecutionException e) {
            System.err.println("execution failure: "+e);
        }
        catch (IOException e) {
            System.err.println("IO exception: "+e);
        }
        finally {
            try {
                service.shutdown();
                System.out.println("shut down completion service");
            }
            catch (IOException e) {
                fail("i/o exception in service shutdown: "+e);
            }
        }
    }

    
    private Callable<String> clientConnector(SocketChannel channel) {
        return connect(channel, new SelectableTask<String>() {
            public String process(Future<String> future, SelectableChannel sc) throws IOException {
                System.out.println("in client connect task");
                SocketChannel channel = (SocketChannel) sc;
                channel.finishConnect();
                service.submit(clientWriter(channel));
                future.cancel(false);
                return "finished connecting";
            }
        });
    }
    
    private Callable<String> clientWriter(SocketChannel channel) {
        return write(channel, new SelectableTask<String>() {
            public String process(Future<String> future, SelectableChannel sc) throws IOException {
                System.out.println("in client write task");
                SocketChannel channel = (SocketChannel) sc;
                buffer.clear();
                buffer.put("Hello from client".getBytes());
                buffer.flip();
                channel.write(buffer);
                future.cancel(false);
                return "client wrote hello";
            }
        });
    }

    private Callable<String> serverAcceptor(ServerSocketChannel channel) {
        return accept(channel, new SelectableTask<String>() {
            public String process(Future<String> future, SelectableChannel sc) throws IOException {
                System.out.println("in server accept task");
                ServerSocketChannel channel = (ServerSocketChannel) sc;
                SocketChannel socket = channel.accept();
                service.submit(serverReader(socket));
                return "server accepted: "+socket;
            }
        });
    }

    private Callable<String> serverReader(SocketChannel channel) {
        return read(channel, new SelectableTask<String>() {
            public String process(Future<String> future, SelectableChannel sc) throws IOException {
                System.out.println("in server read task");
                SocketChannel channel = (SocketChannel) sc;
                StringBuffer sb = new StringBuffer();
                int count = channel.read(buffer);
                return "server read: "+buffer.asCharBuffer().toString();
            }
        });
    }


    public void testOps() {
        // Try out the package-private bits2ops method on
        // various bitmask values.

        int[] values = new int[] {
            OP_ACCEPT,
            OP_CONNECT,
            OP_READ,
            OP_WRITE,
            OP_ACCEPT | OP_CONNECT,
            OP_READ   | OP_WRITE,
            0,
        };

        for (int v : values) {
            System.out.println(""+v+"\t"+SelectorCompletionService.bits2ops(v));
            assertEquals(v, SelectorCompletionService.ops2bits(
                            SelectorCompletionService.bits2ops(v)));
        }
    }
}
