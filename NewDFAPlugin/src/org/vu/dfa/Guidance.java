package org.vu.dfa;

/**
 * This class implements guidance to a tree search. It can be sub-classed
 * to implement various search strategies.
 */
public class Guidance implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Returns a choice, represented by an integer.
     * @return the choice
     */
    public int getDecision(int nChoices) {
        return 0;
    }

    /**
     * Returns wether this guidance is exhausted.
     * @return <code>true</code> if this guidance is exhausted.
     */
    public boolean exhausted() {
        return true;
    }
}
