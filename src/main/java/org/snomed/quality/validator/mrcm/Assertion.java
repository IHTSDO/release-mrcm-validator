package org.snomed.quality.validator.mrcm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.ihtsdo.otf.sqs.service.dto.ConceptResult;
import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.ReferenceSetMember;
import org.springframework.util.CollectionUtils;

public class Assertion {
	public enum FailureType {
		ERROR("error"),
		WARNING("warning");
		final String value;
		
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
	private List<ConceptResult> currentViolatedConcepts;
	private List<ReferenceSetMember> currentViolatedReferenceSetMembers;
	private List<Long> previousViolatedConceptIds;
	private List<ConceptResult> previousViolatedConcepts;
	private final String message;
	private final ValidationType validationType;
	private ValidationSubType validationSubType;
	private final FailureType failureType;
	private String domainConstraint;
	private String failureMessage;

	public Assertion(UUID uuid, ValidationType type, String msg, FailureType failureType) {
		this.uuid =uuid;
		this.validationType = type;
		this.message = msg;
		this.failureType = failureType;
		this.currentViolatedConceptIds = new ArrayList<>();
		this.previousViolatedConceptIds = new ArrayList<>();
		this.currentViolatedConcepts = new ArrayList<>();
	}

	public Assertion(Attribute attribute, ValidationType type, String msg, FailureType failureType) {
		this.uuid = attribute.getUuid();
		this.attribute = attribute;
		this.validationType = type;
		this.message = msg;
		this.failureType = failureType;
		this.currentViolatedConceptIds = new ArrayList<>();
		this.previousViolatedConceptIds = new ArrayList<>();
		this.currentViolatedConcepts = new ArrayList<>();
	}
	
	public Assertion(Attribute attribute, ValidationType type, String msg, FailureType failureType, List<Long> currentViolatedConceptIds) {
		this(attribute, type, msg, failureType);
		this.currentViolatedConceptIds = currentViolatedConceptIds == null ? new ArrayList<>() : currentViolatedConceptIds;
	}
		
	public Assertion(Attribute attribute, ValidationType type, String msg, FailureType failureType,
					  List<ConceptResult> currentViolatedConcepts, List<ConceptResult> previousViolatedConcepts, String domainConstraint) {
		this(attribute, type, msg, failureType, currentViolatedConcepts == null ? new ArrayList<>() : currentViolatedConcepts.stream().map(ConceptResult::getId).map(Long::parseLong).collect(Collectors.toList()));
		this.previousViolatedConceptIds = previousViolatedConcepts == null ? new ArrayList<>() : previousViolatedConcepts.stream().map(ConceptResult::getId).map(Long::parseLong).collect(Collectors.toList());
		this.domainConstraint = domainConstraint;
		this.currentViolatedConcepts = currentViolatedConcepts;
		this.previousViolatedConcepts = previousViolatedConcepts == null ? new ArrayList<>() : previousViolatedConcepts;
	}

	public Assertion(Attribute attribute, ValidationType type, ValidationSubType subType, String msg, FailureType failureType,
					 List<ConceptResult> currentViolatedConcepts, List<ConceptResult> previousViolatedConcepts, String domainConstraint) {
		this(attribute, type, msg, failureType, currentViolatedConcepts == null ? new ArrayList<>() : currentViolatedConcepts.stream().map(ConceptResult::getId).map(Long::parseLong).collect(Collectors.toList()));
		this.validationSubType = subType;
		this.previousViolatedConceptIds = previousViolatedConcepts == null ? new ArrayList<>() : previousViolatedConcepts.stream().map(ConceptResult::getId).map(Long::parseLong).collect(Collectors.toList());
		this.domainConstraint = domainConstraint;
		this.currentViolatedConcepts = currentViolatedConcepts;
		this.previousViolatedConcepts = previousViolatedConcepts == null ? new ArrayList<>() : previousViolatedConcepts;
	}

	public List<ConceptResult> getCurrentViolatedConcepts() {
		return currentViolatedConcepts;
	}

	public void setCurrentViolatedConcepts(List<ConceptResult> currentViolatedConcepts) {
		this.currentViolatedConcepts = currentViolatedConcepts;
	}

	public List<ReferenceSetMember> getCurrentViolatedReferenceSetMembers() {
		return currentViolatedReferenceSetMembers;
	}

	public void setCurrentViolatedReferenceSetMembers(List<ReferenceSetMember> currentViolatedReferenceSetMembers) {
		this.currentViolatedReferenceSetMembers = currentViolatedReferenceSetMembers;
	}

	public void setCurrentViolatedConceptIds(List<Long> currentViolatedConceptIds) {
		this.currentViolatedConceptIds = currentViolatedConceptIds;
	}

	public List<ConceptResult> getPreviousViolatedConcepts() {
		return previousViolatedConcepts;
	}

	public List<Long> getPreviousViolatedConceptIds() {
		return previousViolatedConceptIds;
	}

	public void setPreviousViolatedConceptIds(List<Long> previousViolatedConceptIds) {
		this.previousViolatedConceptIds = previousViolatedConceptIds;
	}

	public boolean invalidConceptsFound() {
		return (currentViolatedConceptIds != null && !currentViolatedConceptIds.isEmpty()) 
				|| (previousViolatedConceptIds != null && !previousViolatedConceptIds.isEmpty())
				|| (currentViolatedReferenceSetMembers != null && !currentViolatedReferenceSetMembers.isEmpty());
	}

	public boolean invalidConceptsNotFound() {
		return CollectionUtils.isEmpty(currentViolatedConceptIds) && CollectionUtils.isEmpty(previousViolatedConceptIds)
				&& CollectionUtils.isEmpty(currentViolatedReferenceSetMembers);
	}
	
