package org.eclipse.core.internal.registry;

import org.eclipse.core.runtime.IConfigurationElement;

public class ThirdLevelConfigurationElementHandle extends ConfigurationElementHandle {

    public ThirdLevelConfigurationElementHandle(int id) {
        super(id);
    }
	
    protected ConfigurationElement getConfigurationElement() {
		return (ConfigurationElement) objectManager.getObject(getId(), RegistryObjectManager.THIRDLEVEL_CONFIGURATION_ELEMENT);
	}
	
	public IConfigurationElement[] getChildren() {
	    return (IConfigurationElement[]) objectManager.getHandles(getConfigurationElement().getRawChildren(), RegistryObjectManager.THIRDLEVEL_CONFIGURATION_ELEMENT);
	}
	
}
