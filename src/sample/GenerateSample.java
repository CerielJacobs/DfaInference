package sample;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import DfaInference.DFA;

/**
 * Utility to generate learning set and test for a specified DFA.
 */
public class GenerateSample {

    /** Log4j logger. */
    private static Logger logger
            = Logger.getLogger(GenerateSample.class.getName());

    private static BufferedWriter learn;
    private static BufferedWriter test;

    /** Random number generator for generating strings. */
    private static Random randomizer = new Random();
    
    private static class SampleComparator implements Comparator<int[]> {

	public int compare(int[] arg0, int[] arg1) {
	    if (arg0.length != arg1.length) {
		return arg1.length - arg0.length;
	    }
	    for (int i = 1; i < arg0.length; i++) {
		if (arg0[i] != arg1[i]) {
		    return arg1[i] - arg0[i];
		}
	    }
	    return 0;
	}
    }

    private static TreeSet<int[]> toAvoid = new TreeSet<int[]>(new SampleComparator());
    
    /**
     * Generates the specified number of random strings with the specified
     * maximum length and the specified number of symbols.
     * The strings are selected according to uniform distribution over the
     * total string space. The strings are printed on standard output
     * @param count the number of strings to generate
     * @param maxl the maximum length
     * @param nsym the number of symbols
     * @param samplesToAvoid samples to avoid, because they are for instance in the test set
     * @return the generated strings.
     */
    private static int[][] generateStrings(int count, int maxl, int nsym, int[][] samplesToAvoid, DFA dfa, boolean negatives) {
	int[][] samples = new int[count][];

	if (samplesToAvoid != null) {
	    for (int[] s : samplesToAvoid) {
		toAvoid.add(s);
	    }
	}

	// Uniform distribution over the total string space.
	// There are nsym^maxl + nsym^(maxl-1) + .... + nsym + 1 such strings.
	double[] nStrings = new double[maxl+1];
	double[] sums = new double[maxl+1];
	nStrings[0] = 1.0;
	sums[0] = 1.0;
	for (int i = 1; i <= maxl; i++) {
	    nStrings[i] = nsym * nStrings[i-1];
	    sums[i]= sums[i-1] + nStrings[i];
	}

	// Generate count strings
	for (int i = 0; i < count; i++) {
	    int[] attempt;
	    do {
		int len = maxl;
		double rand = sums[maxl] * randomizer.nextDouble();
		for (int j = 0; j <= maxl; j++) {
		    if (sums[j] > rand) {
			len = j;
			break;
		    }
		}

		attempt = new int[len+1];
		for (int j = 1; j < attempt.length; j++) {
		    attempt[j] = randomizer.nextInt(nsym);
		}
		if (! toAvoid.contains(attempt)) {
		    toAvoid.add(attempt);
		    if (dfa != null) {
			attempt[0] = -1;

			boolean recognize = dfa.recognize(attempt);
			if (! negatives && ! recognize) {
			    continue;
			}
			attempt[0] = recognize ? 1 : 0;
		    }
		    break;
		}
	    } while (true);
	    samples[i] = attempt;
	}
	return samples;
    }

    public static void main(String[] args) {
        String  machinefile = null;
        int maxl = 15;
        int count = 2000;
        String testfile = null;
        boolean negativeSamples = true;
        int nsym = 2;
        String output = "data";
        String sampleIOClassname = "abbadingo.AbbaDingoIO";

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-m")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-m option requires filename");
                    System.exit(1);
                }
                machinefile = args[i];
            } else if (args[i].equals("-maxlength")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-maxl option requires length");
                    System.exit(1);
                }
                maxl = new Integer(args[i]).intValue();
            } else if (args[i].equals("-nsym")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-nsym option requires length");
                    System.exit(1);
                }
                nsym = new Integer(args[i]).intValue();
            } else if (args[i].equals("-testfile")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-testfile option requires testfile");
                    System.exit(1);
                }
                testfile = args[i];
            } else if (args[i].equals("-sampleio")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-sampleio option requires classname");
                    System.exit(1);
                }
                sampleIOClassname = args[i];
            } else if (args[i].equals("-count")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-count option requires count");
                    System.exit(1);
                }
                count = new Integer(args[i]).intValue();
            } else if (args[i].equals("-no-negatives")) {
                negativeSamples = false;
            } else if (args[i].equals("-negatives")) {
                negativeSamples = true;
            } else if (args[i].equals("-output")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-output option requires filename");
                    System.exit(1);
                }
                output = args[i];
            } else {
                logger.fatal("Usage: java sample.GenerateSample [ -m <machine> ] [ -nsym <nsym> ] [-maxlength <maxlength> ][ -sampleio <implementation> ][ -count <count> ] [ -testfile <testfile> ] [-output <output> ] [-negatives|-no-negatives]");
                System.exit(1);
            }
        }

        DFA dfa = null;
        
        if (machinefile != null) {
            FileReader fr = null;

            // Read the DFA.
            try {
        	fr = new FileReader(machinefile);
            } catch(Exception e) {
        	logger.fatal("Could not open input file");
        	System.exit(1);
            }

            dfa = new DFA(fr);
            Symbols symbols = dfa.symbols;
            nsym = symbols.nSymbols();
        }
        
        SampleIO sampleIO = new SampleIO(sampleIOClassname);
        int[][] samplesToAvoid = null;
        if (testfile != null) {
            try {
		samplesToAvoid = (new Samples(sampleIO, testfile)).getLearningSamples();
	    } catch (IOException e) {
	        logger.fatal("Could not read samples to avoid", e);
	       System.exit(1);
	    }
        }

        // Generate enough strings. If we don't want negatives in the learn
        // sample, we don't know what is enough ...
        int[][] sentences = generateStrings(count, maxl, nsym, samplesToAvoid, dfa, negativeSamples);
        
        Samples s = new Samples(nsym, sentences, null);
        s.setSampleIO(sampleIO);
        SampleString[] strings = s.getStrings();

        try {
            sampleIO.putStrings(nsym, strings, output);
        } catch(IOException e) {
            logger.fatal("Could not write samples", e);
            System.exit(1);
        }
        if (dfa != null) {
            ArrayList<int[]> list = new ArrayList<int[]>();
            for (int[] x : sentences) {
        	list.add(x);
            }
            dfa.setSamples(list);
            System.out.println("MDL score: " + dfa.getMDLComplexity());
            
        }
    }
}
