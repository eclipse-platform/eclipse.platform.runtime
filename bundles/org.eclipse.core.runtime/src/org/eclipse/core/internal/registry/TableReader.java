/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.registry;

import java.io.*;
import org.eclipse.core.internal.runtime.InternalPlatform;
import org.eclipse.core.runtime.*;
import org.osgi.framework.Bundle;

public class TableReader {
	//Markers in the cache 
	static final int NULL = 0;
	static final int OBJECT = 1;

	//The version of the cache
	static final int CACHE_VERSION = 1;

	//Informations representing the MAIN file
	static final String MAIN = ".mainData"; //$NON-NLS-1$
	static File mainDataFile;
	DataInputStream input = null;
	//	int size;

	//Informations representing the EXTRA file
	static final String EXTRA = ".extraData"; //$NON-NLS-1$
	static File extraDataFile;
	DataInputStream extraInput = null;
	//	int sizeExtra;

	//The table file
	static final String TABLE = ".table"; //$NON-NLS-1$
	static File tableFile;

	//The namespace file
	static final String NAMESPACE = ".namespace"; //$NON-NLS-1$
	static File namespaceFile;

	//Status code
	private static final byte fileError = 0;
	private static final boolean DEBUG = false;

	static void setMainDataFile(File main) {
		mainDataFile = main;
	}

	static void setExtraDataFile(File extra) {
		extraDataFile = extra;
	}

	static void setTableFile(File table) {
		tableFile = table;
	}

	static void setNamespaceFile(File namespace) {
		namespaceFile = namespace;
	}

	public TableReader() {
		openInputFile();
		openExtraFile();
	}

	private void openInputFile() {
		try {
			input = new DataInputStream(new BufferedInputStream(new FileInputStream(mainDataFile)));
			//			size = input.available();
		} catch (FileNotFoundException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error readling the registry cache", e));
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error readling the registry cache", e));
		}
	}

	private void openExtraFile() {
		try {
			extraInput = new DataInputStream(new BufferedInputStream(new FileInputStream(extraDataFile)));
			//			sizeExtra = extraInput.available();
		} catch (FileNotFoundException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error readling the registry cache", e));
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error readling the registry cache", e));
		}
	}

	private void closeInputFile() {
		try {
			input.close();
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error closing the registry cache", e));
		}

	}

	private void closeExtraFile() {
		try {
			extraInput.close();
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error closing the registry cache", e));
		}

	}

	public Object[] loadTables(long expectedTimestamp) {
		HashtableOfInt offsets;
		HashtableOfStringAndInt extensionPoints;

		DataInputStream tableInput = null;
		try {
			tableInput = new DataInputStream(new BufferedInputStream(new FileInputStream(tableFile)));
			if (!checkCacheValidity(tableInput, expectedTimestamp))
				return null;

			Integer nextId = new Integer(tableInput.readInt());
			offsets = new HashtableOfInt();
			offsets.load(tableInput);
			extensionPoints = new HashtableOfStringAndInt();
			extensionPoints.load(tableInput);
			return new Object[] {offsets, extensionPoints, nextId};
		} catch (IOException e) {
			if (tableInput != null)
				try {
					tableInput.close();
				} catch (IOException e1) {
					//Ignore
				}
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error while reading the table file", e));
			return null;
		}

	}

	//	Check various aspect of the cache to see if it's valid 
	//TODO Here we may want to check the timestamp and size of other files
	private boolean checkCacheValidity(DataInputStream in, long expectedTimestamp) {
		int version;
		try {
			version = in.readInt();
			if (version != CACHE_VERSION)
				return false;

			long installStamp = in.readLong();
			long registryStamp = in.readLong();
			String osStamp = in.readUTF();
			String windowsStamp = in.readUTF();
			String localeStamp = in.readUTF();
			InternalPlatform info = InternalPlatform.getDefault();
			return ((expectedTimestamp == 0 || expectedTimestamp == registryStamp) && (installStamp == info.getStateTimeStamp()) && (osStamp.equals(info.getOS())) && (windowsStamp.equals(info.getWS())) && (localeStamp.equals(info.getNL())));
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error checking the registry time stamps ", e));
			return false;
		}
	}

	public Object loadConfigurationElement(int offset) {
		try {
			goToInputFile(offset);
			return basicLoadConfigurationElement(input, null);
		} catch (IOException e) {
			//Here an exception may happen because there are cases where we try to get a configuration element without being sure
			if (DEBUG)
				InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error reading a configuration element (" + offset + ") from the registry cache", e));
			return null;
		}
	}

