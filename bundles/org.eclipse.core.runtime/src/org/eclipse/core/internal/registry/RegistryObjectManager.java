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

import java.lang.ref.SoftReference;
import java.util.*;
import org.eclipse.core.internal.runtime.InternalPlatform;

/**
 * This class manage all the object from the registry but does not deal with their dependencies.
 * It serves the objects which are either directly obtained from memory or read from a cache.
 * It also returns handles for objects.
 */
public class RegistryObjectManager {
	//Constants used to get the objects and their handles
	static final byte CONFIGURATION_ELEMENT = 1;
	static final byte EXTENSION = 2;
	static final byte EXTENSION_POINT = 3;
	static final byte THIRDLEVEL_CONFIGURATION_ELEMENT = 4;

	static final int[] EMPTY_INT_ARRAY = new int[0];
	static final String[] EMPTY_STRING_ARRAY = new String[0];

	static int UNKNOWN = -1;

	// key: extensionPointName, value: object id
	private HashtableOfStringAndInt extensionPoints; //This is loaded on startup. Then entries can be added when loading a new plugin from the xml.
	// key: object id, value: an object
	private ReferenceMap cache; //Entries are added by getter. The structure is not thread safe.
	//key: int, value: int
	private HashtableOfInt fileOffsets; //This is read once on startup when loading from the cache. Entries are never added here. They are only removed to prevent "removed" objects to be reloaded. 

	private int nextId = 1; //This is only used to get the next number available.

	//Those two data structures are only used when the addition or the removal of a plugin occurs.
	//They are used to keep track on a bundle basis of the extension being added or removed
	private KeyedHashSet newContributions; //represents the bundles added during this session.
	private Object formerContributions; //represents the bundles encountered in previous sessions. This is loaded lazily.

	// Map key: extensionPointFullyQualifiedName, value int[] of orphan extensions. 
	// The orphan access does not need to be synchronized because the it is protected by the lock in extension registry.
	private Object orphanExtensions;

	private KeyedHashSet heldObjects = new KeyedHashSet(); //strong reference to the objects that must be hold on to

	//Indicate if objects have been removed or added from the table. This only needs to be set in a couple of places (addNamespace and removeNamespace)
	private boolean isDirty = false;

	private boolean fromCache = false;

