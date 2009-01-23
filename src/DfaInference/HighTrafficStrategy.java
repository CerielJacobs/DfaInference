package DfaInference;

/**
 * Implements the "high-traffic" strategy for picking the next blue state
 * to be dealt with. This means that the blue state that has the highest
 * traffic is picked.
 */
public class HighTrafficStrategy implements PickBlueStrategy {

    private static final long serialVersionUID = 1L;

    /**
     * Determines the next blue to be dealt with. The parameter specifies
     * the possibilities, both merges and promotions. From this list, this
     * method chooses a blue state, by determining the blue state which has
     * the highest traffic.
     * @param dfa the DFA.
     * @param choices the merge and promotion possibilities.
     * @return the state number of the blue state to be dealt with next.
     */
    public int getBlue(DFA dfa, Choice[] choices) {
        int blue = choices[0].s2;
        State best = dfa.getState(blue);
        for (int i = 1; i < choices.length; i++) {
            int s = choices[i].s2;
            State state = dfa.getState(s);
            if (state.traffic > best.traffic) {
                best = state;
                blue = s;
            } else if (state.traffic == best.traffic && state.depth < best.depth) {
                best = state;
                blue = s;
            }
        }
        return blue;
    }
}
