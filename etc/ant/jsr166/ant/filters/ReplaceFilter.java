package jsr166.ant.filters;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Parameter;

/**
 * Performs regex-based replacement on each line matching
 * an optional pattern and not matching another optional pattern.
 * Replacement mode is either to replace only the first
 * occurrence or to replace all occurences.
 */
public final class ReplaceFilter extends LineFilter {
    
    private static final int MODE_FIRST = 0;
    private static final int MODE_ALL   = 1;

    /**
     * Constructor for prototype instances.
     *
     * @see BaseFilterReader#BaseFilterReader()
     */
    public ReplaceFilter () {
        super();
    }

    /**
     * Constructs a new filter from a Reader.
     *
     * @param in A Reader object providing the underlying stream.
     *           Must not be <code>null</code>.
     */
    public ReplaceFilter (Reader in) {
        super(in);
    }

    /**
     * Creates a new ReplaceFilter using the passed in
     * Reader for instantiation.
     *
     * @param rdr A Reader object providing the underlying stream.
     *            Must not be <code>null</code>.
     *
     * @return a new filter based on this prototype, but filtering
     *         the specified reader
     */
    public final Reader chain (final Reader rdr) {
        return new ReplaceFilter(rdr);
    }


    protected CharSequence filterLine (String line) {
        if (!getInitialized()) {
            init();
            setInitialized(true);
        }
        
        if (matching != null && !matching.matcher(line).matches()) {
            return line;
        }
        
        if (notmatching != null && notmatching.matcher(line).matches()) {
            return line;
        }
        
        switch (mode) {
        case MODE_FIRST:
            return pattern.matcher(line).replaceFirst(replacement);
        case MODE_ALL:
            return pattern.matcher(line).replaceAll(replacement);
        }
        
        throw new BuildException("invalid mode: " + mode);
    }

    private void init () {
        Parameter[] params = getParameters();
        for (int i = 0; i < params.length; ++i) {
            String pn = params[i].getName();
            String pv = params[i].getValue();
            if (pn.equals("matching")) {
                matching = Pattern.compile(pv);
            }
            else if (pn.equals("notmatching")) {
                notmatching = Pattern.compile(pv);
            }
            else if (pn.equals("pattern")) {
                pattern = Pattern.compile(pv);
            }
            else if (pn.equals("replacement")) {
                replacement = pv;
            }
            else if (pn.equals("mode")) {
                if (!validModes.containsKey(pv))
                    throw new BuildException("invalid mode: " + pv);
                mode = ((Integer) validModes.get(pv)).intValue();
            }
            else {
                throw new BuildException("unsupported parameter: " + pn);
            }
        }
        if (pattern == null || replacement == null) {
            throw new BuildException("must specify pattern and replacement");
        }
    }

    private static final Map validModes = new HashMap();
    static {
        validModes.put("first", new Integer(MODE_FIRST));
        validModes.put("all", new Integer(MODE_ALL));
    }
    
    private Pattern matching;
    private Pattern notmatching;
    private Pattern pattern;
    private String replacement;
    private int mode = MODE_ALL;
}
