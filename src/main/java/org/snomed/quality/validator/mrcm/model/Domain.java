package org.snomed.quality.validator.mrcm.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Domain {

	private String domainId;
	private String domainConstraint;
	private final List<Attribute> attributes;

	public Domain(String domainId) {
		this.domainId = domainId;
		attributes = new ArrayList<>();
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

	public List<Attribute> getAttributes() {
		return attributes;
	}

	@Override
	public String toString() {
		return "Domain{" +
				"domainConstraint='" + domainConstraint + '\'' +
				'}';
	}
}
