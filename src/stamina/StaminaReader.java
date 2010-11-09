package stamina;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.ArrayList;

import sample.SampleString;

/**
 * Reads a file of strings in Stamina format.
 *
 * Each line gives the pattern class followed by the actual sequence.
 * For instance, the input could start as follows:<br>
 * <pre>
 * + 1 0 0 0 0 0 0 0 0
 * + 0 1 1 0 0 0 1 0 1 0 1 0 1 0 0
 * - 0 1 0 0 0
 * + 0 0
 * </pre>
 */
public class StaminaReader {

    /** This StreamTokenizer splits the input into words. */
    private StreamTokenizer d;

    /** Number of sentences in the input. */
    private int numSentences;

    /**
     * Constructor with a reader.
     * @param in the reader.
     */
    private StaminaReader(Reader in) {
        d = new StreamTokenizer(in);
        d.resetSyntax();
        d.wordChars(0,255);
        d.whitespaceChars(0, ' ');
        d.eolIsSignificant(true);
    }

    /**
     * Reads a sentence from the Stamina file.
     * @return the sentence read.
     * @exception IOException on IO error.
     */
    private SampleString readString() throws IOException {
        int t = d.nextToken();
        if (t == StreamTokenizer.TT_EOF) {
            return null;
        }

        if (t == StreamTokenizer.TT_WORD && d.sval.equals("+") || d.sval.equals("-")) {
        } else {
            throw new IOException("expected - or +, not " + d.toString()); 
        }

        boolean accept = d.sval.equals("+");
        
        SampleString sample = new StaminaString(accept);
        
        t = d.nextToken();
        while (t != StreamTokenizer.TT_EOL) {
            sample.addToken(d.sval);
            t = d.nextToken();
        }   
        return sample;
    }

    /**
     * Checks for possible comment lines (which begin with a # (sharp)),
     * and skips them.
     * @exception IOException on IO error.
     */
    private void skipComment() throws IOException {
        int t = d.nextToken();
        if (t == StreamTokenizer.TT_WORD && ! d.sval.equals("#")) {
            d.pushBack();
            return;
        }
        while (t != StreamTokenizer.TT_EOL) {
            if (t == StreamTokenizer.TT_EOF) {
                d.pushBack();
                return;
            }
            t = d.nextToken();
        }
    }

    /**
     * Reads the Stamina file.
     * @return the sentences read.
     * @exception IOException on IO error.
     */
    private SampleString[] readStrings() throws IOException {

        skipComment();
        ArrayList<SampleString> strings = new ArrayList<SampleString>();

        for (;;) {
            skipComment();
            SampleString s = readString();
            if (s == null) {
                break;
            }
            strings.add(s);
        }

        return strings.toArray(new SampleString[strings.size()]);
    }

    /**
     * Reads the specified input stream, which should be in AbbaDingo format.
     * @param s the input stream.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public static SampleString[] getStrings(InputStream s)
            throws java.io.IOException {
        s = new BufferedInputStream(s);
        return getStrings(new InputStreamReader(s));
    }

    /**
     * Reads the specified reader, which should be in AbbaDingo format.
     * @param s the reader.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public static SampleString[] getStrings(Reader s)
            throws java.io.IOException {
        StaminaReader r = new StaminaReader(s);
        return r.readStrings();
    }

    /**
     * Reads the specified file, which should be in AbbaDingo format.
     * @param filename the name of the input file.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public static SampleString[] getStrings(String filename)
            throws java.io.IOException {
        FileInputStream f = new FileInputStream(filename);
        SampleString[] strs = getStrings(f);
        f.close();
        return strs;
    }
}
