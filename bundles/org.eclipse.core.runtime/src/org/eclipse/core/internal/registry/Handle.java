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
