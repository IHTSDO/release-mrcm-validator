package org.snomed.quality.validator.mrcm;

import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidationRun {

	private Map<String, Domain> MRCMDomains;
	private Set<Assertion> assertionsCompleted;

	public ValidationRun() {
		assertionsCompleted = new HashSet<>();
	}

	public void setMRCMDomains(Map<String, Domain> MRCMDomains) {
		this.MRCMDomains = MRCMDomains;
	}

	public Map<String, Domain> getMRCMDomains() {
		return MRCMDomains;
	}

	public void addCompletedAssertion(Domain domain, Attribute attribute, String domainAttributeRangeConstraint, List<Long> conceptIdsWithInvalidAttributeValue) {
		assertionsCompleted.add(new Assertion(domain, attribute, domainAttributeRangeConstraint, conceptIdsWithInvalidAttributeValue));
	}

	public Set<Assertion> getFailedAssertions() {
		return assertionsCompleted.stream().filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}
}
