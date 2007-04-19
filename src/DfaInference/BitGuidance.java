package DfaInference;

/**
 * This class implements guidance to a tree search by means of an array
 * of booleans.
 */
public class BitGuidance extends Guidance {

    /** The guide. */
    boolean[] guide;

    /** Current index in the guide. */
    int guideIndex;

    /**
     * Constructor.
     * @param guide the guide.
     */
    public BitGuidance(boolean[] guide) {
        this.guide = new boolean[guide.length];
        for (int i = 0; i < guide.length; i++) {
            this.guide[i] = guide[i];
        }
        guideIndex = 0;
    }

    /**
     * Selects one from a number of choices. If none of these choices is to
     * be taken, <code>nChoices</code> is returned.
     * Otherwise, the choice number (ranging from
     * <code>0</code> to <code>nChoices-1</code> is returned.
     * It is assumed that the choices are sorted according to some heuristic.
     * @param nChoices the number of possibilities.
     * @return the choice
     */
    public int getDecision(int nChoices) {
        for (int i = 0; i < nChoices; i++) {
            if (guideIndex >= guide.length) {
                return i;
            }
            if (guide[guideIndex++]) {
                return i;
            }
        }
        return nChoices;
    }

    public boolean exhausted() {
        return guideIndex >= guide.length;
    }
}
