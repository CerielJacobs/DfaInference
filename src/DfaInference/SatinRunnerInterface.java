package DfaInference;

/**
 * This interface specifies the spawnable methods in a Satin application.
 */
interface SatinRunnerInterface extends ibis.satin.Spawnable, java.io.Serializable {
    /**
     * Runs the specified command, and returns the runtime in milliseconds.
     * @param command
     * @param name
     * @return the time.
     */
    public long doRun(String command, String name);
}
