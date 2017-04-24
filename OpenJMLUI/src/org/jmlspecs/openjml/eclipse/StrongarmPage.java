/*
 * This file is part of the OpenJML plugin project.
 * Copyright (c) 2004-2013 David R. Cok
 */
package org.jmlspecs.openjml.eclipse;

import java.util.Arrays;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.jmlspecs.openjml.Strings;
import org.jmlspecs.openjml.eclipse.widgets.ButtonFieldEditor;
import org.jmlspecs.openjml.eclipse.widgets.EnumDialog;

/**
 * This class creates a Preferences page in Eclipse
 */
public class StrongarmPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public static final String WEAVE_INLINE = "inline";
    public static final String WEAVE_SEPERATE = "in seperate files";
    
    protected static final String[] persistModes = {
	    WEAVE_INLINE,
	    WEAVE_SEPERATE
    };

    public StrongarmPage() {
	super(FLAT);
    }

    /** A fake preference store key for the add button. */
    final static public String editKey = Options.prefix + "edit"; //$NON-NLS-1$

    public void init(IWorkbench workbench) {
	IPreferenceStore istore = Activator.getDefault().getPreferenceStore();
	setPreferenceStore(istore);
	setDescription(Messages.OpenJMLUI_StrongarmPage_Title);

	try {
	    String d = Options.value(Options.inferTimeout);

	    Integer.parseInt(d);
	} catch (Exception e) {
	    Options.setValue(Options.inferTimeout, "300");
	}

	try {
	    String d = Options.value(Options.inferMaxDepth);

	    Integer.parseInt(d);
	} catch (Exception e) {
	    Options.setValue(Options.inferMaxDepth, "300");
	}

	
	try {
	    String d = Options.value(Options.inferDefaultPrecondition);

	    Boolean.parseBoolean(d);
	} catch (Exception e) {
	    Options.setValue(Options.inferDefaultPrecondition, "true");
	}

    }

    // public void setValue(String out) {
    // Options.setValue(Options.solversKey, out);
    // }
    //
    // public String getValue() {
    // return Options.value(Options.solversKey);
    // }

    @Override
    protected void createFieldEditors() {

	addField(new BooleanFieldEditor(Options.inferDebug, Messages.OpenJMLUI_PreferencesPage_INFER_DEBUG, getFieldEditorParent()));

	addField(new StringFieldEditor(Options.inferTimeout, Messages.OpenJMLUI_PreferencesPage_INFER_TIMEOUT, getFieldEditorParent()));

	addField(new BooleanFieldEditor(Options.inferDefaultPrecondition, Messages.OpenJMLUI_PreferencesPage_INFER_DEFAULT_PRECONDITIONS, getFieldEditorParent()));
	
	addField(new StringFieldEditor(Options.inferMaxDepth, Messages.OpenJMLUI_PreferencesPage_INFER_MAX_DEPTH, getFieldEditorParent()));

	addField(new BooleanFieldEditor(Options.inferDevTools, Messages.OpenJMLUI_PreferencesPage_INFER_DEV_TOOLS, getFieldEditorParent()));

	String[][] choices = new String[persistModes.length][];
	int i = 0;
	for (String m : persistModes) {
	    // The two strings are the label and the value
	    choices[i++] = new String[] { m, m };
	}

	addField(new ComboFieldEditor(Options.inferPersistSpecsTo, Messages.OpenJMLUI_PreferencesPage_INFER_PERSIST_MODE, choices, getFieldEditorParent()));

    }

}
