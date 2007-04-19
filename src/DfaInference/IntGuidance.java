package DfaInference;

/**
 * This class implements guidance to a tree search by means of an array
 * of integers.
 */
public class IntGuidance extends Guidance {

    /** The guide. */
    int[] guide;

    /** Current index in the guide. */
    int guideIndex;

    /**
     * Constructor.
     * @param guide the guide.
     */
    public IntGuidance(int[] guide) {
        this.guide = guide;
        guideIndex = 0;
    }

    public int getDecision(int nChoices) {
        if (guideIndex >= guide.length) {
            return 0;
        }
        return guide[guideIndex++];
    }

    public boolean exhausted() {
        return guideIndex >= guide.length;
    }

    public String toString() {
        String result = "IntGuidance:";
        for (int i = 0; i < guide.length; i++) {
            result += " " + guide[i];
        }
        return result;
    }
}
