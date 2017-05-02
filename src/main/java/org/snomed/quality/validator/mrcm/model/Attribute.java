package org.snomed.quality.validator.mrcm.model;

import java.util.HashMap;
import java.util.Map;

public class Attribute {

	private final String attributeId;
	private final String contentTypeId;
	private String rangeConstraint;

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

	public String getRangeConstraint() {
		return rangeConstraint;
	}

	public void setRangeConstraint(String rangeConstraint) {
		this.rangeConstraint = rangeConstraint;
	}

	@Override
	public String toString() {
		return "Attribute{" +
				"attributeId='" + attributeId + '\'' +
				", contentTypeId='" + contentTypeId + '\'' +
				", rangeConstraint='" + rangeConstraint + '\'' +
				'}';
	}
}
