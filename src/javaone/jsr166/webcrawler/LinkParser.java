package jsr166.webcrawler;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

public final class LinkParser {

    public LinkParser () {}

    public void parse(URL url, Queue<URL> result) throws IOException {
        URLConnection conn = url.openConnection();
        String type = conn.getContentType(); // e.g., text/html; charset=ISO-8859-1
        Matcher typeMatcher = typePat.matcher(type);
        if (!typeMatcher.matches()) {
            //System.out.println("rejecting "+url+" with type="+type);
            return;
        }

        String charset = typeMatcher.group(1);
        if (charset == null) charset = "ISO-8859-1";
        //System.out.println("charset="+charset);

        CharsetDecoder decoder = Charset.forName(charset).newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        ReadableByteChannel source = Channels.newChannel(conn.getInputStream());

        CoderResult coderResult = CoderResult.UNDERFLOW;
        boolean eof = false;

        while (!eof) {
            if (coderResult == CoderResult.UNDERFLOW) {
                buf.clear();
                eof = (source.read(buf) == -1);
                buf.flip();
            }

            coderResult = decoder.decode(buf, cbuf, eof);

            if (coderResult == CoderResult.OVERFLOW) {
                drainTo(result);
            }
        }

        while (decoder.flush(cbuf) == CoderResult.OVERFLOW) {
            drainTo(result);
        }

        drainTo(result);

        source.close();
    }

    private void drainTo(Queue<URL> result) {
        cbuf.flip();

        Matcher matcher = pat.matcher(cbuf);
        while (matcher.find()) {
            String match = matcher.group();
            if (!match.endsWith(SUFFIX)) {
                int start = matcher.start();
                if (start > 0) {
                    cbuf.position(start);
                    cbuf.compact();
                }
                return;
            }

            String found = match.substring(PREFIX.length(), match.length()-SUFFIX.length());
            try {
                result.add(new URL(found));
            }
            catch (MalformedURLException e) {
                // ignore
            }
        }

        cbuf.clear();
    }

    private static final int MAX_URL_LENGTH = 50;

    private static final String PREFIX = "<a href=\"";
    private static final String SUFFIX = "\"";
    private final Pattern pat =
        Pattern.compile(PREFIX+"http://[^"+SUFFIX+"]+(?:"+SUFFIX+")?",
                        Pattern.CASE_INSENSITIVE);
    private final Pattern typePat = Pattern.compile("text/html(?:; charset=([^;]+))?");

    private final ByteBuffer buf = ByteBuffer.allocateDirect(1024);
    private final CharBuffer cbuf =
        CharBuffer.allocate(PREFIX.length()+MAX_URL_LENGTH+SUFFIX.length());
}