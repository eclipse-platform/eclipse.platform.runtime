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

public abstract class Handle {
	static RegistryObjectManager objectManager;	//TODO Need to discuss that during the review
	
	private int self;
	
	protected int getId() {
		return self;
	}

	public Handle(int value) {
		self = value;
	}
	
	abstract NestedRegistryModelObject getObject(); 
}
