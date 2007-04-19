package DfaInference;

import java.util.Random;

/**
 * This class implements guidance to a tree search by means random choice
 * in the first couple of choice points.
 */
public class RandomGuidance extends Guidance {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /** The depth until which random choices are made. */
    int depth;

    /** Current level. */
    int level;
    
    /** Random generator. */
    private static Random random = new Random(1);

    /**
     * Collected decisions. Should be accessible so that the choices can be
     * retrieved.
     */
    int[] guide;

    /**
     * Constructor.
     * The initial decisions are specified, as is the number of decisions
     * that are to be taken by this guide. The decisions not specified by
     * the initial guide are taken at random.
     * @param guide of already fixed choices.
     * @param depth the total number of decisions to be taken by this guide.
     */
    public RandomGuidance(int[] guide, int depth) {
        this.guide = new int[depth];
        for (int i = 0; i < guide.length; i++) {
            this.guide[i] = guide[i];
        }
        for (int i = guide.length; i < this.guide.length; i++) {
            this.guide[i] = -1;
        }

        level = 0;
    }

    public int getDecision(int nChoices) {
        if (exhausted()) {
            return 0;
        }
        if (guide[level] == -1) {
            guide[level] = random.nextInt(nChoices);
        }
        return guide[level++];
    }

    public boolean exhausted() {
        return level >= depth;
    }
}
