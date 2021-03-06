package DfaInference;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * A <code>Choice</code> object represents a merge pair and a corresponding
 * DFA score and number of states.
 */
public class Choice implements Comparable<Choice>, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** The red state of the merge pair. */
    public int s1;

    /** The blue state of the merge pair. */
    public int s2;

    /** The number of states resulting after the merge. */
    transient double extra;

    /**
     * The score corresponding to this merge and the heuristic used.
     * Lower is better, as far as sorting is concerned.
     */
    transient double score;

    /** Links entries in freelist together. */
    private transient Choice next;

    /** Freelist. */
    private static Choice freeList;

    /**
     * Constructor.
     * @param s1 initializer for the <code>s1</code> field
     * @param s2 initializer for the <code>s2</code> field
     * @param extra initializer for the <code>extra</code> field
     * @param score initializer for the <code>score</code> field
     */
    private Choice(int s1, int s2, double extra, double score) {
        this.s1 = s1;
        this.s2 = s2;
        this.extra = extra;
        this.score = score;
    }

    /**
     * Obtains a <code>Choice</code> initialized with the specified
     * fields.
     * @param s1 initializer for the <code>s1</code> field
     * @param s2 initializer for the <code>s2</code> field
     * @param extra initializer for the <code>extra</code> field
     * @param score initializer for the <code>score</code> field
     */
    public static Choice getChoice(int s1, int s2, double extra, double score) {
        if (freeList == null) {
            return new Choice(s1, s2, extra, score);
        }
        Choice c = freeList;
        freeList = freeList.next;
        c.s1 = s1;
        c.s2 = s2;
        c.extra = extra;
        c.score = score;
        return c;
    }

    /**
     * Releases the choices in the specified array, up to the specified
     * array index.
     * @param toBeReleased choices to be released.
     * @param num number of choices to be released.
     */
    public static void release(Choice[] toBeReleased, int num) {
        for (int i = 0; i < num; i++) {
            toBeReleased[i].next = freeList;
            freeList = toBeReleased[i];
        }
    }

    public static void release(Choice c) {
        c.next = freeList;
        freeList = c;
    }
    
    public int compareTo(Choice p) {
        if (p.score == score) {
            if (p.extra == extra) {
                if (p.s1 == s1) {
                    return s2 - p.s2;
                }
                return s1 - p.s1;
            }
            return extra > p.extra ? 1 : -1;
        }
        return score > p.score ? 1 : -1;
    }

    public String toString() {
        return "" + s1 + "\n" + s2 + "\n";
    }

    public String readable() {
        if (s1 == -1) {
            return "Promoting blue state " + s2 + " to red";
        }
        return "Merging blue state " + s2 + " into " + s1;
    }

    /**
     * Constructor reading the fields from a reader.
     * @param r the reader.
     */
    public Choice(BufferedReader r) throws IOException {
        String line = r.readLine();
        s1 = (new Integer(line)).intValue();
        line = r.readLine();
        s2 = (new Integer(line)).intValue();
        score = 0;
        extra = 0;
    }
    
    public String verboseString() {
	return "Red " + s1 + ", blue " + s2 + ", score " + score + ", extra " + extra;
    }
}
