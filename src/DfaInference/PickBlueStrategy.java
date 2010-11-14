package DfaInference;


/**
 * Determines the strategy for choosing a blue state that must be dealt with
 * next.
 */
public interface PickBlueStrategy extends java.io.Serializable {
    /**
     * Determines the next blue to be dealt with. The parameter specifies
     * the possibilities, both merges and promotions. From this list, this
     * method chooses a blue state.
     * @param dfa the DFA.
     * @param choices the merge and promotion possibilities.
     * @return the state number of the blue state to be dealt with next.
     */
    public int getBlue(DFA dfa, Choice[] choices);
}
