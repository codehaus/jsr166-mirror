package jsr166.webcrawler;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class TestWebCrawler {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: Webcrawler rootUrl limit");
            return;
        }

        TestWebCrawler twc = new TestWebCrawler(
            new URL(args[0]),
            Integer.parseInt(args[1]));

        twc.run();
    }

    public TestWebCrawler(URL startUrl, int limit) {
        this.startUrl = startUrl;
        this.limit = limit;
    }

    private final URL startUrl;
    private final int limit;


    public void run() throws InterruptedException {
        WebCrawler webCrawler = new WebCrawler(URL_ORDER);
        try {
            BlockingQueue<URL> rq = new ArrayBlockingQueue<URL>(CAPACITY); // result queue

            System.out.println("Starting crawl from "+startUrl);

            Future<?> crawl = webCrawler.crawl(startUrl, rq);
            try {
                while (!foundEnough()) {
                    URL found = rq.poll(TIMEOUT, UNIT);
                    if (found == null) break;
                    process(found);
                }
            } finally {
                crawl.cancel(true);
            }
        } finally {
            webCrawler.close();
        }

        System.out.println("Finished crawl with "+urlCount+" URLs found");
    }

    private void process(URL url) {
        ++urlCount;
        System.out.println("processing "+url);
    }

    private boolean foundEnough() {
        return urlCount >= limit;
    }

    private int urlCount = 0;

    private final int CAPACITY = 5;
    private final long TIMEOUT = 6;
    private final TimeUnit UNIT = TimeUnit.SECONDS;

    private final Comparator<URL> URL_ORDER = new Comparator<URL>() {
        public int compare(URL a, URL b) {
            int alen = a.toString().length();
            int blen = a.toString().length();
            if (alen != blen)
                return alen - blen;

            return a.toString().compareTo(b.toString());
        }
    };
}