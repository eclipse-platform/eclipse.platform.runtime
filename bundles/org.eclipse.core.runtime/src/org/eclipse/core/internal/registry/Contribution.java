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

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

//This object is used to keep track on a bundle basis of the extension and extension points being contributed.
//It is mainly used on removal so we can quickly get find objects to remove.
//Each contribution is made in the context of a namespace. For a regular bundle, the namespace is the symbolic name of the bundle, whereas for a fragment it is the name of its host.  
public class Contribution implements KeyedElement {
	static final int[] EMPTY_CHILDREN = new int[] {0, 0};
	
	// The actual bundle contributing the object.
	private Bundle contributingBundle;
	private long contributingBundleId;
	
	//The children of the element
	//Here children is used to store both extension points and extensions.
	//The array always a minimum size of 2. The first two values indicate the number of extension points and the number of extensions.
	private int[] children = EMPTY_CHILDREN;
	static final byte EXTENSION_POINT = 0;
	static final byte EXTENSION = 1;
	
	Contribution(Bundle bundle) {
		contributingBundle = bundle;
		contributingBundleId = bundle.getBundleId();
	}
	
	Contribution(long id) {
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

	String getNamespace() {
		if (Platform.isFragment(contributingBundle))
			return Platform.getHosts(contributingBundle)[0].getSymbolicName();
		return contributingBundle.getSymbolicName();
	}
	
	public String toString() {
		return "Contribution: "  +  contributingBundle.getBundleId() + " in namespace" +  getNamespace(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	Bundle getNamespaceBundle() {
		if (Platform.isFragment(contributingBundle)) 
			return Platform.getHosts(contributingBundle)[0];
		return contributingBundle;
	}
	
	//Implements the KeyedElement interface
	public int getKeyHashCode() {
		return getKey().hashCode();
	}
	
	public Object getKey() {
		return new Long(contributingBundleId);
	}
	
	public boolean compare(KeyedElement other) {
		return contributingBundle == ((Contribution) other).contributingBundle;
	}
}
