package org.snomed.quality.validator.mrcm;

import java.util.List;
import java.util.UUID;

import org.snomed.quality.validator.mrcm.model.Attribute;

public class Assertion {
	private final UUID uuid;
	private final Attribute attribute;
	private final List<Long> conceptIdsWithInvalidAttributeValue;
	private final String assertionText;

	public Assertion(Attribute attribute, String assertionText, List<Long> conceptIdsWithInvalidAttributeValue) {
		this.attribute = attribute;
		this.uuid = attribute.getUuid();
		this.assertionText = assertionText;
		this.conceptIdsWithInvalidAttributeValue = conceptIdsWithInvalidAttributeValue;
	}

	public boolean invalidConceptsFound() {
		return !conceptIdsWithInvalidAttributeValue.isEmpty();
	}

	@Override
	public String toString() {
		return "Assertion [uuid=" + uuid + ", attribute=" + attribute + ", conceptIdsWithInvalidAttributeValue=" + conceptIdsWithInvalidAttributeValue + "]";
	}

	public UUID getUuid() {
		return uuid;
	}

	public Attribute getAttribute() {
		return attribute;
	}

	public List<Long> getConceptIdsWithInvalidAttributeValue() {
		return conceptIdsWithInvalidAttributeValue;
	}

	public String getAssertionText() {
		return assertionText;
	}
}
