package jsr166.webcrawler;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;


public final class WebCrawler3 implements WebCrawler {

    private static Logger log =
        Logger.getLogger(WebCrawler3.class.getPackage().getName());


    public WebCrawler3(Comparator<URL> searchOrder, long timeout, TimeUnit unit) {
        this.searchOrder = searchOrder;
        this.timeout = timeout;
        this.unit = unit;
    }

    private final Comparator<URL> searchOrder;
    private final long timeout;
    private final TimeUnit unit;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);


    public Future<?> crawl(URL startUrl, BlockingQueue<URL> rq) {
        return pool.submit(new CrawlTask(startUrl, rq));
    }

    public void shutdown() {
        pool.shutdownNow();
        sched.shutdown();
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
                for (seen.put(url, true); !done(); url = poll()) {
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
                }
            } catch (InterruptedException e) {
                // reset interrupt status
                Thread.currentThread().interrupt();
            } finally {
                log.info("exiting crawl with url="+url);
            }
        }

        void add(URL u) throws InterruptedException {
            if (seen.putIfAbsent(u, true) != null) return;

            String host = u.getHost();
            PeriodicHostTask hostTask = new PeriodicHostTask(host);
            PeriodicHostTask existing = hosts.putIfAbsent(host, hostTask);
            if (existing == null) {
                existing = hostTask;
                if (sched.isShutdown()) {
                    log.info("skipping task creation for host "+host);
                } else {
                    sched.scheduleWithFixedDelay(hostTask, 0, 1, TimeUnit.SECONDS);
                    log.info("scheduled task for host "+host);
                }
            }
            existing.add(u);
            log.fine("added "+u);
        }

        private boolean done() {
            return done || url == null || Thread.currentThread().isInterrupted();
        }

        private URL poll() throws InterruptedException {
            return pq.poll(10L, TimeUnit.SECONDS);
        }

        private final ConcurrentMap<URL, Boolean> seen =
            new ConcurrentHashMap<URL, Boolean>();

        private final BlockingQueue<URL> pq =
            new PriorityBlockingQueue<URL>(PQ_CAPACITY, searchOrder);

        private final ConcurrentMap<String, PeriodicHostTask> hosts =
            new ConcurrentHashMap<String, PeriodicHostTask>();


        private class PeriodicHostTask implements Runnable {

            public PeriodicHostTask(String host) {
                this.host = host;
            }

            public void run() { // called periodically
                URL url = hq.poll();
                if (url != null) {
                    try {
                        pq.put(url);
                    } catch (InterruptedException e) {
                        // reset interrupt status
                        Thread.currentThread().interrupt();
                    }
                }
            }

            private void add(URL url) {
                hq.offer(url);
            }

            private final String host;
            private final Queue<URL> hq = new ConcurrentLinkedQueue<URL>();
        }

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
