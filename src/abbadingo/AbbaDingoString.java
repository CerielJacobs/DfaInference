package abbadingo;

import sample.SampleString;

/**
 * An <code>AbbaDingoString</code> represents an input string. It consists of
 * an array of tokens (each represented by a Java string), and a flag
 * indicating one of three things:<ul>
 * <li>learning string, to be recognized by the DFA.
 * <li>learning string, NOT to be recognized by the DFA.
 * <li>test string.
 * </ul>
 */
public class AbbaDingoString extends SampleString {

    /** The tokens in the string. */
    private String[] words;

    /**
     * Positive for positive string, 0 for negative string,
     * negative for unknown.
     */
    private int flag;

    /** Current index when adding tokens. */
    private int ix;

    /**
     * Constructor.
     * @param len the number of tokens in the string
     * @param flag the flag given in the input.
     */
    public AbbaDingoString(int len, int flag) {
        this.flag = flag;
        ix = 0;
        words = new String[len];
    }

    /* (non-Javadoc)
     * @see abbadingo.SampleString#addToken(java.lang.String)
     */
    public void addToken(String s) {
        words[ix++] = s;
    }

    /* (non-Javadoc)
     * @see abbadingo.SampleString#getString()
     */
    public String[] getString() {
        return words.clone();
    }

    /* (non-Javadoc)
     * @see abbadingo.SampleString#isAccepted()
     */
    public boolean isAccepted() {
        return flag > 0;
    }

    /* (non-Javadoc)
     * @see abbadingo.SampleString#isNotAccepted()
     */
    public boolean isNotAccepted() {
        return flag == 0;
    }

    /* (non-Javadoc)
     * @see abbadingo.SampleString#toString()
     */
    public String toString() {
        String str = "";
        str += flag + " " + words.length;
        for (int i = 0; i < words.length; i++) {
            str += " " + words[i];
        }

        return str;
    }
}
