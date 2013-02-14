/*******************************************************************************
 * Copyright (c) 2006,2012 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Jeff Briggs, Henry Hughes, Ryan Morse
 *******************************************************************************/

package org.eclipse.linuxtools.systemtap.ui.ide.structures;

import java.io.File;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.linuxtools.internal.systemtap.ui.ide.IDEPlugin;
import org.eclipse.linuxtools.internal.systemtap.ui.ide.preferences.IDEPreferenceConstants;
import org.eclipse.linuxtools.systemtap.ui.structures.TreeDefinitionNode;
import org.eclipse.linuxtools.systemtap.ui.structures.TreeNode;

/**
 * Runs stap -vp1 & stap -up2 in order to get all of the probes/functions
 * that are defined in the tapsets.  Builds probeAlias and function trees
 * with the values obtained from the tapsets.
 *
 * Ugly code is a result of two issues with getting stap output.  First,
 * many tapsets do not work under stap -up2.  Second since the output
 * is not a regular language, we can't create a nice lexor/parser combination
 * to do everything nicely.
 * @author Ryan Morse
 */
public class FunctionParser extends TapsetParser {

	private TreeNode functions;
	static FunctionParser parser = null;

	public static FunctionParser getInstance(){
		if (parser != null)
			return parser;

		String[] tapsets = IDEPlugin.getDefault().getPreferenceStore()
				.getString(IDEPreferenceConstants.P_TAPSETS).split(File.pathSeparator);
		parser = new FunctionParser(tapsets);

		return parser;
	}

	private FunctionParser(String[] tapsets) {
		super(tapsets, "Function Parser"); //$NON-NLS-1$
		functions = new TreeNode("", false); //$NON-NLS-1$
	}

	/**
	 * Returns the root node of the tree of functions generated by
	 * parseFiles.  Functions are grouped by source file.
	 * @return A tree of tapset functions grouped by file.
	 */
	public synchronized TreeNode getFunctions() {
		return functions;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		runPass2Functions();
		functions.sortTree();
		fireUpdateEvent();	//Inform listeners that everything is done
		return new Status(IStatus.OK, IDEPlugin.PLUGIN_ID, ""); //$NON-NLS-1$
	}

	/**
	 * This method is used to build up the list of functions that were found
	 * during the first pass of stap.  Stap is invoked by: $stap -v -p1 -e
	 * 'probe begin{}' and parsing the output.
	 */
	private void runPass2Functions() {
		int i = 0;
		TreeNode parent;
		String script = "probe begin{}"; //$NON-NLS-1$
		String result = runStap(new String[] {"-v", "-p1", "-e"}, script);   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
		StringTokenizer st = new StringTokenizer(result, "\n", false); //$NON-NLS-1$
		st.nextToken(); //skip that stap command
		String tok = ""; //$NON-NLS-1$
		String regex = "^function .*\\)\n$"; //match ^function and ending the line with ')' //$NON-NLS-1$
		Pattern p = Pattern.compile(regex, Pattern.MULTILINE | Pattern.UNIX_LINES | Pattern.COMMENTS);
		Pattern secondp = Pattern.compile("[\\W]"); //take our function line and split it up //$NON-NLS-1$
		Pattern underscorep = Pattern.compile("^function _.*"); //remove any lines that "^function _" //$NON-NLS-1$
		Pattern allCaps = Pattern.compile("[A-Z_1-9]*"); //$NON-NLS-1$
		while(st.hasMoreTokens()) {
			tok = st.nextToken().toString();
			Matcher m = p.matcher(tok);
			while(m.find()) {
				// this gives us function foo (bar, bar)
				// we need to strip the ^function and functions with a leading _
				String[] us = underscorep.split(m.group().toString());

				for(String s : us) {
					String[] test = secondp.split(s);
					i = 0;
					for(String t : test) {
						// If i== 1 this is a function name.
						// Ignore ALL_CAPS functions; they are not meant for end
						// user use.
						if(i == 1 && !allCaps.matcher(t).matches()) {
							functions.add(new TreeNode(t, t, true));
						}
						else if(i > 1 && t.length() >= 1) {
							parent = functions.getChildAt(functions.getChildCount()-1);
							parent.add(new TreeDefinitionNode("function " + t, t, parent.getData().toString(), false)); //$NON-NLS-1$
						}
						i++;
					}
				}
			}
		}
		functions.sortTree();
	}

	/**
	 * This method will clean up everything from the run.
	 */
	public void dispose() {
		functions.dispose();
	}

}
