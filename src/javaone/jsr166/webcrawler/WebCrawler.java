package jsr166.webcrawler;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;


public interface WebCrawler {

    /**
     * Search starting from <tt>startUrl</tt> putting results
     * in <tt>rq</tt>.
     */
    Future<?> crawl(URL startUrl, BlockingQueue<URL> rq);

    /**
     * Cancel all running crawls. No other crawls may be
     * started after this method is called.
     */
    void shutdown();
}
