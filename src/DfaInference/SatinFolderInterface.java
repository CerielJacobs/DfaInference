package DfaInference;

/**
 * This interface specifies the spawnable methods in a Satin application.
 */
interface SatinFolderInterface extends ibis.satin.Spawnable, java.io.Serializable {
    /**
     * Spawnable method to determine the score of the specified branche.
     * @param r the search branch.
     * @param learningSamples samples to initialize the dfa with.
     * @param maxSteps fold this number of steps.
     * @return the resulting score.
     */
    public double buildPair(ControlResultPair r, Samples learningSamples);
}