	public boolean reportAsError() {
		//default to error
        return FailureType.WARNING != failureType;
    }
	
	public boolean reportAsWarning() {
		//default to error
        return FailureType.WARNING == failureType;
    }
	
	public String getAssertionText() {
		if (ValidationSubType.ATTRIBUTE_RANGE_INACTIVE_CONCEPT == validationSubType
				|| ValidationSubType.ATTRIBUTE_RANGE_INVALID_CONCEPT == validationSubType
				|| ValidationSubType.ATTRIBUTE_RANGE_INVALID_TERM == validationSubType
				|| ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE == validationType) {
			return getMessage();
		}
		String assertionText = String.format("%s must conform to the MRCM %s",
				attribute.getAttributeId() + (attribute.getAttributeFsn() == null ? "" : " |" + attribute.getAttributeFsn() + "|"),
				validationType.getName().toLowerCase());

		if (ValidationType.ATTRIBUTE_CARDINALITY == validationType) {
			assertionText += " [" + attribute.getAttributeCardinality() + "]";
		} else if (ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY == validationType) {
			assertionText += " [" + attribute.getAttributeInGroupCardinality() + "]";
		} else if (ValidationType.ATTRIBUTE_RANGE == validationType || ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE == validationType) {
			assertionText = String.format("The attribute value of %s must conform to the MRCM %s",
					attribute.getAttributeId() + (attribute.getAttributeFsn() == null ? "" : " |" + attribute.getAttributeFsn() + "|"),
					validationType.getName().toLowerCase());
			if (ValidationType.ATTRIBUTE_RANGE == validationType) {
				assertionText += " " + attribute.getRangeConstraint();
			}
		} else if (ValidationType.ATTRIBUTE_DOMAIN == validationType) {
			assertionText += domainConstraint != null ? (" " + domainConstraint) : " ";
		}
		return assertionText;
	}

	public String getDetails() {
		String detail = null;
		if (ValidationType.ATTRIBUTE_CARDINALITY == validationType) {
			detail = String.format("The MRCM attribute cardinality is [%s] for %s but found %s",
					attribute.getAttributeCardinality(),
					attribute.getAttributeId() + (attribute.getAttributeFsn() == null ? "" : " |" + attribute.getAttributeFsn() + "|"),
					getFailureCardinalityMessage(attribute.getAttributeCardinality()));
		} else if (ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY == validationType) {
			detail = String.format("The MRCM attribute group cardinality is [%s] for %s but found %s",
					attribute.getAttributeInGroupCardinality(),
					attribute.getAttributeId() + (attribute.getAttributeFsn() == null ? "" : " |" + attribute.getAttributeFsn() + "|"),
					getFailureCardinalityMessage(attribute.getAttributeInGroupCardinality()));
		} else if (ValidationType.ATTRIBUTE_RANGE == validationType) {
			detail = String.format("Attribute %s has value which is not conformed to the MRCM %s",
					attribute.getAttributeId() + (attribute.getAttributeFsn() == null ? "" : " |" + attribute.getAttributeFsn() + "|"),
					validationType.getName().toLowerCase());
			if (ValidationSubType.ATTRIBUTE_RANGE_INACTIVE_CONCEPT == validationSubType) {
				detail = "The concept is inactive";
			} else if (ValidationSubType.ATTRIBUTE_RANGE_INVALID_CONCEPT == validationSubType) {
				detail = "The concept doest not exist";
			} else if (ValidationSubType.ATTRIBUTE_RANGE_INVALID_TERM == validationSubType) {
				detail = "The term is invalid";
			} else {
				detail += " " + attribute.getRangeConstraint();
			}
		} else if (ValidationType.ATTRIBUTE_DOMAIN == validationType) {
			detail = String.format(" %s is applied to concepts not conforming to the MRCM %s ",
					attribute.getAttributeId() + (attribute.getAttributeFsn() == null ? "" : " |" + attribute.getAttributeFsn() + "|"),
					validationType.getName().toLowerCase());
			detail +=  domainConstraint != null ? domainConstraint : " ";
		} else if (ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE == validationType && !CollectionUtils.isEmpty(this.currentViolatedConcepts)) {
			detail = "Concept Id= %s should be %s Lateralizable reference set";
		}
		return detail;
	}

	public String getFailureMessage() {
		return failureMessage;
	}

	public void setFailureMessage(String failureMessage) {
		this.failureMessage = failureMessage;
	}

	private String getFailureCardinalityMessage(String cardinality) {
		char minCardinality = getMinCardinality(cardinality);
		char maxCardinality = getMaxCardinality(cardinality);

		boolean isMaxGreaterThanMin = Character.getNumericValue(maxCardinality) > Character.getNumericValue(minCardinality);
		if (minCardinality == '0') {
			if (maxCardinality != '*' && isMaxGreaterThanMin) {
				return "more than " + maxCardinality;
			}
		}
		else {
			if (maxCardinality == '*') {
				return "less than " + minCardinality;
			} else if (minCardinality == maxCardinality) {
				return  "more than " + minCardinality + " or don't have " + minCardinality + " at all";
			} else if (isMaxGreaterThanMin) {
				return "less than " + minCardinality + " or more than " + maxCardinality;
			}
		}
		return  "";
	}

	private char getMinCardinality(String cardinality) {
		return cardinality.charAt(0);
	}

	private char getMaxCardinality(String cardinality) {
		return cardinality.charAt(cardinality.length() - 1);
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
        return validationType == other.validationType;
    }
	
	
}
