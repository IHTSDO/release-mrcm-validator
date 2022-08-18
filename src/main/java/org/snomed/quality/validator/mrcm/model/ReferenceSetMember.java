package org.snomed.quality.validator.mrcm.model;

public class ReferenceSetMember {

	private final String memberId;
	private final String effectiveTime;
	private final boolean active;
	private final String moduleId;
	private final String refsetId;
	private final String referencedComponentId;

	public ReferenceSetMember(String memberId, String effectiveTime, boolean active, String moduleId, String refsetId, String referencedComponentId) {
		this.memberId = memberId;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.moduleId = moduleId;
		this.refsetId = refsetId;
		this.referencedComponentId = referencedComponentId;
	}

	public String getMemberId() {
		return memberId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public boolean isActive() {
		return active;
	}

	public String getModuleId() {
		return moduleId;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}
}
