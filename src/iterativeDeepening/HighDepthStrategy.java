package iterativeDeepening;

import DfaInference.Choice;
import DfaInference.DFA;
import DfaInference.PickBlueStrategy;
import DfaInference.State;

/**
 * Implements the "highest depth" strategy for picking the next blue state
 * to be dealt with.
 */
public class HighDepthStrategy implements PickBlueStrategy {

    private static final long serialVersionUID = 1L;

    /**
     * Determines the next blue to be dealt with. The parameter specifies
     * the possibilities, both merges and promotions. From this list, this
     * method chooses the blue state with the highest depth.
     * @param dfa the DFA.
     * @param choices the merge and promotion possibilities.
     * @return the state number of the blue state to be dealt with next.
     */
    public int getBlue(DFA dfa, Choice[] choices) {
        int blue = -1;
        int depth = -1;
        for (int i = 0; i < choices.length; i++) {
            State s = dfa.getState(choices[i].s2);
            if (s.getDepth() > depth) {
                blue = choices[i].s2;
                depth = s.getDepth();
            }
        }
        return blue;
    }
}
