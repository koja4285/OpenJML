/*
 * This file is part of the OpenJML plugin project. 
 * Copyright (c) 2006-2013 David R. Cok
 */
package org.jmlspecs.openjml.eclipse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.tools.JavaFileObject;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.annotation.Nullable;
import org.jmlspecs.annotation.Pure;
import org.jmlspecs.annotation.Query;
import org.jmlspecs.openjml.JmlSpecs;
import org.jmlspecs.openjml.Main.Cmd;
import org.jmlspecs.openjml.Strings;
import org.jmlspecs.openjml.eclipse.PathItem.ProjectPath;
import org.jmlspecs.openjml.proverinterface.IProverResult;
import org.osgi.framework.Bundle;

// FIXME - review UI Utils class

/**
 * This class holds utility values and methods to support the Eclipse plugin for
 * OpenJML.
 * 
 * @author David Cok
 * 
 */
public class Utils {

	/** This class is used to wrap arbitrary exceptions coming from OpenJML */
	public static class OpenJMLException extends RuntimeException {
		/** Default serial version ID */
		private static final long serialVersionUID = 1L;

		/**
		 * Used to signal some unexpected error situation in doing JML
		 * processing.
		 */
		public OpenJMLException(@NonNull String error) {
			super(error);
		}

		/**
		 * Used to signal some unexpected error situation in doing JML
		 * processing.
		 */
		public OpenJMLException(@NonNull String error, @NonNull Exception e) {
			super(error, e);
		}
	}

	/** String used in openjml-specific properties. */
	final public static @NonNull String OPENJML = "openjml"; //$NON-NLS-1$

	/** The ID of the marker, which must match that in the plugin file. */
	final public static @NonNull String JML_MARKER_ID = Activator.PLUGIN_ID + ".JMLProblem"; //$NON-NLS-1$

	/** The ID of the marker, which must match that in the plugin file. */
	final public static @NonNull
	String JML_HIGHLIGHT_ID = Activator.PLUGIN_ID + ".JMLHighlight"; //$NON-NLS-1$

	/** The ID of the marker, which must match that in the plugin file. */
	final public static @NonNull
	String JML_HIGHLIGHT_ID_TRUE = JML_HIGHLIGHT_ID + ".True"; //$NON-NLS-1$

	/** The ID of the marker, which must match that in the plugin file. */
	final public static @NonNull
	String JML_HIGHLIGHT_ID_FALSE = JML_HIGHLIGHT_ID + ".False"; //$NON-NLS-1$

	/** The ID of the marker, which must match that in the plugin file. */
	final public static @NonNull // FIXME - is this still used?
	String JML_HIGHLIGHT_ID_EXCEPTION = JML_HIGHLIGHT_ID + ".Exception"; //$NON-NLS-1$

	/** The ID of the marker, which must match that in the plugin file. */
	final public static @NonNull
	String ESC_MARKER_ID = Activator.PLUGIN_ID + ".JMLESCProblem"; //$NON-NLS-1$

	/** An empty string */
	final public static @NonNull String emptyString = ""; //$NON-NLS-1$

	/** An single space */
	final public static @NonNull String space = " "; //$NON-NLS-1$

	/** The .java suffix */
	final public static @NonNull String dotJava = ".java";  //$NON-NLS-1$

	/** The .jml suffix */
	final public static @NonNull String dotJML = ".jml";  //$NON-NLS-1$

	/**
	 * A map relating java projects to the instance of OpenJMLInterface that
	 * handles openjml stuff for that project. We have a separate instance for
	 * each project since options can be different by project.
	 */
	final protected @NonNull
	Map<IJavaProject, OpenJMLInterface> projectMap = new HashMap<IJavaProject, OpenJMLInterface>();

	/**
	 * Returns the unique OpenJMLInterface for a given project
	 * 
	 * @param jproject
	 *            the Java project whose interface is desired
	 * @return the OpenJMLInterface for that project
	 */
	public @NonNull
	OpenJMLInterface getInterface(@NonNull IJavaProject jproject) {
		// readProjectProperties(jproject.getProject());
		OpenJMLInterface i = projectMap.get(jproject);
		if (i == null) {
			projectMap.put(jproject, i = new OpenJMLInterface(jproject));
		}
		return i;
	}

	protected String getInternalSystemSpecs() {
		String filesep = "/"; // Not File.separator I think
		boolean verbose = Utils.verboseness >= Utils.VERBOSE;
		StringBuilder ss = new StringBuilder();
		try {
			boolean somethingPresent = false;
			String versionString = System.getProperty("java.version"); // FIXME
																		// - use
																		// eclipse
																		// version?
			int version = 7; // the current default
			if (versionString.startsWith("1.") && versionString.length() > 3
					&& (version = (versionString.charAt(2) - '0')) >= 4 && version <= 9) {
				// found OK version number
			} else {
				Log.log("Unrecognized version: " + versionString);
				version = 7; // default, if the version string is in an
								// unexpected format
			}

			String[] defspecs = new String[8]; // null entries OK

			Bundle specsBundle = Platform.getBundle(Activator.SPECS_PLUGIN_ID);
			if (specsBundle == null) {
				if (verbose)
					Log.log("No specification plugin "
							+ Activator.SPECS_PLUGIN_ID);
			} else {
				String loc = null;
				URL url = FileLocator.toFileURL(specsBundle.getResource(""));
				File root = new File(url.toURI());
				loc = root.getAbsolutePath();
				loc = loc.replace("\\", "/");
				if (verbose)
					Log.log("JMLSpecs plugin found " + root + " "
							+ root.exists());
				if (root.isFile()) {
					// Presume it is a jar or zip file
					JarFile j = new JarFile(root);
					int i = 0;
					for (int v = version; v >= 4; --v) {
						JarEntry f = j.getJarEntry("java" + v);
						if (f != null)
							defspecs[i++] = loc + "!java" + v;
					}
					j.close();
				} else if (root.isDirectory()) {
					// Normal file system directory
					int i = 0;
					for (int v = version; v >= 4; --v) {
						File f = new File(root, "java" + v);
						if (f.exists())
							defspecs[i++] = root.getAbsolutePath().replace(
									'\\', '/')
									+ filesep + "java" + v;
					}
				} else {
					if (verbose)
						Log.log("Expected contents (javaN subdirectories) not found in specs bundle at "
								+ root);
				}
				for (String z : defspecs) {
					if (z != null) {
						somethingPresent = true;
						if (verbose)
							Log.log("Set library specspath defaults from the Specs plugin");
						break;
					}
				}
			}
			if (!somethingPresent) {
				Bundle selfBundle = Platform.getBundle(Activator.PLUGIN_ID);
				if (selfBundle == null) {
					if (verbose)
						Log.log("No self plugin");
				} else {
					URL url = FileLocator.toFileURL(selfBundle.getResource(""));
					if (url != null) {
						File root = new File(url.toURI());
						if (verbose)
							Log.log("Self bundle found " + root + " "
									+ root.exists());
						int i = 0;
						if (root.isDirectory()) {
							for (int v = version; v >= 4; --v) {
								File f = new File(root, ".." + filesep
										+ "specs" + filesep + "java" + v);
								if (f.exists())
									defspecs[i++] = f.toString();
							}
						} else {
							JarFile j = new JarFile(root);
							for (int v = version; v >= 4; --v) {
								JarEntry f = j.getJarEntry("specs" + filesep
										+ "java" + v);
								if (f != null)
									defspecs[i++] = root + "!specs" + filesep
											+ "java" + v;
							}
							j.close();
						}
						if (i > 0)
							somethingPresent = true;
					}
				}
			}
			if (!somethingPresent)
				Log.errorlog("No internal library specs found", null);
			for (String z : defspecs)
				if (z != null) {
					ss.append(z);
					ss.append(File.pathSeparator);
				}
			if (ss.length() > 0)
				ss.setLength(ss.length() - File.pathSeparator.length());
		} catch (Exception e) {
			Log.log("Failure finding internal specs: " + e);
		}
		return ss.toString();
	}

