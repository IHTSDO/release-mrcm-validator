package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.ihtsdo.otf.sqs.service.ReleaseImportManager;
import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.dto.ConceptResult;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.ihtsdo.otf.sqs.service.store.ReleaseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.quality.validator.mrcm.ValidationType;
import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Attribute.Type;
import org.snomed.quality.validator.mrcm.model.Domain;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ValidationService {
	private static final String RULE_STRENGTH_IS_NOT_MANDATORY = "Rule strength is not mandatory";
	private static final String CONTENT_TYPE_IS_OUT_OF_SCOPE = "Content type is out of scope:";
	private static final String NO_CARDINALITY_CONSTRAINT = "0..*";
	public static final String MANDATORY = "723597001";
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

	public void validateRelease(File releaseDirectory, ValidationRun run) throws ReleaseImportException, IOException, ServiceException, ParseException {
		LoadingProfile profile = run.isStatedView() ? 
				LoadingProfile.light.withFullRelationshipObjects().withStatedRelationships()
				.withStatedAttributeMapOnConcept().withoutAllRefsets().withoutInferredAttributeMapOnConcept() : LoadingProfile.light.withFullRelationshipObjects().withoutAllRefsets();
		ReleaseStore releaseStore = new ReleaseImportManager().loadReleaseFilesToMemoryBasedIndex(releaseDirectory, profile);
		SnomedQueryService queryService = new SnomedQueryService(releaseStore);
		//checking data is loaded properly
		LOGGER.info("Total concepts loaded:" + queryService.getConceptCount());
		
		List<Long> precoordinatedTypes = queryService.eclQueryReturnConceptIdentifiers("<<" + ALL_NEW_PRECOORDINATED_CONTENT_CONCEPT, 0, 100).getConceptIds();
		Assert.notEmpty(precoordinatedTypes, "Concept " + ALL_NEW_PRECOORDINATED_CONTENT_CONCEPT + " and descendants must be accessible.");
		for (ValidationType type : run.getValidationTypes()) {
			switch (type) {
			case ATTRIBUTE_DOMAIN :
				executeAttributeDomainValidation(run, queryService, precoordinatedTypes);
				break;
			case ATTRIBUTE_RANGE :
				executeAttributeRangeValidation(run, queryService, precoordinatedTypes);
				break;
			case ATTRIBUTE_CARDINALITY :
				executeAttributeCardinalityValidation(run, queryService, precoordinatedTypes);
				break;
			case ATTRIBUTE_GROUP_CARDINALITY :
				executeAttributeGroupCardinalityValidation(run, queryService, precoordinatedTypes);
				break;
			default :
				LOGGER.error("ValidationType:" + type + " is not implemented yet!");
				break;
			}
		}
	}

	private void executeAttributeGroupCardinalityValidation(ValidationRun run, SnomedQueryService queryService, List<Long> precoordinatedTypes) throws ServiceException {
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (!precoordinatedTypes.contains(Long.parseLong(attribute.getContentTypeId()))) {
					//skip
					run.addSkippedAssertion(attribute, ValidationType.ATTRIBUTE_GROUP_CARDINALITY, CONTENT_TYPE_IS_OUT_OF_SCOPE + attribute.getContentTypeId());
					continue;
				}
				String domainPartEcl = "<<" + domain.getDomainId() + ":"; 
				//				String domainPartEcl = "*" + ":"; 
				String attributePartEcl = "<<" + attribute.getAttributeId() + "=*";
				if (MANDATORY.equals(attribute.getRuleStrengthId()) && attribute.isGrouped() && !NO_CARDINALITY_CONSTRAINT.equals(attribute.getAttributeIngroupCardinality())) {
					String eclWithoutCardinality = domainPartEcl + "[0..*]" + " { [0..*] " + attributePartEcl + " }";
					//run ECL query to retrieve failures
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' without group cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithoutCardinality);
					List<Long> conceptIdsWithoutCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithoutCardinality, 0, -1).getConceptIds();
					String eclWithCardinality = domainPartEcl + "[" + attribute.getAttributeCardinality() + "]" + "{ [" + attribute.getAttributeIngroupCardinality() + "] " + attributePartEcl + "}";
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' with cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithCardinality);
					List<Long> conceptIdsWithCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithCardinality, 0, -1).getConceptIds();
					List<Long> invalidIds = new ArrayList<>();
					if (conceptIdsWithoutCardinality.size() != conceptIdsWithCardinality.size()) {
						invalidIds = conceptIdsWithoutCardinality;
						invalidIds.removeAll(conceptIdsWithCardinality);
					}
					processValidationResults(run, queryService, attribute, invalidIds, ValidationType.ATTRIBUTE_GROUP_CARDINALITY);
				} else {
					String skipMsg = "ValidationType:" + ValidationType.ATTRIBUTE_GROUP_CARDINALITY.getName() + " Skipped reason: ";
					if (!MANDATORY.equals(attribute.getRuleStrengthId())) {
						skipMsg += RULE_STRENGTH_IS_NOT_MANDATORY;
					} else if (NO_CARDINALITY_CONSTRAINT.equals(attribute.getAttributeIngroupCardinality())) {
						skipMsg += " Attribute group cardinality constraint is " + attribute.getAttributeIngroupCardinality();
					} else if (!attribute.isGrouped()) {
						skipMsg += " Attribute constraint is not grouped.";
					}
					run.addSkippedAssertion(attribute,ValidationType.ATTRIBUTE_GROUP_CARDINALITY, skipMsg);
				}
			}
		}

	}

	private void processValidationResults(ValidationRun run, SnomedQueryService queryService, Attribute attribute,
			List<Long> invalidIds, ValidationType type) throws ServiceException {
		String msg = "";
		if (ALL_NEW_PRECOORDINATED_CONTENT_CONCEPT.equals(attribute.getContentTypeId()) && run.getReleaseDate() != null) {
			List<Long> currentRelease = new ArrayList<>();
			for (Long conceptId : invalidIds) {
				ConceptResult result = queryService.retrieveConcept(conceptId.toString());
				if (run.getReleaseDate().equals(result.getEffectiveTime())) {
					currentRelease.add(conceptId);
				} 
			}
			if (invalidIds.size() > currentRelease.size()) {
				msg += " Total failures=" + invalidIds.size() + " and failures with release date:" + run.getReleaseDate() + "=" + currentRelease.size();
			}
			run.addCompletedAssertion(attribute, type, msg, currentRelease);
		} else {
			run.addCompletedAssertion(attribute, type, msg, invalidIds);
		}
	}

	private void executeAttributeCardinalityValidation(ValidationRun run, SnomedQueryService queryService, List<Long> precoordinatedTypes) throws ServiceException {
		
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (!precoordinatedTypes.contains(Long.parseLong(attribute.getContentTypeId()))) {
					//skip
					run.addSkippedAssertion(attribute, ValidationType.ATTRIBUTE_CARDINALITY, CONTENT_TYPE_IS_OUT_OF_SCOPE + attribute.getContentTypeId());
					continue;
				}
				String domainPartEcl = domain.getDomainId() + ":"; 
				String attributePartEcl = "<<" + attribute.getAttributeId() + "=*";
				if (MANDATORY.equals(attribute.getRuleStrengthId()) && !NO_CARDINALITY_CONSTRAINT.equals(attribute.getAttributeCardinality())) {
					String eclWithoutCardinality = domainPartEcl +  attributePartEcl;
					//run ECL query to retrieve failures
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' without cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithoutCardinality);
					List<Long> conceptIdsWithoutCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithoutCardinality, 0, -1).getConceptIds();
					
					String eclWithCardinality = domainPartEcl + " [" + attribute.getAttributeCardinality() + "] " + attributePartEcl;
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' with cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithCardinality);
					List<Long> conceptIdsWithCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithCardinality, 0, -1).getConceptIds();
					List<Long> invalidIds = new ArrayList<>();
					if (conceptIdsWithoutCardinality.size() != conceptIdsWithCardinality.size()) {
						invalidIds = conceptIdsWithoutCardinality;
						invalidIds.removeAll(conceptIdsWithCardinality);
					}
					processValidationResults(run, queryService, attribute, invalidIds, ValidationType.ATTRIBUTE_CARDINALITY);
				} else {
					String skipMsg = "";
					if (!MANDATORY.equals(attribute.getRuleStrengthId())) {
						skipMsg += RULE_STRENGTH_IS_NOT_MANDATORY;
					} else {
						if (NO_CARDINALITY_CONSTRAINT.equals(attribute.getAttributeCardinality())) {
							skipMsg += "Attribute cardinality constraint is " + attribute.getAttributeCardinality();
						}
					}
					run.addSkippedAssertion(attribute, ValidationType.ATTRIBUTE_CARDINALITY, skipMsg);
				}
			}
		}
	}

	private void executeAttributeRangeValidation(ValidationRun run, SnomedQueryService queryService, List<Long> precoordinatedTypes) throws ServiceException {
		Set<String> validationCompleted = new HashSet<>();
		for (Domain domain : run.getMRCMDomains().values()) {
			runAttributeRangeValidation(run, queryService, domain, precoordinatedTypes,validationCompleted);
		}
		
	}

	/**
	 * @param precoordinatedTypes 
	 * @param domain
	 * VALIDATE: The domain of a given attribute
	 * RETURNS: Incorrect relationships
	 *  APPLIES TO: Each attribute <ATTRIBUTE_ID> (which may have one or more domains)
	 *             and rule strength = Mandatory and content type in {|All SNOMED CT content|, |All precoordinated content|}
	 *  EXAMPLE: Finding site domain = 363698007 |Clinical finding|
	 *  
	 *  ECL: (*:<<272741003=*) MINUS (<<91723000 OR <<723264001)
	 *  ECL: (*:<<272741003=*) MINUS <<91723000
	 * @throws ServiceException *
	 * 
	*/
	private void executeAttributeDomainValidation(ValidationRun run, SnomedQueryService queryService, List<Long> precoordinatedTypes) throws ServiceException {
		Map<String,List<String>> attributeDomainMap = new HashMap<>();
		Map<String, Attribute> attributeIdMap = new HashMap<>();
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (MANDATORY.equals(attribute.getRuleStrengthId()) && precoordinatedTypes.contains(Long.parseLong(attribute.getContentTypeId()))) {
					attributeIdMap.put(attribute.getAttributeId(), attribute);
					if ( attributeDomainMap.containsKey(attribute.getAttributeId())) {
						 attributeDomainMap.get(attribute.getAttributeId()).add(domain.getDomainId());
					} else {
						List<String> domainList = new ArrayList<>();
						domainList.add(domain.getDomainId());
						attributeDomainMap.put(attribute.getAttributeId(), domainList);
					}
				} else {
					run.addSkippedAssertion(attribute, ValidationType.ATTRIBUTE_DOMAIN, " is skipped due to either the rule strengh is not mandatory:" 
							+ attribute.getRuleStrengthId() + " or the content type is out of scope:" + attribute.getContentTypeId() );
				}
			}
		}
		
		for (String attributeId : attributeDomainMap.keySet()) {
			List<String> domains = attributeDomainMap.get(attributeId);
			if (domains.isEmpty()) {
				LOGGER.error("Attribute:" + attributeId + " has no domain.");
				continue;
			}
			String withAttributeButWrongDomainEcl = "(*:<<" + attributeId + "=*) MINUS ";
			if (domains.size() > 1) {
				withAttributeButWrongDomainEcl += "(";
			}
			int counter = 0;
			for (String domain : domains) {
				if (counter++ > 0) {
					withAttributeButWrongDomainEcl += " OR ";
				}
				withAttributeButWrongDomainEcl += "<<" + domain;
			}
			if (domains.size() > 1) {
				withAttributeButWrongDomainEcl += ")";
			}
			//run ECL query to retrieve failures
			LOGGER.info("Selecting content within domain '{}' with attribute '{}' with any range using expression '{}'", domains.toArray(), attributeId, withAttributeButWrongDomainEcl);
			List<Long> conceptIdsWithInvalidAttributeValue = queryService.eclQueryReturnConceptIdentifiers(withAttributeButWrongDomainEcl, 0, -1).getConceptIds();
			processValidationResults(run, queryService, attributeIdMap.get(attributeId), conceptIdsWithInvalidAttributeValue, ValidationType.ATTRIBUTE_DOMAIN);
		}
	}

	private void runAttributeRangeValidation(ValidationRun run, SnomedQueryService queryService, Domain domain, List<Long> precoordinatedTypes, Set<String> validationProcessed) throws ServiceException {
		for (Attribute attribute : domain.getAttributes()) {
			if (domain.getAttributeRanges(attribute.getAttributeId()).isEmpty()) {
				LOGGER.error("No range constraint found for attribute {} domain {}.", attribute.getAttributeId(),domain.getDomainId());
				continue;
			}
			for (Attribute attributeRange : domain.getAttributeRanges(attribute.getAttributeId())) {
				String domainConstraint = domain.getDomainConstraint();
				String attributeId = attributeRange.getAttributeId();
				String rangeConstraint = attributeRange.getRangeConstraint();
				String rangeKey = attributeId + "_" + rangeConstraint+ "_" + attributeRange.getContentTypeId();
				if (validationProcessed.contains(rangeKey)) {
					LOGGER.info("Attribute range is done already:" + attributeRange);
					continue;
				}
				validationProcessed.add(rangeKey);
				// Find concepts in this domain where the value of this attribute is outside of the permitted range
				LOGGER.info("Asserting domain:'{}', attribute:'{}', range:'{}'", domainConstraint, attributeId, rangeConstraint);
				if (rangeConstraint == null) {
					LOGGER.error("Invalid attribute range is found:" +  attributeRange);
					continue;
				}
				if (precoordinatedTypes.contains(Long.parseLong(attributeRange.getContentTypeId()))) {
					String baseEcl = domainConstraint;
					baseEcl += baseEcl.contains(":") ? ", " : ": ";
					baseEcl += attributeId + " = ";
					String matchAllAttributeValues = baseEcl + "*";
					String matchAttributeValuesWithinRange = containsMultipleSCTIDs(rangeConstraint)
							? baseEcl + "(" + rangeConstraint + ")"
									: baseEcl + rangeConstraint;

					if (matchAttributeValuesWithinRange.contains("[")) {
						String msg = "Cardinality is not currently supported. This assertion will be skipped." + "ECL:" + matchAttributeValuesWithinRange;
						LOGGER.warn(msg);
						run.addSkippedAssertion(attributeRange, ValidationType.ATTRIBUTE_RANGE, msg);
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
					processValidationResults(run, queryService, attributeRange, conceptIdsWithInvalidAttributeValue, ValidationType.ATTRIBUTE_RANGE);
				} else {
					run.addSkippedAssertion(attributeRange, ValidationType.ATTRIBUTE_RANGE, "content type:" + attributeRange.getContentTypeId() + " is out of scope.");
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
		private Map<String, List<Attribute>> attributes = new HashMap<>();

		@Override
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			synchronized (this) {
				if ("1".equals(active)) {
					switch (refsetId) {
						case MRCM_DOMAIN_REFSET:
							getCreateDomain(referencedComponentId).setDomainConstraint(otherValues[domDomainConstraintIndex]);
							break;
						case MRCM_ATTRIBUTE_DOMAIN_REFSET:
							Domain domain = getCreateDomain(otherValues[attDomainIdIndex]);
							Attribute attribute = createAttributeDomain(id, referencedComponentId, otherValues);
							domain.addAttribute(attribute);
							updateAttributeRange(attribute.getAttributeId(),domain);
							break;
						case MRCM_ATTRIBUTE_RANGE_REFSET:
							createAttributeRange(id, referencedComponentId, otherValues);
							break;
						default:
							LOGGER.error("Invalid refsetId:" + refsetId);
							break;
					}
				}
			}
		}

		private void updateAttributeRange(String attributeId, Domain domain) {
			for (Attribute attribute : domain.getAttributes()) {
				if (attributes.get(attribute.getAttributeId()) == null) {
					return;
				}
				for (Attribute range : attributes.get(attribute.getAttributeId())) {
					domain.addAttributeRange(range);
				}
			}
		}

		private void updateDomainWithAttributeRange() {
			for (Domain domain : domains.values()) {
				for (Attribute attribute : domain.getAttributes()) {
					if (attributes.containsKey(attribute.getAttributeId())) {
						for (Attribute range : attributes.get(attribute.getAttributeId())) {
							domain.addAttributeRange(range);
						}
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
		
		public Attribute createAttributeDomain(String id,String attributeId, String ... otherValues) {
			Attribute attribute = new Attribute(attributeId,  otherValues[attContentTypeIndex]);
			if (id != null && !id.isEmpty()) {
				attribute.setUuid(UUID.fromString(id));
			}
			attribute.setGrouped(otherValues[1]);
			attribute.setAttributeCardinality(otherValues[2]);
			attribute.setAttributeIngroupCardinality(otherValues[3]);
			attribute.setRuleStrengthId(otherValues[4]);
			attribute.setType(Attribute.Type.DOMAIN);
			return attribute;
		}

		
		public Attribute createAttributeRange (String uuid, String attributeId,String... otherValues) {
			Attribute attribute = new Attribute(attributeId, otherValues[ranContentTypeIndex]);
			if (uuid != null && !uuid.isEmpty()) {
				attribute.setUuid(UUID.fromString(uuid));
			}
			attribute.setRangeConstraint(otherValues[ranRangeConstraintIndex]);
			attribute.setRuleStrengthId(otherValues[2]);
			attribute.setType(Type.RANGE);
			if (attributes.containsKey(attribute.getAttributeId())) {
				attributes.get(attribute.getAttributeId()).add(attribute);
			} else {
				List<Attribute> list = new ArrayList<>();
				list.add(attribute);
				attributes.put(attributeId,list);
			}
			updateDomainWithAttributeRange();
			return attribute;
		}
	}
}
