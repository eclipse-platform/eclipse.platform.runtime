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

/**
 * A handle is the super class to all registry objects that are now served to users.
 * The handles never hold on to any "real" content of the object being represented.
 * A handle can become stale if its referenced object has been removed from the registry.
 * @since 3.1. 
 */
public abstract class Handle {
	static RegistryObjectManager objectManager;

	static RegistryObjectManager getObjectManager() {
		if (objectManager == null)
			objectManager = new RegistryObjectManager();
		return objectManager;
	}

	private int objectId;

	protected int getId() {
		return objectId;
	}

	Handle(int value) {
		objectId = value;
	}

	/**
	 * Return the actual object corresponding to this handle.
	 * @throws InvalidHandleException when the handle is stale.
	 */
	abstract RegistryObject getObject();
}
