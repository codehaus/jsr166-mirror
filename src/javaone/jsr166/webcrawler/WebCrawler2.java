package jsr166.webcrawler;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;


public final class WebCrawler2 implements WebCrawler {

    private static Logger log =
        Logger.getLogger(WebCrawler2.class.getPackage().getName());


    public WebCrawler2(Comparator<URL> searchOrder, long timeout, TimeUnit unit) {
        this.searchOrder = searchOrder;
        this.timeout = timeout;
        this.unit = unit;
    }

    private final Comparator<URL> searchOrder;
    private final long timeout;
    private final TimeUnit unit;
    private final ExecutorService pool = Executors.newCachedThreadPool();


    public Future<?> crawl(URL startUrl, BlockingQueue<URL> rq) {
        return pool.submit(new CrawlTask(startUrl, rq));
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private class CrawlTask implements Runnable {

        CrawlTask (URL startUrl, BlockingQueue<URL> resultQueue) {
            this.url = startUrl;
            this.rq = resultQueue;
        }

        private URL url;
        private boolean done = false;
        private final BlockingQueue<URL> rq;

        public void run() {
            try {
                for (seen.put(url, true); !done(); url = poll())
                    pool.execute(new Runnable() {
                        public void run() {
                            try {
                                for (URL link : fetchLinks(url)) add(link);
                                if (!rq.offer(url, timeout, unit)) {
                                    done = true;
                                    return;
                                }
                            } catch (IOException e) {
                                // skip over url, couldn’t get its contents
                            } catch (InterruptedException e) {
                                // reset interrupt status
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    });
            } catch (InterruptedException e) {
                // reset interrupt status
                Thread.currentThread().interrupt();
            } finally {
                log.info("exiting crawl with url="+url);
            }
        }

        private void add(URL u) throws InterruptedException {
            if (seen.putIfAbsent(u, true) == null) {
                pq.put(u);
                log.fine("added "+u);
            }
        }

        private boolean done() {
            return done || url == null || Thread.currentThread().isInterrupted();
        }

        private URL poll() throws InterruptedException {
            return pq.poll(timeout, unit);
        }

        private final ConcurrentMap<URL, Boolean> seen =
            new ConcurrentHashMap<URL, Boolean>();
        private final BlockingQueue<URL> pq =
            new PriorityBlockingQueue<URL>(PQ_CAPACITY, searchOrder);


        /**
         * Gets URL contents and parses list of URL links
         * from them; may block indefinitely.
         */
        private List<URL> fetchLinks(URL url) throws IOException {
            LinkedList<URL> links = new LinkedList<URL>();
            LinkParser parser = new LinkParser();
            parser.parse(url, links);
            return links;
        }
    }

    private static final int PQ_CAPACITY = 20;
}
