package DfaInference;

/**
 * Utility object to deliver increasing numbers without using a static variable.
 */
public final class Numberer {

    private int counter = 0;

    /**
     * Delivers the next number.
     * @return the next number.
     */
    public int next() {
        return counter++;
    }
    
    /**
     * Resets the counter. The next call to {@link #next()} will deliver
     * 0.
     */
    public void reset() {
        counter = 0;
    }
}
