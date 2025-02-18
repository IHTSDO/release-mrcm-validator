package org.snomed.quality.validator.mrcm;

public enum ValidationType {

	ATTRIBUTE_DOMAIN("Attribute domain"),
	ATTRIBUTE_RANGE("Attribute range"),
	ATTRIBUTE_CARDINALITY("Attribute cardinality"),
	ATTRIBUTE_IN_GROUP_CARDINALITY("Attribute in group cardinality"),
	CONCRETE_ATTRIBUTE_DATA_TYPE("Concrete attribute data type"),
	LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE("Lateralizable reference set type"),
	SEP_REFSET_TYPE("SEP reference set type");
	private final String name;

	ValidationType(final String name) {
		this.name = name;
	}

	String getName() {
		return name;
	}
}
