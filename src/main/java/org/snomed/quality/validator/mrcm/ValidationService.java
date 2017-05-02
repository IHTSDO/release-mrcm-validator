package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.ihtsdo.otf.sqs.service.ReleaseImportManager;
import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.ihtsdo.otf.sqs.service.store.ReleaseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ValidationService {

	public static final String MRCM_DOMAIN_REFSET = "723560006";
	public static final String MRCM_ATTRIBUTE_DOMAIN_REFSET = "723561005";
	public static final String MRCM_ATTRIBUTE_RANGE_REFSET = "723562003";
	public static final LoadingProfile MRCM_REFSET_LOADING_PROFILE = new LoadingProfile()
			.withRefsets(MRCM_DOMAIN_REFSET, MRCM_ATTRIBUTE_DOMAIN_REFSET, MRCM_ATTRIBUTE_RANGE_REFSET)
			.withFullRefsetMemberObjects()
			.withJustRefsets();
	public static final String ALL_NEW_PRECOORDINATED_CONTENT_CONCEPT = "723593002";
	private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

	public void loadMRCM(File sourceDirectory, ValidationRun run) throws ReleaseImportException {
		MRCMFactory mrcmFactory = new MRCMFactory();
		new ReleaseImporter().loadSnapshotReleaseFiles(sourceDirectory.getPath(), MRCM_REFSET_LOADING_PROFILE, mrcmFactory);
		Map<String, Domain> domains = mrcmFactory.getDomains();
		Assert.notEmpty(domains, "No MRCM Domains Found");
		for (Domain domain : domains.values()) {
			Assert.notNull(domain.getDomainConstraint(), "Constraint for domain " + domain.getDomainId() + " must not be null.");
		}
		run.setMRCMDomains(domains);
	}

	public void validateRelease(File releaseDirectory, ValidationRun run) throws ReleaseImportException, IOException, ServiceException {
		ReleaseStore releaseStore = new ReleaseImportManager().loadReleaseFilesToMemoryBasedIndex(releaseDirectory,
				LoadingProfile.light
						.withStatedRelationships().withStatedAttributeMapOnConcept()
						.withoutInferredAttributeMapOnConcept());

		SnomedQueryService queryService = new SnomedQueryService(releaseStore);

		List<Long> precoordinatedTypes = queryService.eclQueryReturnConceptIdentifiers("<<" + ALL_NEW_PRECOORDINATED_CONTENT_CONCEPT, 0, 100).getConceptIds();
		Assert.notEmpty(precoordinatedTypes, "Concept " + ALL_NEW_PRECOORDINATED_CONTENT_CONCEPT + " and descendants must be accessible.");

		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (precoordinatedTypes.contains(Long.parseLong(attribute.getContentTypeId()))) {

					String domainConstraint = domain.getDomainConstraint();
					String attributeId = attribute.getAttributeId();
					String rangeConstraint = attribute.getRangeConstraint();

					// Find concepts in this domain where the value of this attribute is outside of the permitted range

					LOGGER.info("Asserting domain:'{}', attribute:'{}', range:'{}'", domainConstraint, attributeId, rangeConstraint);
					if (rangeConstraint == null) {
						LOGGER.error("This range constraint is null. Are the MRCM reference sets correct?");
						continue;
					}

					String baseEcl = domainConstraint;
					baseEcl += baseEcl.contains(":") ? ", " : ": ";
					baseEcl += attributeId + " = ";
					String matchAllAttributeValues = baseEcl + "*";
					String matchAttributeValuesWithinRange = containsMultipleSCTIDs(rangeConstraint)
							? baseEcl + "(" + rangeConstraint + ")"
							: baseEcl + rangeConstraint;

					if (matchAttributeValuesWithinRange.contains("[")) {
						// TODO: Implement cardinality within the snomed-query-service project.
						LOGGER.warn("Cardinality is not currently supported. This assertion will be skipped.");
						continue;
					}

					// All concepts
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' with any range using expression '{}'", domainConstraint, attributeId, matchAllAttributeValues);
					List<Long> conceptsWithAnyAttributeValue = queryService.eclQueryReturnConceptIdentifiers(matchAllAttributeValues, 0, -1).getConceptIds();

					LOGGER.info("Selecting content within domain '{}' with attribute '{}' within range constraint using expression '{}'", domainConstraint, attributeId, matchAttributeValuesWithinRange);
					List<Long> conceptsWithAttributeValueWithinRange = queryService.eclQueryReturnConceptIdentifiers(matchAttributeValuesWithinRange, 0, -1).getConceptIds();

					List<Long> conceptIdsWithInvalidAttributeValue = new ArrayList<>();
					if (conceptsWithAnyAttributeValue.size() > conceptsWithAttributeValueWithinRange.size()) {
						LOGGER.info("Invalid content found. Collecting identifiers.");
						conceptIdsWithInvalidAttributeValue.addAll(conceptsWithAnyAttributeValue);
						conceptIdsWithInvalidAttributeValue.removeAll(conceptsWithAttributeValueWithinRange);
					}
					run.addCompletedAssertion(domain, attribute, rangeConstraint, conceptIdsWithInvalidAttributeValue);
				}
			}
		}
	}

	private boolean containsMultipleSCTIDs(String expression) {
		return expression.matches(".*(\\d){6,18}[^\\d]*(\\d){6,18}.*");
	}

	private class MRCMFactory extends ImpotentComponentFactory {

		private static final int domDomainConstraintIndex = 0;
		private static final int attDomainIdIndex = 0;
		private static final int ranDomainIdIndex = 0;
		private static final int ranRangeConstraintIndex = 0;
		private static final int ranContentTypeIndex = 3;
		public static final int attContentTypeIndex = 5;

		private Map<String, Domain> domains = new HashMap<>();
		private Map<String, Attribute> attributes = new HashMap<>();

		@Override
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			synchronized (this) {
				if ("1".equals(active)) {
					switch (refsetId) {
						case MRCM_DOMAIN_REFSET:
							getCreateDomain(referencedComponentId).setDomainConstraint(otherValues[domDomainConstraintIndex]);
							break;
						case MRCM_ATTRIBUTE_DOMAIN_REFSET:
							// TODO use grouping and cardinality rules
							Attribute attribute = getCreateAttribute(referencedComponentId, otherValues[attContentTypeIndex]);
							getCreateDomain(otherValues[attDomainIdIndex]).addAttribute(attribute);
							break;
						case MRCM_ATTRIBUTE_RANGE_REFSET:
							getCreateAttribute(referencedComponentId, otherValues[ranContentTypeIndex])
									.setRangeConstraint(otherValues[ranRangeConstraintIndex]);
							break;
					}
				}
			}
		}

		public Map<String, Domain> getDomains() {
			return domains;
		}

		private Domain getCreateDomain(String referencedComponentId) {
			if (!domains.containsKey(referencedComponentId)) {
				domains.put(referencedComponentId, new Domain(referencedComponentId));
			}
			return domains.get(referencedComponentId);
		}

		public Attribute getCreateAttribute(String attributeId, String contentTypeId) {
			String key = attributeId + "|" + contentTypeId;
			if (!attributes.containsKey(key)) {
				attributes.put(key, new Attribute(attributeId, contentTypeId));
			}
			return attributes.get(key);
		}
	}
}
