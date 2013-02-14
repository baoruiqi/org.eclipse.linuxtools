/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jeff Briggs, Henry Hughes, Ryan Morse, Roland Grunberg, Anithra P J
 *******************************************************************************/

package org.eclipse.linuxtools.systemtap.ui.ide.actions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.linuxtools.internal.systemtap.ui.ide.IDEPlugin;
import org.eclipse.linuxtools.internal.systemtap.ui.ide.Localization;
import org.eclipse.linuxtools.internal.systemtap.ui.ide.editors.stp.STPEditor;
import org.eclipse.linuxtools.internal.systemtap.ui.ide.launcher.SystemTapScriptTester;
import org.eclipse.linuxtools.internal.systemtap.ui.ide.preferences.IDEPreferenceConstants;
import org.eclipse.linuxtools.systemtap.ui.consolelog.ClientSession;
import org.eclipse.linuxtools.systemtap.ui.consolelog.ScpClient;
import org.eclipse.linuxtools.systemtap.ui.consolelog.Subscription;
import org.eclipse.linuxtools.systemtap.ui.consolelog.dialogs.SelectServerDialog;
import org.eclipse.linuxtools.systemtap.ui.consolelog.structures.ScriptConsole;
import org.eclipse.linuxtools.systemtap.ui.editor.PathEditorInput;
import org.eclipse.linuxtools.systemtap.ui.ide.IDESessionSettings;
import org.eclipse.linuxtools.systemtap.ui.ide.structures.StapErrorParser;
import org.eclipse.linuxtools.systemtap.ui.ide.structures.TapsetLibrary;
import org.eclipse.linuxtools.systemtap.ui.systemtapgui.preferences.EnvironmentVariablesPreferencePage;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.ResourceUtil;

import com.jcraft.jsch.JSchException;

/**
 * This <code>Action</code> is used to run a SystemTap script that is currently open in the editor.
 * Contributors:
 *    Ryan Morse - Original author.
 *    Red Hat Inc. - Copied most code from RunScriptAction here and made it into
 *                   base class for run actions.
 * @since 1.2
 */

public class RunScriptAction extends Action implements IWorkbenchWindowActionDelegate {

	protected boolean runLocal = true;
	protected boolean continueRun = true;
	protected String fileName = null;
	protected String tmpfileName = null;
	protected String serverfileName = null;
	protected IWorkbenchWindow fWindow;
	private IAction act;
	protected Subscription subscription;
	protected int SCRIPT_ID;
	protected ScriptConsole console;
	protected IPath path;

	@Override
	public void dispose() {
		fWindow= null;
	}

	@Override
	public void init(IWorkbenchWindow window) {
		fWindow= window;
	}

	@Override
	public void run(IAction action) {
		run();
	}

	public void setPath(IPath path){
		this.path = path;
	}

