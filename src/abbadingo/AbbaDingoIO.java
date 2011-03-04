package abbadingo;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.Writer;

import sample.SampleIOInterface;
import sample.SampleString;
import sample.Symbols;

/**
 * Reads or writes a file of strings in AbbaDingo format.
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
public class AbbaDingoIO implements SampleIOInterface {

    /** This StreamTokenizer splits the input into words. */
    private StreamTokenizer d;

    /** Number of sentences in the input. */
    private int numSentences;

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
    private SampleString readString() throws IOException {

        int flag = readInteger();
        int len = readInteger();
        int t;

        SampleString s = new AbbaDingoString(len, flag);

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

    public SampleString[] readStrings(Reader in)
            throws java.io.IOException {
        
        d = new StreamTokenizer(in);
        d.resetSyntax();
        d.wordChars(0,255);
        d.whitespaceChars(0, ' ');
        d.eolIsSignificant(true);
        
        skipComment();
        readHeader();

        SampleString[] strs = new SampleString[numSentences];
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

    public void writeStrings(int nsym, SampleString[] s, Writer w) throws IOException {
        // Print number of strings and number of symbols.
        w.write("" + s.length + " " + nsym + "\n");
        for (int i = 0; i < s.length; i++) {
            w.write(s[i].toString() + "\n");
        }
    }

    public SampleString convert2Sample(Symbols symbols, int[] sentence) {
	AbbaDingoString s = new AbbaDingoString(sentence.length - 1, sentence[0]);
	for (int i = 1; i < sentence.length; i++) {
	    s.addToken(symbols.getSymbol(sentence[i]));
	}
	return s;
    }
    
    
}
