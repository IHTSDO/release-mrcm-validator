package org.snomed.quality.validator.mrcm;

import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;

import java.util.*;
import java.util.stream.Collectors;

public class ValidationRun {
	
	private List<ValidationType> validationTypes;
	private Map<String, Domain> MRCMDomains;
	private Map<String, List<Attribute>> attributeRangesMap;
	private List<Assertion> assertionsCompleted;
	private List<Assertion> assertionSkipped;
	private String releaseDate = null;
	private boolean isStatedView = false;
	private Set<Long> ungroupedAttributes;
	private boolean reportSkippedAssertions;

	public ValidationRun(String releaseDate, boolean isStatedView) {
		this(releaseDate, isStatedView, false);
	}

	public ValidationRun(String releaseDate, boolean isStatedView, boolean reportSkippedAssertions) {
		assertionsCompleted = new ArrayList<>();
		validationTypes = Arrays.asList(ValidationType.values());
		assertionSkipped = new ArrayList<>();
		this.releaseDate = releaseDate;
		this.isStatedView = isStatedView;
		ungroupedAttributes = new HashSet<>();
		this.reportSkippedAssertions = reportSkippedAssertions;
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

	public Map<String, List<Attribute>> getAttributeRangesMap() {
		return attributeRangesMap;
	}

	public void setAttributeRangesMap(Map<String, List<Attribute>> attributeRangesMap) {
		this.attributeRangesMap = attributeRangesMap;
	}

	public void addSkippedAssertion(Assertion skippedAssertion) {
		if (reportSkippedAssertions) {
			assertionSkipped.add(skippedAssertion);
		}
	}
	public void addCompletedAssertion(Assertion completedAssertion) {
		assertionsCompleted.add(completedAssertion);
	}
	
	public Set<Assertion> getFailedAssertions() {
		return assertionsCompleted.stream().filter(Assertion::reportAsError).filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}
	
	public Set<Assertion> getAssertionsWithWarning() {
		return assertionsCompleted.stream().filter(Assertion :: reportAsWarning).filter(Assertion :: invalidConceptsFound).collect(Collectors.toSet());
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

	public void setUngroupedAttributes(Set<Long> ungroupedAttributes) {
		this.ungroupedAttributes = ungroupedAttributes;
	}

	public Set<Long> getUngroupedAttributes() {
		return ungroupedAttributes;
	}

	public boolean reportSkippedAssertions() {
		return reportSkippedAssertions;
	}
}
