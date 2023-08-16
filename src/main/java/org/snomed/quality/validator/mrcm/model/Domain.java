package org.snomed.quality.validator.mrcm.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Domain {

	private final String domainId;
	private String domainConstraint;
	private final Set<Attribute> attributes;
	private final Map<String,Set<Attribute>> attributeRanges;

	public Domain(String domainId) {
		this.domainId = domainId;
		attributes = new HashSet<>();
		attributeRanges = new HashMap<>();
	}

	public void setDomainConstraint(String domainConstraint) {
		this.domainConstraint = domainConstraint;
	}

	public String getDomainId() {
		return domainId;
	}

	public String getDomainConstraint() {
		return domainConstraint;
	}

	public void addAttribute(Attribute attribute) {
		attributes.add(attribute);
	}

	public Set<Attribute> getAttributes() {
		return attributes;
	}
	
	public Set<Attribute> getAttributeRanges(String attributeId) {
		if (!attributeRanges.containsKey(attributeId)) {
			return new HashSet<>();
		} else {
			return attributeRanges.get(attributeId);
		}
	}
	
	public void addAttributeRange(Attribute attributeRange) {
		if (attributeRanges.containsKey(attributeRange.getAttributeId())) {
			attributeRanges.get(attributeRange.getAttributeId()).add(attributeRange);
		} else {
			Set<Attribute> ranges = new HashSet<>();
			ranges.add(attributeRange);
			attributeRanges.put(attributeRange.getAttributeId(), ranges);
		}
	}

	@Override
	public String toString() {
		return "Domain [domainId=" + domainId + ", domainConstraint=" + domainConstraint + "]";
	}
}
