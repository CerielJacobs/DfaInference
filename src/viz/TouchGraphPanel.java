/*
 * TouchGraph LLC. Apache-Style Software License
 *
 *
 * Copyright (c) 2001-2002 Alexander Shapiro. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:  
 *       "This product includes software developed by 
 *        TouchGraph LLC (http://www.touchgraph.com/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "TouchGraph" or "TouchGraph LLC" must not be used to endorse 
 *    or promote products derived from this software without prior written 
 *    permission.  For written permission, please contact 
 *    alex@touchgraph.com
 *
 * 5. Products derived from this software may not be called "TouchGraph",
 *    nor may "TouchGraph" appear in their name, without prior written
 *    permission of alex@touchgraph.com.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL TOUCHGRAPH OR ITS CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR 
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 */

package viz;

import com.touchgraph.graphlayout.GLPanel;
import com.touchgraph.graphlayout.TGException;
import com.touchgraph.graphlayout.TGPanel;

/**
 * GLPanel contains code for adding scrollbars and interfaces to the TGPanel The
 * "GL" prefix indicates that this class is GraphLayout specific, and will
 * probably need to be rewritten for other applications
 * 
 * @author Alexander Shapiro
 * @version 1.21 $Id: GLPanel.java,v 1.31 2002/04/04 06:19:49 x_ander Exp $
 * 
 *          ..... so Wico extended this one.... now, after hours of mind
 *          puzzling why the app hang after rebuilding the TGPanel... he needed
 *          to change one extra thing in TGPanel.java...: public synchronized
 *          void repaintAfterMove() { // Called by TGLayout + others to indicate
 *          that graph has moved processGraphMove(); findMouseOver(); //
 *          overcome_hangup_thanks_to_wico // fireMovedEvent(); ... and removed
 *          this line .... repaint(); }
 */
public class TouchGraphPanel extends GLPanel {

    private static final long serialVersionUID = 1L;
    private TouchGraphBuilder builder;

    public void initialize() {
	buildPanel();
	buildLens();
	tgPanel.setLensSet(tgLensSet);
	addUIs();
	tgPanel.clearAll();
    }

    public void update(TouchGraphBuilder builder) {
	this.builder = builder;
	try {
	    //tgPanel.clearAll();
	    builder.build(tgPanel);
	} catch (TGException tge) {
	    System.out.println(tge.getMessage());
	}
	//if (tgPanel.getGES().getFirstNode() != null)
	//	tgPanel.setSelect(tgPanel.getGES().getFirstNode()); // Select first
    }

    public TGPanel getTgPanel() {
	return tgPanel;
    }

}