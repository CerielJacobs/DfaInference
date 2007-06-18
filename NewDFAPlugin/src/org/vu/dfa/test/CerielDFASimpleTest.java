package org.vu.dfa.test;

import java.awt.Dimension;
import java.util.ArrayList;

import javax.swing.JFrame;

import org.vu.dfa.DFA;
import org.vu.dfa.Guidance;
import org.vu.dfa.MDLEdFold;
import org.vu.dfa.MDLFold;
import org.vu.dfa.Samples;
import org.vu.dfa.Symbols;
import org.vu.dfa.abbadingo.AbbaDingoString;

public class CerielDFASimpleTest extends JFrame {

	private ArrayList<String> positivestrings = new ArrayList<String>();

	public CerielDFASimpleTest() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		positivestrings.add("aaab");
		positivestrings.add("aaaaab");
		positivestrings.add("aaabc");

		int accept = 1;
		int reject = 0;

		// make samples for the DFA
		
		AbbaDingoString[] samples = new AbbaDingoString[positivestrings.size()];
		int t = 0;
		for (String s : positivestrings) {
			AbbaDingoString abbadingostring = new AbbaDingoString(1, accept);
			abbadingostring.addToken(s);
			samples[t++] = abbadingostring;
		}
		
		
		int[][] symbols = Symbols.convert2learn(samples);

		DFA dfa = new DFA(symbols);

		System.out.println("MDL Complexity:" + dfa.getMDLComplexity());
		System.out.println(dfa);
/*
		MDLFold m = new MDLFold();
		m.printInfo = true;
		DFA done = m.doFold(dfa, new Guidance(), 0);

		// DFA bestDFA = m.doFold(dfa,new Guidance(), 0);

*/
		// /
		ViewPanel viewpanel = new ViewPanel(dfa, 20, 20);
		this.getContentPane().add(viewpanel);
		viewpanel.layoutNodes();
		viewpanel.repaint();
		Dimension d=viewpanel.getPreferredSize();
		setSize(d.width+100, d.height);
		setVisible(true);

	}

	public static void main(String[] args) {
		CerielDFASimpleTest engine = new CerielDFASimpleTest();
	}

}
