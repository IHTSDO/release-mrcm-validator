package org.snomed.quality.validator.mrcm;

public enum ValidationType {
	ATTRIBUTE_DOMAIN("Attribute domain"),
	ATTRIBUTE_RANGE("Attribute domain range"), 
	ATTRIBUTE_CARDINALITY("Attribute cardinality"),
	ATTRIBUTE_GROUP_CARDINALITY("Attribute group cardinality");
	private String name;
	private ValidationType(String name) {
		this.name=name;
	}
	String getName() {
		return this.name;
	}
}