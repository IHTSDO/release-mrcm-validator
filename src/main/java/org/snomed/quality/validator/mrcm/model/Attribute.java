package org.snomed.quality.validator.mrcm.model;

import java.util.UUID;

public class Attribute {
	public static enum Type {DOMAIN, RANGE};

	private UUID uuid;

	private final String attributeId;

	private String attributeFsn;

	private final String contentTypeId;

	private String rangeConstraint;

	private String rangeRule;

	private boolean isGrouped;

	private String attributeCardinality;

	private String attributeInGroupCardinality;

	private String ruleStrengthId;

	private Type type;

	public UUID getUuid() {
		return uuid;
	}

	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public Attribute(String attributeId, String contentTypeId) {
		this.attributeId = attributeId;
		this.contentTypeId = contentTypeId;
	}

	public String getAttributeId() {
		return attributeId;
	}

	public String getContentTypeId() {
		return contentTypeId;
	}

	public void setAttributeFsn(String attributeFsn) {
		this.attributeFsn = attributeFsn;
	}

	public String  getAttributeFsn() {
		return attributeFsn;
	}

	public String getRangeConstraint() {
		return rangeConstraint;
	}

	public void setRangeConstraint(String rangeConstraint) {
		this.rangeConstraint = rangeConstraint;
	}

	public String getRangeRule() {
		return rangeRule;
	}

	public void setRangeRule(String rangeRule) {
		this.rangeRule = rangeRule;
	}

	public boolean isGrouped() {
		return isGrouped;
	}

	public void setGrouped(String grouped) {
		this.isGrouped = "1".equals(grouped) ? true:false;
	}

	public String getAttributeCardinality() {
		return attributeCardinality;
	}

	public void setAttributeCardinality(String attributeCardinality) {
		this.attributeCardinality = attributeCardinality;
	}

	public String getAttributeInGroupCardinality() {
		return attributeInGroupCardinality;
	}

	public void setAttributeInGroupCardinality(String attributeInGroupCardinality) {
		this.attributeInGroupCardinality = attributeInGroupCardinality;
	}

	public void setType(Type type) {
		this.type = type;
	}
	
	public String getRuleStrengthId() {
		return ruleStrengthId;
	}

	public void setRuleStrengthId(String ruleStrengthId) {
		this.ruleStrengthId = ruleStrengthId;
	}

	@Override
	public String toString() {
	 String result = "Attribute [attributeId=" + attributeId + ", contentTypeId=" + contentTypeId + ", ruleStrengthId=" + ruleStrengthId;
	 if (Type.RANGE == type) {
		 result += ", rangeConstraint=" + rangeConstraint;
	 } 
	 return  result +=']';
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((attributeCardinality == null) ? 0 : attributeCardinality.hashCode());
		result = prime * result + ((attributeId == null) ? 0 : attributeId.hashCode());
		result = prime * result + ((attributeInGroupCardinality == null) ? 0 : attributeInGroupCardinality.hashCode());
		result = prime * result + ((contentTypeId == null) ? 0 : contentTypeId.hashCode());
		result = prime * result + (isGrouped ? 1231 : 1237);
		result = prime * result + ((rangeConstraint == null) ? 0 : rangeConstraint.hashCode());
		result = prime * result + ((ruleStrengthId == null) ? 0 : ruleStrengthId.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		Attribute other = (Attribute) obj;
		if (attributeCardinality == null) {
			if (other.attributeCardinality != null)
				return false;
		} else if (!attributeCardinality.equals(other.attributeCardinality))
			return false;
		if (attributeId == null) {
			if (other.attributeId != null)
				return false;
		} else if (!attributeId.equals(other.attributeId))
			return false;
		if (attributeInGroupCardinality == null) {
			if (other.attributeInGroupCardinality != null)
				return false;
		} else if (!attributeInGroupCardinality.equals(other.attributeInGroupCardinality))
			return false;
		if (contentTypeId == null) {
			if (other.contentTypeId != null)
				return false;
		} else if (!contentTypeId.equals(other.contentTypeId))
			return false;
		if (isGrouped != other.isGrouped)
			return false;
		if (rangeConstraint == null) {
			if (other.rangeConstraint != null)
				return false;
		} else if (!rangeConstraint.equals(other.rangeConstraint))
			return false;
		if (ruleStrengthId == null) {
			if (other.ruleStrengthId != null)
				return false;
		} else if (!ruleStrengthId.equals(other.ruleStrengthId))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	public Type getType() {
		return this.type;
	}
}
