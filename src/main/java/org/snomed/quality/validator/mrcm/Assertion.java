package org.snomed.quality.validator.mrcm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.snomed.quality.validator.mrcm.model.Attribute;

public class Assertion {
	private final UUID uuid;
	private final Attribute attribute;
	private List<Long> currentViolatedConceptIds;
	private List<Long> previousViolatedConceptIds;
	private final String message;
	private ValidationType validationType;
	
	public Assertion(Attribute attribute, ValidationType type, String msg) {
		this.attribute = attribute;
		this.uuid = attribute.getUuid();
		this.message = msg;
		this.currentViolatedConceptIds = new ArrayList<>();
		this.previousViolatedConceptIds = new ArrayList<>();
		this.validationType = type;
	}
	
	public Assertion(Attribute attribute, ValidationType type, String msg, List<Long> currentViolatedConceptIds) {
		this(attribute, type, msg);
		this.currentViolatedConceptIds = currentViolatedConceptIds;
	}
	
	public Assertion(Attribute attribute, ValidationType type, String msg, 
			List<Long> currentViolatedConceptIds, List<Long> previousViolatedConceptIds) {
		this(attribute, type, msg, currentViolatedConceptIds);
		this.previousViolatedConceptIds = previousViolatedConceptIds;
	}
	
	public List<Long> getPreviousViolatedConceptIds() {
		return previousViolatedConceptIds;
	}

	public void setPreviousViolatedConceptIds(List<Long> previousViolatedConceptIds) {
		this.previousViolatedConceptIds = previousViolatedConceptIds;
	}

	public boolean invalidConceptsFound() {
		return (!currentViolatedConceptIds.isEmpty() || !previousViolatedConceptIds.isEmpty());
	}
	
	
	public String getAssertionText() {
		String assertionText = "MRCM rule must be applied to attribute:" 
				+ attribute.getAttributeId() +  " within content type:" + attribute.getContentTypeId() + " with constraint:" + validationType.getName();
		if (ValidationType.ATTRIBUTE_CARDINALITY == validationType) {
			assertionText += " [" + attribute.getAttributeCardinality() + "]";
		} else if (ValidationType.ATTRIBUTE_GROUP_CARDINALITY == validationType) {
			assertionText += " [" + attribute.getAttributeIngroupCardinality() + "]";
		} else if (ValidationType.ATTRIBUTE_RANGE == validationType) {
			assertionText += " " + attribute.getRangeConstraint();
		}
		return assertionText;
	}

	@Override
	public String toString() {
		String base = "Assertion [uuid=" + uuid + ", attribute=" + attribute + ",validationType=" + validationType;
		if (message != null && !message.isEmpty()) {
			return base + " message=" + message + "]";
		} else {
			return base + "]";
		}
	}

	public UUID getUuid() {
		return uuid;
	}

	public Attribute getAttribute() {
		return attribute;
	}

	public List<Long> getCurrentViolatedConceptIds() {
		return currentViolatedConceptIds;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((attribute == null) ? 0 : attribute.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		result = prime * result + ((validationType == null) ? 0 : validationType.hashCode());
		result = prime * result + ((currentViolatedConceptIds == null) ? 0 : currentViolatedConceptIds.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Assertion other = (Assertion) obj;
		if (attribute == null) {
			if (other.attribute != null)
				return false;
		} else if (!attribute.equals(other.attribute))
			return false;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!message.equals(other.message))
			return false;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		if (validationType != other.validationType)
			return false;
		if (currentViolatedConceptIds == null) {
			if (other.currentViolatedConceptIds != null)
				return false;
		} else if (!currentViolatedConceptIds.equals(other.currentViolatedConceptIds))
			return false;
		return true;
	}
	
}
