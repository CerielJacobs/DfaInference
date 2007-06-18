package org.vu.dfa;

/**
 * Implements the "low choice count" strategy for picking the next blue state
 * to be dealt with.
 */
public class ChoiceCountStrategy implements PickBlueStrategy {

    private static final long serialVersionUID = 1L;

    /**
     * Determines the next blue to be dealt with. The parameter specifies
     * the possibilities, both merges and promotions. From this list, this
     * method chooses a blue state, by determining the blue state which has
     * the lowest number of possibilities.
     * @param dfa the DFA.
     * @param choices the merge and promotion possibilities.
     * @return the state number of the blue state to be dealt with next.
     */
    public int getBlue(DFA dfa, Choice[] choices) {
        int[] counts = new int[dfa.idMap.length];
        for (int i = 0; i < choices.length; i++) {
            counts[choices[i].s2]++;
        }
        State best = dfa.getState(choices[0].s2);
        int blue = choices[0].s2;
        int score = counts[blue];
        for (int i = 1; i < choices.length; i++) {
            int s = choices[i].s2;
            if (counts[s] <= score) {
                State b = dfa.getState(s);
                if (counts[s] < score
                        || (counts[s] == score && b.depth < best.depth)) {
                    blue = s;
                    score = counts[s];
                    best = b;
                }
            }
        }
        return blue;
    }
}