	/**
	 * The main body of this event. Starts by making sure the current editor is valid to run,
	 * then builds the command line arguments for stap and retrieves the environment variables.
	 * Finally, it gets an instance of <code>ScriptConsole</code> to run the script.
	 */
	@Override
	public void run() {

		if(isValid()) {
			if(getRunLocal() == false) {
				try{

					ScpClient scpclient = new ScpClient();
					serverfileName = fileName.substring(fileName.lastIndexOf('/')+1);
					tmpfileName="/tmp/"+ serverfileName; //$NON-NLS-1$
					 scpclient.transfer(fileName,tmpfileName);
			        } catch (JSchException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
			}
			final String[] script = buildStandardScript();
			final String[] envVars = getEnvironmentVariables();
            if(continueRun)
            {
            	Display.getDefault().asyncExec(new Runnable() {
            		@Override
					public void run() {
            			final ScriptConsole console;
            			if(getRunLocal() == false) {
            				console = ScriptConsole.getInstance(serverfileName);
            				console.run(script, envVars, new StapErrorParser());
            			} else {
            				console = ScriptConsole.getInstance(fileName);
            				console.runLocally(script, envVars, new StapErrorParser());
            			}
                        scriptConsoleInitialized(console);
            		}
            	});
            }
		}
	}

	/**
	 * Once a console for running the script has been created this
	 * function is called so that observers can be added for example
	 * @param console
	 */
	protected void scriptConsoleInitialized(ScriptConsole console){
	}

	/**
	 * Returns the path that was set for this action. If one was not set it
	 * returns the path of the current editor in the window this action is
	 * associated with.
	 *
	 * @return The string representation of the path of the script to run.
	 */
	protected String getFilePath() {
		if (path != null){
			return path.toOSString();
		}
		IEditorPart ed = fWindow.getActivePage().getActiveEditor();
		if(ed.getEditorInput() instanceof PathEditorInput){
			return ((PathEditorInput)ed.getEditorInput()).getPath().toString();
		} else {
			return ResourceUtil.getFile(ed.getEditorInput()).getLocation().toString();
		}
	}

	/**
	 * Checks if the current editor is operating on a file that actually exists and can be
	 * used as an argument to stap (as opposed to an unsaved buffer).
	 * @return True if the file is valid.
	 */
	private boolean isValid() {
		// If the path is not set this action will run the script from
		// the active editor
		if (this.path == null){
			IEditorPart ed = fWindow.getActivePage().getActiveEditor();
			if(!isValidEditor(ed)){
				return false;
			}
		}

		return this.getFilePath().endsWith(SystemTapScriptTester.STP_SUFFIX)
				&& isValidDirectory(this.getFilePath());
	}

	private boolean isValidEditor(IEditorPart ed) {
		if(null == ed) {
			String msg = MessageFormat.format(Localization.getString("RunScriptAction.NoScriptFile"),(Object[]) null); //$NON-NLS-1$
			MessageDialog.openWarning(fWindow.getShell(), Localization.getString("RunScriptAction.Problem"), msg); //$NON-NLS-1$
			return false;
		}

		if(ed.isDirty())
			ed.doSave(new ProgressMonitorPart(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), new FillLayout()));

		return true;
	}

