package org.snomed.quality.validator.mrcm;

import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;

import java.util.*;
import java.util.stream.Collectors;

public final class ValidationRun {

	private List<ValidationType> validationTypes;
	private Map<String, Domain> mrcmDomains;
	private Map<String, List<Attribute>> attributeRangesMap;
	private final List<Assertion> assertionsCompleted;
	private final List<Assertion> assertionSkipped;
	private final String releaseDate;
	private final ContentType contentType;
	private Set<Long> ungroupedAttributes;
	private Set<Long> conceptsUsedInMRCMTemplates;
	private final boolean reportSkippedAssertions;

	public ValidationRun(final String releaseDate, final ContentType contentType, final boolean reportSkippedAssertions) {
		assertionsCompleted = new ArrayList<>();
		validationTypes = Arrays.asList(ValidationType.values());
		assertionSkipped = new ArrayList<>();
		this.releaseDate = releaseDate;
		this.contentType = contentType;
		ungroupedAttributes = new HashSet<>();
		this.reportSkippedAssertions = reportSkippedAssertions;
	}

	public final List<ValidationType> getValidationTypes() {
		return validationTypes;
	}

	public final void setValidationTypes(final List<ValidationType> validationTypes) {
		this.validationTypes = validationTypes;
	}

	public final void setMRCMDomains(final Map<String, Domain> mrcmDomains) {
		this.mrcmDomains = mrcmDomains;
	}

	public final Map<String, Domain> getMRCMDomains() {
		return mrcmDomains;
	}

	public final Map<String, List<Attribute>> getAttributeRangesMap() {
		return attributeRangesMap;
	}

	public final void setAttributeRangesMap(final Map<String, List<Attribute>> attributeRangesMap) {
		this.attributeRangesMap = attributeRangesMap;
	}

	public final void addSkippedAssertion(final Assertion skippedAssertion) {
		if (reportSkippedAssertions) {
			assertionSkipped.add(skippedAssertion);
		}
	}

	public final void addCompletedAssertion(final Assertion completedAssertion) {
		assertionsCompleted.add(completedAssertion);
	}

	public final Set<Assertion> getFailedAssertions() {
		return assertionsCompleted.stream().filter(Assertion::reportAsError).filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}

	public final Set<Assertion> getAssertionsWithWarning() {
		return assertionsCompleted.stream().filter(Assertion::reportAsWarning).filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}

	public final List<Assertion> getCompletedAssertions() {
		return assertionsCompleted;
	}

	public final List<Assertion> getSkippedAssertions() {
		return assertionSkipped;
	}

	public final String getReleaseDate() {
		return releaseDate;
	}

	public final ContentType getContentType() {
		return contentType;
	}

	public final void setUngroupedAttributes(final Set<Long> ungroupedAttributes) {
		this.ungroupedAttributes = ungroupedAttributes;
	}

	public final Set<Long> getUngroupedAttributes() {
		return ungroupedAttributes;
	}

	public final Set<Long> getConceptsUsedInMRCMTemplates() {
		return conceptsUsedInMRCMTemplates;
	}

	public final void setConceptsUsedInMRCMTemplates(Set<Long> conceptsUsedInMRCMTemplates) {
		this.conceptsUsedInMRCMTemplates = conceptsUsedInMRCMTemplates;
	}

	public final boolean reportSkippedAssertions() {
		return reportSkippedAssertions;
	}

	@Override
	public final boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ValidationRun that = (ValidationRun) o;
		return reportSkippedAssertions == that.reportSkippedAssertions &&
				Objects.equals(validationTypes, that.validationTypes) &&
				Objects.equals(mrcmDomains, that.mrcmDomains) &&
				Objects.equals(attributeRangesMap, that.attributeRangesMap) &&
				Objects.equals(assertionsCompleted, that.assertionsCompleted) &&
				Objects.equals(assertionSkipped, that.assertionSkipped) &&
				Objects.equals(releaseDate, that.releaseDate) &&
				contentType == that.contentType &&
				Objects.equals(ungroupedAttributes, that.ungroupedAttributes);
	}

	@Override
	public final int hashCode() {
		return Objects.hash(validationTypes, mrcmDomains, attributeRangesMap, assertionsCompleted, assertionSkipped,
				releaseDate, contentType, ungroupedAttributes, reportSkippedAssertions);
	}
}
