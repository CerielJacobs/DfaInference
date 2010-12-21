package iterativeDeepening;

import DfaInference.Choice;
import DfaInference.DFA;
import DfaInference.PickBlueStrategy;
import DfaInference.State;

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
        State best = dfa.getState(blue);        // return dfa.getMDLComplexity();
        // This needs modification in DFA.java, because the weight of the endstates is wrong for MDL,
        // since a sentence in the sample may occur more than once in Stamina.
        int bestTraffic = best.getTraffic() + best.getxTraffic();
        for (int i = 1; i < choices.length; i++) {
            int s = choices[i].s2;
            State state = dfa.getState(s);
            int traffic = state.getTraffic() + state.getxTraffic();
            if (traffic > bestTraffic) {
                best = state;
                blue = s;
                bestTraffic = traffic;
            } else if (bestTraffic == traffic && state.getDepth() < best.getDepth()) {
                best = state;
                blue = s;
            }
        }
        return blue;
    }
}
