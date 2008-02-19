package DfaInference;

/**
 * This exception gets thrown on a merge attempt that resulted in a conflict, which
 * is a merge attempt between an accepting state and a rejecting state.
 */
public class ConflictingMerge extends Exception {

    private static final long serialVersionUID = 1L;

    public ConflictingMerge() {
    }

    public ConflictingMerge(String message) {
        super(message);
    }

    public ConflictingMerge(Throwable cause) {
        super(cause);
    }

    public ConflictingMerge(String message, Throwable cause) {
        super(message, cause);
    }
}