	public RegistryObjectManager() {
		extensionPoints = new HashtableOfStringAndInt();
		if ("true".equalsIgnoreCase(System.getProperty(InternalPlatform.PROP_NO_REGISTRY_FLUSHING))) { //$NON-NLS-1$
			cache = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);
		} else {
			cache = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.SOFT);
		}
		newContributions = new KeyedHashSet();
	}

	/**
	 * Initialize the object manager. Return true if the initialization succeeded, false otherwise
	 */
	synchronized boolean init(long timeStamp) {
		TableReader reader = new TableReader();
		Object[] results = reader.loadTables(timeStamp);
		if (results == null) {
			return false;
		}
		fileOffsets = (HashtableOfInt) results[0];
		extensionPoints = (HashtableOfStringAndInt) results[1];
		nextId = ((Integer) results[2]).intValue();
		fromCache = true;

		if ("true".equalsIgnoreCase(System.getProperty(InternalPlatform.PROP_NO_LAZY_CACHE_LOADING))) { //$NON-NLS-1$
			reader.setHoldObjects(true);
			markOrphansHasDirty(getOrphans());
			fromCache = reader.readAllCache(this);
			formerContributions = getFormerContributions();
		}
		return fromCache;
	}

	synchronized void addContribution(Contribution contribution) {
		isDirty = true;
		newContributions.add(contribution);
	}

	synchronized int[] getExtensionPointsFrom(long id) {
		KeyedElement tmp = newContributions.getByKey(new Long(id));
		if (tmp == null)
			tmp = getFormerContributions().getByKey(new Long(id));
		if (tmp == null)
			return EMPTY_INT_ARRAY;
		return ((Contribution) tmp).getExtensionPoints();
	}

	synchronized Set getNamespaces() {
		KeyedElement[] formerElts = getFormerContributions().elements();
		KeyedElement[] newElts = newContributions.elements();
		Set tmp = new HashSet(formerElts.length + newElts.length);
		for (int i = 0; i < formerElts.length; i++) {
			tmp.add(((Contribution) formerElts[i]).getNamespace());
		}
		for (int i = 0; i < newElts.length; i++) {
			tmp.add(((Contribution) newElts[i]).getNamespace());
		}
		return tmp;
	}

	synchronized boolean hasContribution(long id) {
		Object result = newContributions.getByKey(new Long(id));
		if (result == null)
			result = getFormerContributions().getByKey(new Long(id));
		return result != null;
	}

	private KeyedHashSet getFormerContributions() {
		KeyedHashSet result;
		if (fromCache == false)
			return new KeyedHashSet(0);

		if (formerContributions == null || (result = ((KeyedHashSet) ((formerContributions instanceof SoftReference) ? ((SoftReference) formerContributions).get() : formerContributions))) == null) {
			TableReader reader = new TableReader();
			result = reader.loadNamespaces();
			formerContributions = new SoftReference(result);
		}
		return result;
	}

	synchronized public void add(RegistryObject registryObject, boolean hold) {
		if (registryObject.getObjectId() == UNKNOWN) {
			int id = nextId++;
			registryObject.setObjectId(id);
		}
		cache.put(new Integer(registryObject.getObjectId()), registryObject);
		if (hold)
			hold(registryObject);
	}

	private void remove(RegistryObject registryObject, boolean release) {
		cache.remove(new Integer(registryObject.getObjectId()));
		if (release)
			release(registryObject);
	}

	synchronized void remove(int id, boolean release) {
		RegistryObject toRemove = (RegistryObject) cache.get(new Integer(id));
		if (fileOffsets != null)
			fileOffsets.removeKey(id);
		if (toRemove != null)
			remove(toRemove, release);
	}

	private void hold(RegistryObject toHold) {
		heldObjects.add(toHold);
	}

	private void release(RegistryObject toRelease) {
		heldObjects.remove(toRelease);
	}

	synchronized Object getObject(int id, byte type) {
		return basicGetObject(id, type);
	}

	private Object basicGetObject(int id, byte type) {
		Object result = cache.get(new Integer(id));
		if (result == null) {
			result = load(id, type);
			if (result == null)
				throw new InvalidHandleException("Can not find the object for the " + id + ". The plugin may have been uninstalled");
			cache.put(new Integer(id), result);
		}
		return result;
	}

	synchronized RegistryObject[] getObjects(int[] values, byte type) {
		if (values.length == 0) {
			switch (type) {
				case EXTENSION_POINT :
					return ExtensionPoint.EMPTY_ARRAY;
				case EXTENSION :
					return Extension.EMPTY_ARRAY;
				case CONFIGURATION_ELEMENT :
				case THIRDLEVEL_CONFIGURATION_ELEMENT :
					return ConfigurationElement.EMPTY_ARRAY;
			}
		}

		RegistryObject[] results = null;
		switch (type) {
			case EXTENSION_POINT :
				results = new ExtensionPoint[values.length];
				break;
			case EXTENSION :
				results = new Extension[values.length];
				break;
			case CONFIGURATION_ELEMENT :
			case THIRDLEVEL_CONFIGURATION_ELEMENT :
				results = new ConfigurationElement[values.length];
				break;
		}
		for (int i = 0; i < values.length; i++) {
			results[i] = (RegistryObject) basicGetObject(values[i], type);
		}
		return results;
	}

	synchronized ExtensionPoint getExtensionPointObject(String xptUniqueId) {
		int id;
		if ((id = extensionPoints.get(xptUniqueId)) == HashtableOfStringAndInt.MISSING_ELEMENT)
			return null;
		return (ExtensionPoint) getObject(id, EXTENSION_POINT);
	}

	Handle getHandle(int id, byte type) {
		switch (type) {
			case EXTENSION_POINT :
				return new ExtensionPointHandle(id);

			case EXTENSION :
				return new ExtensionHandle(id);

			case CONFIGURATION_ELEMENT :
				return new ConfigurationElementHandle(id);

			case THIRDLEVEL_CONFIGURATION_ELEMENT :
			default : //avoid compiler error, type should always be known
				return new ThirdLevelConfigurationElementHandle(id);
		}
	}

	Handle[] getHandles(int[] ids, byte type) {
		Handle[] results = null;
		int nbrId = ids.length;
		switch (type) {
			case EXTENSION_POINT :
				if (nbrId == 0)
					return ExtensionPointHandle.EMPTY_ARRAY;
				results = new ExtensionPointHandle[nbrId];
				for (int i = 0; i < nbrId; i++) {
					results[i] = new ExtensionPointHandle(ids[i]);
				}
				break;

			case EXTENSION :
				if (nbrId == 0)
					return ExtensionHandle.EMPTY_ARRAY;
				results = new ExtensionHandle[nbrId];
				for (int i = 0; i < nbrId; i++) {
					results[i] = new ExtensionHandle(ids[i]);
				}
				break;

			case CONFIGURATION_ELEMENT :
				if (nbrId == 0)
					return ConfigurationElementHandle.EMPTY_ARRAY;
				results = new ConfigurationElementHandle[nbrId];
				for (int i = 0; i < nbrId; i++) {
					results[i] = new ConfigurationElementHandle(ids[i]);
				}
				break;

			case THIRDLEVEL_CONFIGURATION_ELEMENT :
				if (nbrId == 0)
					return ConfigurationElementHandle.EMPTY_ARRAY;
				results = new ThirdLevelConfigurationElementHandle[nbrId];
				for (int i = 0; i < nbrId; i++) {
					results[i] = new ThirdLevelConfigurationElementHandle(ids[i]);
				}
				break;
		}
		return results;
	}

	synchronized ExtensionPointHandle[] getExtensionPointsHandles() {
		return (ExtensionPointHandle[]) getHandles(extensionPoints.getValues(), EXTENSION_POINT);
	}

	synchronized ExtensionPointHandle getExtensionPointHandle(String xptUniqueId) {
		int id = extensionPoints.get(xptUniqueId);
		if (id == HashtableOfStringAndInt.MISSING_ELEMENT)
			return null;
		return (ExtensionPointHandle) getHandle(id, EXTENSION_POINT);
	}

	private Object load(int id, byte type) {
		TableReader reader = new TableReader();
		switch (type) {
			case CONFIGURATION_ELEMENT :
				return reader.loadConfigurationElement(fileOffsets.get(id));

			case THIRDLEVEL_CONFIGURATION_ELEMENT :
				return reader.loadThirdLevelConfigurationElements(fileOffsets.get(id), this);

			case EXTENSION :
				return reader.loadExtension(fileOffsets.get(id));

			case EXTENSION_POINT :
			default :	//avoid compile errors. type must always be known
				return reader.loadExtensionPointTree(fileOffsets.get(id), this);
		}
	}

	synchronized public int[] getExtensionsFrom(long bundleId) {
		KeyedElement tmp = newContributions.getByKey(new Long(bundleId));
		if (tmp == null)
			tmp = getFormerContributions().getByKey(new Long(bundleId));
		if (tmp == null)
			return EMPTY_INT_ARRAY;
		return ((Contribution) tmp).getExtensions();
	}

	synchronized void addExtensionPoint(ExtensionPoint currentExtPoint, boolean hold) {
		add(currentExtPoint, hold);
		extensionPoints.put(currentExtPoint.getUniqueIdentifier(), currentExtPoint.getObjectId());
	}

	synchronized void removeExtensionPoint(String extensionPointId) {
		int pointId = extensionPoints.removeKey(extensionPointId);
		if (pointId == HashtableOfStringAndInt.MISSING_ELEMENT)
			return;
		remove(pointId, true);
	}

	public boolean isDirty() {
		return isDirty;
	}

	synchronized void removeContribution(long bundleId) {
		boolean removed = newContributions.removeByKey(new Long(bundleId));
		if (removed == false) {
			removed = getFormerContributions().removeByKey(new Long(bundleId));
			if (removed)
				formerContributions = getFormerContributions(); //This forces the removed namespace to stay around, so we do not forget about removed namespaces
		}

		if (removed) {
			isDirty = true;
			return;
		}

	}

	private Map getOrphans() {
		Object result = orphanExtensions;
		if (orphanExtensions == null && !fromCache) {
			result = new HashMap();
			orphanExtensions = result;
		} else if (orphanExtensions == null || (result = ((HashMap) ((orphanExtensions instanceof SoftReference) ? ((SoftReference) orphanExtensions).get() : orphanExtensions))) == null) {
			TableReader reader = new TableReader();
			result = reader.loadOrphans();
			orphanExtensions = new SoftReference(result);
		}
		return (HashMap) result;
	}

	void addOrphans(String extensionPoint, int[] extensions) {
		Map orphans = getOrphans();
		int[] existingOrphanExtensions = (int[]) orphans.get(extensionPoint);

		if (existingOrphanExtensions != null) {
			// just add
			int[] newOrphanExtensions = new int[existingOrphanExtensions.length + extensions.length];
			System.arraycopy(existingOrphanExtensions, 0, newOrphanExtensions, 0, existingOrphanExtensions.length);
			System.arraycopy(extensions, 0, newOrphanExtensions, existingOrphanExtensions.length, extensions.length);
			orphans.put(extensionPoint, newOrphanExtensions);
		} else {
			// otherwise this is the first one
			orphans.put(extensionPoint, extensions);
		}
		markOrphansHasDirty(orphans);
	}

	void markOrphansHasDirty(Map orphans) {
		orphanExtensions = orphans;
	}

	void addOrphan(String extensionPoint, int extension) {
		Map orphans = getOrphans();
		int[] existingOrphanExtensions = (int[]) orphans.get(extensionPoint);

		if (existingOrphanExtensions != null) {
			// just add
			int[] newOrphanExtensions = new int[existingOrphanExtensions.length + 1];
			System.arraycopy(existingOrphanExtensions, 0, newOrphanExtensions, 0, existingOrphanExtensions.length);
			newOrphanExtensions[existingOrphanExtensions.length] = extension;
			orphans.put(extensionPoint, newOrphanExtensions);
		} else {
			// otherwise this is the first one
			orphans.put(extensionPoint, new int[] {extension});
		}
		markOrphansHasDirty(orphans);
	}

	int[] removeOrphans(String extensionPoint) {
		Map orphans = getOrphans();
		int[] existingOrphanExtensions = (int[]) orphans.remove(extensionPoint);
		if (existingOrphanExtensions != null) {
			markOrphansHasDirty(orphans);
		}
		return existingOrphanExtensions;
	}

	boolean removeOrphan(String extensionPoint, int extension) {
		Map orphans = getOrphans();
		int[] existingOrphanExtensions = (int[]) orphans.get(extensionPoint);

		if (existingOrphanExtensions == null)
			return false;

		markOrphansHasDirty(orphans);
		int newSize = existingOrphanExtensions.length - 1;
		if (newSize == 0) {
			orphans.remove(extensionPoint);
			return true;
		}

		int[] newOrphanExtensions = new int[existingOrphanExtensions.length - 1];
		for (int i = 0, j = 0; i < existingOrphanExtensions.length; i++)
			if (extension != existingOrphanExtensions[i])
				newOrphanExtensions[j++] = existingOrphanExtensions[i];

		orphans.put(extensionPoint, newOrphanExtensions);
		return true;
	}

	//This method is only used by the writer to reach in
	Map getOrphanExtensions() {
		return getOrphans();
	}

	//	This method is only used by the writer to reach in
	int getNextId() {
		return nextId;
	}

	//	This method is only used by the writer to reach in
	HashtableOfStringAndInt getExtensionPoints() {
		return extensionPoints;
	}

	//	This method is only used by the writer to reach in
	KeyedHashSet[] getContributions() {
		return new KeyedHashSet[] {newContributions, getFormerContributions()};
	}
}
