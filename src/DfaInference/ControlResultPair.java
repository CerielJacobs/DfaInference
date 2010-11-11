package DfaInference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

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

    int depth;

    /** Index in the array of choices from which this branch originates. */
    int fromChoiceIndex;
    
    /** To organize ControlResultPairs in a tree (see ControlResultPairTable). */
    ArrayList<ControlResultPair> table = null;

    /**
     * Constructor initializing from the specified values.
     * @param score value for <code>score</code>.
     * @param control value for <code>control</code>.
     * @param c value for <code>fromChoiceIndex</code>.
     */
    public ControlResultPair(double score, int[] control, int d, int c) {
        this.score = score;
        this.control = control;
        this.depth = d;
        this.fromChoiceIndex = c;
    }
    
    public ControlResultPair(ControlResultPair p) {
        this.score = p.score;
        this.control = p.control;
        this.depth = p.depth;
        this.fromChoiceIndex = p.fromChoiceIndex;
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
        return fromChoiceIndex - p.fromChoiceIndex;
    }

    /**
     * Returns a string representation of this object.
     * @return a string representation.
     */
    public String toString() {
        String str = "";
        str += score + "\n";
        str += depth + "\n";
        str += fromChoiceIndex + "\n";
        if (control == null) {
            str += "-1\n";
        }
        else {
            str += control.length + "\n";
            for (int i = 0; i < control.length; i++) {
                str += control[i] + "\n";
            }
        }
        return str;
    }

    /**
     * Writes a string representation of this object to the
     * specified writer.
     * @param w the writer.
     */
    public void write(Writer w) throws IOException {
        w.write(score + "\n");
        w.write(depth + "\n");
        w.write(fromChoiceIndex + "\n");
        if (control == null) {
            w.write("-1\n");
        } else {
            w.write(control.length + "\n");
            for (int i = 0; i < control.length; i++) {
                w.write(control[i] + "\n");
            }
        }
        if (table != null) {
            w.write(table.size() + "\n");
            for (int i = 0; i < table.size(); i++) {
                 table.get(i).write(w);
            }
        } else {
            w.write("-1\n");
        }
    }

    /**
     * Constructor initializing from the specified reader.
     * @param r the reader.
     */
    public ControlResultPair(BufferedReader r) throws IOException {
        String line = r.readLine();
        score = (new Double(line)).doubleValue();
        line = r.readLine();
        depth = (new Integer(line)).intValue();
        line = r.readLine();
        fromChoiceIndex = (new Integer(line)).intValue();
        line = r.readLine();
        int len = (new Integer(line)).intValue();
        if (len >= 0) {
            control = new int[len];
            for (int i = 0; i < len; i++) {
                line = r.readLine();
                control[i] = (new Integer(line)).intValue();
            }
        } else {
            control = null;
        }
        line = r.readLine();
        int tableLength = (new Integer(line)).intValue();
        if (tableLength >= 0) {
            table = new ArrayList<ControlResultPair>();
            for (int i = 0; i < tableLength; i++) {
                table.add(new ControlResultPair(r));
            }
        }
    }
}
