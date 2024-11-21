package org.snomed.quality.validator.mrcm;

import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;
import org.snomed.quality.validator.mrcm.model.ReferenceSetMember;

import java.util.*;
import java.util.stream.Collectors;

public final class ValidationRun {

	private List<ValidationType> validationTypes;
	private Map<String, Domain> mrcmDomains;
	private Set<ReferenceSetMember> lateralizableRefsetMembers;
	private List<ReferenceSetMember> anatomyStructureAndPartRefsets;
	private List<ReferenceSetMember> anatomyStructureAndEntireRefsets;
	private Map<String, List<Attribute>> attributeRangesMap;
	private final List<Assertion> assertionsCompleted;
	private final List<Assertion> assertionSkipped;
	private final List<Assertion> assertionsIncomplete;
	private final String releaseDate;
	private final ContentType contentType;
	private Set<Long> ungroupedAttributes;
	private Set<Long> conceptsUsedInMRCMTemplates;
	private Set<String> moduleIds;
	private final boolean reportSkippedAssertions;
	private boolean fullSnapshotRelease;

	public ValidationRun(final String releaseDate, final ContentType contentType, final boolean reportSkippedAssertions) {
		assertionsCompleted = new ArrayList<>();
		assertionsIncomplete = new ArrayList<>();
		validationTypes = Arrays.asList(ValidationType.values());
		assertionSkipped = new ArrayList<>();
		this.releaseDate = releaseDate;
		this.contentType = contentType;
		ungroupedAttributes = new HashSet<>();
		this.reportSkippedAssertions = reportSkippedAssertions;
	}

	public List<ValidationType> getValidationTypes() {
		return validationTypes;
	}

	public void setValidationTypes(final List<ValidationType> validationTypes) {
		this.validationTypes = validationTypes;
	}

	public void setMRCMDomains(final Map<String, Domain> mrcmDomains) {
		this.mrcmDomains = mrcmDomains;
	}

	public Map<String, Domain> getMRCMDomains() {
		return mrcmDomains;
	}

	public Map<String, List<Attribute>> getAttributeRangesMap() {
		return attributeRangesMap;
	}

	public void setAttributeRangesMap(final Map<String, List<Attribute>> attributeRangesMap) {
		this.attributeRangesMap = attributeRangesMap;
	}

	public void addSkippedAssertion(final Assertion skippedAssertion) {
		if (reportSkippedAssertions) {
			assertionSkipped.add(skippedAssertion);
		}
	}

	public void addCompletedAssertion(final Assertion completedAssertion) {
		assertionsCompleted.add(completedAssertion);
	}

	public void addIncompleteAssertion(final Assertion incompleteAssertion) {
		assertionsIncomplete.add(incompleteAssertion);
	}

	public Set<Assertion> getIncompleteAssertions() {
		return new HashSet<>(assertionsIncomplete);
	}

	public Set<Assertion> getFailedAssertions() {
		return assertionsCompleted.stream().filter(Assertion::reportAsError).filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
	}

	public Set<Assertion> getPassedAssertions() {
		return assertionsCompleted.stream().filter(Assertion::invalidConceptsNotFound).collect(Collectors.toSet());
	}

	public Set<Assertion> getAssertionsWithWarning() {
		return assertionsCompleted.stream().filter(Assertion::reportAsWarning).filter(Assertion::invalidConceptsFound).collect(Collectors.toSet());
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

	public ContentType getContentType() {
		return contentType;
	}

	public void setUngroupedAttributes(final Set<Long> ungroupedAttributes) {
		this.ungroupedAttributes = ungroupedAttributes;
	}

	public Set<Long> getUngroupedAttributes() {
		return ungroupedAttributes;
	}

	public Set<Long> getConceptsUsedInMRCMTemplates() {
		return conceptsUsedInMRCMTemplates;
	}

	public void setConceptsUsedInMRCMTemplates(Set<Long> conceptsUsedInMRCMTemplates) {
		this.conceptsUsedInMRCMTemplates = conceptsUsedInMRCMTemplates;
	}

	public boolean reportSkippedAssertions() {
		return reportSkippedAssertions;
	}

	public void setFullSnapshotRelease(boolean fullSnapshotRelease) {
		this.fullSnapshotRelease = fullSnapshotRelease;
	}

	public boolean isFullSnapshotRelease() {
		return fullSnapshotRelease;
	}

	public void setModuleIds(Set<String> moduleIds) {
		this.moduleIds = moduleIds;
	}

	public Set<String> getModuleIds() {
		return moduleIds;
	}

	public void setLateralizableRefsetMembers(Set<ReferenceSetMember> lateralizableRefsetMembers) {
		this.lateralizableRefsetMembers = lateralizableRefsetMembers;
	}

	public Set<ReferenceSetMember> getLateralizableRefsetMembers() {
		return lateralizableRefsetMembers;
	}

	public void setAnatomyStructureAndEntireRefsets(List<ReferenceSetMember> anatomyStructureAndEntireRefsets) {
		this.anatomyStructureAndEntireRefsets = anatomyStructureAndEntireRefsets;
	}

	public List<ReferenceSetMember> getAnatomyStructureAndEntireRefsets() {
		return anatomyStructureAndEntireRefsets;
	}

	public void setAnatomyStructureAndPartRefsets(List<ReferenceSetMember> anatomyStructureAndPartRefsets) {
		this.anatomyStructureAndPartRefsets = anatomyStructureAndPartRefsets;
	}

	public List<ReferenceSetMember> getAnatomyStructureAndPartRefsets() {
		return anatomyStructureAndPartRefsets;
	}

	@Override
	public boolean equals(final Object o) {
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
	public int hashCode() {
		return Objects.hash(validationTypes, mrcmDomains, attributeRangesMap, assertionsCompleted, assertionSkipped,
				releaseDate, contentType, ungroupedAttributes, reportSkippedAssertions);
	}
}
