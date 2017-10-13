package org.snomed.quality.validator.mrcm;

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

	public void addSkippedAssertion(Assertion skippedAssertion) {
		assertionSkipped.add(skippedAssertion);
	}
	public void addCompletedAssertion(Assertion completedAssertion) {
		assertionsCompleted.add(completedAssertion);
	}
	
	public Set<Assertion> getFailedAssertions() {
		return assertionsCompleted.stream().filter(Assertion:: reportAsError).filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}
	
	public Set<Assertion> getAssertionsWithWarning() {
		return assertionsCompleted.stream().filter(Assertion :: reportAsWarning).filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
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
