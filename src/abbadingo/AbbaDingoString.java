package abbadingo;

/**
 * An <code>AbbaDingoString</code> represents an input string. It consists of
 * an array of tokens (each represented by a Java string), and a flag
 * indicating one of three things:<ul>
 * <li>learning string, to be recognized by the DFA.
 * <li>learning string, NOT to be recognized by the DFA.
 * <li>test string.
 * </ul>
 */
public class AbbaDingoString {

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

    /**
     * Adds a token to the string.
     * @param s the token to be added.
     */
    public void addToken(String s) {
        words[ix++] = s;
    }

    /**
     * Returns the string as an array of Java strings.
     * Creates a copy of the array, so as to prevent access to the internal
     * data structures.
     * @return the string.
     */
    public String[] getString() {
        return (String[]) words.clone();
    }

    /**
     * Determines if this string should be accepted.
     * @return <code>true</code> if it should be accepted, <code>false</code>
     * otherwise (if not or unknown).
     */
    public boolean isAccepted() {
        return flag > 0;
    }

    /**
     * Determines if this string should not be accepted.
     * @return <code>true</code> if it should not be accepted,
     * <code>false</code> otherwise (if it should be accepted or unknown).
     */
    public boolean isNotAccepted() {
        return flag == 0;
    }

    public String toString() {
        String str = "";
        str += flag + " " + words.length;
        for (int i = 0; i < words.length; i++) {
            str += " " + words[i];
        }

        return str;
    }
}
