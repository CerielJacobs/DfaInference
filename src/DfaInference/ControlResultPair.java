package DfaInference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;

/**
 * This class represents a branch of the search by means of its guidance,
 * which, in turn, is represented by an array of integers, each representing
 * the choice to be taken at the depth represented by the array index.
 * The choices themselves are implicitly determined by the search heuristic
 * used.
 */
public class ControlResultPair implements Comparable<ControlResultPair>, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** The score resulting from this branch. */
    double score;

    /** The guidance that leads to this branch. */
    int[] control;

    /** Index in the window from which this branch originates. */
    int fromWindowIndex;

    /** Index in the array of choices from which this branch originates. */
    int fromChoiceIndex;

    /**
     * Constructor initializing from the specified values.
     * @param score value for <code>score</code>.
     * @param control value for <code>control</code>.
     * @param w value for <code>fromWindowIndex</code>.
     * @param c value for <code>fromChoiceIndex</code>.
     */
    public ControlResultPair(double score, int[] control, int w, int c) {
        this.score = score;
        this.control = control;
        this.fromWindowIndex = w;
        this.fromChoiceIndex = c;
    }

    /**
     * Comparison for sorting purposes.
     * @param p the object to compare to.
     * @return comparison value. Lower means that the current object
     * is better.
     */
    public int compareTo(ControlResultPair p) {
        if (score != p.score) {
            return (score - p.score > 0) ? 1 : -1;
        }
        if (fromWindowIndex != p.fromWindowIndex) {
            return fromWindowIndex - p.fromWindowIndex;
        }
        return fromChoiceIndex - p.fromChoiceIndex;
    }

    /**
     * Returns a string representation of this object.
     * @return a string representation.
     */
    public String toString() {
        String str = "";
        str += score + "\n";
        str += fromWindowIndex + "\n";
        str += fromChoiceIndex + "\n";
        str += control.length + "\n";
        for (int i = 0; i < control.length; i++) {
            str += control[i] + "\n";
        }
        return str;
    }

    /**
     * Writes a string representation of this object to the
     * specified writer.
     * @param w the writer.
     */
    public void write(Writer w) throws IOException {
        w.write(toString());
    }

    /**
     * Constructor initializing from the specified reader.
     * @param r the reader.
     */
    public ControlResultPair(BufferedReader r) throws IOException {
        String line = r.readLine();
        score = (new Double(line)).doubleValue();
        line = r.readLine();
        fromWindowIndex = (new Integer(line)).intValue();
        line = r.readLine();
        fromChoiceIndex = (new Integer(line)).intValue();
        line = r.readLine();
        int len = (new Integer(line)).intValue();
        control = new int[len];
        for (int i = 0; i < len; i++) {
            line = r.readLine();
            control[i] = (new Integer(line)).intValue();
        }
    }
}
