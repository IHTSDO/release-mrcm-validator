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
	
	private List<ValidationType> validationTypes;
	private Map<String, Domain> MRCMDomains;
	private List<Assertion> assertionsCompleted;
	private List<Assertion> assertionSkipped;
	private String releaseDate = null;
	private boolean isStatedView = false;

	public ValidationRun(String releaseDate, boolean isStatedView) {
		assertionsCompleted = new ArrayList<>();
		validationTypes = Arrays.asList(ValidationType.values());
		assertionSkipped = new ArrayList<>();
		this.releaseDate = releaseDate;
		this.isStatedView = isStatedView;
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

	public void addSkippedAssertion(Attribute attribute, ValidationType type, String msg) {
		assertionSkipped.add(new Assertion(attribute, type, "Skipped reason:" + msg));
	}
	public void addCompletedAssertion(Attribute attribute, ValidationType type, String message, 
			List<Long> currentViolatedConceptIds, List<Long> previousViolatedConceptIds, String domainConstraint) {
		assertionsCompleted.add(new Assertion(attribute, type, message, currentViolatedConceptIds, previousViolatedConceptIds, domainConstraint));
	}
	
	public void addCompletedAssertion(Attribute attribute, ValidationType type, String message, List<Long> currentViolatedConceptIds) {
		assertionsCompleted.add(new Assertion(attribute, type, message, currentViolatedConceptIds));
	}

	public Set<Assertion> getFailedAssertions() {
		return assertionsCompleted.stream().filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}
	public List<Assertion> getCompletedAssertions() {
		return assertionsCompleted;
	}

	public List<Assertion> getSkippedAssertions() {
		return assertionSkipped;
	}

	public String getReleaseDate() {
		return releaseDate;
	}

	public boolean isStatedView() {
		return isStatedView;
	}
	
}
