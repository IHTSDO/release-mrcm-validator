package org.snomed.quality.validator.mrcm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.snomed.quality.validator.mrcm.model.Attribute;

public class Assertion {
	public static enum FailureType {
		ERROR("error"),
		WARNING("warning");
		String value;
		
		FailureType(String value) {
			this.value = value;
		}
		
		String getValue() {
			return this.value;
		}
	}

	private final UUID uuid;
	private Attribute attribute;
	private List<Long> currentViolatedConceptIds;
	private List<Long> previousViolatedConceptIds;
	private final String message;
	private ValidationType validationType;
	private FailureType failureType;
	private String domainConstraint;
	
	public Assertion(Attribute attribute, ValidationType type, String msg, FailureType failureType) {
		this.uuid = attribute.getUuid();
		this.attribute = attribute;
		this.validationType = type;
		this.message = msg;
		this.failureType = failureType;
		this.currentViolatedConceptIds = new ArrayList<>();
		this.previousViolatedConceptIds = new ArrayList<>();
		domainConstraint = null;
	}
	
	public Assertion(Attribute attribute, ValidationType type, String msg, FailureType failureType, List<Long> currentViolatedConceptIds) {
		this(attribute, type, msg, failureType);
		this.currentViolatedConceptIds = currentViolatedConceptIds == null ?  
				new ArrayList<>() : currentViolatedConceptIds;
		
	}
		
	public Assertion(Attribute attribute, ValidationType type, String msg, FailureType failureType,
			List<Long> currentViolatedConceptIds, List<Long> previousViolatedConceptIds, String domainConstraint) {
		this(attribute, type, msg, failureType, currentViolatedConceptIds);
		this.previousViolatedConceptIds = previousViolatedConceptIds == null ? new ArrayList<>() : previousViolatedConceptIds;
		this.domainConstraint = domainConstraint;
	}

	public List<Long> getPreviousViolatedConceptIds() {
		return previousViolatedConceptIds;
	}

	public void setPreviousViolatedConceptIds(List<Long> previousViolatedConceptIds) {
		this.previousViolatedConceptIds = previousViolatedConceptIds;
	}

	public boolean invalidConceptsFound() {
		return (currentViolatedConceptIds != null && !currentViolatedConceptIds.isEmpty()) 
				|| (previousViolatedConceptIds != null && !previousViolatedConceptIds.isEmpty());
	}
	
	public boolean reportAsError() {
		//default to error
		if (FailureType.WARNING == failureType ) {
			return false;
		}
		return true;
	}
	
	public boolean reportAsWarning() {
		//default to error
		if (FailureType.WARNING == failureType ) {
			return true;
		}
		return false;
	}
	
	public String getAssertionText() {
		String assertionText = String.format("MRCM rule must be applied to attribute:%s for content type:%s within %s constraint:", 
				attribute.getAttributeId(), attribute.getContentTypeId(), validationType.getName().toLowerCase());
		if (ValidationType.ATTRIBUTE_CARDINALITY == validationType) {
			assertionText += " [" + attribute.getAttributeCardinality() + "]";
		} else if (ValidationType.ATTRIBUTE_GROUP_CARDINALITY == validationType) {
			assertionText += " [" + attribute.getAttributeIngroupCardinality() + "]";
		} else if (ValidationType.ATTRIBUTE_RANGE == validationType) {
			assertionText += " " + attribute.getRangeConstraint();
		} else if (ValidationType.ATTRIBUTE_DOMAIN == validationType) {
			assertionText +=  domainConstraint != null ? domainConstraint : " ";
		}
		return assertionText;
	}
	
	public FailureType getFailureType() {
		return this.failureType;
	}
	
	public Attribute getAttribute() {
		return this.attribute;
	}

	@Override
	public String toString() {
		String base = "Assertion [uuid=" + uuid + ", assertionText=" + getAssertionText() + ",validationType=" + validationType + ",failureType=" + failureType.toString();
		if (message != null && !message.isEmpty()) {
			return base + " message=" + message + "]";
		} else {
			return base + "]";
		}
	}

	public UUID getUuid() {
		return uuid;
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
		result = prime * result + ((failureType == null) ? 0 : failureType.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		result = prime * result + ((validationType == null) ? 0 : validationType.hashCode());
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
		if (failureType != other.failureType)
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
		return true;
	}
	
	
}
