package DfaInference;

/**
 * This interface specifies the spawnable methods in a Satin application.
 */
interface BestBlueInterface extends ibis.satin.Spawnable, java.io.Serializable {
    /**
     * Spawnable method to determine the score of the specified branche.
     * @param r the search branch.
     * @param depth the current depth.
     * @param learningSamples the samples to learn from.
     * @return the resulting search branch.
     */
    public ControlResultPair buildPair(ControlResultPair r,
            Samples learningSamples, int depth);
}
