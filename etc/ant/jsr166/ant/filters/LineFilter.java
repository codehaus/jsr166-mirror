package jsr166.ant.filters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.tools.ant.filters.BaseParamFilterReader;
import org.apache.tools.ant.filters.ChainableReader;

/**
 * Abstract base to make line based filtering easy.
 */
public abstract class LineFilter extends BaseParamFilterReader
                                 implements ChainableReader
{
    /**
     * Constructor for "dummy" instances.
     *
     * @see BaseFilterReader#BaseFilterReader()
     */
    public LineFilter () {
        super();
    }

    /**
     * Creates a new filtered reader.
     *
     * @param in A Reader object providing the underlying stream.
     *           Must not be <code>null</code>.
     */
    public LineFilter (Reader in) {
        super(in instanceof BufferedReader ? in : new BufferedReader(in));
        assert this.in == in;
    }

    /**
     * Returns the next character in the filtered stream, not including
     * Java comments, by reading a line at a time, filtering it, and then
     * doling out characters one at a time from the filtered line.
     *
     * @return the next character in the resulting stream, or -1
     *         if the end of the resulting stream has been reached
     *
     * @exception IOException if the underlying stream throws an IOException
     *            during reading
     */
    public final int read () throws IOException {
        if (index == -1) {
            String nextLine = ((BufferedReader) in).readLine();
            if (nextLine == null) return -1;
            line = filterLine(nextLine);
            index = 0;
        }
        if (index == line.length()) {
            index = -1;
            return (int) '\n';
        }
        return line.charAt(index++);
    }

    /**
     * Define this in subclasses to transform the given line.
     */
    protected abstract CharSequence filterLine (String line);


    private CharSequence line = null;
    private int index = -1;
}
