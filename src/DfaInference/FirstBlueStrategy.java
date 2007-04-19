package DfaInference;

/**
 * Implements the strategy for picking the next blue state
 * to be dealt with that the blue is chosen that would be picked first
 * by the default strategy.
 */
public class FirstBlueStrategy implements PickBlueStrategy {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Determines the next blue to be dealt with. The parameter specifies
     * the possibilities, both merges and promotions. From this list, this
     * method picks the first blue.
     * @param dfa the DFA.
     * @param choices the merge and promotion possibilities.
     * @return the state number of the blue state to be dealt with next.
     */
    public int getBlue(DFA dfa, Choice[] choices) {
        return choices[0].s2;
    }
}
