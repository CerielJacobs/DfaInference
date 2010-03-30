package sample;

public interface SampleString {

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

}