	/**
	 * This routine initiates (as a Job) checking the JML of all the Java files
	 * in the selection; if any containers (including working sets) are
	 * selected, the operation applies to the contents of the container ; if any
	 * Java elements are selected (e.g. a method), the operation applies to the
	 * containing file.
	 * 
	 * @param selection
	 *            the current selection (ignored unless it is an
	 *            IStructuredSelection)
	 * @param window
	 *            null or the currently active IWorkbenchWindow
	 * @param shell
	 *            the current shell
	 */
	public void checkSelection(@NonNull final ISelection selection,
			@Nullable final IWorkbenchWindow window, @NonNull final Shell shell) {
		if (!checkForDirtyEditors()) return;
		List<IResource> res = getSelectedResources(selection, window, shell);
		if (res.size() == 0) {
			showMessage(shell, "JML Check", "Nothing appropriate to check");
			return;
		}
		deleteMarkers(res, shell);
		final Map<IJavaProject, List<IResource>> sorted = sortByProject(res);
		Job j = new Job("JML Manual Check") {
			public IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("JML type-checking", sorted.size());
				boolean c = false;
				for (final IJavaProject jp : sorted.keySet()) { // FIXME - should impose an order on the projects
					final List<IResource> ores = sorted.get(jp);
					try {
						getInterface(jp).executeExternalCommand(Cmd.CHECK,
								ores, monitor);
					} catch (Exception e) {
						showExceptionInUI(shell, null, e);
						c = true;
					}
					monitor.worked(1);
				}
				return c ? Status.CANCEL_STATUS : Status.OK_STATUS;
			}
		};
        IResourceRuleFactory ruleFactory = 
                ResourcesPlugin.getWorkspace().getRuleFactory();
// FIXME        ISchedulingRule rule = ruleFactory.markerRule(r);
		if (sorted.keySet().size() == 1) {
			j.setRule(sorted.keySet().iterator().next().getProject());
		} else {
			j.setRule(ResourcesPlugin.getWorkspace().getRoot());
		}
		j.setUser(true); // true since the job has been initiated by an end-user
		j.schedule();
	}
	
	/** Checks for dirty editors; pops up a dialog to ask the user what
	 * to do. Returns false if the operation is to be canceled.
	 * @return
	 */
	public boolean checkForDirtyEditors() {
		return PlatformUI.getWorkbench().saveAllEditors(true);
	}

	/**
	 * This routine initiates (as a Job) executing ESC on all the Java files in
	 * the selection; if any containers are selected, the operation applies the
	 * contents of the container (including working sets). If a Type or Method
	 * is selected, the operation is applied to that element only. (FIXME - not
	 * yet)
	 * 
	 * @param selection
	 *            the current selection (ignored unless it is an
	 *            IStructuredSelection)
	 * @param window
	 *            null or the currently active IWorkbenchWindow
	 * @param shell
	 *            the current shell
	 */
	public void checkESCSelection(ISelection selection,
			@Nullable IWorkbenchWindow window, @Nullable final Shell shell) {
		if (!checkForDirtyEditors()) return;
		final List<Object> res = getSelectedElements(selection, window, shell);
		if (res.size() == 0) {
			showMessage(shell, "ESC", "Nothing applicable to check");
			return;
		}
		final Map<IJavaProject, List<Object>> sorted = sortByProject(res);
		deleteMarkers(res, shell);
		for (final IJavaProject jp : sorted.keySet()) {
			checkESCProject(jp,sorted.get(jp),shell,"Static Checks - Manual");
		}
	}
	
	public void checkESCProject(final IJavaProject jp, final List<?> ores, /*@ nullable */Shell shell, String reason) {
		Job j = new Job(reason) {
			public IStatus run(IProgressMonitor monitor) {
				// We are processing the projects sequentially.
				// FIXME - they should be done in dependency order
				monitor.beginTask("Static checking of " + jp.getElementName(), 1);
				boolean c = false;
				try {
					if (ores == null) {
						LinkedList<Object> list = new LinkedList<Object>();
						list.add(jp);
						final List<Object> res = list;
						getInterface(jp).executeESCCommand(Cmd.ESC, res,
								monitor);
					} else {
						getInterface(jp).executeESCCommand(Cmd.ESC, ores,
								monitor);
					}
				} catch (Exception e) {
					// FIXME - this will block, preventing progress on the rest of the projects
					Log.errorlog("Exception during Static Checking - " + jp.getElementName(), e);
					showExceptionInUI(null, "Exception during Static Checking - " + jp.getElementName(), e);
					c = true;
				}
				return c ? Status.CANCEL_STATUS : Status.OK_STATUS;
			}
		};
        IResourceRuleFactory ruleFactory = 
                ResourcesPlugin.getWorkspace().getRuleFactory();
// FIXME        ISchedulingRule rule = ruleFactory.markerRule(r);
		j.setRule(jp.getProject());
		j.setUser(true); // true since the job has been initiated by an end-user
		j.schedule();
	}

	static public java.util.Properties getProperties() {
		return org.jmlspecs.openjml.Utils.findProperties(null);
	}

	public void initializeProperties() {
		try {
			verboseness = Integer.parseInt(Options.value(Options.verbosityKey));
		} catch (Exception e) { // In particular, NumberFormatException
			verboseness = 1; 
		}
	}

	static public java.util.Properties readProperties() {
		// FIXME - as different projects are processed, they continually
		// overwrite each other's properties
		// Log.log("About to read properties");
		java.util.Properties properties = new java.util.Properties();
		{
			// Note: It appears that a file in the workspace root is not seen as
			// a member of the workspace - just projects - because findMember on
			// the workspace root returns null. So we find the file directly in
			// the local file system.
			IPath path = ResourcesPlugin.getWorkspace().getRoot().getLocation()
					.append(org.jmlspecs.openjml.Strings.propertiesFileName);
			try {
				boolean found = org.jmlspecs.openjml.Utils.readProps(
						properties, path.toFile().getAbsolutePath());
				if (found && Utils.verboseness >= Utils.VERBOSE)
					Log.log("Properties read from the workspace: "
							+ path.toOSString());
			} catch (java.io.IOException e) {
				Log.errorlog("Failed to read a properties file", e);
			}
		}
		return properties;
	}

	public java.util.Properties readProjectProperties(IProject project) {
		// FIXME - as different projects are processed, they continually
		// overwrite each other's properties
		// Log.log("About to read properties");
		readProperties();
		java.util.Properties properties = new java.util.Properties();
		{
			// Log.log("Project location: " + project.getLocation());
			IResource res = project
					.findMember(org.jmlspecs.openjml.Strings.propertiesFileName);
			if (res != null) {
				try {
					boolean found = org.jmlspecs.openjml.Utils.readProps(
							properties, res.getLocation().toOSString());
					if (found && Utils.verboseness >= Utils.VERBOSE)
						Log.log("Properties read from the project directory: "
								+ res.getLocation().toOSString());
				} catch (java.io.IOException e) {
					Log.errorlog("Failed to read a properties file", e);
				}
			}
		}
		return properties;
	}

	/**
	 * This routine initiates (as a Job) compiling RAC for all the Java files in
	 * the selection; if any containers are selected, the operation applies the
	 * contents of the container (including working sets); if any Java elements
	 * are selected (e.g. a method), the operation applies to the containing
	 * file.
	 * 
	 * @param selection
	 *            the current selection (ignored unless it is an
	 *            IStructuredSelection)
	 * @param window
	 *            null or the currently active IWorkbenchWindow
	 * @param shell
	 *            the current shell
	 */
	public void racSelection(final @NonNull ISelection selection,
			@Nullable final IWorkbenchWindow window, final Shell shell) {
		if (!checkForDirtyEditors()) return;
		
		// For now at least, only IResources are accepted for selection
		final @NonNull List<IResource> res = getSelectedResources(selection, window, shell);
		if (res.size() == 0) {
			showMessage(shell, "JML RAC Selected", "Nothing appropriate to check");
			return;
		}
		
		final @NonNull Map<IJavaProject, List<IResource>> sorted = sortByProject(res);
		for (final IJavaProject jp : sorted.keySet()) {
			Job j = new Job("Compiling Runtime Assertions on selected resources") {
				public IStatus run(IProgressMonitor monitor) {
					boolean c = false;
					try {
						getInterface(jp).executeExternalCommand(Cmd.RAC,
						    sorted.get(jp), monitor);
					} catch (Exception e) {
						showExceptionInUI(shell,
							"Failure while compiling runtime assertions", e);
						c = true;
					}
					return c ? Status.CANCEL_STATUS : Status.OK_STATUS;
				}
			};
	        IResourceRuleFactory ruleFactory = 
	                ResourcesPlugin.getWorkspace().getRuleFactory();
	// FIXME        ISchedulingRule rule = ruleFactory.markerRule(r);
			j.setRule(jp.getProject());
			j.setUser(true); // true since the job has been initiated by an
								// end-user
			j.schedule();
		}
	}

	/**
	 * This routine initiates (as a Job) compiling RAC for all the Java files in
	 * the selection; if any containers are selected, the operation applies the
	 * contents of the container (including working sets); if any Java elements
	 * are selected (e.g. a method), the operation applies to the containing
	 * file.
	 * 
	 * @param selection
	 *            the current selection (ignored unless it is an
	 *            IStructuredSelection)
	 * @param window
	 *            null or the currently active IWorkbenchWindow
	 * @param shell
	 *            the current shell
	 */
	public void racMarked(final @NonNull ISelection selection,
			@Nullable final IWorkbenchWindow window, final Shell shell) {

		if (!checkForDirtyEditors()) return;

		// For now at least, only IResources are accepted for selection
		final @NonNull Collection<IJavaProject> projects = getSelectedProjects(true,selection, window, shell);
		if (projects.size() == 0) {
			showMessage(shell, "JML RAC Marked", "No projects selected");
			return;
		}
		for (final IJavaProject jp : projects) {
			racMarked(jp);
		}
	}
	
	public void racMarked(final IJavaProject jp) {
		Job j = new Job("Compiling Runtime Assertions on marked resources") {
			public IStatus run(IProgressMonitor monitor) {
				boolean c = false;
				try {
					Set<IResource> resources = getRacFiles(jp);
					getInterface(jp).executeExternalCommand(Cmd.RAC,
							resources, monitor);
				} catch (Exception e) {
					showExceptionInUI(null,
							"Failure while compiling runtime assertions", e);
					c = true;
				}
				return c ? Status.CANCEL_STATUS : Status.OK_STATUS;
			}
		};
        IResourceRuleFactory ruleFactory = 
                ResourcesPlugin.getWorkspace().getRuleFactory();
// FIXME        ISchedulingRule rule = ruleFactory.markerRule(r);
        j.setRule(jp.getProject());
		j.setUser(true); // true since the job has been initiated by an
		// end-user
		j.schedule();
	}

	// TODO - document doBuildRac - put in a Job - or not - because called from
	// a computation thread anyway
	protected void doBuildRac(IJavaProject jproject,
			List<IResource> resourcesToBuild, IProgressMonitor monitor) {
		Set<IResource> enabledForRac = getRacFiles(jproject);
		List<IResource> newlist = new ArrayList<IResource>(enabledForRac.size());
		for (IResource r : resourcesToBuild) {
			if (enabledForRac.contains(r))
				newlist.add(r);
		}
		if (newlist.size() != 0) {
			try {
				if (Utils.verboseness >= Utils.NORMAL)
					Log.log("Starting RAC " + newlist.size() + " files");
				getInterface(jproject).executeExternalCommand(Cmd.RAC, newlist,
						monitor);
				if (Utils.verboseness >= Utils.NORMAL)
					Log.log("Completed RAC");
			} catch (Exception e) {
				showExceptionInUI(null, null, e);
			}
		} else {
			if (Utils.verboseness >= Utils.NORMAL)
				Log.log("Nothing to RAC");
		}
	}

	/**
	 * This routine initiates (as a Job) generating jmldoc pages for each
	 * project in the selection. If non-projects are selected, the containing
	 * project is used. For each project, the contents of the source folders are
	 * documented.
	 * 
	 * @param selection
	 *            the current selection (ignored unless it is an
	 *            IStructuredSelection)
	 * @param window
	 *            null or the currently active IWorkbenchWindow
	 * @param shell
	 *            the current shell
	 */
	public void jmldocSelection(final ISelection selection,
			@Nullable final IWorkbenchWindow window, final Shell shell) {
		// For now at least, only IResources are accepted for selection
		final List<IResource> res = getSelectedResources(selection, window,
				shell);
		if (res.size() == 0) {
			showMessage(shell, "JML - jmldoc", "Nothing appropriate to check");
			return;
		}
		Job j = new Job("Generating jmldoc") {
			public IStatus run(IProgressMonitor monitor) {
				boolean c = false;
				try {
					Collection<IJavaProject> projects = getSelectedProjects(
							false, selection, window, shell);
					if (projects.size() == 0)
						projects = getSelectedProjects(true, selection, window,
								shell);
					for (IJavaProject p : projects) {
						getInterface(p).generateJmldoc(p);
					}
				} catch (Exception e) {
					showExceptionInUI(shell, null, e);
					c = true;
				}
				return c ? Status.CANCEL_STATUS : Status.OK_STATUS;
			}
		};
		// FIXME - use some proper scheduling rule?
		j.setRule(ResourcesPlugin.getWorkspace().getRoot());
		j.setUser(true);
		j.schedule();
	}

	/**
	 * This method pops up an information window to show the specifications of
	 * each selected type, method, or field. Executed entirely in the UI thread.
	 * 
	 * @param selection
	 *            the selection (multiple items may be selected)
	 * @param window
	 *            the current window
	 * @param shell
	 *            the current shell
	 */
	public void showSpecsForSelection(ISelection selection,
			@Nullable IWorkbenchWindow window, Shell shell) {
		List<Object> list = getSelectedElements(selection, window, shell);
		if (list.isEmpty()) {
			showMessage(shell, "OpenJML - Show specifications for Java element",
						"Choose a type, method or field whose specifications are to be shown");
			return;
		}
		String sn = emptyString;
		for (Object o : list) {
			try {
				String s = null;
				if (o instanceof IType) {
					IType t = (IType) o;
					s = getInterface(t.getJavaProject()).getAllSpecs(t);
					if (s != null)
						s = s.replace('\r', ' ');
					sn = "type " + t.getFullyQualifiedName();
				} else if (o instanceof IMethod) {
					IMethod m = (IMethod) o;
					s = getInterface(m.getJavaProject()).getAllSpecs(m);
					if (s != null)
						s = s.replace('\r', ' ');
					sn = "method "
							+ m.getDeclaringType().getFullyQualifiedName()
							+ "." + m.getElementName();
				} else if (o instanceof IField) {
					IField f = (IField) o;
					s = getInterface(f.getJavaProject()).getSpecs(f);
					if (s != null)
						s = s.replace('\r', ' ');
					sn = "field "
							+ f.getDeclaringType().getFullyQualifiedName()
							+ "." + f.getElementName();
				} else if (o instanceof IFile) {
					IFile f = (IFile) o;
					ICompilationUnit cu = JavaCore.createCompilationUnitFrom(f);
					IType[] types = cu.getTypes();
					IType t = types[0]; // FIXME - find the one public type
					JavaFileObject jfo = JmlSpecs.instance(
							getInterface(JavaCore.create(f.getProject())).api.context()).findAnySpecFile(
							t.getFullyQualifiedName());
					Log.log("JFO = " + jfo.getName());
					Path p = new Path(jfo.getName());
					IFile ff = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(p);
					Log.log("File = " + ff);
				}
				if (s != null) {
					if (s.length() == 0)
						s = "<no specifications>";
					showMessageInUINM(shell, "Specifications for " + sn, s);
				} else if (sn != null) {
					showMessageInUINM(shell, "Specifications",
							"No JML check has been run");
					return;
				}
			} catch (Exception e) {
				showMessage(shell, "OpenJML - Exception", e.getMessage());
			}
		}
	}
	
	public List<IType> findMatchingClassNames(IJavaProject jp, String text) throws JavaModelException {
		String classname = "/" + text.replace('.', '/') + ".class"; //$NON-NLS-1$ //$NON-NLS-2$
		String dotText = "." + text;
		String dollarText = "$" + text;
		List<IType> matches = new LinkedList<IType>();
		for (IClasspathEntry cpe : jp.getResolvedClasspath(true)) {
			// cpe is SOURCE, PROJECT, or LIBRARY
			if (cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
				// findPackageFragmentRoots does not work for
				// library entries
				try {
					ZipFile z = new ZipFile(cpe.getPath()
							.toString());
					Enumeration<? extends ZipEntry> en = z
							.entries();
					while (en.hasMoreElements()) {
						ZipEntry ze = en.nextElement();
						String zs = ze.getName();
						if (zs.endsWith(classname)) {
							zs = zs.replace('/', '.');
							zs = zs.substring(0, zs.length()
									- ".class".length()); //$NON-NLS-1$
							matches.add(jp.findType(zs,
									(IProgressMonitor) null));
						}
					}
					z.close();
				} catch (java.io.IOException ex) {
					Log.errorlog("Failed to open jar file "
							+ cpe.getPath().toString(), ex);
					// Pretend there is no match
				}
			} else {
				for (IPackageFragmentRoot pfr : jp
						.findPackageFragmentRoots(cpe)) {
					if (!pfr.isOpen())
						continue;
					for (IJavaElement element : pfr.getChildren()) { 
						// element is a IPackageFragment
						for (IJavaElement je : ((IPackageFragment) element)
								.getChildren()) { // je is aICompilationUnit
							if (je instanceof ICompilationUnit) {
								for (IType ee : ((ICompilationUnit) je).getAllTypes()) {
									if (ee.getFullyQualifiedName().endsWith(dotText)) {
										matches.add(ee);
									} else {
										matchNestedTypes(ee,dollarText,matches);
									}
								}
							}
						}
					}
				}
			}
		}
		return matches;
	}
	
	protected void matchNestedTypes(IType ee, String dotText, List<IType> matches) throws JavaModelException {
		for (IJavaElement e: ee.getChildren()) {
			if (e instanceof IType) {
				IType t = (IType)e;
				if (t.getFullyQualifiedName().endsWith(dotText)) {
					matches.add(t);
				} else {
					matchNestedTypes(t,dotText,matches);
				}
			}
		}
	}
 
	/**
	 * This method opens an editor on the specs corresponding to a selected
	 * class; if text is selected, an attempt is made to interpret the text as
	 * the name (possibly fully qualified) of a class; otherwise if a type is
	 * selected that type is used; if a file or compilation unit is selected,
	 * the primary type of that unit is used. Then we search for the
	 * specifications file corresponding to that type. Executed entirely in the
	 * UI thread.
	 * 
	 * @param selection
	 *            the selection (multiple items may be selected)
	 * @param window
	 *            the current window
	 * @param shell
	 *            the current shell
	 */
	public void openSpecEditorForSelection(ISelection selection,
			@Nullable IWorkbenchWindow window, Shell shell) {
		// Note that we could get the specs file through IAPI. However, that
		// requires that the project be type-checked. Here we duplicate the
		// logic for looking up specification files, which is not good, but
		// there is less dependence on what is already typechecked, which is
		// good.
		ITextSelection textSelection = getSelectedText(selection);
		List<Object> list = new LinkedList<Object>();
		String text;
		if (textSelection != null && window != null
				&& (text = textSelection.getText()).length() != 0) {
			// We have some selected text - try to interpret it as a class name
			if (Utils.verboseness >= Utils.VERBOSE)
				Log.log("Selected text: " + text); //$NON-NLS-1$
			// Get the string of text, with .class at the end
			String classname = "/" + text.replace('.', '/') + ".class"; //$NON-NLS-1$ //$NON-NLS-2$
			// Get the Java project corresponding to the file the text is in
			IEditorPart p = window.getActivePage().getActiveEditor();
			IEditorInput e = p == null ? null : p.getEditorInput();
			IFile o = e == null ? null : (IFile) e.getAdapter(IFile.class);
			IJavaProject jp = o == null ? null : JavaCore.create(o)
					.getJavaProject();
			List<IType> matches = new LinkedList<IType>();

			if (jp == null) {
				showMessageInUI(shell,
						Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
						"Could not determine the containing Java project");
				return;
			}
			try {
				// FIXME - check that nested and secondary types work and
				// resolve eventually to the primary type
				// First try fully-qualified names
				IType t = jp.findType(text, (IProgressMonitor) null);
				if (t != null) {
					matches.add(t);
				} else {
					// Look for partial matches in the classpath of the project
					matches = findMatchingClassNames(jp,text);
				}
			} catch (JavaModelException ex) {
				Log.errorlog(
						"Failed to match text to a type name because of an exception",
						ex);
				// Pretend there is no match
			}
			if (matches.size() == 1) {
				list.add(matches.get(0));
			} else if (matches.size() > 1) {
				StringBuilder sb = new StringBuilder();
				sb.append("More than one match of type names to ");
				sb.append(text);
				sb.append(" with no complete match. Specify the text more completely:");
				for (IType t : matches) {
					sb.append(space);
					sb.append(t.getFullyQualifiedName());
				}
				showMessageInUI(shell,
						Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
						sb.toString());
				return;
			} else {
				showMessageInUI(shell,
						Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
						"Could not find a type that matches \"" + text + "\"");
				return;
			}
		} else {
			list = getSelectedElements(selection, window, shell);
			if (list.isEmpty()) {
				showMessageInUI(shell,
						Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
						"Nothing selected to open editors for");
				return;
			}
		}
		String kinds = emptyString;
		IResource firstEditableLocation = null;
		// FIXME - perhaps put the Dialog boxes outside the loop and accumulate all the information
		outer: for (Object o : list) {
			try {
				IType t = null;
				IType origType = null;
				if (o instanceof IType) {
					t = (IType) o;
					origType = t;
					t = t.getCompilationUnit().findPrimaryType();
				} else if (o instanceof ICompilationUnit) {
					t = ((ICompilationUnit) o).findPrimaryType();
				} else if (o instanceof IFile) {
					IJavaElement cu = JavaCore.create((IFile) o);
					if (cu instanceof ICompilationUnit)
						t = ((ICompilationUnit) cu).findPrimaryType();
				} else if (o instanceof IAdaptable) {
					ICompilationUnit cu = (ICompilationUnit) ((IAdaptable) o)
							.getAdapter(ICompilationUnit.class);
					if (cu != null)
						t = cu.findPrimaryType();
					if (t == null)
						t = (IType) ((IAdaptable) o).getAdapter(IType.class);
				}
				if (t == null) {
					kinds = kinds + o.getClass() + space;
					continue;
				}
				if (origType == null) origType = t;
				String name = t.getFullyQualifiedName().replace('.', '/');
				String pname = t.getPackageFragment().getElementName();
				pname = pname.replace('.', '/');
				String[] fullnames = new String[suffixes.length];
				for (int i = 0; i < suffixes.length; i++)
					fullnames[i] = name + suffixes[i];
				String[] absolutePaths = PathItem.getAbsolutePath(
						t.getJavaProject(), Utils.SPECSPATH_ID).split(
						File.pathSeparator);
				List<File> roots = new LinkedList<File>();
				for (String p : absolutePaths) {
					File f = new File(p);
					if (f.exists())
						roots.add(f);
					else {
						// FIXME - warn?
					}
				}
				for (String fname : fullnames) {
					for (File root : roots) {
						if (root.isDirectory()) {
							if (firstEditableLocation == null) {
								firstEditableLocation = ResourcesPlugin
										.getWorkspace()
										.getRoot()
										.getFileForLocation(
												new Path(root.getAbsolutePath()));
							}
							File ff = new File(root, fname);
							if (!ff.exists())
								continue;
							IResource r = ResourcesPlugin
									.getWorkspace()
									.getRoot()
									.getFileForLocation(
											new Path(ff.getAbsolutePath()));
							if (r == null) {
								showMessageInUI(
										shell,
										Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
										"The specifications for type "
												+ origType.getFullyQualifiedName()
												+ " are in "
												+ Env.eol
												+ ff.getAbsolutePath()
												+ Env.eol
												+ "but an editor can not be opened since the location is not in the workspace."
												+ Env.eol
												+ "Try linking to the package root from within the project.");
								continue outer;
							}
							if (r.exists() && r instanceof IFile) {
								launchJavaEditor((IFile) r);
								showMessageInUI(
										shell,
										Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
										"The specifications for type "
												+ origType.getFullyQualifiedName()
												+ " are in " + Env.eol 
												+ r.getLocation().toString());
								return;
							}
						} else {
							ZipFile jarfile = new ZipFile(root);
							ZipEntry jarentry = jarfile.getEntry(fname);
							if (jarentry != null) {
								// FIXME - this will launch duplicate editors
								InputStream is = jarfile
										.getInputStream(jarentry);
								int size = (int) jarentry.getSize();
								byte[] bytes = new byte[size];
								is.read(bytes, 0, size);
								String s = new String(bytes);
								showMessage(
										shell,
										Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
										"Specification file for "
												+ t.getFullyQualifiedName()
												+ " in " + Env.eol
												+ jarfile.getName() + Env.eol
												+ " is read-only");
								String nm = jarentry.getName();
								int k = nm.lastIndexOf('/');
								if (k >= 0)
									nm = nm.substring(k + 1);
								launchJavaEditor(s, nm);
								return;
							}
							jarfile.close();
						}
					}
				}
				showMessage(
						shell,
						Messages.OpenJMLUI_OpenSpecsEditor_DialogTitle,
						kinds.length() == 0 ? "Nothing found for which to open an editor"
								: "Cannot show specification files for "
										+ kinds);
			} catch (Exception e) {
				showExceptionInUI(shell,
						"Failure while finding specifications file", e);
				return;
			}
		}
	}

	// FIXME - add default content - document

	public void generateDefaultSpecs(PrintWriter ww, IType t) {
		StringWriter sw = new StringWriter();
		PrintWriter w = new PrintWriter(sw);
		Set<String> imports = new HashSet<String>();
		try {
			// No extending Object
			// No importing java.lang
			// No duplicate imports
			// type parameters names and bounds not handled correctly
			// Need methods and fields and nested classes
			// Need secondary types
			printClass(w, t, imports);
		} catch (JavaModelException e) {
			w.println("<Error in generating default content>");
		}
		w.close();
		ww.println("package " + t.getPackageFragment().getElementName() + ";");
		for (String s : imports) {
			ww.println("import " + s + ";");
		}
		ww.println();
		ww.println(sw.toString());
		ww.close();
	}

	// FIXME - document
	protected void printClass(PrintWriter w, IType t, Set<String> imports)
			throws JavaModelException {
		ITypeHierarchy th = t.newSupertypeHierarchy(null);
		IType sup = th.getSuperclass(t);
		if (sup.getFullyQualifiedName().equals("java.lang.Object"))
			sup = null;
		IType[] ifaces = th.getSuperInterfaces(t);
		if (sup != null)
			imports.add(sup.getPackageFragment().getElementName());
		for (IType i : ifaces)
			imports.add(i.getPackageFragment().getElementName());
		w.println();
		// FIXME - annotations
		w.print(Flags.toString(t.getFlags()));
		w.print(" class ");
		printType(w, t, imports);
		if (sup != null) {
			w.print(" extends ");
			printType(w, sup, imports);
		}
		if (ifaces.length > 0)
			w.print(" implements");
		boolean isFirst = true;
		for (IType i : ifaces) {
			if (isFirst)
				isFirst = false;
			else
				w.print(",");
			w.print(" ");
			printType(w, i, imports);
		}
		w.println(" {");
		w.println();
		// w.println("  //@ requires true;");
		// w.println("  //@ ensures true;");
		// w.println("  //@ static_initalizer;");
		// w.println();
		// w.println("  //@ requires true;");
		// w.println("  //@ ensures true;");
		// w.println("  //@ initalizer;");
		//
		// for (IMethod m : t.getMethods()) {
		// w.println();
		// w.print("    ");
		// // FIXME - annotations
		// w.print(Flags.toString(m.getFlags()));
		// w.print(" ");
		// w.print(m.getReturnType());
		// w.print(" ");
		// w.print(m.getElementName());
		// w.print("(");
		// boolean isFirst2 = true;
		// String[] pn = m.getParameterNames();
		// String[] pt = m.getParameterTypes();
		// for (int i=0; i<pn.length; i++) {
		// if (isFirst2) isFirst2 = false; else w.print(", ");
		// // FIXME _ modifierse
		// w.print(pt[i]);
		// w.print(" ");
		// w.print(pn[i]);
		// }
		// w.print(")");
		// // FIXME - exceptions
		// w.print(";");
		// }
		w.println();
		w.println("}");
	}

	// FIXME - document
	protected void printType(PrintWriter w, IType t, Set<String> imports)
			throws JavaModelException {
		w.print(t.getElementName());
		ITypeParameter[] tparams = t.getTypeParameters();
		if (tparams.length != 0) {
			w.print("<");
			boolean isFirst = true;
			for (ITypeParameter tp : tparams) {
				if (isFirst)
					isFirst = false;
				else
					w.print(",");
				w.print(tp.getElementName());
				String[] bounds = tp.getBounds();
				if (bounds.length > 0) {
					w.print(" extends");
					boolean isFirst2 = true;
					for (String s : bounds) {
						if (isFirst2)
							isFirst2 = false;
						else
							w.print(" &");
						w.print(" ");
						w.print(s);
					}
				}
			}
			w.print(">");
		}
	}

	/**
	 * This method pops up an information window to show the proof result for
	 * each selected method. Executed entirely in the UI thread.
	 * 
	 * @param selection
	 *            the selection (multiple items may be selected)
	 * @param window
	 *            the current window
	 * @param shell
	 *            the current shell
	 */
	public void showProofInfoForSelection(ISelection selection,
			@Nullable IWorkbenchWindow window, Shell shell) {
		List<Object> olist = getSelectedElements(selection, window, shell);
		List<IMethod> list = new LinkedList<IMethod>();
		for (Object o : olist) {
			if (o instanceof IMethod)
				list.add((IMethod) o);
			// Ignore anything that does not match. Other types of items can be
			// selected, particularly with a MenuAction.
		}
		if (list.isEmpty()) {
			showMessage(shell, "JML",
					"No methods were selected for the 'show proof info' operation");
		} else {
			for (IMethod m : list) {
				OpenJMLInterface jml = getInterface(m.getJavaProject());
				jml.showProofInfo(m, shell); // This puts up an appropriate
												// dialog
			}
		}
	}

	/**
	 * This method pops up an information window to show the value of an
	 * expression in the current counterexample. Uses the computational thread.
	 * 
	 * @param selection
	 *            the selection (multiple items may be selected)
	 * @param window
	 *            the current window
	 * @param shell
	 *            the current shell
	 */
	public void showCEValueForTextSelection(ISelection selection,
			@Nullable IWorkbenchWindow window, Shell shell) {
		if (!checkForDirtyEditors()) return;
		ITextSelection selectedText = getSelectedText(selection);
		if (selectedText == null) {
			showMessage(shell, "JML", "No text is selected");
			return;
		}
		int pos = selectedText.getOffset();
		int end = pos + selectedText.getLength() - 1;
		String s = selectedText.getText();
		IResource r = getSelectedResources(selection, window, shell).get(0);
		// FIXME - need to do this in another thread. ?
		String result = getInterface(JavaCore.create(r.getProject()))
				.getCEValue(pos, end, s, r);
		showMessage(shell, "Counterexample value", result);
	}

	/**
	 * This method interprets the selection returning a List of IResources or
	 * IJavaElements, and ignoring things it does not know how to handle. The
	 * selection is ignored if it is not an IStructuredSelection (e.g.
	 * ITextSelections are ignored). If the selection is empty or the resulting
	 * list is empty, and 'window' is non-null, then the routine attempts to
	 * find a resource that corresponds to the currently active editor.
	 * <UL>
	 * <LI>IJavaElement - returned in the list
	 * <LI>IResource - added directly to list, whether a file or a container
	 * <LI>working set - adds the elements of the working set if they can be
	 * converted (through IAdaptor) to an IResource
	 * <LI>otherwise - attempts to be converted to an IResource, and added to
	 * list if successful, ignored otherwise
	 * </UL>
	 * 
	 * @param selection
	 *            The selection to inspect
	 * @param window
	 *            The window in which a selected editor exists, or null
	 * @param shell
	 *            the shell to use in displaying information dialogs, or null to
	 *            use a default shell
	 * @return A List of IResource or IJavaElement
	 */
	public List<Object> getSelectedElements(@NonNull ISelection selection,
			@NonNull IWorkbenchWindow window, @Nullable Shell shell) {
		List<Object> list = new LinkedList<Object>();
		if (!selection.isEmpty() && selection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			for (Iterator<?> iter = structuredSelection.iterator(); iter
					.hasNext();) {
				Object element = iter.next();
				if (element instanceof IJavaElement) {
					list.add(element);
				} else if (element instanceof IResource) {
					// Log.log("Selected " + ((IResource)element).getName());
					list.add(element);
				} else if (element instanceof IWorkingSet) {
					for (IAdaptable a : ((IWorkingSet) element).getElements()) {
						IResource r = (IResource) a.getAdapter(IResource.class);
						if (r != null)
							list.add(r);
					}
					continue;
				} else if (element instanceof IAdaptable) {
					// TODO: curious as to just what might fall into this
					// category
					IResource r = ((IResource) ((IAdaptable) element)
							.getAdapter(IResource.class));
					if (r != null)
						list.add(r);
				}
			}
		} else {
			// We had nothing selected
			// Look for the active editor instead
			try {
				IEditorPart p = window.getActivePage().getActiveEditor();
				IEditorInput e = p == null ? null : p.getEditorInput();
				Object o = e == null ? null : e.getAdapter(ICompilationUnit.class);
				o = o != null ? o : e != null ? e.getAdapter(IFile.class) : null;
				o = o == null ? e : o;
				if (o != null) {
					// Log.log("Selected " + o);
					list.add(o);
				}
			} catch (Exception ee) {
				Log.errorlog("Exception when finding selected targets: " + ee,
						ee);
				showMessage(window.getShell(), "JML Plugin Exception",
						"Exception occurred when finding selected targets: "
								+ ee);
			}
		}
		return list;
	}

	// TODO - document
	public ITextSelection getSelectedText(@NonNull ISelection selection) {
		if (selection instanceof ITextSelection) {
			return (ITextSelection) selection;
		} else {
			return null;
		}
	}

	/**
	 * This method interprets the selection returning a List of IResources or
	 * IJavaElements, and ignoring things it does not know how to handle. The
	 * selection is ignored if it is not an IStructuredSelection (e.g.
	 * ITextSelections are ignored). If the selection is empty or the resulting
	 * list is empty, and 'window' is non-null, then the routine attempts to
	 * find a resource that corresponds to the currently active editor.
	 * <UL>
	 * <LI>IJavaElement - adds the containing java project
	 * <LI>IResource - adds the containing project, as a Java project, if it is
	 * one
	 * <LI>working set - adds the elements of the working set if they are Java
	 * projects
	 * <LI>otherwise - attempts to be converted to a IJavaProject or an
	 * IResource, and added to list if successful, ignored otherwise
	 * </UL>
	 * 
	 * @param convert
	 *            if false, then returned elements were precisely Java Projects
	 *            in the selection; if true, the returned projects may also be
	 *            the containing projects of selected non-project elements
	 * @param selection
	 *            The selection to inspect
	 * @param window
	 *            The window in which a selected editor exists, or null
	 * @param shell
	 *            the shell to use in displaying information dialogs, or null to
	 *            use a default shell
	 * @return A List of IResource or IJavaElement
	 */
	public Collection<IJavaProject> getSelectedProjects(boolean convert,
			@NonNull ISelection selection, @NonNull IWorkbenchWindow window,
			@Nullable Shell shell) {
		Set<IJavaProject> list = new HashSet<IJavaProject>();
		if (!selection.isEmpty()) {
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection = (IStructuredSelection) selection;
				for (Iterator<?> iter = structuredSelection.iterator(); iter
						.hasNext();) {
					Object element = iter.next();
					if (!convert) {
						if (element instanceof IJavaProject)
							list.add((IJavaProject) element);
					} else if (element instanceof IJavaElement) {
						list.add(((IJavaElement) element).getJavaProject());
					} else if (element instanceof IResource) {
						IJavaProject jp = JavaCore.create(((IResource) element)
								.getProject());
						if (jp != null)
							list.add(jp);
					} else if (element instanceof IWorkingSet) {
						for (IAdaptable a : ((IWorkingSet) element)
								.getElements()) {
							// IJavaProject jp =
							// JavaCore.create(((IResource)element).getProject());
							IJavaProject jp = (IJavaProject) a
									.getAdapter(IJavaProject.class);
							if (jp != null)
								list.add(jp);
						}
						continue;
					} else if (element instanceof IAdaptable) {
						// TODO: curious as to just what might fall into this
						// category
						IJavaProject jp = (IJavaProject) ((IAdaptable) element)
								.getAdapter(IJavaProject.class);
						if (jp != null)
							list.add(jp);
						else {
							IResource r = ((IResource) ((IAdaptable) element)
									.getAdapter(IResource.class));
							jp = JavaCore.create(((IResource) element)
									.getProject());
							if (r != null)
								list.add(jp);
						}
					}
				}
				// } else {
				// showMessage(shell,"Unknown selection",selection.getClass().toString());
			}
		}
		if (convert && list.size() == 0) {
			// We had nothing selected or it was not a structured selection
			// Look for the active editor instead
			try {
				IEditorPart p = window.getActivePage().getActiveEditor();
				IEditorInput e = p == null ? null : p.getEditorInput();
				IFile o = e == null ? null : (IFile) e.getAdapter(IFile.class);
				if (o != null) {
					IJavaProject jp = JavaCore.create(o.getProject());
					if (jp != null)
						list.add(jp);
				}
			} catch (Exception ee) {
				Log.errorlog("Exception when finding selected targets: " + ee,
						ee);
				showMessage(window.getShell(), Messages.OpenJMLUI_ExceptionTitle,
						"Exception occurred when finding selected targets: "
								+ ee);
			}
		}
		return list;
	}

	/**
	 * This method interprets the selection returning a List of IResources, and
	 * ignoring things it does not know how to handle; note that the resources
	 * in the returned list are not necessarily Java files. The selection is
	 * ignored if it is not an IStructuredSelection (e.g. ITextSelections are
	 * ignored). If the selection is empty and 'window' is non-null, then the
	 * routine attempts to find a resource that corresponds to the currently
	 * active editor.
	 * <UL>
	 * <LI>IResource - added directly to list, whether a file or a container
	 * <LI>working set - adds the elements of the working set if they can be
	 * converted (through IAdaptor) to an IResource
	 * <LI>IJavaElement - adds the IResource that contains the element
	 * <LI>otherwise - ignored
	 * </UL>
	 * 
	 * @param selection
	 *            The selection to inspect
	 * @param window
	 *            The window in which a selected editor exists
	 * @param shell
	 *            the shell to use in displaying information dialogs
	 * @return A List of IResources found in the selection
	 */
	public List<IResource> getSelectedResources(@NonNull ISelection selection,
			@Nullable IWorkbenchWindow window, @Nullable Shell shell) {
		List<IResource> list = new LinkedList<IResource>();
		if (!selection.isEmpty() && selection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			for (Iterator<?> iter = structuredSelection.iterator(); iter
					.hasNext();) {
				Object element = iter.next();
				if (element instanceof IResource) {
					list.add((IResource) element);
				} else if (element instanceof IWorkingSet) {
					for (IAdaptable a : ((IWorkingSet) element).getElements()) {
						IResource r = (IResource) a.getAdapter(IResource.class);
						if (r != null)
							list.add(r);
					}
					continue;
				} else if (element instanceof IJavaElement) {
					// try {
					IResource r = ((IJavaElement) element).getResource();
					if (r != null)
						list.add(r);
					else if (element instanceof IAdaptable
							&& (r = (IResource) ((IAdaptable) element)
									.getAdapter(IResource.class)) != null) {
						list.add(r);
					} else {
						if (Utils.verboseness >= Utils.NORMAL)
							Log.log("No resource for "
									+ ((IJavaElement) element).getElementName());
					}
				}
			}
		} else {
			// We had nothing selected
			// Look for the active editor instead
			try {
				IEditorPart p = window.getActivePage().getActiveEditor();
				IEditorInput e = p == null ? null : p.getEditorInput();
				IResource o = e == null ? null : (IFile) e
						.getAdapter(IFile.class);
				if (o != null) {
					// Log.log("Selected " + o);
					list.add(o); // This is an IFile
				}
			} catch (Exception ee) {
				Log.errorlog("Exception when finding selected targets: " + ee,
						ee);
				showMessage(shell, "JML Plugin Exception",
						"Exception occurred when finding selected targets: "
								+ ee);
			}
		}
		return list;
	}

	/**
	 * Alters whether the JML nature is enabled or disabled for the given
	 * selected objects. The operation makes sense only for IJavaProject
	 * objects; if other types of objects are selected, the enclosing
	 * IJavaProject is used; if there is none, the selected object is ignored.
	 * The operation is performed entirely in the UI thread (and should be
	 * called from the UI thread).
	 * 
	 * @param enable
	 *            if true, the JML nature is enabled; if false, it is disabled
	 * @param selection
	 *            the objects selected in the UI
	 * @param window
	 *            the current window
	 * @param shell
	 *            the current shell (for any dialogs)
	 */
	public void changeJmlNatureSelection(boolean enable, ISelection selection,
			IWorkbenchWindow window, Shell shell) {
		Collection<IJavaProject> list = Activator.getDefault().utils
				.getSelectedProjects(true, selection, window, shell);
		Iterator<IJavaProject> i = list.iterator();
		if (!i.hasNext()) {
			Utils.showMessage(shell, "JML Plugin", "No Java projects selected");
			return;
		}
		int maxdialogs = 5;
		while (i.hasNext()) {
			IJavaProject p = i.next();
			try {
				if (enable)
					JMLNature.enableJMLNature(p.getProject());
				else
					JMLNature.disableJMLNature(p.getProject());
				PlatformUI.getWorkbench().getDecoratorManager()
						.update(PopupActions.JML_DECORATOR_ID);
			} catch (Exception e) {
				if (window != null && (maxdialogs--) > 0) {
					Utils.showMessage(
							shell,
							"JML Plugin Exception",
							"Exception while "
									+ (enable ? "enabling" : "disabling")
									+ " JML "
									+ (p != null ? "on " + p.getElementName()
											: "") + e.toString());
				}
				Log.errorlog(
						"Failed to "
								+ (enable ? "enable" : "disable")
								+ " JML nature"
								+ (p != null ? " on project "
										+ p.getElementName() : ""), e);
			}
		}
		if (Utils.verboseness >= Utils.NORMAL)
			Log.log("Completed JML Nature operation ");
	}

	// Do this right here in the UI thread
	// FIXME - should resource things be happening in another thread?
	/**
	 * Deletes all JML markers from the items selected, right within the UI
	 * thread, without a progress dialog. The resources for which markers are
	 * deleted are those returned by Utils.getSelectedResources. This should be
	 * called from the UI thread.
	 * 
	 * @param selection
	 *            the IStructuredSelection whose markers are to be deleted
	 * @param window
	 *            the current workbench window, or null (used in
	 *            getSelectedResources)
	 * @param shell
	 *            the current Shell, or null for the default shell (for message
	 *            dialogs)
	 */
	public void deleteMarkersInSelection(ISelection selection,
			IWorkbenchWindow window, Shell shell) {
		List<IResource> list = getSelectedResources(selection, window, shell);
		if (list.isEmpty()) {
			showMessage(shell, "JML Plugin",
					"Nothing appropriate to delete markers of");
			return;
		}
		deleteMarkers(list, shell);
		return;
	}
	
	public @Nullable PathItem toPathItem(IJavaProject jp, Object r) {
		IResource f;
		if (r instanceof IPackageFragmentRoot) {
			IPackageFragmentRoot pfr = (IPackageFragmentRoot) r;
			f = pfr.getResource();
		} else if (r instanceof IFile
				&& "jar".equals(((IFile) r).getFileExtension())) {
			f = (IFile) r;
		} else if (!(r instanceof IFolder)) {
			return null;
		} else {
			f = (IFolder) r;
		}
		PathItem p;
		if (f.getProject().equals(jp.getProject())) {
			// Same project - use a project relative path
			p = new PathItem.ProjectPath(f.getProjectRelativePath()
					.toString());
		} else {
			p = new PathItem.WorkspacePath(f.getFullPath().toString());
		}
		return p;
	}
	
	// FIXME - add/remove - a source folder that is in the ALL_SOURCE_FOLDERs
	// is not recognized as already present

	/**
	 * Expects the selection to hold exactly one Java project, plus one or more
	 * folders or jar files; those folders and jar files are added to the
	 * beginning of the specs path for the given project.
	 * 
	 * @param selection the current selection in the UI
	 * @param window    the currently active window
	 * @param shell     the current shell (for dialogs)
	 */
	public void addSelectionToSpecsPath(ISelection selection,
			IWorkbenchWindow window, @Nullable Shell shell) {
		Collection<IJavaProject> projects = getSelectedProjects(false,
				selection, window, shell);
		if (projects.size() != 1) {
			showMessage(shell, "OpenJML - Add to Specs Path",
					"Select exactly one Java Project along with the desired folders");
			return;
		}
		IJavaProject jp = projects.iterator().next();
		List<Object> list = getSelectedElements(selection, window, shell);
		String notadded = emptyString;
		boolean added = false;
		for (Object r : list) {
			if (r instanceof IJavaProject || r instanceof IProject) continue;
			PathItem p = toPathItem(jp,r);
			if (p == null) {
				if (r instanceof IResource) notadded = notadded + ((IResource) r).getName() + space;
				continue;
			}
			try {
				if (PathItem.add(jp, PathItem.specsKey, p)) {
					added = true;
				} else {
					notadded = notadded + space + p.display();
				}
			} catch (CoreException e) {
				showMessage(shell, "OpenJML - Remove from Specs Path",
						"Exception on reading or writing persistent property: "
								+ e);
			}
		}
		if (notadded.length() != 0) {
			showMessage(shell, "JML - Add to Specs Path",
					"These were already present and not added:" + notadded);
		} else if (!added) {
			showMessage(shell, "JML - Add to Specs Path", "Nothing was added");
		}
	}

	/**
	 * Expects the selection to hold exactly one Java project, plus one or more
	 * folders or jar files; those folders and jar files are removed from the
	 * the specs path of the given project.
	 * 
	 * @param selection
	 *            the current selection in the UI
	 * @param window
	 *            the currently active window
	 * @param shell
	 *            the current shell (for dialogs)
	 */
	public void removeSelectionFromSpecsPath(ISelection selection,
			@Nullable IWorkbenchWindow window, @Nullable Shell shell) {
		Collection<IJavaProject> projects = getSelectedProjects(false,
				selection, window, shell);
		if (projects.size() != 1) {
			showMessage(shell, "JML - Remove from Specs Path",
					"Select exactly one Java Project along with the desired folders");
			return;
		}
		IJavaProject jp = projects.iterator().next();
		List<Object> list = getSelectedElements(selection, window, shell);
		String notremoved = emptyString;
		for (Object r : list) {
			IResource f;
			String n;
			if (r instanceof IJavaProject || r instanceof IProject) continue;
			PathItem p = toPathItem(jp,r);
			try {
				if (p == null) {
					notremoved = notremoved + r + space;
				} else if (!PathItem.remove(jp, PathItem.specsKey, p)) {
					notremoved = notremoved + p.display() + space;
				}
			} catch (CoreException e) {
				showMessage(shell, "OpenJML - Remove from Specs Path",
						"Exception on reading or writing persistent property: "
								+ e);
			}
		}
		if (notremoved.length() != 0) {
			showMessage(shell, "OpenJML - Remove from Specs Path",
					"These were not found: " + notremoved);
		}
	}

	/** Puts up dialogs to edit each the paths of each selected Java project. */
	public void manipulateSpecsPath(ISelection selection,
			IWorkbenchWindow window, Shell shell) {
		Collection<IJavaProject> projects = getSelectedProjects(true,
				selection, window, shell);
		if (projects.isEmpty()) {
			showMessageInUI(shell,"OpenJML Paths Editor",
					"No projects selected");
			return;
		}
		final Shell finalShell = shell;
		for (IJavaProject jproject : projects) {
			final IJavaProject jp = jproject;
			// FIXME - none of these implementations lets the dialogs come up
			// simultaneously
			// Even if we inherit PathsEditor from ModelessDialog

			// Display d = finalShell == null ? Display.getDefault() :
			// finalShell.getDisplay();
			// d.asyncExec(new Runnable() {
			// public void run() {
			// Dialog dialog = new PathsEditor(finalShell,
			// "OpenJML Paths Editor - " + jp.getElementName(), jp);
			// dialog.create();
			// dialog.open(); // OK actions are handled in the dialog
			// }
			// });

			try {
				jproject.getProject().getWorkspace()
						.run(new IWorkspaceRunnable() {
							public void run(IProgressMonitor monitor) {
								Dialog dialog = new PathsEditor(finalShell,
										"OpenJML Paths Editor - "
												+ jp.getElementName(), jp);
								dialog.create();
								dialog.open(); // OK actions are handled in the
												// dialog
							}
						}, null);
			} catch (CoreException e) {
				showExceptionInUI(shell, "Failure while editing paths", e);
			}

			// Dialog dialog = new PathsEditor(finalShell,
			// "OpenJML Paths Editor - " + jp.getElementName(), jp);
			// dialog.create();
			// dialog.open(); // OK actions are handled in the dialog

		}
	}

	/**
	 * Shows the classpath for selected projects. SHould be called from the UI
	 * thread; is executed entirely in the calling thread.
	 * 
	 * @param selection
	 *            the current selection in the UI
	 * @param window
	 *            the currently active window
	 * @param shell
	 *            the currently active shell (or null for default)
	 */
	public void showPaths(ISelection selection, IWorkbenchWindow window,
			Shell shell) {
		Collection<IJavaProject> projects = getSelectedProjects(true,
				selection, window, shell);
		if (projects.isEmpty()) {
			showMessage(shell, "Show JML Paths", "No projects selected");
		}
		for (IJavaProject jp : projects) {
			List<String> list = getClasspath(jp);
			StringBuilder ss = new StringBuilder();
			ss.append("Classpath:");
			ss.append(Env.eol);
			for (String s : list) {
				ss.append(s);
				ss.append(Env.eol);
			}
			ss.append(Env.eol);
			// source path
			ss.append("Source path:");
			ss.append(Env.eol);
			String sp = PathItem.getAbsolutePath(jp, PathItem.sourceKey);
			sp = sp.replace(File.pathSeparator, Env.eol);
			ss.append(sp);
			ss.append(Env.eol);
			ss.append(Env.eol);
			// specs path
			ss.append("Specs path:");
			ss.append(Env.eol);
			sp = PathItem.getAbsolutePath(jp, PathItem.specsKey);
			sp = sp.replace(File.pathSeparator, Env.eol);
			ss.append(sp);
			ss.append(Env.eol);

			showMessage(shell, "JML paths for project " + jp.getElementName(),
					"Edit the paths in the JML preferences or use the"
							+ Env.eol
							+ "Add to/Remove from JML Specs Path menu items"
							+ Env.eol + Env.eol + ss.toString());
		}
	}

	private boolean changingClasspath = false;

	/**
	 * Adds a Library classpath entry holding the internal run-time library to
	 * the end of the given project's classpath, if the library is not already
	 * on the classpath. This will trigger a build, if auto-building is turned
	 * on.
	 * 
	 * @param jproject
	 *            the project whose classpath is to be adjusted
	 */
	public IClasspathEntry addRuntimeToProjectClasspath(final IJavaProject jproject) {
		if (changingClasspath)
			return null;
		try {
			String runtime = findInternalRuntime();
			if (runtime == null) {
				if (Utils.verboseness >= Utils.DEBUG)
					Log.log("No internal runtime found");
				return null;
			}
			IPath path = new Path(runtime);
			IClasspathEntry libentry = JavaCore.newLibraryEntry(path, null,
					null);

			IClasspathEntry[] entries = jproject.getResolvedClasspath(true);
			for (IClasspathEntry i : entries) {
				if (i.getEntryKind() == IClasspathEntry.CPE_LIBRARY
						&& i.equals(libentry)) {
					if (Utils.verboseness >= Utils.DEBUG)
						Log.log("Internal runtime already on classpath: "
								+ runtime);
					return i;
				}
			}
			final IClasspathEntry[] newentries = new IClasspathEntry[entries.length + 1];
			System.arraycopy(entries, 0, newentries, 0, entries.length);
			newentries[entries.length] = libentry;
			try {
				changingClasspath = true;
				if (Utils.verboseness >= Utils.DEBUG)
					Log.log("Internal runtime being added to classpath: "
							+ runtime);

				try {
					jproject.getProject().getWorkspace()
							.run(new IWorkspaceRunnable() {
								public void run(IProgressMonitor monitor) {
									try {
										jproject.setRawClasspath(newentries,
												monitor);
									} catch (Exception e) {
										showMessageInUI(null, "Error Dialog",
												"Exception while changing classpath: "
														+ e);
									}
								}
							}, null);
				} catch (CoreException e) {
					Log.errorlog("Core Exception while changing classpath", e);
					// just continue
				}
			} finally {
				changingClasspath = false;
			}
			return libentry;
		} catch (JavaModelException e) {
			throw new Utils.OpenJMLException(
					"Failed in adding internal runtime library to classpath: "
							+ e.getMessage(), e);
		}
	}
	
	protected void removeFromClasspath(final IJavaProject jproject, IClasspathEntry entry) {
		if (changingClasspath)
			return;
		try {
			if (entry == null) {
				String runtime = findInternalRuntime();
				if (runtime == null) {
					if (Utils.verboseness >= Utils.DEBUG)
						Log.log("No internal runtime found");
					return;
				}
				IPath path = new Path(runtime);
				entry = JavaCore.newLibraryEntry(path, null,
						null);
			}
			IClasspathEntry[] entries = jproject.getResolvedClasspath(true);
			final IClasspathEntry[] newentries = new IClasspathEntry[entries.length];
			int j = 0;
			for (IClasspathEntry i : entries) {
				if (!i.equals(entry)) {
					newentries[j++] = i;
				}
			}
			if (j < entries.length) {
				final IClasspathEntry[] newerentries = new IClasspathEntry[j];
				System.arraycopy(newentries, 0, newerentries, 0, j);
				try {
					changingClasspath = true;
					if (Utils.verboseness >= Utils.DEBUG)
						Log.log("Internal runtime being removed from classpath: "
								+ entry);

					try {
						jproject.getProject().getWorkspace()
						.run(new IWorkspaceRunnable() {
							public void run(IProgressMonitor monitor) {
								try {
									jproject.setRawClasspath(newerentries,
											monitor);
								} catch (Exception e) {
									showMessageInUI(null, "Error Dialog",
											"Exception while changing classpath: "
													+ e);
								}
							}
						}, null);
					} catch (CoreException e) {
						Log.errorlog("Core Exception while changing classpath", e);
						// just continue
					}
				} finally {
					changingClasspath = false;
				}
			}
			return ;
		} catch (JavaModelException e) {
			throw new Utils.OpenJMLException(
					"Failed in removing internal runtime library from classpath: "
							+ e.getMessage(), e);
		}
	}

	/**
	 * Gets the classpath of the given project, interpreting all Eclipse entries
	 * and converting them into file system paths to directories or jars.
	 * 
	 * @param jproject
	 *            the Java project whose class path is wanted
	 * @return a List of Strings giving the paths to the files and directories
	 *         on the class path
	 */
	@NonNull
	protected List<String> getClasspath(@NonNull IJavaProject jproject) {
		return getClasspath(jproject, false);
	}

	/**
	 * Gets the classpath of the given project, interpreting all Eclipse entries
	 * and converting them into file system paths to directories or jars.
	 * 
	 * @param jproject
	 *            the Java project whose class path is wanted
	 * @param onlyExported
	 *            if true, only classpath entries that are marked as exported
	 *            are added to the output list
	 * @return a List of Strings giving the paths to the files and directories
	 *         on the class path
	 */
	@NonNull
	protected List<String> getClasspath(@NonNull IJavaProject jproject,
			boolean onlyExported) {
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			IClasspathEntry[] entries = jproject.getResolvedClasspath(true);
			List<String> cpes = new LinkedList<String>();
			for (IClasspathEntry i : entries) {
				if (onlyExported && !i.isExported())
					continue;
				// Log.log("ENTRY " + i);
				switch (i.getEntryKind()) {
				case IClasspathEntry.CPE_CONTAINER:
				case IClasspathEntry.CPE_SOURCE:
					IResource p = root.getFolder(i.getPath());
					String s = p.getLocation().toString();
					cpes.add(s);
					break;
				case IClasspathEntry.CPE_LIBRARY:
					IFile f = root.getFile(i.getPath());
					if (f == null || f.getLocation() == null) {
						cpes.add(i.getPath().toString());
					} else {
						cpes.add(f.getLocation().toString());
					}
					break;
				case IClasspathEntry.CPE_PROJECT:
					// FIXME - this has not been tested - pay attention to
					// isExported?
					IProject proj = (IProject) root.getProject(i.getPath()
							.toString());
					List<String> plist = getClasspath(JavaCore.create(proj),
							true);
					cpes.addAll(plist);
					break;
				case IClasspathEntry.CPE_VARIABLE:
					// Variables and containers are already resolved
				default:
					Log.errorlog(
							"An unexpected kind of ClassPathEntry was ignored (project "
									+ jproject.getElementName() + "): " + i,
							null);
					break;
				}
			}
			// Bundle b = Platform.getBundle("org.jmlspecs.OpenJMLUI");
			// URL url = b.getEntry("");
			// URI uri = url.toURI();
			// String s = uri.getPath();
			// String ss = url.toExternalForm(); // FIXME - should this be used?
			// We are trying to include the contents of OpenJMLUI on the
			// classpath
			// Why? Don't we already have annotations, specs, and the runtime
			// library? FIXME
			// This just ends up as a /
			// cpes.add(s);
			return cpes;
			// } catch (URISyntaxException e) {
			// throw new
			// Utils.OpenJMLException("Failed in determining classpath",e);
		} catch (JavaModelException e) {
			throw new Utils.OpenJMLException("Failed in determining classpath",
					e);
		}
	}

	/**
	 * This class is an implementation of the interfaces needed to provide input
	 * to and launch editors in the workspace.
	 * 
	 * @author David R. Cok
	 */
	public static class StringStorage implements IStorage, IStorageEditorInput {
		/** The initial content of the editor */
		private @NonNull
		String content;
		/** The name of storage unit (e.g. the file name) */
		private @NonNull
		String name;

		/** A constructor for a new storage unit */
		// @ assignable this.*;
		public StringStorage(@NonNull String content, @NonNull String name) {
			this.content = content;
			this.name = name;
		}

		/** Interface method that returns the contents of the storage unit */
		@Override
		public InputStream getContents() throws CoreException {
			return new ByteArrayInputStream(content.getBytes());
		}

		/**
		 * Returns the path to the underlying resource
		 * 
		 * @return null (not needed for readonly Strings)
		 */
		@Override
		public IPath getFullPath() {
			return null;
		}

		/**
		 * Returns the name of the storage object
		 * 
		 * @return the name of the storage unit
		 */
		@Override
		@Query
		public @NonNull
		String getName() {
			return name;
		}

		/**
		 * Returns whether the storage object is read only
		 * 
		 * @return always true
		 */
		@Override
		public boolean isReadOnly() {
			return true;
		}

		/**
		 * Returns the object adapted to the given class. It appears we can
		 * ignore this and always return null.
		 * 
		 * @return null
		 */
		@Override
		@SuppressWarnings("rawtypes")
		// Can't add type parameters because the parent interface does not have
		// them
		public @Nullable
		Object getAdapter(@NonNull Class arg0) {
			return null;
		}

		/**
		 * Returns self
		 * 
		 * @return this object
		 */
		// @ ensures \return == this;
		@Override
		public IStorage getStorage() throws CoreException {
			return (IStorage) this;
		}

		/**
		 * Returns whether the underlying storage object exists
		 * 
		 * @return always true
		 */
		@Override
		public boolean exists() {
			return true;
		}

		/**
		 * Returns an ImageDescriptor, here ignored
		 * 
		 * @return always null
		 */
		@Override
		public ImageDescriptor getImageDescriptor() {
			return null;
		}

		/**
		 * Returns a corresponding Persistable object, here ignored
		 * 
		 * @return always null
		 */
		@Override
		public IPersistableElement getPersistable() {
			return null;
		}

		/**
		 * Return the text desired in a tool tip, here the name of the storage
		 * unit
		 */
		@NonNull
		@Override
		public String getToolTipText() {
			return name;
		}

	}

	/**
	 * Launches a read-only text editor with the given content and name
	 * 
	 * @param content
	 *            the content of the editor
	 * @param name
	 *            the name (as in the title) of the editor
	 */
	public void launchEditor(String content, String name) {
		try {
			IEditorInput editorInput = new StringStorage(content, name);
			IWorkbenchWindow window = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow();
			IWorkbenchPage page = window.getActivePage();
			page.openEditor(editorInput, "org.eclipse.ui.DefaultTextEditor");
		} catch (Exception e) {
			showExceptionInUI(null, "Failure while launching an editor", e);
		}
	}

	/**
	 * Launches a read-only text editor with the given content and name
	 * 
	 * @param content
	 *            the content of the editor
	 * @param name
	 *            the name (as in the title) of the editor
	 */
	public void launchJavaEditor(String content, String name) {
		try {
			IEditorInput editorInput = new StringStorage(content, name);
			IWorkbenchWindow window = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow();
			IWorkbenchPage page = window.getActivePage();
			page.openEditor(editorInput, org.eclipse.jdt.ui.JavaUI.ID_CU_EDITOR);
		} catch (Exception e) {
			showExceptionInUI(null, "Failure while launching an editor", e);
		}
	}

	/**
	 * Launches a editable Java editor with the given file
	 * 
	 * @param file
	 *            the file to edit
	 */
	public void launchJavaEditor(IFile file) {
		try {
			IFileEditorInput editorInput = new FileEditorInput(file);
			IWorkbenchWindow window = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow();
			IWorkbenchPage page = window.getActivePage();
			page.openEditor(editorInput, org.eclipse.jdt.ui.JavaUI.ID_CU_EDITOR);
		} catch (Exception e) {
			showExceptionInUI(null, "Failure while launching an editor", e);
		}
	}

	// public void launchJavaEditor(File file) {
	// try {
	// IFileStore fileStore = EFS.getLocalFileSystem().getStore();//new
	// Path(filterPath));
	// fileStore = fileStore.getChild(file.getAbsolutePath());
	// IFileEditorInput editorInput = new FileEditorInput(file);
	// IWorkbenchWindow window =
	// PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	// IWorkbenchPage page = window.getActivePage();
	// page.openEditor(editorInput, org.eclipse.jdt.ui.JavaUI.ID_CU_EDITOR );
	// } catch (Exception e) {
	// showExceptionInUI(null,"Failure while launching an editor", e);
	// }
	// }

	/**
	 * Deletes the markers in any of the objects in the List that are IResource
	 * objects; if the object is a container, markers are deleted for any
	 * resources in the container; other kinds of objects are ignored.
	 * 
	 * @param <T>
	 *            just the type of the list
	 * @param list
	 *            a list of objects whose markers are to be deleted
	 * @param shell
	 *            the current shell for dialogs (or null for default)
	 */
	public <T> void deleteMarkers(List<T> list, @Nullable Shell shell) {
		int maxdialogs = 5;
		for (T t : list) {
			if (!(t instanceof IResource))
				continue;
			IResource resource = (IResource) t;
			try {
				deleteMarkers(resource, shell);
			} catch (Exception e) {
				Log.errorlog("Exception while deleting markers: " + e, e);
				if ((maxdialogs--) > 0) {
					showMessage(
							shell,
							"JML Plugin Exception",
							"Exception while deleting markers "
									+ (resource != null ? "on "
											+ resource.getName() : "")
									+ e.toString());
				}
			}
		}
	}

	/**
	 * Deletes the markers (and highlighting) in the given resource; if the
	 * object is a container, markers are deleted for any resources in the
	 * container; other kinds of objects are ignored.
	 * 
	 * @param resource
	 *            the resource whose markers are to be deleted
	 * @param shell
	 *            the current shell for dialogs (or null for default)
	 */
	public void deleteMarkers(IResource resource, @Nullable Shell shell) {
		try {
			if (Utils.verboseness >= Utils.VERBOSE)
				Log.log("Deleting markers in " + resource.getName());
			resource.deleteMarkers(JML_MARKER_ID, false,
					IResource.DEPTH_INFINITE);
			resource.deleteMarkers(ESC_MARKER_ID, false,
					IResource.DEPTH_INFINITE);
			resource.deleteMarkers(JML_HIGHLIGHT_ID, false,
					IResource.DEPTH_INFINITE);
			resource.deleteMarkers(JML_HIGHLIGHT_ID_TRUE, false,
					IResource.DEPTH_INFINITE);
			resource.deleteMarkers(JML_HIGHLIGHT_ID_FALSE, false,
					IResource.DEPTH_INFINITE);
			resource.deleteMarkers(JML_HIGHLIGHT_ID_EXCEPTION, false,
					IResource.DEPTH_INFINITE);
		} catch (CoreException e) {
			String msg = "Failed to delete markers on " + resource.getProject();
			Log.errorlog(msg, e);
			Utils.showMessage(shell, "JML Plugin Exception", msg + " - " + e);
		}
	}

	// TODO _ needs more documentation

