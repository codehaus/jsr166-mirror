package jsr166.webcrawler;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;


public final class WebCrawler1 implements WebCrawler {

    private static Logger log =
        Logger.getLogger(WebCrawler1.class.getPackage().getName());


    public WebCrawler1(Comparator<URL> searchOrder, long timeout, TimeUnit unit) {
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
        private final BlockingQueue<URL> rq;

        public void run() {
            try {
                for (seen.add(url); !done(); url = poll())
                    try {
                        for (URL link : fetchLinks(url)) add(link);
                        if (!rq.offer(url, timeout, unit)) return;
                    } catch (IOException e) {
                        // skip over url, couldn’t get its contents
                    } catch (InterruptedException e) {
                        // reset interrupt status
                        Thread.currentThread().interrupt();
                        return;
                    }
            } finally {
                log.info("exiting crawl with url="+url);
            }
        }

        private void add(URL u) {
            if (!seen.contains(u)) {
                seen.add(u);
                pq.offer(u);
                log.fine("added "+u);
            }
        }

        private boolean done() {
            return url == null || Thread.currentThread().isInterrupted();
        }

        private URL poll() {
            return pq.poll();
        }

        private final Set<URL> seen = new HashSet<URL>();
        private final Queue<URL> pq =
            new PriorityQueue<URL>(PQ_CAPACITY, searchOrder);


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
