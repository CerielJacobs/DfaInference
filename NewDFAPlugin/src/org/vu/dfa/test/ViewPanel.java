package org.vu.dfa.test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;

import org.vu.dfa.DFA;
import org.vu.dfa.State;
import org.vu.dfa.Symbols;

public class ViewPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	protected int maxCol = 1;

	protected int maxRow = 1;

	public static final int X_SPACE_DFLT = 100;

	public static final int Y_SPACE_DFLT = 100;

	public int xSpace = X_SPACE_DFLT;

	public int ySpace = Y_SPACE_DFLT;

	public static final double PI_BY_TWO = Math.PI / 2;

	public static final double PI_BY_THREE = Math.PI / 3;

	protected Color nodeColor = Color.gray;

	protected Color acceptNodeColor = Color.red;

	protected Color linkColor = Color.black;

	protected int linkThickness = 1;

	protected Color arrowColor = Color.black;

	protected Color textColor = Color.black;

	protected boolean paintLabels = true;

	protected int nodeSize = 30;

	protected int arrowLength = 12;

	protected DFA dfa = null;

	/**
	 * The minimum possible horizontal distance between two node centers. (set
	 * anew each paint call).
	 */
	protected int minSepX = 0;

	/**
	 * The minimum possible vertical distance between two node centers. (set
	 * anew each paint call).
	 */
	protected int minSepY = 0;

	protected Map<State, Point> positions = new HashMap<State, Point>();

	public ViewPanel(DFA dfa, int minSepX, int minSepY) {
		super(null, true); // no layout manager, double-buffered

		setBackground(Color.white);

		this.dfa = dfa;
		this.minSepX = minSepX;
		this.minSepY = minSepY;
	}

	/**
	 * Get the Dfa value.
	 * 
	 * @return the Dfa value.
	 */
	public DFA getDFA() {
		return dfa;
	}

	/**
	 * Set the Dfa value.
	 * 
	 * @param newDfa
	 *            The new Dfa value.
	 */
	public void setDFA(DFA newDfa) {
		this.dfa = newDfa;
	}

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (dfa != null) {
			for (State state : positions.keySet()) {

				Point p = getNodeCenter(state);
				paintNode(g2d, state, p);
			}
			for (State state : positions.keySet()) {
				paintEdges(g2d, state);
			}
		}
	}

	protected void paintNode(Graphics2D g, State n, Point p) {
		g.setColor(nodeColor);
		g.fillOval(p.x - nodeSize / 2, p.y - nodeSize / 2, nodeSize, nodeSize);
		if (n.isAccepting()) {
			g.setColor(acceptNodeColor);
			g.fillOval(p.x - nodeSize / 2 + 2, p.y - nodeSize / 2 + 2, nodeSize - 4, nodeSize - 4);
		}
	}

	/**
	 * Collapse a list of distinct edges to calls to paintEdge with maximal
	 * label lists. That is, given a list of edges, collect all those with the
	 * same source and destination and call paintEdge <i>once</i> for each such
	 * group, with the right labels list.
	 * 
	 * Note: modifies the incoming list (sorts it)
	 * 
	 * @param edges (
	 *            <code>List</code> of <code>Edge</code> objects)
	 */
	protected void paintEdges(Graphics2D g, State n) {
		Map<String, String> labels = new HashMap<String, String>();
		for (int i = 0; i < n.getNsym(); i++) {
			State child = n.traverseLink(i);
			if (child != null) {
				String label = labels.get(""+n+child);
				if (label != null) {
					label = label + "," + Symbols.getSymbol(i);
				} else
					label = Symbols.getSymbol(i);
				labels.put(""+n+child, label);
			}
		}

		for (int i = 0; i < n.getNsym(); i++) {
			State child = n.traverseLink(i);
			if (child != null) {
				String label=labels.get(""+n+child);
				paintEdge(g, getNodeCenter(n), getNodeCenter(child), label);
			}
		}
	}

	protected void paintEdge(Graphics2D g, Point start, Point end, String label) {

		Point length = new Point(end.x - start.x, end.y - start.y);
		Point mid = new Point(start.x + length.x / 2, start.y + length.y / 2);
		double theta = Math.atan2(length.y, length.x);
		int arcHeight = (int) Math.min(Math.sqrt(length.x * length.x + length.y * length.y) * 0.5, minSepY * 0.5);
		Point controlDistance = new Point((int) (arcHeight * Math.cos(theta - PI_BY_TWO)), (int) (arcHeight * Math.sin(theta - PI_BY_TWO)));
		g.setColor(linkColor);
		g.setStroke(new BasicStroke(linkThickness));
		Point arcMid = null;
		if (start.equals(end)) {
			g.drawOval((int) (start.x - minSepX * 0.35), (int) (start.y - minSepY * 0.7), (int) (minSepX * 0.7), (int) (minSepY * 0.7));
			arcMid = new Point(start.x, (int) (start.y - minSepY * 0.7));
			theta = 0;
		} else {
			g.draw(new QuadCurve2D.Double(start.x, start.y, mid.x + controlDistance.x, mid.y + controlDistance.y, end.x, end.y));
			arcMid = new Point(mid.x + controlDistance.x / 2, mid.y + controlDistance.y / 2);
		}
		g.setStroke(new BasicStroke(1));
		GeneralPath arrow = new GeneralPath();
		arrow.moveTo(arcMid.x + (int) (arrowLength * Math.cos(theta) / 2), arcMid.y + (int) (arrowLength * Math.sin(theta) / 2));
		arrow.lineTo(arcMid.x + (int) (arrowLength * Math.cos(theta - 2 * PI_BY_THREE) / 2), arcMid.y + (int) (arrowLength * Math.sin(theta - 2 * PI_BY_THREE) / 2));
		arrow.lineTo(arcMid.x + (int) (arrowLength * Math.cos(theta + 2 * PI_BY_THREE) / 2), arcMid.y + (int) (arrowLength * Math.sin(theta + 2 * PI_BY_THREE) / 2));
		arrow.closePath();
		g.setColor(arrowColor);
		g.fill(arrow);

		if (paintLabels) {

			g.setColor(textColor);
			g.setFont(new Font("times",Font.PLAIN,16));
			g.drawString(label.toString(), arcMid.x + (int) (arrowLength * Math.cos(theta)), arcMid.y + (int) (arrowLength * Math.sin(theta)));
		}
	}

	public Dimension getPreferredSize() {
		if (dfa == null) {
			return new Dimension(400, 400);
		} else {
			return new Dimension(maxCol * xSpace + xSpace, maxRow * ySpace + ySpace);
		}
	}

	
	protected void layoutNodes() {
		// System.out.println("layout nodes called");
		ArrayList list=new ArrayList();
		HashSet <State>checked=new HashSet<State>();
		maxRow = maxCol = 0;
		positions.put(dfa.getStartState(), new Point(1, 1));
		layoutNode(dfa.getStartState(), 1, 1,list ,checked);

		// This removed because it interacts badly with scroll
		// panel. Need to query the Viewport for its size, but cannot
		// assume that we appear in a viewport at all.
		/*
		 * xSpace = Math.max(X_SPACE_DFLT, getWidth() / (maxCol + 1)); ySpace =
		 * Math.max(Y_SPACE_DFLT, getWidth() / (maxRow + 1));
		 */

		java.awt.Container parent = getParent();
		if (parent != null) {
			xSpace = Math.max(X_SPACE_DFLT, parent.getWidth() / (maxCol + 1));
			ySpace = Math.max(Y_SPACE_DFLT, parent.getWidth() / (maxRow + 1));
		} else {
			xSpace = X_SPACE_DFLT;
			ySpace = Y_SPACE_DFLT;
		}
		setSize(xSpace * (maxRow + 1), ySpace * (maxCol + 1));
	}

	/**
	 * From the CGSuite tree layout code. Layout the node, given where you
	 * expect it to be. The col will not change, but the row may if the position
	 * is already taken or if it has many children.
	 * 
	 * @param n (
	 *            <code>Node</code>)
	 * @param col (
	 *            <code>int</code>) the anticipated column
	 * @param row (
	 *            <code>int</code>) the anticipated row
	 * @param lastRows (
	 *            <code>ArrayList</code>) the indices of the last used row in
	 *            each col
	 */
	private void layoutNode(State n, int row, int col, ArrayList lastRows, Set<State> checked) {
		checked.add(n);
		Point pos = getNodePosition(n);
		if (pos == null) {
			int lastRow = safeGetLastRow(lastRows, col);
			// find a good spot for the node
			if (lastRow < row) {
				safeSetLastRow(lastRows, col, row);
				positions.put(n, new Point(col, row));
			} else {
				// try again from the first available space in this col
				layoutNode(n, lastRow + 1, col, lastRows, checked);
			}
		} else {
			row = pos.y;
			col = pos.x;
			safeSetLastRow(lastRows, col, row);
		}
		layoutChildren(n, row, col, lastRows, checked);
	}

	/*
	 * Layout the children of the node, assuming it will be placed at the given
	 * position. Paramaters are as for {@link #layoutNode() layoutNode}.
	 * 
	 */
	private void layoutChildren(State n, int row, int col, ArrayList lastRows, Set<State> checked) {
		for (State child : n.getChildren()) {
			if (child != null && (!checked.contains(child))) {
				layoutNode(child, row, col + 1, lastRows, checked);
			}
		}
	}

	private int safeGetLastRow(ArrayList lastRows, int col) {
		if (col >= lastRows.size()) {
			return 0;
		} else {
			Integer row = (Integer) lastRows.get(col);
			if (row == null) {
				return 0;
			} else {
				return row.intValue();
			}
		}
	}

	/**
	 * Set the last row for the given column, without worrying about sizes and
	 * nulls. Also can give a smaller value than the current last row, in which
	 * case no change is made.
	 * 
	 * @param lastRows (
	 *            <code>ArrayList</code>)
	 * @param col (
	 *            <code>int</code>)
	 * @param row (
	 *            <code>int</code>)
	 */
	private void safeSetLastRow(ArrayList lastRows, int col, int row) {
		while (col + 1 > lastRows.size()) {
			lastRows.add(null);
		}
		int lastRow = safeGetLastRow(lastRows, col);
		if (row > lastRow) {
			lastRows.set(col, new Integer(row));
		}
		if (col > maxCol) {
			maxCol = col;
		}
		if (row > maxRow) {
			maxRow = row;
		}
	}

	private Point getNodePosition(State n) {
		if (positions.containsKey(n)) {
			return (Point) positions.get(n);
		} else {
			return null;
		}
	}

	/**
	 * Return the center of the node in screen coordinates, or null if node
	 * doesn't exist or has not been laid out.
	 * 
	 * @param n (
	 *            <code>Node</code>)
	 * @return a <code>Point</code> value or <code>null</code>
	 */
	public Point getNodeCenter(State n) {
		Point p = getNodePosition(n);
		if (p == null) {
			// System.out.println("node " + n.getID() + " has no position");
			return null;
		}
		return new Point(p.x * xSpace, p.y * ySpace);
	}

	/**
	 * Get the center of the node chosen by ID, or null if it doesn't exist or
	 * has not been laid out.
	 * 
	 * @param id (
	 *            <code>int</code>)
	 * @return a <code>Point</code> value or <code>null</code>
	 */
	public Point getNodeCenter(int id) {
		State n = dfa.getState(id);
		if (n == null) {
			// System.out.println("node " + id + " doesn't exist");
		}
		return (n == null) ? null : getNodeCenter(n);
	}

} // ViewPanel
