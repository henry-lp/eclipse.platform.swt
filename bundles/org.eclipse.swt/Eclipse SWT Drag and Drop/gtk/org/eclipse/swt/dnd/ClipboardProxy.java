package org.eclipse.swt.dnd;

/*
 * (c) Copyright IBM Corp. 2000, 2001, 2002.
 * All Rights Reserved
 */
 
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.Converter;
import org.eclipse.swt.internal.gtk.GtkSelectionData;
import org.eclipse.swt.internal.gtk.GtkTargetEntry;
import org.eclipse.swt.internal.gtk.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

class ClipboardProxy {
	/* Data is not flushed to the clipboard immediately.
	 * This class will remember the data and provide it when requested. 
	 */
	Object[] data;
	Transfer[] dataTypes;
	
	Display display;
	int pGtkClipboard;
	int pGtkPrimary;
	boolean onPrimary = false;
	boolean onClipboard = false;
	Callback getFunc;
	Callback clearFunc;
	
	static String ID = "CLIPBOARD PROXY OBJECT";

static ClipboardProxy _getInstance(Display display) {
	ClipboardProxy proxy = (ClipboardProxy) display.getData(ID);
	if (proxy != null) return proxy;
	proxy = new ClipboardProxy(display);
	display.setData(ID, proxy);
	display.addListener(SWT.Dispose, new Listener() {
		public void handleEvent(Event event) {
			Display display = event.display;
			ClipboardProxy proxy = (ClipboardProxy)display.getData(ID);
			if (proxy == null) return;
			display.setData(ID, null);
			proxy.dispose();
		}
	});
	return proxy;
}	

private ClipboardProxy(Display display) {	
	this.display = display;
	getFunc = new Callback( this, "getFunc", 4);
	clearFunc = new Callback( this, "clearFunc", 2);
	pGtkClipboard = OS.gtk_clipboard_get(OS.GDK_NONE);
	byte[] buffer = Converter.wcsToMbcs(null, "PRIMARY", true);
	int primary = OS.gdk_atom_intern(buffer, false);
	pGtkPrimary = OS.gtk_clipboard_get(primary);
}

private int clearFunc(int clipboard,int user_data_or_owner){
	if (clipboard == pGtkClipboard) {
		onClipboard = false;
	}
	if (clipboard == pGtkPrimary) {
		onPrimary = false;
	}
	if (!onClipboard && !onPrimary) {	
		data = null;
		dataTypes = null;
	}
	return 1;
}
private void dispose () {
	if (pGtkClipboard == 0) return;
	if (onPrimary) OS.gtk_clipboard_clear(pGtkPrimary);
	if (onClipboard) OS.gtk_clipboard_clear(pGtkClipboard);
	pGtkClipboard = 0;
	pGtkPrimary = 0;
	display = null;
	if (getFunc != null ) getFunc.dispose();
	getFunc = null;
	if (clearFunc != null) clearFunc.dispose();
	clearFunc = null;
	data = null;
	dataTypes = null;
}
/**
 * This function provides the data to the clipboard on request.
 * When this clipboard is disposed, the data will no longer be available.
 */
private int getFunc( int clipboard, int selection_data, int info, int user_data_or_owner){
	if (selection_data == 0) return 0;
	GtkSelectionData selectionData = new GtkSelectionData();
	OS.memmove(selectionData, selection_data, GtkSelectionData.sizeof);
	TransferData tdata = new TransferData();
	tdata.type = selectionData.target;
	int index = -1;
	for (int i = 0; i < dataTypes.length; i++) {
		if (dataTypes[i].isSupportedType(tdata)) {
			index = i;
			break;
		}
	}
	if (index == -1) return 0;
	dataTypes[index].javaToNative(data[index], tdata);
	OS.gtk_selection_data_set(selection_data, tdata.type, tdata.format, tdata.pValue, tdata.length);	
	return 1;
}
boolean setData(Object[] data, Transfer[] dataTypes) {
	if (onClipboard) {	
		OS.gtk_clipboard_clear(pGtkClipboard);
	}
	if (onPrimary) {
		OS.gtk_clipboard_clear(pGtkPrimary);
	}
		
	GtkTargetEntry[] entries = new  GtkTargetEntry [0];
	for (int i = 0; i < dataTypes.length; i++) {
		Transfer transfer = dataTypes[i];
		int[] typeIds = transfer.getTypeIds();
		String[] typeNames = transfer.getTypeNames();
		for (int j = 0; j < typeIds.length; j++) {
			GtkTargetEntry	entry = new GtkTargetEntry();						
			entry.info = typeIds[j];
			byte[] buffer = Converter.wcsToMbcs(null, typeNames[j], true);
			int pName = OS.g_malloc(buffer.length);
			OS.memmove(pName, buffer, buffer.length);
			entry.target = pName;
			GtkTargetEntry[] tmp = new GtkTargetEntry [entries.length + 1];
			System.arraycopy(entries, 0, tmp, 0, entries.length);
			tmp[entries.length] = entry;
			entries = tmp;				
		}	
	}
	
	int pTargetsList = OS.g_malloc(GtkTargetEntry.sizeof * entries.length);
	int offset = 0;
	for (int i = 0; i < entries.length; i++) {
		OS.memmove(pTargetsList + offset, entries[i], GtkTargetEntry.sizeof);
		offset += GtkTargetEntry.sizeof;
	}

	this.data = data;
	this.dataTypes = dataTypes;

	onPrimary = OS.gtk_clipboard_set_with_data(pGtkPrimary, pTargetsList, entries.length, getFunc.getAddress(), clearFunc.getAddress(), 0);
	onClipboard = OS.gtk_clipboard_set_with_data(pGtkClipboard, pTargetsList, entries.length, getFunc.getAddress(), clearFunc.getAddress(), 0);
	
	for (int i = 0; i < entries.length; i++) {
		GtkTargetEntry entry = entries[i];
		if( entry.target != 0) OS.g_free(entry.target);
	}
	if (pTargetsList != 0) OS.g_free(pTargetsList);
	
	return (onClipboard && onPrimary);
}
}
