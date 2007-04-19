package DfaInference;

/**
 * Implements the "lowest depth" strategy for picking the next blue state
 * to be dealt with.
 */
public class LowDepthStrategy implements PickBlueStrategy {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Determines the next blue to be dealt with. The parameter specifies
     * the possibilities, both merges and promotions. From this list, this
     * method chooses the blue state with the lowest depth.
     * @param dfa the DFA.
     * @param choices the merge and promotion possibilities.
     * @return the state number of the blue state to be dealt with next.
     */
    public int getBlue(DFA dfa, Choice[] choices) {
        int blue = -1;
        int depth = Integer.MAX_VALUE;
        for (int i = 0; i < choices.length; i++) {
            State s = dfa.getState(choices[i].s2);
            if (s.depth < depth) {
                blue = choices[i].s2;
                depth = s.depth;
            }
        }
        return blue;
    }
}
