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
import java.util.HashSet;
import java.util.Set;

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

	public static int UNKNOWN = -1;

	// key: extensionPointName, value: object id
	HashtableOfStringAndInt extensionPoints;			//This is loaded on startup. Then entries can be added when loading a new plugin from the xml.
	// key: object id, value: an object
	protected ReferenceMap cache;							//Entries are added by getter. The structure is not thread safe.
	//key: int, value: int
	private HashtableOfInt fileOffsets;						//This is read once on startup when loading from the cache. Entries are never added here. They are only removed to prevent "removed" objects to be reloaded. 

	int nextId = 1;	//This is only used to get the next number available.

	//Those two data structures are only used when the addition or the removal of a plugin occurs
	KeyedHashSet newNamespaces; //represents the namespaces added and removed during this session.
	private Object formerNamespaces; //represents the namespaces encountered in previous sessions. This is loaded lazily

	private KeyedHashSet heldObjects = new KeyedHashSet(); //strong reference to the objects that must be hold on to

	//Indicate if objects have been removed or added from the table. This only needs to be set in a couple of places (addNamespace and removeNamespace)
	private boolean isDirty = false;

	protected boolean fromCache = false;

	public RegistryObjectManager() {
		Handle.objectManager = this;
		extensionPoints = new HashtableOfStringAndInt();
		cache = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.SOFT);
		newNamespaces = new KeyedHashSet();
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
		return true;
	}

	synchronized void addNamespace(Namespace namespace) {
		isDirty = true;
		newNamespaces.add(namespace);
	}

	synchronized int[] getExtensionPointsFrom(long id) {
		KeyedElement tmp;
		tmp = newNamespaces.getByKey(new Long(id));
		if (tmp == null)
			tmp = getFormersNamespaces().getByKey(new Long(id));
		if (tmp == null)
			return EMPTY_INT_ARRAY;
		Namespace namespace = (Namespace) tmp;
		return namespace.getExtensionPoints();
	}

	synchronized Set getNamespaces() {
		KeyedElement[] formerElts;
		KeyedElement[] newElts;
		formerElts = getFormersNamespaces().elements();
		newElts = newNamespaces.elements();
		Set tmp = new HashSet(formerElts.length + newElts.length);
		for (int i = 0; i < formerElts.length; i++) {
			tmp.add(((Namespace) formerElts[i]).getUniqueIdentifier());
		}
		for (int i = 0; i < newElts.length; i++) {
			tmp.add(((Namespace) newElts[i]).getUniqueIdentifier());
		}
		return tmp;
	}

	synchronized boolean hasNamespace(long id) {
		Object result = newNamespaces.getByKey(new Long(id));
		if (result == null) 
			result = getFormersNamespaces().getByKey(new Long(id));
		return result != null;
	}
	
	private KeyedHashSet getFormersNamespaces() {
		KeyedHashSet result;
		if (fromCache == false)
			return new KeyedHashSet(0);
		
		if (formerNamespaces == null || (result = ((KeyedHashSet)( (formerNamespaces instanceof SoftReference) ? ((SoftReference)formerNamespaces).get() : formerNamespaces))) == null) {
			TableReader reader = new TableReader();
			result = reader.loadNamespaces();
			formerNamespaces = new SoftReference(result);
		}
		return result;
	}

	synchronized public void add(NestedRegistryModelObject registryObject, boolean hold) {
		if (registryObject.getObjectId() == UNKNOWN) {
			int id = nextId++;
			registryObject.setObjectId(id);
		}
		cache.put(new Integer(registryObject.getObjectId()), registryObject);
		if (hold)
			hold(registryObject);
	}

	private void remove(NestedRegistryModelObject registryObject, boolean release) {
		cache.remove(registryObject);
		if (release)
			release(registryObject);
	}

	synchronized void remove(int id, boolean release) {
		NestedRegistryModelObject toRemove = (NestedRegistryModelObject) cache.get(new Integer(id));
		if (fileOffsets != null)
			fileOffsets.removeKey(id);
		if (toRemove != null)
			remove(toRemove, release);
	}

	private void hold(NestedRegistryModelObject toHold) {
		heldObjects.add(toHold);
	}

	private void release(NestedRegistryModelObject toRelease) {
		heldObjects.remove(toRelease);
	}

	synchronized Object getUnknownObject(int id, byte type) {
		Object result = cache.get(new Integer(id));
		if (result == null) {
			result = load(id, type);
			if (result != null) {
				cache.put(new Integer(id), result);
			}
		}
		return result;
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

	synchronized NestedRegistryModelObject[] getObjects(int[] values, byte type) {
		if (values == null || values.length == 0) {
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

		NestedRegistryModelObject[] results = null;
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
			results[i] = (NestedRegistryModelObject) basicGetObject(values[i], type);
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
				return new ThirdLevelConfigurationElementHandle(id);
		}
		return null;
	}

	Handle[] getHandles(int[] ids, byte type) {
		Handle[] results = null;
		int nbrId = ids == null ? 0 : ids.length;
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
		int[] extensionPointIds = extensionPoints.getValues();
		return (ExtensionPointHandle[]) getHandles(extensionPointIds, EXTENSION_POINT);
	}

	synchronized ExtensionPointHandle getExtensionPointHandle(String xptUniqueId) {
		int id = extensionPoints.get(xptUniqueId);
		if (id == HashtableOfStringAndInt.MISSING_ELEMENT)
			return null;
		return (ExtensionPointHandle) getHandle(id, EXTENSION_POINT);
	}

	private Object load(int id, byte type) {
		Object result = null;
		TableReader reader = new TableReader();
		switch (type) {
			case CONFIGURATION_ELEMENT :
				//				System.out.println("reading configuration element " + id); //$NON-NLS-1$
				result = reader.loadConfigurationElement(fileOffsets.get(id));
				break;

			case THIRDLEVEL_CONFIGURATION_ELEMENT :
				//				System.out.println("third level " + id); //$NON-NLS-1$
				result = reader.loadThirdLevelConfigurationElements(fileOffsets.get(id), this);
				break;

			case EXTENSION :
				//System.out.println("reading extension element " + id); //$NON-NLS-1$
				result = reader.loadExtension(fileOffsets.get(id));
				break;

			case EXTENSION_POINT :
				result = reader.loadExtensionPointTree(fileOffsets.get(id), this);
				break;

			default :
				break;
		}
		return result;
	}

	synchronized public int[] getExtensionsFrom(long bundleId) {
		KeyedElement tmp = newNamespaces.getByKey(new Long(bundleId));
		if (tmp == null)
			tmp = getFormersNamespaces().getByKey(new Long(bundleId));
		if (tmp == null)
			return null;
		return ((Namespace) tmp).getExtensions();
	}

	synchronized void addExtensionPoint(ExtensionPoint currentExtPoint, boolean hold) {
		add(currentExtPoint, hold);
		extensionPoints.put(currentExtPoint.getUniqueIdentifier(), currentExtPoint.getObjectId());
	}

	synchronized void removeExtensionPoint(String extensionPointId) {
		int pointId = extensionPoints.removeKey(extensionPointId);
		//TODO Remove from the offset table
		if (pointId == HashtableOfStringAndInt.MISSING_ELEMENT)
			return;
		remove(pointId, true);
	}

	public boolean isDirty() {
		return isDirty;
	}

	synchronized void removeNamespace(long bundleId) {
		boolean removed = newNamespaces.removeByKey(new Long(bundleId));
		if (removed == false) {
			removed = getFormersNamespaces().removeByKey(new Long(bundleId));
			if (removed)
				formerNamespaces = getFormersNamespaces();	//This forces the removed namespace to stay around, so we do not forget about removed namespaces
		}

		if (removed) {
			isDirty = true;
			return;
		}

	}
}