//	public Map<IJavaProject, Set<IResource>> enabledMaps = new HashMap<IJavaProject, Set<IResource>>();
//
//	public Set<IResource> getSet(IJavaProject jp) {
//		Set<IResource> set = enabledMaps.get(jp);
//		if (set == null) {
//			enabledMaps.put(jp, set = new HashSet<IResource>());
//		}
//		return set;
//	}
	
	Set<IResource> getRacFiles(IJavaProject jp) {
		String encoded = PathItem.getEncodedPath(jp,PathItem.racKey);
		Set<IResource> items = new HashSet<IResource>();
		for (PathItem item: PathItem.parseAll(encoded)) {
			if (item instanceof ProjectPath) {
				IResource r = jp.getProject().findMember(((ProjectPath)item).projectRelativeLocation);
				items.add(r);
			}
		}
		return items;
	}

	void setRacFiles(IJavaProject jp, Set<IResource> items) {
		List<PathItem> paths = new LinkedList<PathItem>();
		for (IResource r: items) {
			paths.add(new ProjectPath(r.getProjectRelativePath().toString()));
		}
		String encoded = PathItem.concat(paths);
		PathItem.setEncodedPath(jp,PathItem.racKey,encoded);
	}

	public void racMark(boolean enable, ISelection selection,
			IWorkbenchWindow window, @Nullable Shell shell) {
		List<IResource> res = getSelectedResources(selection, window, shell);
		final Map<IJavaProject, List<IResource>> sorted = sortByProject(res);
		for (final IJavaProject jp : sorted.keySet()) {
			Set<IResource> items = getRacFiles(jp);
			for (IResource r : sorted.get(jp)) {
				mark(enable, r, items);
			}
			setRacFiles(jp,items);
		}
	}

	protected void mark(boolean enable, IResource r, Set<IResource> set) {
		try {
			if (r instanceof IFolder) {
				if (enable)
					set.add(r);
				else {
					set.remove(r);
					IPath p = new Path(getRacDir()).append(r.getProjectRelativePath());
					p = p.removeFileExtension().addFileExtension(".class");
					r.getProject().findMember(p).delete(true,null);
				}
//				for (IResource rr : ((IFolder) r).members()) {
//					mark(enable, rr, set);
//				}
			} else if (r instanceof IFile) {
				if (r.getName().endsWith(Strings.javaSuffix)) {
					if (enable)
						set.add(r);
					else {
						set.remove(r);
						IPath p = r.getProject().getProjectRelativePath().append(getRacDir()).append(r.getProjectRelativePath().removeFirstSegments(1));
						p = p.removeFileExtension().addFileExtension("class");
						IProject pr = r.getProject();
						IResource rr = pr.findMember(p);
						if (rr != null) rr.delete(true,null);
					}
				}
			} else {
				// FIXME - needs an error message
				Log.log("Not handling " + r.getClass());
			}
		} catch (CoreException e) {
			Log.errorlog(
					"Core Exception while traversing Resource tree (mark for RAC)",
					e);
			// just continue
		}
	}

	public void highlight(final IResource r, final int finalOffset,
			final int finalEnd, final int type) {
		IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				String name = type == IProverResult.Span.NORMAL ? ".JMLHighlight"
						: type == IProverResult.Span.TRUE ? ".JMLHighlightTrue"
								: type == IProverResult.Span.FALSE ? ".JMLHighlightFalse"
										: type == IProverResult.Span.EXCEPTION ? ".JMLHighlightException"
												: ".JMLHighlight";
				IMarker marker = r.createMarker(Activator.PLUGIN_ID + name);
				// marker.setAttribute(IMarker.LINE_NUMBER,
				// finalLineNumber >= 1? finalLineNumber : 1);
				// if (column >= 0) {
				marker.setAttribute(IMarker.CHAR_START, finalOffset);
				marker.setAttribute(IMarker.CHAR_END, finalEnd);
				// }
				// Note - it appears that CHAR_START is measured from the
				// beginning of the
				// file and overrides the line number when drawing the squiggly
				// The line number is used in the information about the problem
				// in
				// the Problem View

				// marker.setAttribute(IMarker.SEVERITY,b == null ? 0 : b ? 2 :
				// 1);
				// marker.setAttribute(IMarker.MESSAGE, finalErrorMessage);
			}
		};
		try {
			r.getWorkspace().run(runnable, null);
		} catch (CoreException e) {
			Log.errorlog("Core Exception while highlighting", e);
			// just continue
		}

	}

	public void racClear(ISelection selection, IWorkbenchWindow window,
			final @Nullable Shell shell) {
		Collection<IJavaProject> res = getSelectedProjects(true,selection, window, shell);

		for (final IJavaProject jp : res) {
			Job j = new Job("JML Clear RAC") {
				public IStatus run(IProgressMonitor monitor) {
					return racClear(jp,shell,monitor);
				}
			};
			// FIXME - use some proper scheduling rule?
			j.setRule(jp.getProject());
			j.setUser(true);
			j.schedule();
		}
	}

	public IStatus racClear(IJavaProject jp,
			final @Nullable Shell shell, IProgressMonitor monitor) {
		boolean c = false;
		String racFolder = getRacDir();
		IFolder dir = jp.getProject().getFolder(racFolder);
		StringBuilder sb = new StringBuilder();
		try {
			for (IResource r : dir.members()) {
				try {
					r.delete(IResource.FORCE,monitor);
				} catch (Exception e) {
					sb.append(r.getName());
					sb.append(" - ");
					sb.append(e.getMessage());
					sb.append(Env.eol);
				}
				if (monitor != null && monitor.isCanceled()) { c = true; break; }
			}
			if (sb.length() != 0) {
				showMessageInUI(shell, "OpenJML Exception",
						sb.toString());
			}
		} catch (CoreException e) {
			showMessageInUI(shell, "OpenJML Exception",
					e.getMessage());
		}
		return c ? Status.CANCEL_STATUS : Status.OK_STATUS;
	}
	
	public void racChoose(ISelection selection, IWorkbenchWindow window,
			final @Nullable Shell shell) {
		Collection<IJavaProject> res = getSelectedProjects(true,selection, window, shell);
		for (IJavaProject jp : res) {
			Dialog d = new RACDialog(shell,"Select files in " + jp.getElementName() + " for Runtime Assertion Checking",jp);
			d.create();
			d.open();
		}
	}