	/**
	 * Checks whether the directory to which the given file
	 * belongs is a valid directory. Currently this function just
	 * checks if the given file does not belong to the tapset
	 * directory.
	 * @param fileName
	 * @return true if the given path is valid false otherwise.
	 * @since 1.2
	 */
	private boolean isValidDirectory(String fileName) {
		this.fileName = fileName;
		if(0 == IDESessionSettings.tapsetLocation.trim().length()){
			TapsetLibrary.getTapsetLocation(IDEPlugin.getDefault().getPreferenceStore());
		}

		if(fileName.contains(IDESessionSettings.tapsetLocation)) {
			String msg = MessageFormat.format(Localization.getString("RunScriptAction.TapsetDirectoryRun"),(Object []) null); //$NON-NLS-1$
			MessageDialog.openWarning(fWindow.getShell(), Localization.getString("RunScriptAction.Error"), msg); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	/**
	 * The command line argument generation method used by <code>RunScriptAction</code>. This generates
	 * a stap command line that includes the tapsets specified in user preferences, a guru mode flag
	 * if necessary, and the path to the script on disk.
	 * @return The command to invoke to start the script running in stap.
	 */
	protected String[] buildStandardScript() {
	//FixMe: Take care of this in the next release. For now only the guru mode is sent
		ArrayList<String> cmdList = new ArrayList<String>();
		String[] script;

		getImportedTapsets(cmdList);

		if(isGuru())
			cmdList.add("-g"); //$NON-NLS-1$

		script = finalizeScript(cmdList);

		return script;
	}

	/**
	 * Adds the tapsets that the user has added in preferences to the input <code>ArrayList</code>
	 * @param cmdList The list to add the user-specified tapset locations to.
	 */

	protected void getImportedTapsets(ArrayList<String> cmdList) {
		IPreferenceStore preferenceStore = IDEPlugin.getDefault().getPreferenceStore();
		String[] tapsets = preferenceStore.getString(IDEPreferenceConstants.P_TAPSETS).split(File.pathSeparator);

		//Get all imported tapsets
		if(null != tapsets && tapsets.length > 0 && tapsets[0].trim().length() > 0) {
	   		for(int i=0; i<tapsets.length; i++) {
	   			cmdList.add("-I"); //$NON-NLS-1$
	   			cmdList.add(tapsets[i]);
	   		}
		}
	}

	/**
	 * Checks the current script to determine if guru mode is required in order to run. This is determined
	 * by the presence of embedded C.
	 * @return True if the script contains embedded C code.
	 */
	protected boolean isGuru() {
		try {
			File f = new File(fileName);
			FileReader fr = new FileReader(f);

			int curr = 0;
			int prev = 0;
			boolean front = false;
			boolean imbedded = false;
			boolean inLineComment = false;
			boolean inBlockComment = false;
			while(-1 != (curr = fr.read())) {
				if(!inLineComment && !inBlockComment && '%' == prev && '{' == curr)
					front = true;
				else if(!inLineComment && !inBlockComment && '%' == prev && '}' == curr && front) {
					imbedded = true;
					break;
				} else if(!inBlockComment && (('/' == prev && '/' == curr) || '#' == curr)) {
					inLineComment = true;
				} else if(!inLineComment && '/' == prev && '*' == curr) {
					inBlockComment = true;
				} else if('\n' == curr) {
					inLineComment = false;
				} else if('*' == prev && '/' == curr) {
					inBlockComment = false;
				}
				prev = curr;
			}
			fr.close();
			if(imbedded)
				return true;
		} catch (FileNotFoundException fnfe) {
			fnfe.printStackTrace();
		} catch (IOException ie) {
			ie.printStackTrace();
		}
		return false;
	}

	protected boolean createClientSession()
	{
		if (!ClientSession.isConnected() && new SelectServerDialog(fWindow.getShell()).open()) {
			subscription = new Subscription(fileName,isGuru());
			/*	if (ClientSession.isConnected())
				{
				console = ScriptConsole.getInstance(fileName, subscription);
				console.run();
				}*/
		}
		return true;
	}

	/**
	 * Produces a <code>String[]</code> from the <code>ArrayList</code> passed in with stap inserted
	 * as the first entry, and the filename as the last entry. Used to convert the arguments generated
	 * earlier in <code>buildStandardScript</code> such as tapset locations and guru mode into an actual
	 * command line argument array that can be passed to <code>Runtime.exec</code>.
	 * @param cmdList The list of arguments for stap for this script
	 * @return An array suitable to pass to <code>Runtime.exec</code> to start stap on this file.
	 */
	protected String[] finalizeScript(ArrayList<String> cmdList) {

		String[] script;

		script = new String[cmdList.size() + 4];
		script[0] = "stap"; //$NON-NLS-1$

		if(getRunLocal() == false)
			script[script.length-1] = tmpfileName;
		else
			script[script.length-1] = fileName;

		for(int i=0; i< cmdList.size(); i++) {
			script[i+1] = cmdList.get(i).toString();
		}
		script[script.length-3]="-m"; //$NON-NLS-1$

		String modname;
		if(getRunLocal() == false) {
			modname = serverfileName.substring(0, serverfileName.lastIndexOf(".stp")); //$NON-NLS-1$
		}
		/* We need to remove the directory prefix here because in the case of
		 * running the script remotely, this is already done.  Not doing so
		 * causes a modname error.
		 */
		else {
			modname = fileName.substring(fileName.lastIndexOf('/')+1);
			modname = modname.substring(0, modname.lastIndexOf(".stp")); //$NON-NLS-1$
		}

		// Make sure script name only contains underscores and/or alphanumeric characters.
		Pattern validModName = Pattern.compile("^[a-z0-9_]+$"); //$NON-NLS-1$
		Matcher modNameMatch = validModName.matcher(modname);
		if (!modNameMatch.matches()) {
			continueRun = false;

			Shell parent = PlatformUI.getWorkbench().getDisplay()
					.getActiveShell();
			MessageDialog.openError(parent,
					Messages.ScriptRunAction_InvalidScriptTitle,
					Messages.ScriptRunAction_InvalidScriptTMessage);

			return new String[0];
		}

		script[script.length-2]=modname;
		return script;
	}

	private String[] getEnvironmentVariables() {
		return EnvironmentVariablesPreferencePage.getEnvironmentVariables();
	}

	@Override
	public void selectionChanged(IAction act, ISelection select) {
		this.act = act;
		setEnablement(false);
		buildEnablementChecks();
	}

	private void buildEnablementChecks() {
		if(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor() instanceof STPEditor)
			setEnablement(true);
	}

	private void setEnablement(boolean enabled) {
		act.setEnabled(enabled);
	}

	public void setLocalScript(boolean enabled) {
		runLocal = enabled;
	}

	private boolean getRunLocal() {
		return runLocal;
	}

}
