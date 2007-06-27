package DfaInference;

/**
 * This interface specifies the spawnable methods in a Satin application.
 */
interface SatinRunnerInterface extends ibis.satin.Spawnable, java.io.Serializable {
    public void doRun(String command, String name);
}
