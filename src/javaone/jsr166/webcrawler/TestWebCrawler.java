package jsr166.webcrawler;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public final class TestWebCrawler {

    private static Logger log =
        Logger.getLogger(TestWebCrawler.class.getPackage().getName());


    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: Webcrawler className rootUrl limit");
            return;
        }

        TestWebCrawler twc = new TestWebCrawler(
            ((Class<WebCrawler>) Class.forName(args[0])),
            new URL(args[1]),
            Integer.parseInt(args[2]));

        try {
            twc.run();
        }
        finally {
            twc.finish();
        }

        log.info("exiting main");
    }


    public TestWebCrawler(Class<WebCrawler> wcClass, URL startUrl, int limit) throws Exception {
        this.webCrawler = wcClass.getConstructor(Comparator.class, long.class, TimeUnit.class)
                                 .newInstance(URL_ORDER, TIMEOUT, UNIT);
        this.startUrl = startUrl;
        this.limit = limit;
    }

    private static final Comparator<URL> URL_ORDER = new Comparator<URL>() {
        public int compare(URL a, URL b) {
            int alen = a.toString().length();
            int blen = a.toString().length();
            if (alen != blen)
                return alen - blen;

            return a.toString().compareTo(b.toString());
        }
    };

    private static final long TIMEOUT = 8;
    private static final TimeUnit UNIT = TimeUnit.SECONDS;

    private final WebCrawler webCrawler;
    private final URL startUrl;
    private final int limit;


    public void run() throws InterruptedException {
        BlockingQueue<URL> rq = new ArrayBlockingQueue<URL>(CAPACITY); // result queue

        log.info("starting crawl using "+webCrawler.getClass());

        Future<?> crawl = webCrawler.crawl(startUrl, rq);
        try {
            while (!foundEnough()) {
                URL found = rq.poll(TIMEOUT, UNIT);
                if (found == null) break;
                process(found);
            }
        } finally {
            log.info("canceling crawl");
            crawl.cancel(true);
            log.info("canceled crawl");
        }
    }

    public void finish() {
        log.info("about to shutdown");
        webCrawler.shutdown();
        log.info("webCrawler is shutdown");
    }

    private void process(URL url) {
        ++urlCount;
        log.info("#"+urlCount+": "+url);
    }

    private boolean foundEnough() {
        return urlCount >= limit;
    }

    private int urlCount = 0;

    private final int CAPACITY = 30;
}