package org.snomed.quality.validator.mrcm;

public enum ValidationSubType {

	ATTRIBUTE_RANGE_INVALID_CONCEPT("Invalid concept in attribute range"),
	ATTRIBUTE_RANGE_INACTIVE_CONCEPT("Inactive concept in attribute range"),
	ATTRIBUTE_RANGE_INVALID_TERM("Invalid term in attribute range");

	private final String name;

	ValidationSubType(final String name) {
		this.name = name;
	}

	String getName() {
		return name;
	}
}
