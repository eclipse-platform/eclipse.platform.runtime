/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.registry;

import org.osgi.framework.Bundle;

public class Namespace implements KeyedElement {
	static final int[] EMPTY_CHILDREN = new int[] {0, 0};
	
	// The bundle contributing the object.
	private Bundle contributingBundle;
	private long contributingBundleId;
	
	//The children of the element
	//Here children is used to store both extension points and extensions.
	//The array always a minimum size of 2. The first two values indicate the number of extension points and the number of extensions.
	private int[] children = EMPTY_CHILDREN;
	static final byte EXTENSION_POINT = 0;
	static final byte EXTENSION = 1;
	
	Namespace(Bundle bundle) {
		contributingBundle = bundle;
		contributingBundleId = bundle.getBundleId();
	}
	
	Namespace(long id) {
		contributingBundleId = id;
	}
	
	void setRawChildren(int[] children) {
		this.children = children;
	}

	int[] getRawChildren() {
		return children;
	}
	
	int[] getExtensions() {
		int[] results = new int[children[EXTENSION]];
		System.arraycopy(children, 2 + children[EXTENSION_POINT], results, 0, children[EXTENSION]);
		return results;
	}

	Bundle getContributingBundle() {
		return contributingBundle;
	}
	
	int[] getExtensionPoints() {
		int[] results = new int[children[EXTENSION_POINT]];
		System.arraycopy(children, 2, results, 0, children[EXTENSION_POINT]);
		return results;
	}

	String getUniqueIdentifier() {
//		if (Platform.isFragment(contributingBundle))
//			return Platform.getHosts(contributingBundle)[0].getSymbolicName();
		return contributingBundle.getSymbolicName();
	}
	
	boolean isFragment() {
		return false;
//		return Platform.isFragment(contributingBundle);
	}

	public String toString() {
		return "Namespace: " + getUniqueIdentifier(); //$NON-NLS-1$
	}

	//Implements the KeyedElement interface
	public int getKeyHashCode() {
		return getKey().hashCode();
	}
	
	public Object getKey() {
		return new Long(contributingBundleId);
	}
	
	public boolean compare(KeyedElement other) {
		return contributingBundle == ((Namespace) other).contributingBundle;
	}
}
