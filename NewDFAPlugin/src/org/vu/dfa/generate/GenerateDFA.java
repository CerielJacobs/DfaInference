package org.vu.dfa.generate;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Random;

import org.apache.log4j.Logger;
import org.vu.dfa.DFA;


/**
 * Utility to generate a DFA. Parameters are the number of symbols and
 * the approximate number of states.
 */
public class GenerateDFA {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(GenerateDFA.class.getName());

    /** Random number generator for generating strings. */
    private static Random randomizer = new Random();


    /**
     * Generate a random DFA with approximately the specified number of states,
     * with the specified number of symbols.
     * Note that the number of states is not exact.
     *
     * @param nthe approximate number of states
     * @param nSyms the number of symbols
     * @return the reduced, minimized DFA.
     */
    private static DFA generate(int n, int nSyms) {

        // Generating an initial random DFA of (5/4)n states is emperically
        // determined to result in a DFA of approximately n states (at least,
        // when the number of symbols is 2).
        int nStates = 5 * n/4;

        boolean[] endStates = new boolean[nStates];
        boolean[] reachable = new boolean[nStates];
        boolean[] productive = new boolean[nStates];
        int[][] states = new int[nStates][];

        for (int i = 0; i < nStates; i++) {
            states[i] = new int[nSyms];
        }

        for (int i = 0; i < nStates; i++) {
            endStates[i] = randomizer.nextInt(2) == 0;
            // All end states are productive.
            productive[i] = endStates[i];
            for (int j = 0; j < nSyms; j++) {
                states[i][j] = randomizer.nextInt(nStates);
            }
        }

        int startState = randomizer.nextInt(nStates);

        boolean changes = true;

        // Determine productivity.
        // If a state is not yet proven to be productive, but it has an
        // edge to a productive state, it is marked productive. Repeat
        // as long as there are changes.
        while (changes) {
            changes = false;
            for (int i = 0; i < nStates; i++) {
                if (! productive[i]) {
                    for (int j = 0; j < nSyms; j++) {
                        if (productive[states[i][j]]) {
                            changes = true;
                            productive[i] = true;
                            break;
                        }
                    }
                }
            }
        }

        // Determine reachability.
        // If a state is marked reachable, all states that are the destination
        // of an edge stemming from this state are marked reachable as well.
        // Repeat as long as there are changes.
        // Initially, only the start state is marked reachable.
        reachable[startState] = true;
        changes = true;
        while (changes) {
            changes = false;
            for (int i = 0; i < nStates; i++) {
                if (reachable[i]) {
                    for (int j = 0; j < nSyms; j++) {
                        if (! reachable[states[i][j]]) {
                            changes = true;
                            reachable[states[i][j]] = true;
                        }
                    }
                }
            }
        }

        // If the startState is not productive, this DFA does not produce
        // anything!
        if (! productive[startState]) {
            return null;
        }

        // Determine depth.
        int[] depths = new int[nStates];
        for (int i = 0; i < nStates; i++) {
            depths[i] = Integer.MAX_VALUE;
        }
        depths[startState] = 0;
        changes = true;
        while (changes) {
            changes = false;
            for (int i = 0; i < nStates; i++) {
                if (reachable[i] && productive[i] && depths[i] != Integer.MAX_VALUE) {
                    int d = depths[i]+1;
                    for (int j = 0; j < nSyms; j++) {
                        int dest = states[i][j];
                        if (productive[dest] && d < depths[dest]) {
                            changes = true;
                            depths[dest] = d;
                        }
                    }
                }
            }
        }
        int maxDepth = -1;
        for (int i = 0; i < nStates; i++) {
            if (reachable[i] && productive[i] && depths[i] > maxDepth) {
                maxDepth = depths[i];
            }
        }

        // Determine 2log, rounded to the next integer.
        int bit = -n & n;
        if (bit != n) {
            bit <<= 1;
        }
        int log = -1;
        while (bit != 0) {
            log++;
            bit >>= 1;
        }
        int requiredDepth = 2 * log - 2;

        if (logger.isInfoEnabled()) {
            logger.info("Generated DFA of depth " + maxDepth + ", required "
                    + requiredDepth);
        }

        // Abbadingo rejects if the depth is not exactly 2 * log(n) - 2.

        if (maxDepth != requiredDepth) {
            return null;
        }

        StringWriter w = new StringWriter();
        w.write("T" + nSyms + "\n");
        w.write("S" + startState + "\n");
        for (int i = 0; i < nStates; i++) {
            if (reachable[i] && productive[i]) {
                w.write("N" + i + "\n");
                for (int j = 0; j < nSyms; j++) {
                    if (productive[states[i][j]]) {
                        w.write("E" + i + ":" + j + ":" + states[i][j] + "\n");
                    }
                }
                if (endStates[i]) {
                    w.write("A" + i + "\n");
                }
            }
        }

        String s = w.toString();

        StringReader rdr = new StringReader(s);
        DFA dfa = new DFA(rdr);
        // dfa.minimize(); Should we? Abbadingo does not mention this.

        return dfa;
    }

    public static void main(String[] args) {
        int nStates = 64;
        int nSyms = 2;
        String filename = "machine";

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-states")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-states option requires number of states");
                    System.exit(1);
                }
                nStates = new Integer(args[i]).intValue();
            } else if (args[i].equals("-syms")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-syms option requires number of symbols");
                    System.exit(1);
                }
                nSyms = new Integer(args[i]).intValue();
            } else if (args[i].equals("-f")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-f option requires filename");
                    System.exit(1);
                }
                filename = args[i];
            }
        }

        DFA dfa = null;
        
        while (dfa == null) {
            dfa = generate(nStates, nSyms);
        }

        dfa.write(filename);
    }
}