//	protected void clear(IResource r, IFolder dir) {
//		try {
//			if (r instanceof IFolder) {
//				for (IResource rr : ((IFolder) r).members()) {
//					clear(rr, dir);
//				}
//			} else if (r instanceof IFile) {
//				if (r.getName().endsWith(".java")) {
//					ICompilationUnit cu = (ICompilationUnit) r
//							.getAdapter(ICompilationUnit.class);
//					if (cu != null) {
//						for (IType t : cu.getTypes()) {
//							String s = t.getFullyQualifiedName();
//							s = s.replace('.', '/');
//							s = s.substring(0, s.length() - (".java").length());
//							s = s + ".class";
//							IFile f = dir.getFile(s);
//							f.delete(IResource.FORCE, null);
//						}
//					}
//				}
//			} else {
//				if (Utils.verboseness >= Utils.VERBOSE)
//					Log.log("Not handling " + r.getClass());
//			}
//		} catch (CoreException e) {
//			Log.errorlog(
//					"Core Exception while traversing Resource tree (clear for RAC)",
//					e);
//			// just continue
//		}
//	}

	/**
	 * Creates a map indexed by IJavaProject, with the value for each
	 * IJavaProject being a Collection consisting of the subset of the argument
	 * that belongs to the Java project.
	 * 
	 * @param elements
	 *            The set of elements to sort
	 * @return The resulting Map of IJavaProject to Collection
	 */
	/*
	 * @ requires elements != null; requires elements.elementType <: IResource
	 * || elements.elementType <: IJavaElement; ensures \result != null;
	 */
	public static @NonNull
	<T> Map<IJavaProject, List<T>> sortByProject(@NonNull Collection<T> elements) {
		Map<IJavaProject, List<T>> map = new HashMap<IJavaProject, List<T>>();
		Iterator<T> i = elements.iterator();
		while (i.hasNext()) {
			T o = i.next();
			IJavaProject jp;
			if (o instanceof IResource) {
				jp = JavaCore.create(((IResource) o).getProject());
			} else if (o instanceof IJavaElement) {
				jp = ((IJavaElement) o).getJavaProject();
			} else {
				Log.errorlog(
						"INTERNAL ERROR: Unexpected content for a selection List - "
								+ o.getClass(), null);
				continue;
			}
			if (jp != null && jp.exists())
				addToMap(map, jp, o);
		}
		return map;
	}

	/**
	 * If key is not a key in the map, it is added, with an empty Collection for
	 * its value; then the given object is added to the Collection for that key.
	 * 
	 * @param map
	 *            A map of key values to Collections
	 * @param key
	 *            A key value to add to the map, if it is not already present
	 * @param object
	 *            An item to add to the Collection for the given key
	 */
	private static <T> void addToMap(@NonNull Map<IJavaProject, List<T>> map,
			@NonNull IJavaProject key, @NonNull T object) {
		List<T> list = map.get(key);
		if (list == null)
			map.put(key, list = new LinkedList<T>());
		list.add(object);
	}

	/**
	 * Displays a message in a dialog in the UI thread - this may be called from
	 * other threads.
	 * 
	 * @param shell
	 *            The shell to use to display the dialog, or a top-level shell
	 *            if the parameter is null
	 * @param title
	 *            The title of the dialog window
	 * @param msg
	 *            The message to display in the dialog
	 */
	public void showMessageInUI(@Nullable Shell shell,
			@NonNull final String title, @NonNull final String msg) {
		final Shell fshell = shell;
		Display d = fshell == null ? Display.getDefault() : fshell.getDisplay();
		d.asyncExec(new Runnable() {
			public void run() {
				MessageDialog.openInformation(fshell, title, msg);
			}
		});
	}

	// TODO _ document
	public void showExceptionInUI(@Nullable Shell shell, String message,
			Exception e) {
//		String s = message != null ? message + Env.eol + e.getMessage() : e
//				.getMessage();
//		if (s == null || s.isEmpty())
//			s = e.getClass().toString();
		
		String emsg = e == null ? null : e.getMessage();
		if (emsg != null && !emsg.isEmpty()) message = message + " (" + emsg + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		if (e != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			e.printStackTrace(pw);
			message = message + Env.eol + sw.toString(); 
		}

		showMessageInUI(shell, "OpenJML Exception", message);
	}

	// FIXME - fix non-modal dialog
	/**
	 * Displays a message in a non-modal dialog in the UI thread - this may be
	 * called from other threads.
	 * 
	 * @param shell
	 *            The shell to use to display the dialog, or a top-level shell
	 *            if the parameter is null
	 * @param title
	 *            The title of the dialog window
	 * @param msg
	 *            The message to display in the dialog
	 */
	public void showMessageInUINM(@Nullable Shell shell,
			@NonNull final String title, @NonNull final String msg) {
		final Shell fshell = shell;
		Display d = fshell == null ? Display.getDefault() : fshell.getDisplay();
		d.asyncExec(new Runnable() {
			public void run() {
				Dialog d = new NonModalDialog(fshell, title, msg);
				d.open();
			}
		});
	}

	// FIXME this does not seem to be working
	static public class NonModalDialog extends MessageDialog {
		final static String[] buttons = { "OK" };

		public NonModalDialog(Shell shell, String title, String message) {
			super(new Shell(), title, null, message, INFORMATION, buttons, 0);
			setShellStyle(getShellStyle() | SWT.MODELESS);
			setBlockOnOpen(false);
		}
	}

	public final static QualifiedName SPECSPATH_ID = new QualifiedName(
			Activator.PLUGIN_ID, "specspath");
	public final static QualifiedName SOURCEPATH_ID = new QualifiedName(
			Activator.PLUGIN_ID, "sourcepath");


	/**
	 * Displays a message in a information dialog; must be called from the UI
	 * thread.
	 * 
	 * @param shell
	 *            Either the parent shell
	 * @param title
	 *            A title for the dialog window
	 * @param msg
	 *            The message to display in the dialog window
	 */
	// @ requires shell != null;
	// @ requires title != null;
	// @ requires msg != null;
	static public void showMessage(Shell shell, String title, String msg) {
		MessageDialog.openInformation(shell, title, msg);
	}

	// FIXME -document

	/**
	 * Shows a dialog regarding an exception that has been thrown; this must be
	 * called within the UI thread.
	 */
	public void topLevelException(Shell shell, String title, Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String s = "Please report this internal bug, along with information about the context that caused the problem:"
				+ Env.eol + Env.eol + sw.toString();
		final int maxlength = 2000; // Just a limit to keep from overfilling a
									// dialog box
		if (s.length() > maxlength) {
			s = s.substring(0, maxlength) + " ...";
		}
		showMessage(shell, "JML Top-level Exception: " + title, s);
	}

	/** The suffixes allowed for JML files. */
	static public final String[] suffixes = { ".jml", ".java" };
	// static public final String[] suffixes = { ".refines-java",
	// ".refines-spec", ".refines-jml", ".java", ".spec", ".jml" };

	/**
	 * These constants should be the same as in org.jmlspecs.openjml.Utils, but
	 * we don't reference them directly to avoid a dependency (in case we reuse
	 * this plugin)
	 */
	static public final int QUIET = 0;
	static public final int NORMAL = 1;
	static public final int PROGRESS = 2;
	static public final int VERBOSE = 3;
	static public final int DEBUG = 4;
	/**
	 * A value used within the plugin to control printing - compare this value
	 * to the constants.
	 */
	static public int verboseness = NORMAL; // Should this be in options? FIXME

	/**
	 * This method returns an int giving the precedence of the suffix of the
	 * file name: -1 indicates not a JML file; 0 is the preferred suffix;
	 * increasing positive numbers indicate decreasing precedence of suffixes.
	 * 
	 * @param name
	 *            the file name to be assessed
	 * @return the precedence of the suffix (0 highest, more positive lower, -1
	 *         is not JML)
	 */
	@Pure
	static public int suffixOK(/* @ non_null */String name) {
		int i = 0;
		for (String s : suffixes) {
			if (name.endsWith(s))
				return i;
			i++;
		}
		return -1;
	}
	
	public String getRacDir() {
 	   String racdir = Options.value(Options.racbinKey);
 	   if (racdir == null || racdir.isEmpty()) racdir = "racbin";
 	   return racdir;
	}

	/**
	 * Returns an absolute, local file-system path to the internal run-time
	 * library (which holds definitions of annotations and some runtime
	 * utilities for RAC), or null without an error message if the library could
	 * not be found.
	 * 
	 * @return the absolute path
	 */
	public String findInternalRuntime() {
		String file = null;
		try {
			Bundle selfBundle = Platform.getBundle(Activator.PLUGIN_ID);
			if (selfBundle == null) {
				if (Utils.verboseness >= Utils.NORMAL)
					Log.log("No self plugin"); // FIXME - an error?
			} else {
				// We want to include the runtime library in the classpath for
				// the user. The runtime library is part of the UI plug-in, but
				// not as a jar file.
				URL url = null;
				if (url == null) {
					url = FileLocator.toFileURL(selfBundle.getResource(""));
					if (url != null) {
						// In development mode (launching the plug-in from
						// Eclipse, the url of the selfBundle is the bin
						// directory of the OpenJMLUI plug-in; we
						// can find the jmlruntime.jar library one level up.
						File root = new File(url.toURI());
						if (root.isDirectory()) {
							File f = new File(root, "jmlruntime.jar");
							if (f.exists()) {
								file = f.toString().replace('\\', '/');
								if (Utils.verboseness >= Utils.VERBOSE)
									Log.log("Internal runtime location: " + file);
							} else {
								f = new File(root, "../jmlruntime.jar");
								if (f.exists()) {
									file = f.toString().replace('\\', '/');
									if (Utils.verboseness >= Utils.VERBOSE)
										Log.log("Internal runtime location: "
												+ file);
								}
							}
						}
					}

				}
			}
		} catch (Exception e) {
			Log.errorlog("Failure finding internal runtime", e);
		}
		return file;
	}
	
	public int countMethods(IJavaProject jp) throws Exception {
		int count = 0;
		for (IPackageFragmentRoot pfr: jp.getPackageFragmentRoots()) {
			count += countMethods(pfr);
		}
		return count;
	}
	
	public int countMethods(IProject jp) throws Exception {
		return countMethods(JavaCore.create(jp));
	}
	
	public int countMethods(IPackageFragmentRoot pfr) throws Exception {
		int count = 0;
		for (IJavaElement pf: pfr.getChildren()) {
			if (pf instanceof IPackageFragment) {
				count += countMethods((IPackageFragment)pf);
			}
		}
		return count;
	}
	
	public int countMethods(IPackageFragment pf) throws Exception {
		int count = 0;
		for (ICompilationUnit cu: pf.getCompilationUnits()) {
			count += countMethods(cu);
		}
		return count;
	}
	public int countMethods(ICompilationUnit cu) throws Exception {
		int count = 0;
		for (IType t: cu.getTypes()) {
			count += countMethods(t);
		}
		return count;
	}

	public int countMethods(IResource f) throws Exception {
		int count = 0;
		if (f instanceof IFolder) {
			for (IResource r: ((IFolder)f).members()) {
				count += countMethods(r);
			}
		} else if (f instanceof IFile) {
			ICompilationUnit cu = (ICompilationUnit) f.getAdapter(ICompilationUnit.class);
			if (cu != null) count += countMethods(cu);
		} else {
			Log.log("Can't count a " + f.getClass());
		}
		return count;
	}

	public int countMethods(IType t) throws Exception {
		// FIXME - this does not count methods in anonymous or local types
		int count = 0;
		IType[] types = t.getTypes();
		for (IType tt: types) {
			count += countMethods(tt);
		}
		IMethod[] methods = t.getMethods();
		boolean hasDeclaredConstructor = false;
		for (IMethod m : methods) {
			if (m.isConstructor()) { hasDeclaredConstructor = true; break; }
		}
		if (!hasDeclaredConstructor) count++;
		return methods.length + count;  
	}

	public int countMethods(IMethod m) throws Exception {
		return 1;
	}

	public int countMethods(IJavaElement f) {
		Log.log("Can't count a " + f.getClass());
		return 0;
	}

	// FIXME - have not been able to get this to work as I would like
	public static class ModelessDialog extends Dialog {
		public ModelessDialog(Shell parentShell) {
			super(parentShell);
			setBlockOnOpen(false);
		}

		protected void setShellStyle(int newShellStyle) {
			int newstyle = newShellStyle & ~SWT.APPLICATION_MODAL; /*
																	 * turn off
																	 * APPLICATION_MODAL
																	 */
			newstyle |= SWT.MODELESS; /* turn on MODELESS */

			super.setShellStyle(newstyle);
		}

		public int open() {
			int retVal = super.open();
			pumpMessages(); /*
							 * this will let the caller wait till OK, Cancel is
							 * pressed, but will let the other GUI responsive
							 */
			return retVal;
		}

		protected void pumpMessages() {
			Shell sh = getShell();
			Display disp = sh.getDisplay();
			while (!sh.isDisposed()) {
				if (!disp.readAndDispatch())
					disp.sleep();
			}
			disp.update();
		}
	}

}
