package org.eclipse.core.internal.registry;

import org.eclipse.core.runtime.*;

public class ExtensionHandle extends Handle implements IExtension {
	static final ExtensionHandle[] EMPTY_ARRAY = new ExtensionHandle[0];

	public ExtensionHandle(int i) {
		super(i);
	}

	private Extension getExtension() {
		return (Extension) objectManager.getObject(getId(), RegistryObjectManager.EXTENSION);
	}
	
	/**
	 * @deprecated
	 */
	public IPluginDescriptor getDeclaringPluginDescriptor() {
		return getExtension().getDeclaringPluginDescriptor();
	}

	public String getNamespace() {
		return getExtension().getNamespace();
	}

	public String getExtensionPointUniqueIdentifier() {
		return getExtension().getExtensionPointIdentifier();
	}

	public String getLabel() {
		return getExtension().getLabel();
	}

	public String getSimpleIdentifier() {
		return getExtension().getSimpleIdentifier();
	}

	public String getUniqueIdentifier() {
		return getExtension().getUniqueIdentifier();
	}
	
	public IConfigurationElement[] getConfigurationElements() {
		return (IConfigurationElement[]) objectManager.getHandles(getExtension().getRawChildren(), RegistryObjectManager.CONFIGURATION_ELEMENT);
	}
	
	NestedRegistryModelObject getObject() {
		return getExtension();
	}
}
