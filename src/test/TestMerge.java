package test;

import java.io.FileReader;

import DfaInference.DFA;

/**
 * Reads two DFAs, merges them and prints the result.
 */
public class TestMerge {

    public static void main(String[] args) {
        String arg1 = args[0];
        String arg2 = args[1];

        FileReader fr = null;
        try {
            fr = new FileReader(arg1);
        } catch(Exception e) {
            System.err.println("Could not open input file " + arg1);
            System.exit(1);
        }
        DFA dfa1 = new DFA(fr);

        try {
            fr = new FileReader(arg2);
        } catch(Exception e) {
            System.err.println("Could not open input file " + arg2);
            System.exit(1);
        }
        DFA dfa2 = new DFA(fr);

        DFA result = new DFA(dfa1, dfa2);
        System.out.println(result.dumpDFA());
    }
}