	private ConfigurationElement basicLoadConfigurationElement(DataInputStream is, Bundle actualContributingBundle) throws IOException {
		int self = is.readInt();
		long contributingBundle = is.readLong();
		String name = readStringOrNull(is, false);
		int parentId = is.readInt();
		byte parentType = is.readByte();
		int misc = is.readInt();//this is set in second level CEs, to indicate where in the extra data file the children ces are
		String[] propertiesAndValue = readPropertiesAndValue(is);
		int[] children = readArray(is);
			actualContributingBundle = getBundle(contributingBundle);
		return new ConfigurationElement(self, actualContributingBundle, name, propertiesAndValue, children, misc, parentId, parentType);
	}

	public Object loadThirdLevelConfigurationElements(int offset, RegistryObjectManager objectManager) {
		try {
			goToExtraFile(offset);
			return loadConfigurationElementAndChildren(extraInput, objectManager, null);
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error reading a third level configuration element (" + offset + ") from the registry cache", e));
			return null;
		}
	}

	//Read a whole configuration element subtree from the given input stream.
	private ConfigurationElement loadConfigurationElementAndChildren(DataInputStream is, RegistryObjectManager objectManager, Bundle actualContributingBundle) throws IOException {
		ConfigurationElement ce = basicLoadConfigurationElement(is, actualContributingBundle);
		if (actualContributingBundle == null)
			actualContributingBundle = ce.getContributingBundle();
		int[] children = ce.getRawChildren();
		for (int i = 0; i < children.length; i++) {
			ConfigurationElement tmp = loadConfigurationElementAndChildren(is, objectManager, actualContributingBundle);
			objectManager.add(tmp, false);
		}
		return ce;
	}

	private String[] readPropertiesAndValue(DataInputStream inputStream) throws IOException {
		int numberOfProperties = inputStream.readInt();
		if (numberOfProperties == 0)
			return RegistryObjectManager.EMPTY_STRING_ARRAY;
		String[] properties = new String[numberOfProperties];
		for (int i = 0; i < numberOfProperties; i++) {
			properties[i] = readStringOrNull(inputStream, false);
		}
		return properties;
	}

	public Object loadExtension(int offset) {
		try {
			goToInputFile(offset);
			return basicLoadExtension();
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error reading an extension (" + offset + ") from the registry cache", e));
		}
		return null;
	}

	private Bundle getBundle(long id) {
		return InternalPlatform.getDefault().getBundleContext().getBundle(id);
	}

	private Extension basicLoadExtension() throws IOException {
		int self = input.readInt();
		String simpleId = readStringOrNull(input, false);
		int[] children = readArray(input);
		int extraData = input.readInt();
		return new Extension(self, simpleId, children, extraData);
	}

	public ExtensionPoint loadExtensionPointTree(int offset, RegistryObjectManager objects) {
		try {
			ExtensionPoint xpt = (ExtensionPoint) loadExtensionPoint(offset);
			int[] children = xpt.getRawChildren();
			int nbrOfExtension = children.length;
			for (int i = 0; i < nbrOfExtension; i++) {
				Extension loaded = basicLoadExtension();
				objects.add(loaded, false);
			}

			for (int i = 0; i < nbrOfExtension; i++) {
				int nbrOfCe = input.readInt();
				for (int j = 0; j < nbrOfCe; j++) {
					Bundle contributingBundle = null; //The contributing bundle for the extension for which we are reading the configuration elements
					ConfigurationElement ce = basicLoadConfigurationElement(input, contributingBundle);
					objects.add(ce, false);
					if (contributingBundle == null)
						contributingBundle = ce.getContributingBundle();

					int nbrSecondLevelCEs = ce.getRawChildren().length;
					for (int k = 0; k < nbrSecondLevelCEs; k++) {
						ConfigurationElement secondLevel = basicLoadConfigurationElement(input, contributingBundle);
						objects.add(secondLevel, false);
					}

				}
			}
			return xpt;
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error reading an extension point tree (" + offset + ") from the registry cache", e));
			return null;
		}
	}

	public Object loadExtensionPoint(int offset) {
		try {
			goToInputFile(offset);
			return basicLoadExtensionPoint();
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error reading an extension point (" + offset + ") from the registry cache", e));
			return null;
		}
	}

	private ExtensionPoint basicLoadExtensionPoint() throws IOException {
		int self = input.readInt();
		int[] children = readArray(input);
		int extraData = input.readInt();
		return new ExtensionPoint(self, children, extraData);
	}

	private int[] readArray(DataInputStream in) throws IOException {
		int arraySize = in.readInt();
		if (arraySize == 0)
			return RegistryObjectManager.EMPTY_INT_ARRAY;
		int[] result = new int[arraySize];
		for (int i = 0; i < arraySize; i++) {
			result[i] = in.readInt();
		}
		return result;
	}

