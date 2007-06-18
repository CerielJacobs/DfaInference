package org.vu.dfa.generate;

import junit.framework.Test;
import junit.framework.TestSuite;


public class Tests extends TestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(org.vu.dfa.abbadingo.Tests.suite());
        return suite;
    }
}
