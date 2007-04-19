package abbadingo;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;

/**
 * Reads a file of strings in AbbaDingo format.
 *
 * The first line states the number of sequences in the file, the number of
 * token classes, and optionally the nominal number of states in the target
 * machine.
 * Each subsequent line gives the pattern class followed by the sequence
 * length, followed by the actual sequence.
 * For instance, the input could start as follows:<br>
 * <pre>
 * 5000 2 10
 * 0 9 1 1 0 1 0 0 1 1 0
 * 1 15 1 1 1 1 0 1 0 1 1 1 0 0 0 1 0
 * 0 15 1 1 0 1 1 0 1 0 0 0 1 1 1 0 1
 * 1 15 1 0 0 1 1 1 1 1 1 0 0 0 1 1 0
 * </pre>
 */
public class AbbaDingoReader {

    /** This StreamTokenizer splits the input into words. */
    private StreamTokenizer d;

    /** Number of sentences in the input. */
    private int numSentences;

    /**
     * Constructor with a reader.
     * @param in the reader.
     */
    private AbbaDingoReader(Reader in) {
        d = new StreamTokenizer(in);
        d.resetSyntax();
        d.wordChars(0,255);
        d.whitespaceChars(0, ' ');
        d.eolIsSignificant(true);
    }

    /**
     * Reads an integer from the stream tokenizer.
     * @return the integer read.
     * @exception IOException on IO error.
     */
    private int readInteger() throws IOException {
        int t = d.nextToken();
        if (t != StreamTokenizer.TT_WORD) {
            throw new IOException("integer expected, got " + d.toString());
        }
        try {
            Integer i = new Integer(d.sval);
            return i.intValue();
        } catch (NumberFormatException e) {
            throw new IOException("integer expected, got " + d.toString());
        }
    }

    /**
     * Reads the first line of the AbbaDingo file.
     * @exception IOException on IO error.
     */
    private void readHeader() throws IOException {
        numSentences = readInteger();
        readInteger(); // numtokens, ignored. We'll find out along the way ...

        int t = d.nextToken();

        if (t == StreamTokenizer.TT_WORD) {
            // Ignore optional nominal size of target machine.
            t = d.nextToken();
        }

        if (t != StreamTokenizer.TT_EOL) {
            throw new IOException("new line expected, got " + d.toString());
        }
    }

    /**
     * Reads a sentence from the AbbaDingo file.
     * @return the sentence read.
     * @exception IOException on IO error.
     */
    private AbbaDingoString readString() throws IOException {

        int flag = readInteger();
        int len = readInteger();
        int t;

        AbbaDingoString s = new AbbaDingoString(len, flag);

        for (int i = 0; i < len; i++) {
            t = d.nextToken();
            if (t != StreamTokenizer.TT_WORD) {
                throw new IOException("token expected, got " + d.toString());
            }
            s.addToken(d.sval);
        }

        t = d.nextToken();

        if (t != StreamTokenizer.TT_EOL) {
            throw new IOException("new line expected, got " + d.toString());
        }

        return s;
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
     * Reads the AbbaDingo file.
     * @return the sentences read.
     * @exception IOException on IO error.
     */
    private AbbaDingoString[] readStrings() throws IOException {

        skipComment();
        readHeader();

        AbbaDingoString[] strs = new AbbaDingoString[numSentences];
        for (int i = 0; i < numSentences; i++) {
            skipComment();
            strs[i] = readString();
        }

        skipComment();

        int t = d.nextToken();

        if (t != StreamTokenizer.TT_EOF) {
            throw new IOException("EOF expected, got " + d.toString());
        }

        return strs;
    }

    /**
     * Reads the specified input stream, which should be in AbbaDingo format.
     * @param s the input stream.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public static AbbaDingoString[] getStrings(InputStream s)
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
    public static AbbaDingoString[] getStrings(Reader s)
            throws java.io.IOException {
        AbbaDingoReader r = new AbbaDingoReader(s);
        return r.readStrings();
    }

    /**
     * Reads the specified file, which should be in AbbaDingo format.
     * @param filename the name of the input file.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public static AbbaDingoString[] getStrings(String filename)
            throws java.io.IOException {
        FileInputStream f = new FileInputStream(filename);
        AbbaDingoString[] strs = getStrings(f);
        f.close();
        return strs;
    }
}
