package org.snomed.quality.validator.mrcm;

import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;

import java.util.List;

public class Assertion {

	private final Domain domain;
	private final Attribute attribute;
	private final String domainAttributeRangeConstraint;
	private final List<Long> conceptIdsWithInvalidAttributeValue;

	public Assertion(Domain domain, Attribute attribute, String domainAttributeRangeConstraint, List<Long> conceptIdsWithInvalidAttributeValue) {
		this.domain = domain;
		this.attribute = attribute;
		this.domainAttributeRangeConstraint = domainAttributeRangeConstraint;
		this.conceptIdsWithInvalidAttributeValue = conceptIdsWithInvalidAttributeValue;
	}

	public boolean invalidConceptsFound() {
		return !conceptIdsWithInvalidAttributeValue.isEmpty();
	}

	@Override
	public String toString() {
		return "Assertion{" +
				"domain=" + domain +
				", attribute=" + attribute +
				", domainAttributeRangeConstraint='" + domainAttributeRangeConstraint + '\'' +
				", conceptIdsWithInvalidAttributeValue=" + conceptIdsWithInvalidAttributeValue +
				'}';
	}
}
