package stamina;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.Writer;
import java.util.ArrayList;

import sample.SampleIOInterface;
import sample.SampleString;
import sample.Symbols;

/**
 * Reads strings in Stamina format.
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
public class StaminaIO implements SampleIOInterface {

    /** This StreamTokenizer splits the input into words. */
    private StreamTokenizer d;

    public StaminaIO() {
    }
    
    public SampleString[] readStrings(Reader r) throws IOException {
        
        d = new StreamTokenizer(r);
        d.resetSyntax();
        d.wordChars(0,255);
        d.whitespaceChars(0, ' ');
        d.eolIsSignificant(true);
        
        skipComment();
        ArrayList<SampleString> strings = new ArrayList<SampleString>();

        for (;;) {
            skipComment();
            SampleString s = readString();
            if (s == null) {
                break;
            }
            strings.add(s);
            // System.out.println(s);
        }

        return strings.toArray(new SampleString[strings.size()]);
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

        if (t == StreamTokenizer.TT_WORD && d.sval.equals("+") || d.sval.equals("-") || d.sval.equals("?")) {
        } else {
            throw new IOException("expected - or +, not " + d.toString()); 
        }
        
        SampleString sample = new StaminaString(d.sval.charAt(0));
        
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
    
    public void writeStrings(int nsym, SampleString[] s, Writer w) throws IOException {
        for (int i = 0; i < s.length; i++) {
            w.write(s[i].toString() + "\n");
        }
    }
    

    public SampleString convert2Sample(Symbols symbols, int[] sentence) {
	StaminaString s = new StaminaString(sentence[0] == -1 ? '?' : (sentence[0] == 1 ? '+' : '-'));
	for (int i = 1; i < sentence.length; i++) {
	    s.addToken(symbols.getSymbol(sentence[i]));
	}
	return s;
    }
}
