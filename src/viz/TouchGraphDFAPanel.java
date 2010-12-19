package viz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.JPanel;

import org.apache.log4j.Logger;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;
import com.touchgraph.graphlayout.TGException;
import com.touchgraph.graphlayout.TGPanel;
import DfaInference.DFA;
import DfaInference.State;

public class TouchGraphDFAPanel extends JPanel implements TouchGraphBuilder {

    private static final long serialVersionUID = 1L;
    private TouchGraphPanel panel;

    private DFA dfa;
    static Logger log = Logger.getLogger(TouchGraphDFAPanel.class.getName());
    private ArrayList<Node> newnodes;
    private ArrayList<Edge> newedges;
    private ArrayList<Node> oldnodes;
    private ArrayList<Edge> oldedges;

    public void setDFA(DFA dfa) {
	this.dfa = dfa;
	if (dfa != null) {
	    panel.update(this);
	}
    }

    public DFA getDFA() {
	return dfa;
    }

    public TouchGraphDFAPanel() {
	setLayout(new BorderLayout());
	panel = new TouchGraphPanel();
	panel.setZoomValue(-60);
	panel.update(this);
	add(panel);
    }

    public static TouchGraphDFAPanel createPanel() {

        final Frame frame;

        final TouchGraphDFAPanel glPanel = new TouchGraphDFAPanel();

        frame = new Frame("DFA");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                frame.remove(glPanel);
                frame.dispose();
            }
        });
        frame.add("Center", glPanel);
        frame.setSize(800, 600);
        frame.setVisible(true);
        return glPanel;
    }

    public void setStyle(Node node, State state) {
	if (state.isAccepting()) {
	    node.setTextColor(Color.white);
	    node.setBackColor(Color.blue);
	} else if (state.isRejecting()) {
	    node.setTextColor(Color.white);
	    node.setBackColor(Color.red);
	} else {
	    node.setTextColor(Color.black);
	    node.setBackColor(Color.white);
	}
    }

    private Node addNode(State s, TGPanel tgPanel) throws TGException {
	Node node = tgPanel.findNode("" + s.getId());
	if (node == null) {
	    node = tgPanel.addNode("" + s.getId(), s.isAccepting() ? ("" + s.getWeight() + "/0") : ("0/" + s.getWeight()));
	    setStyle(node, s);
	    newnodes.add(node);
	}
	return node;
    }

    public Node parseState(State state, TGPanel tgPanel) {
	Node n = tgPanel.findNode("" + state.getId());
	try {
	    if (n == null) {
		n = addNode(state, tgPanel);
		State[] children = state.getChildren();
		for (int i = 0; i < children.length; i++) {
		    State child = children[i];
		    if (child != null) {
			Node node1 = parseState(child, tgPanel);
			Edge edge = tgPanel.addEdge(n, node1, Edge.DEFAULT_LENGTH);
			edge.setLbl(dfa.symbols.getSymbol(i) + "(" + state.getEdgeWeight(i) + "/" + state.getXEdgeWeight(i) + ")");
			newedges.add(edge);
		    }
		}
	    }
	} catch (TGException e) {
	    log.error(e);
	}
	return n;
    }

    public void build(TGPanel tgPanel) throws TGException {
	Edge.setEdgeMouseOverColor(Color.DARK_GRAY);
	Edge.setEdgeDefaultColor(Color.GRAY);
	Edge.setEdgeDefaultLength(5000);
	if (dfa != null) {
	    newnodes = new ArrayList<Node>();
	    newedges = new ArrayList<Edge>();
	    parseState(dfa.getStartState(), tgPanel);

	    for (Edge e : oldedges) {
		if (!newedges.contains(e)) {
		    tgPanel.deleteEdge(e);
		}
	    }
	    for (Node n : oldnodes) {
		if (!newnodes.contains(n)) {
		    tgPanel.deleteNode(n);
		}
	    }

	    oldnodes = new ArrayList<Node>(newnodes);
	    oldedges = new ArrayList<Edge>(newedges);

	} else {
	    oldnodes = new ArrayList<Node>();
	    oldedges = new ArrayList<Edge>();
	}
    }

}
