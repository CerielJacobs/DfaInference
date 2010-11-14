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
    public double score;

    /** The guidance that leads to this branch. */
    public int[] control;

    private int depth;

    /** Index in the array of choices from which this branch originates. */
    private int fromChoiceIndex;
    
    /** To organize ControlResultPairs in a tree (see ControlResultPairTable). */
    private ArrayList<ControlResultPair> table = null;

    /**
     * Constructor initializing from the specified values.
     * @param score value for <code>score</code>.
     * @param control value for <code>control</code>.
     * @param c value for <code>fromChoiceIndex</code>.
     */
    public ControlResultPair(double score, int[] control, int d, int c) {
        this.score = score;
        this.control = control;
        this.setDepth(d);
        this.setFromChoiceIndex(c);
    }
    
    public ControlResultPair(ControlResultPair p) {
        this.score = p.score;
        this.control = p.control;
        this.setDepth(p.getDepth());
        this.setFromChoiceIndex(p.getFromChoiceIndex());
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
        return getFromChoiceIndex() - p.getFromChoiceIndex();
    }

    /**
     * Returns a string representation of this object.
     * @return a string representation.
     */
    public String toString() {
        String str = "";
        str += score + "\n";
        str += getDepth() + "\n";
        str += getFromChoiceIndex() + "\n";
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
        w.write(getDepth() + "\n");
        w.write(getFromChoiceIndex() + "\n");
        if (control == null) {
            w.write("-1\n");
        } else {
            w.write(control.length + "\n");
            for (int i = 0; i < control.length; i++) {
                w.write(control[i] + "\n");
            }
        }
        if (getTable() != null) {
            w.write(getTable().size() + "\n");
            for (int i = 0; i < getTable().size(); i++) {
                 getTable().get(i).write(w);
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
        setDepth((new Integer(line)).intValue());
        line = r.readLine();
        setFromChoiceIndex((new Integer(line)).intValue());
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
            setTable(new ArrayList<ControlResultPair>());
            for (int i = 0; i < tableLength; i++) {
                getTable().add(new ControlResultPair(r));
            }
        }
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void setFromChoiceIndex(int fromChoiceIndex) {
        this.fromChoiceIndex = fromChoiceIndex;
    }

    public int getFromChoiceIndex() {
        return fromChoiceIndex;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    public void setTable(ArrayList<ControlResultPair> table) {
        this.table = table;
    }

    public ArrayList<ControlResultPair> getTable() {
        return table;
    }
}
