package jsr166.util.concurrent;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;


public class TestCompletionFunction {

    public static void main(String[] args) {
        addImage("A", 1, 1, TimeUnit.SECONDS);
        addImage("B", 1, 1, TimeUnit.SECONDS);
        addImage("C", 1, 7, TimeUnit.SECONDS);
        addImage("D", 4, 1, TimeUnit.SECONDS);
        addImage("E", 4, 2, TimeUnit.SECONDS);

        ExecutorService threadPool = Executors.newCachedThreadPool();
        try {
            CompletionFunction<String, Image, IOException> function =
                Functions.newCompletionFunction(
                    threadPool,
                    new Function<String, Image, IOException>() {
                        public Image call(String id) throws IOException {
                            return loadImage(id);
                        }
                    }, IOException.class);

            function.call(
                images.keySet(), 5, TimeUnit.SECONDS,
                new CompletionHandler<Image>() {
                public void handle(Image image) {
                    try { image.render(); } catch (IOException e) {}
                }
            });
        } finally {
            threadPool.shutdown();
        }
    }

    static class Image {
        Image(String id, long timeToProduce, long timeToRender, TimeUnit unit) {
            this.id = id;
            nanosToProduce = unit.toNanos(timeToProduce);
            nanosToRender = unit.toNanos(timeToRender);
        }
        void load() throws IOException {
            //System.out.println("producing "+id);
            block(nanosToProduce);
            System.out.println("produced "+id);
        }
        void render() throws IOException {
            //System.out.println("rendering "+id);
            block(nanosToRender);
            System.out.println("rendered "+id);
        }
        void block(long t) throws IOException {
            try {
                NANOSECONDS.sleep(t);
            } catch (InterruptedException e) {
                new IOException("interrupted");
            }
        }
        private final String id;
        private final long nanosToProduce;
        private final long nanosToRender;
    }

    static Image loadImage(String id) throws IOException {
        Image image = images.get(id);
        image.load();
        return image;
    }

    static void addImage(String id, long p, long r, TimeUnit u) {
        images.put(id, new Image(id, p, r, u));
    }

    static Map<String, Image> images = new HashMap<String, Image>();
}
