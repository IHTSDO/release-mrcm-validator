package org.snomed.quality.validator.mrcm.model;

public record ReferenceSetMember(String memberId, String effectiveTime, boolean active, String moduleId,
								 String refsetId, String referencedComponentId, String... otherValues) {


}
