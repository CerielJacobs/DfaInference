package iterativeDeepening;

import DfaInference.ControlResultPair;
import DfaInference.Samples;

/**
 * This interface specifies the spawnable methods in a Satin application.
 */
interface BestBlueInterface extends ibis.satin.Spawnable, java.io.Serializable {
    /**
     * Spawnable method to determine the score of the specified branche.
     * @param r the search branch.
     * @param depth the current depth.
     * @param learningSamples the samples to learn from.
     * @param table the table of already known results from an earlier run.
     * @return the resulting search branch.
     */
    public ControlResultPair buildPair(int fixDepth, ControlResultPair r,
            Samples learningSamples, ControlResultPairTable table, int depth);
    
}
