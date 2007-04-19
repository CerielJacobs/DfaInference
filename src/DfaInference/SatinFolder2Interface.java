package DfaInference;

/**
 * This interface specifies the spawnable methods in a Satin application.
 */
interface SatinFolder2Interface extends ibis.satin.Spawnable, java.io.Serializable {
    /**
     * Spawnable method to determine the score of the specified branche.
     * @param r the search branch.
     * @param learningSamples the samples to learn from.
     * @return the resulting search branch.
     */
    public ControlResultPair buildPair(ControlResultPair r,
            Samples learningSamples);
}
