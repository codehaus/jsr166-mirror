package jsr166.webcrawler;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.regex.*;

public final class WebCrawler {

    public WebCrawler(Comparator<URL> searchOrder) {
        this.searchOrder = searchOrder;
    }

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Comparator<URL> searchOrder;


    public Future<?> crawl(URL startUrl, BlockingQueue<URL> rq) {
        return pool.submit(new CrawlTask(startUrl, rq));
    }

    public void close() {
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
          for (seen.add(url); !done(); url = poll())
              try {
                  for (URL link : fetchLinks(url)) add(link);
                  if (!rq.offer(url, TIMEOUT, UNIT)) return;
              } catch (IOException e) {
                  // skip over url, couldn’t get its contents
              } catch (InterruptedException e) {
                  return;
              }
        }

        private void add(URL u) {
            if (!seen.contains(u)) {
                seen.add(u);
                pq.offer(u);
            }
        }

        private boolean done() {
            return url == null;
        }

        private URL poll() { return pq.poll(); }

        private final Set<URL> seen = new HashSet<URL>();
        private final Queue<URL> pq =
          new PriorityQueue<URL>(CAPACITY, searchOrder);


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

    private static final int CAPACITY = 4;
    private static final long TIMEOUT = 5;
    private static final TimeUnit UNIT = TimeUnit.SECONDS;
}
