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

    /**
     * Overrides the <code>fillInStackTrace</code> from <code>Throwable</code>.
     * This version does not actually create a stack trace, as they are useless
     * for this exception, and take up a lot of time.
     * @return this inlet.
     */
    public Throwable fillInStackTrace() {
        return this;
    }

}
