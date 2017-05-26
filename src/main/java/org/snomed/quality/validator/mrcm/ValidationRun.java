package org.snomed.quality.validator.mrcm;

import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidationRun {
	
	public enum ValidationType {
		ATTRIBUTE_DOMAIN("Attribute domain"),
		ATTRIBUTE_RANGE("Attribute domain range");
		private String name;
		private ValidationType(String name) {
			this.name=name;
		}
		String getName() {
			return this.name;
		}
		
	}
	private List<ValidationType> validationTypes;
	private Map<String, Domain> MRCMDomains;
	private Set<Assertion> assertionsCompleted;
	private Set<Assertion> assertionSkipped;

	public ValidationRun() {
		assertionsCompleted = new HashSet<>();
		validationTypes = Arrays.asList(ValidationType.values());
		assertionSkipped = new HashSet<>();
	}
	
	public List<ValidationType> getValidationTypes() {
		return validationTypes;
	}

	public void setValidationTypes(List<ValidationType> validationTypes) {
		this.validationTypes = validationTypes;
	}

	public void setMRCMDomains(Map<String, Domain> MRCMDomains) {
		this.MRCMDomains = MRCMDomains;
	}

	public Map<String, Domain> getMRCMDomains() {
		return MRCMDomains;
	}

	public void addSkippedAssertion(Attribute attribute,String assertionText) {
		assertionSkipped.add(new Assertion(attribute, assertionText, new ArrayList<Long>()));
	}
	public void addCompletedAssertion(Attribute attribute,String assertionText,List<Long> conceptIdsWithInvalidAttributeValue) {
		assertionsCompleted.add(new Assertion(attribute, assertionText, conceptIdsWithInvalidAttributeValue));
	}

	public Set<Assertion> getFailedAssertions() {
		return assertionsCompleted.stream().filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}
	public Set<Assertion> getCompletedAssertions() {
		return assertionsCompleted;
	}

	public Set<Assertion> getSkippedAssertions() {
		return assertionSkipped;
	}
}
