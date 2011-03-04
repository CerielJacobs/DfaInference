package sample;

public abstract class SampleString {

    /**
     * Adds a token to the string.
     * @param s the token to be added.
     */
    public abstract void addToken(String s);

    /**
     * Returns the string as an array of Java strings.
     * Creates a copy of the array, so as to prevent access to the internal
     * data structures.
     * @return the string.
     */
    public abstract String[] getString();

    /**
     * Determines if this string should be accepted.
     * @return <code>true</code> if it should be accepted, <code>false</code>
     * otherwise (if not or unknown).
     */
    public abstract boolean isAccepted();

    /**
     * Determines if this string should not be accepted.
     * @return <code>true</code> if it should not be accepted,
     * <code>false</code> otherwise (if it should be accepted or unknown).
     */
    public abstract boolean isNotAccepted();

    public abstract String toString();
    
    /**
     * Extracts the tokens from this SampleString,
     * and converts them to token numbers.
     * The first element of the resulting array is 1 for accept, 0 for reject,
     * and -1 for unknown. The other elements represent the symbols in the
     * string.
     * @param syms the Symbol table.
     * @return an array with token numbers.
     */
    public int[] convert2Learn(Symbols syms) {
        String[] str = getString();
        int[] tokens = new int[str.length+1];
        
        tokens[0] = isAccepted() ? 1 : isNotAccepted() ? 0 : -1;
        for (int i = 0; i < str.length; i++) {
            tokens[i+1] = syms.addSymbol(str[i]);
        }

        return tokens;
    }

}