	private void goToInputFile(int offset) throws IOException {
		//		int where = size - input.available();
		//		if (where < offset) {
		//			input.skipBytes(offset - where);
		//		} else {
		//			closeInputFile();
		//			openInputFile();
		//			input.skipBytes(offset);
		//		}
		input.skipBytes(offset);
	}

	private void goToExtraFile(int offset) throws IOException {
		//		int where = sizeExtra - extraInput.available();
		//		if (where < offset) {
		//			extraInput.skipBytes(offset - where);
		//		} else {
		//			closeExtraFile();
		//			openExtraFile();
		//			extraInput.skipBytes(offset);
		//		}
		extraInput.skipBytes(offset);
	}

	private String readStringOrNull(DataInputStream in, boolean intern) throws IOException {
		byte type = in.readByte();
		if (type == NULL)
			return null;
		if (intern)
			return in.readUTF().intern();
		return in.readUTF();
	}

	public String[] loadExtensionExtraData(int dataPosition) {
		try {
			goToExtraFile(dataPosition);
			return basicLoadExtensionExtraData();
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error reading extension label (" + dataPosition + ") from the registry cache", e));
		}
		return null;
	}

	private String[] basicLoadExtensionExtraData() throws IOException {
		return new String[] { readStringOrNull(extraInput, false), readStringOrNull(extraInput, false), readStringOrNull(extraInput, false) };
	}

	public String[] loadExtensionPointExtraData(int offset) {
		try {
			goToExtraFile(offset);
			return basicLoadExtensionPointExtraData();
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error reading extension point data (" + offset + ") from the registry cache", e));
		}
		return null;
	}

	private String[] basicLoadExtensionPointExtraData() throws IOException {
		String[] result = new String[5];
		result[0] = readStringOrNull(extraInput, false); //the label
		result[1] = readStringOrNull(extraInput, false); //the schema
		result[2] = readStringOrNull(extraInput, false); //the fully qualified name
		result[3] = readStringOrNull(extraInput, false); //the namespace
		result[4] = Long.toString(extraInput.readLong());
		return result;
	}

	public KeyedHashSet loadNamespaces() {
		try {
			DataInputStream namespaceInput = new DataInputStream(new BufferedInputStream(new FileInputStream(namespaceFile)));
			int size = namespaceInput.readInt();
			KeyedHashSet result = new KeyedHashSet(size);
			for (int i = 0; i < size; i++) {
				Namespace n = new Namespace(namespaceInput.readLong());
				n.setRawChildren(readArray(namespaceInput));
				result.add(n);
			}
			return result;
		} catch (IOException e) {
			return null;
		}
	}

	public ExtensionPoint readAllExtensionPointTree(RegistryObjectManager objectManager) {
		try {
			ExtensionPoint xpt = basicLoadExtensionPoint();
			String[] tmp = basicLoadExtensionPointExtraData();
			xpt.setLabel(tmp[0]);
			xpt.setSchema(tmp[1]);
			xpt.setUniqueIdentifier(tmp[2]);
			xpt.setNamespace(tmp[3]);
			xpt.setBundleId(Long.parseLong(tmp[4]));
			int[] children = xpt.getRawChildren();
			int nbrOfExtension = children.length;
			for (int i = 0; i < nbrOfExtension; i++) {
				Extension loaded = basicLoadExtension();
				tmp = basicLoadExtensionExtraData();
				loaded.setLabel(tmp[0]);
				loaded.setExtensionPointIdentifier(tmp[1]);
				objectManager.add(loaded, false);
			}

			for (int i = 0; i < nbrOfExtension; i++) {
				int nbrOfCe = input.readInt();
				for (int j = 0; j < nbrOfCe; j++) {
					Bundle contributingBundle = null; //The contributing bundle for the extension for which we are reading the configuration elements
					ConfigurationElement ce = basicLoadConfigurationElement(input, contributingBundle);
					objectManager.add(ce, false);
					if (contributingBundle == null)
						contributingBundle = ce.getContributingBundle();

					int nbrSecondLevelCEs = ce.getRawChildren().length;
					for (int k = 0; k < nbrSecondLevelCEs; k++) {
						ConfigurationElement secondLevel = basicLoadConfigurationElement(input, contributingBundle);
						objectManager.add(secondLevel, false);
						loadConfigurationElementAndChildren(extraInput, objectManager, contributingBundle);
					}
				}
			}
			return xpt;
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error while reading the whole cache", e));
		}
		return null;
	}

}
