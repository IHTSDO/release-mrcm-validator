package org.snomed.quality.validator.mrcm;

import com.google.common.base.Strings;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.domain.Concept;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.ihtsdo.otf.snomedboot.domain.Description;
import org.ihtsdo.otf.snomedboot.factory.FactoryUtils;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.ihtsdo.otf.snomedboot.factory.implementation.HighLevelComponentFactoryAdapterImpl;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentStore;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentStoreComponentFactoryImpl;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ConceptImpl;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.DescriptionImpl;
import org.ihtsdo.otf.sqs.service.ReleaseImportManager;
import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.dto.ConceptResult;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.ihtsdo.otf.sqs.service.store.RamReleaseStore;
import org.ihtsdo.otf.sqs.service.store.ReleaseStore;
import org.semanticweb.owlapi.io.OWLParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.snomed.quality.validator.mrcm.Assertion.FailureType;
import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Attribute.Type;
import org.snomed.quality.validator.mrcm.model.Domain;
import org.snomed.quality.validator.mrcm.model.ReferenceSetMember;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.snomed.quality.validator.mrcm.Constants.*;

public class ValidationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

	private static final Pattern CONCEPT_TERM_PATTERN = Pattern.compile("\\d+\\s\\|(.*?)\\|");

	private static final LoadingProfile MRCM_AND_SIMPLE_REFSET_LOADING_PROFILE = new LoadingProfile()
			.withRefsets(MRCM_DOMAIN_REFSET, MRCM_ATTRIBUTE_DOMAIN_REFSET, MRCM_ATTRIBUTE_RANGE_REFSET, LATERALIZABLE_BODY_STRUCTURE_REFSET, ANATOMY_STRUCTURE_AND_ENTIRE_REFSET, ANATOMY_STRUCTURE_AND_PART_REFSET)
			.withIncludedReferenceSetFilenamePattern(".*MRCM.*")
			.withIncludedReferenceSetFilenamePattern(".*_Refset_Simple.*")
			.withIncludedReferenceSetFilenamePattern(".*_cRefset_Association.*")
			.withInactiveRefsetMembers()
			.withJustRefsets();

	public final void loadMRCM(final File sourceDirectory, final ValidationRun run) throws ReleaseImportException {
		final MRCMFactory mrcmFactory = new MRCMFactory();
		new ReleaseImporter().loadSnapshotReleaseFiles(sourceDirectory.getPath(), MRCM_AND_SIMPLE_REFSET_LOADING_PROFILE, mrcmFactory, false);
		setMRCM(run, mrcmFactory);
	}

	public final void loadMRCM(final Set<String> extractedRF2FilesDirectories, final ValidationRun run) throws ReleaseImportException {
		if (run.isFullSnapshotRelease()) {
			loadMRCM(new File(extractedRF2FilesDirectories.iterator().next()), run);
			return;
		}

		final MRCMFactory mrcmFactory = new MRCMFactory();
		boolean loadDelta = RF2ReleaseFilesUtil.anyDeltaFilesPresent(extractedRF2FilesDirectories);
		if (loadDelta) {
			new ReleaseImporter().loadEffectiveSnapshotAndDeltaReleaseFiles(extractedRF2FilesDirectories, MRCM_AND_SIMPLE_REFSET_LOADING_PROFILE, mrcmFactory, false);
		} else {
			new ReleaseImporter().loadEffectiveSnapshotReleaseFiles(extractedRF2FilesDirectories, MRCM_AND_SIMPLE_REFSET_LOADING_PROFILE, mrcmFactory, false);
		}
		setMRCM(run, mrcmFactory);
	}

	public void validateRelease(Set<String> extractedRF2FilesDirectories, ValidationRun run) throws ReleaseImportException, IOException, ServiceException {
		executeValidation(extractedRF2FilesDirectories, run);
	}

	public void validateRelease(File releaseDirectory, ValidationRun run, Set<String> modules) throws ReleaseImportException, IOException, ServiceException {
		run.setModuleIds(modules);
		executeValidation(Collections.singleton(releaseDirectory.getPath()), run);
	}

	public void validateRelease(File releaseDirectory, ValidationRun run) throws ReleaseImportException, IOException, ServiceException {
		executeValidation(Collections.singleton(releaseDirectory.getPath()), run);
	}

	private void executeValidation(Set<String> extractedRF2FilesDirectories, ValidationRun run) throws ReleaseImportException, IOException, ServiceException {
		OWLExpressionAndDescriptionFactory owlExpressionAndDescriptionFactory = new OWLExpressionAndDescriptionFactory(new ComponentStore(), run.getUngroupedAttributes(),
				run.getConceptsUsedInMRCMTemplates());
		SnomedQueryService queryService = getSnomedQueryService(extractedRF2FilesDirectories, run.getContentType(), owlExpressionAndDescriptionFactory, run.isFullSnapshotRelease());

		final Map<Long, List<DescriptionImpl>> descriptions = owlExpressionAndDescriptionFactory.getDescriptions();
		LOGGER.info("Total in-use concepts in attribute range {}", descriptions.keySet().size());

		//checking data is loaded properly
		LOGGER.info("Total concepts loaded {}", queryService.getConceptCount());
		List<Long> preCoordinatedTypes = queryService.eclQueryReturnConceptIdentifiers("<<" + ALL_NEW_PRE_COORDINATED_CONTENT_CONCEPT, 0, 100).conceptIds();
		Assert.notEmpty(preCoordinatedTypes, "Concept " + ALL_NEW_PRE_COORDINATED_CONTENT_CONCEPT + " and descendants must be accessible.");
		for (ValidationType type : run.getValidationTypes()) {
            switch (type) {
                case ATTRIBUTE_DOMAIN -> executeAttributeDomainValidation(run, queryService, preCoordinatedTypes);
                case ATTRIBUTE_RANGE ->
                        executeAttributeRangeValidation(run, queryService, descriptions, preCoordinatedTypes);
                case ATTRIBUTE_CARDINALITY ->
                        executeAttributeCardinalityValidation(run, queryService, preCoordinatedTypes);
                case ATTRIBUTE_IN_GROUP_CARDINALITY ->
                        executeAttributeGroupCardinalityValidation(run, queryService, preCoordinatedTypes);
                case CONCRETE_ATTRIBUTE_DATA_TYPE ->
                        executeConcreteDataTypeValidation(extractedRF2FilesDirectories, run, queryService);
                case LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE -> {
                    if (ContentType.INFERRED.equals(run.getContentType()) && CollectionUtils.isEmpty(run.getModuleIds())) {
                        executeLateralizableRefsetValidation(run, queryService);
                    }
                }
				case SEP_REFSET_TYPE -> {
					if (ContentType.INFERRED.equals(run.getContentType()) && CollectionUtils.isEmpty(run.getModuleIds())) {
						executeSEPRefsetValidation(run, queryService);
					}
				}
                default -> LOGGER.error("Validation Type: '{}' is not implemented yet!", type);
            }
		}
	}

	protected SnomedQueryService getSnomedQueryService(Set<String> extractedRF2FilesDirectories, ContentType contentType, OWLExpressionAndDescriptionFactory owlExpressionAndDescriptionFactory, boolean fullSnapshotRelease) throws ReleaseImportException, IOException {
		LoadingProfile profile = contentType == ContentType.STATED ?
				LoadingProfile.light
						.withStatedRelationships()
						.withStatedAttributeMapOnConcept()
						.withRefsets(LATERALIZABLE_BODY_STRUCTURE_REFSET, OWL_AXIOM_REFSET)
						.withoutInferredAttributeMapOnConcept()
						.withInactiveConcepts()
				: LoadingProfile.light.withRefsets(LATERALIZABLE_BODY_STRUCTURE_REFSET, OWL_AXIOM_REFSET).withInactiveConcepts();

		ReleaseStore releaseStore = new MRCMValidatorReleaseImportManager().loadReleaseFilesToMemoryBasedIndex(extractedRF2FilesDirectories, profile, owlExpressionAndDescriptionFactory, fullSnapshotRelease);
		return new SnomedQueryService(releaseStore);
	}

	private void setMRCM(ValidationRun run, MRCMFactory mrcmFactory) {
		final Map <String, Domain> domains = mrcmFactory.getDomains();
		Assert.notEmpty(domains, "No MRCM Domains Found");
		domains.values().forEach(domain -> Assert.notNull(domain.getDomainConstraint(), "Constraint for domain " + domain.getDomainId() + " must not be null."));
		run.setMRCMDomains(domains);
		run.setAttributeRangesMap(mrcmFactory.getAttributeRangeMap());
		run.setUngroupedAttributes(mrcmFactory.getUngroupedAttributes());
		run.setConceptsUsedInMRCMTemplates(mrcmFactory.getInUseConceptIds());
		run.setLateralizableRefsetMembers(mrcmFactory.getLateralizableRefsets());
		run.setAnatomyStructureAndEntireRefsets(mrcmFactory.getAnatomyStructureAndEntireRefsets());
		run.setAnatomyStructureAndPartRefsets(mrcmFactory.getAnatomyStructureAndPartRefsets());
	}

	private void executeLateralizableRefsetValidation(ValidationRun run, SnomedQueryService queryService) {
		// Concrete attribute data type validation
		LateralizableRefsetValidationService lateralizableRefsetValidationService = new LateralizableRefsetValidationService();
		lateralizableRefsetValidationService.validate(queryService, run);
	}

	private void executeSEPRefsetValidation(ValidationRun run, SnomedQueryService queryService) throws ServiceException, IOException {
		SEPRefsetValidationService sepRefsetValidationService = new SEPRefsetValidationService();
		sepRefsetValidationService.validate(queryService, run);
	}

	private void executeConcreteDataTypeValidation(Set<String> extractedRF2FilesDirectories, ValidationRun run, SnomedQueryService queryService) throws ReleaseImportException, ServiceException {
		// Concrete attribute data type validation
		ConcreteAttributeDataTypeValidationService dataTypeValidationService = new ConcreteAttributeDataTypeValidationService();
		dataTypeValidationService.validate(extractedRF2FilesDirectories, run);
		for (Assertion assertion : run.getFailedAssertions()) {
			List<Long> conceptIds = assertion.getCurrentViolatedConceptIds();
			for (Long conceptId : conceptIds) {
				ConceptResult result = queryService.retrieveConcept(String.valueOf(conceptId));
				if (!CollectionUtils.isEmpty(run.getModuleIds()) && !run.getModuleIds().contains(result.getModuleId())) {
					continue;
				}
				assertion.getCurrentViolatedConcepts().add(result);
			}
		}
	}

	private void executeAttributeDomainValidation(ValidationRun run, SnomedQueryService queryService, List <Long> precoordinatedTypes) throws ServiceException {
		executeAttributeDomainValidation(run,queryService,precoordinatedTypes, MANDATORY);
		executeAttributeDomainValidation(run,queryService,precoordinatedTypes, OPTIONAL);
	}

	private void executeAttributeGroupCardinalityValidation(ValidationRun run, SnomedQueryService queryService, List<Long> precoordinatedTypes) throws ServiceException {
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (!precoordinatedTypes.contains(Long.parseLong(attribute.getContentTypeId()))) {
					//skip
					run.addSkippedAssertion(constructAssertion(queryService, attribute, ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY, CONTENT_TYPE_IS_OUT_OF_SCOPE + attribute.getContentTypeId()));
					continue;
				}
				String domainPartEcl = "<<" + domain.getDomainId() + ":"; 
				String attributePartEcl = attribute.getAttributeId() + "=*";
				if (attribute.isGrouped() && !NO_CARDINALITY_CONSTRAINT.equals(attribute.getAttributeInGroupCardinality())) {
					String eclWithoutCardinality = domainPartEcl + "[0..*]" + " { [0..*] " + attributePartEcl + " }";
					//run ECL query to retrieve failures
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' without group cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithoutCardinality);
					List<Long> conceptIdsWithoutCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithoutCardinality, 0, -1).conceptIds();
					String eclWithCardinality = domainPartEcl + "[" + attribute.getAttributeCardinality() + "]" + "{ [" + attribute.getAttributeInGroupCardinality() + "] " + attributePartEcl + "}";
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' with cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithCardinality);
					List<Long> conceptIdsWithCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithCardinality, 0, -1).conceptIds();
					List<Long> invalidIds = new ArrayList<>();
					if (conceptIdsWithoutCardinality.size() != conceptIdsWithCardinality.size()) {
						invalidIds = conceptIdsWithoutCardinality;
						invalidIds.removeAll(conceptIdsWithCardinality);
					}
					processValidationResults(run, queryService, attribute, invalidIds, ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY, null);
				} else {
					String skipMsg = "ValidationType:" + ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY.getName() + " Skipped reason: ";
					if (NO_CARDINALITY_CONSTRAINT.equals(attribute.getAttributeInGroupCardinality())) {
						skipMsg += " Attribute group cardinality constraint is " + attribute.getAttributeInGroupCardinality();
					} else if (!attribute.isGrouped()) {
						skipMsg += " Attribute constraint is not grouped.";
					}
					run.addSkippedAssertion(constructAssertion(queryService, attribute,ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY, skipMsg));
				}
			}
		}

	}

	private void processValidationResults(ValidationRun run, SnomedQueryService queryService, Attribute attribute,
										  List <Long> invalidIds, ValidationType type, String domainConstraint) throws ServiceException {
		String msg = "";
		List <ConceptResult> newInvalidConcepts = new ArrayList<>();
		if (run.getReleaseDate() != null) {
			//Filter out failures for current release and previous published release.
			List<ConceptResult> currentRelease = new ArrayList<>();
			for (Long conceptId : invalidIds) {
				ConceptResult result = queryService.retrieveConcept(conceptId.toString());
				if (!CollectionUtils.isEmpty(run.getModuleIds()) && !run.getModuleIds().contains(result.getModuleId())) {
					continue;
				}
				newInvalidConcepts.add(result);
				if (run.getReleaseDate().equals(result.getEffectiveTime())) {
					currentRelease.add(result);
				} 
			}
			if (newInvalidConcepts.size() > currentRelease.size()) {
				msg += " Total failures=" + newInvalidConcepts.size() + ". Failures with release date:" + run.getReleaseDate() + "=" + currentRelease.size();
			}
			if (ALL_NEW_PRE_COORDINATED_CONTENT_CONCEPT.equals(attribute.getContentTypeId())) {
				run.addCompletedAssertion(constructAssertion(queryService, attribute, type, msg, currentRelease, null, domainConstraint));
			} else {
				newInvalidConcepts.removeAll(currentRelease);
				run.addCompletedAssertion(constructAssertion(queryService, attribute, type, msg, currentRelease, newInvalidConcepts, domainConstraint));
			}
		} else {
			// for ALL_NEW_PRECOORDINATED_CONTENT_CONCEPT display message that no effect date is supplied
			if (ALL_NEW_PRE_COORDINATED_CONTENT_CONCEPT.equals(attribute.getContentTypeId())) {
				msg += " Content type is for new concept only but there is no current release date specified.";
			}
			for (Long conceptId : invalidIds) {
				ConceptResult result = queryService.retrieveConcept(conceptId.toString());
				if (!CollectionUtils.isEmpty(run.getModuleIds()) &&  !run.getModuleIds().contains(result.getModuleId())) {
					continue;
				}
				newInvalidConcepts.add(result);
			}

			run.addCompletedAssertion(constructAssertion(queryService, attribute, type, msg, newInvalidConcepts, null, domainConstraint));
		}
	}

	private void executeAttributeCardinalityValidation(ValidationRun run, SnomedQueryService queryService, List<Long> precoordinatedTypes) throws ServiceException {
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (!precoordinatedTypes.contains(Long.parseLong(attribute.getContentTypeId()))) {
					//skip
					run.addSkippedAssertion(constructAssertion(queryService, attribute, ValidationType.ATTRIBUTE_CARDINALITY, CONTENT_TYPE_IS_OUT_OF_SCOPE + attribute.getContentTypeId()));
					continue;
				}
				if (NO_CARDINALITY_CONSTRAINT.equals(attribute.getAttributeCardinality())) {
					run.addSkippedAssertion(constructAssertion(queryService,attribute, ValidationType.ATTRIBUTE_CARDINALITY,
							"Attribute cardinality constraint is " + attribute.getAttributeCardinality()));
				} else {
					String domainConstraint = domain.getDomainConstraint() + " : ";
					String attributeWithoutRange = attribute.getAttributeId() + " =* ";
					String eclWithoutCardinality = domainConstraint +  attributeWithoutRange;
					//run ECL query to retrieve failures
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' without cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithoutCardinality);
					List<Long> conceptIdsWithoutCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithoutCardinality, 0, -1).conceptIds();
					String eclWithCardinality = domainConstraint + " [" + attribute.getAttributeCardinality() + "] " + attributeWithoutRange;
					LOGGER.info("Selecting content within domain '{}' with attribute '{}' with cardinality ECL:'{}'", domain.getDomainId(), attribute.getAttributeId(), eclWithCardinality);
					List<Long> conceptIdsWithCardinality = queryService.eclQueryReturnConceptIdentifiers(eclWithCardinality, 0, -1).conceptIds();
					List<Long> invalidIds = new ArrayList<>();
					if (conceptIdsWithoutCardinality.size() != conceptIdsWithCardinality.size()) {
						invalidIds = conceptIdsWithoutCardinality;
						invalidIds.removeAll(conceptIdsWithCardinality);
					}
					processValidationResults(run, queryService, attribute, invalidIds, ValidationType.ATTRIBUTE_CARDINALITY, null);
				} 
			}
		}
	}

	private Assertion constructAssertion(SnomedQueryService queryService, Attribute attribute, ValidationType attributeCardinality, String skipMsg) {
		return constructAssertion(queryService, attribute, attributeCardinality, skipMsg, null,null,null);
	}

	private Assertion constructAssertion(SnomedQueryService queryService, Attribute attribute, ValidationType validationType, String msg,
			List<ConceptResult> currentInvalidConcepts, List<ConceptResult> previousInvalidConcepts, String domainConstraint) {
		FailureType failureType = FailureType.ERROR;
		if (!MANDATORY.equals(attribute.getRuleStrengthId())) {
			failureType = FailureType.WARNING;
		}
		try {
			attribute.setAttributeFsn(queryService.retrieveConcept(attribute.getAttributeId()).getFsn());
		} catch (ServiceException e) {
			LOGGER.error("Error while retrieving concept details for attribute '{}' and Content Type '{}'", attribute.getAttributeId(), attribute.getContentTypeId());
		}
		return new Assertion(attribute, validationType, msg, failureType, currentInvalidConcepts, previousInvalidConcepts, domainConstraint);
	}
	private void executeAttributeRangeValidation(ValidationRun run, SnomedQueryService queryService, Map<Long, List<DescriptionImpl>> descriptions,
			List<Long> precoordinatedTypes) throws ServiceException {

		Set<String> validationCompleted = new HashSet<>();
		for (Domain domain : run.getMRCMDomains().values()) {
			runAttributeRangeValidation(run, queryService, descriptions, domain, precoordinatedTypes, validationCompleted);
		}
	}

	/**
	 * @param preCoordinatedTypes
	 * VALIDATE: The domain of a given attribute
	 * RETURNS: Incorrect relationships
	 *  APPLIES TO: Each attribute <ATTRIBUTE_ID> (which may have one or more domains)
	 *             and rule strength = Mandatory and content type in {|All SNOMED CT content|, |All precoordinated content|}
	 *  EXAMPLE: Finding site domain = 363698007 |Clinical finding|
	 *  
	 *  ECL: (*:272741003=*) MINUS (<<91723000 OR <<723264001)
	 *  ECL: (*:272741003=*) MINUS <<91723000
	 *  
	 *  ^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|
	 * @throws ServiceException *
	 * 
	*/
	private void executeAttributeDomainValidation(ValidationRun run, SnomedQueryService queryService, List<Long> preCoordinatedTypes, String ruleStrength) throws ServiceException {
		Map<String,List<Domain>> attributeDomainMap = new HashMap<>();
		Map<String, List<Attribute>> attributesById = new HashMap<>();
		filterAttributeDomainByStrength(run, queryService, preCoordinatedTypes, ruleStrength, attributeDomainMap, attributesById);
		
		for (String attributeId : attributeDomainMap.keySet()) {
			List<Domain> domains = attributeDomainMap.get(attributeId);
			if (domains.isEmpty()) {
				LOGGER.error("Attribute {} has no domain.", attributeId);
				continue;
			}

			List<Long> violatedConcepts;
			StringBuilder domainConstraintBuilder = new StringBuilder();
			if (LATERALITY_ATTRIBUTE.equals(attributeId) && hasLateralizableDomain(domains)) {
				violatedConcepts = processLateralizableDomainConstraintQuery(queryService, attributeId, domains, domainConstraintBuilder);
			} else {
				violatedConcepts = processNonNestedDomainConstraintQuery(queryService, attributeId, domains, domainConstraintBuilder);
			}
			for (Attribute attribute : attributesById.get(attributeId)) {
				processValidationResults(run, queryService, attribute, violatedConcepts, ValidationType.ATTRIBUTE_DOMAIN, domainConstraintBuilder.toString());
			}
		}
	}

	private boolean hasLateralizableDomain(List<Domain> domains) {
		return domains.stream().map(Domain::getDomainId).anyMatch(d -> d.equals(LATERALIZABLE_BODY_STRUCTURE_REFSET));
	}
	private List<Long> processLateralizableDomainConstraintQuery(SnomedQueryService queryService, String attributeId, List<Domain> domains, StringBuilder msgBuilder) throws ServiceException {
		// This is a workaround for domain constraint ^ 723264001 but 272741003 |Laterality (attribute)| can only be used by a concept
		// if one of its parents is a member of Lateralizable body structure reference set
		// It was << ^ 723264001 before 20180731 release and changed to ^ 723264001 however based on above logic
		// I think the domain constraint should be childOrSelfOf <<! ^ 723264001 but the query service doesn't support childOf or parentOf yet.

		String withAttributeQuery = "*:" + attributeId + "=*";
		List<Long> conceptsWithAttribute = queryService.eclQueryReturnConceptIdentifiers(withAttributeQuery, 0, -1).conceptIds();
		List<Long> memberOfLateralizbleRefset = new ArrayList<>();
		for (Domain domain : domains) {
			if (domain.getDomainId().equals(LATERALIZABLE_BODY_STRUCTURE_REFSET)) {
				List<Long> result = queryService.eclQueryReturnConceptIdentifiers(domain.getDomainConstraint(), 0, -1).conceptIds();
				memberOfLateralizbleRefset.addAll(result);
				msgBuilder.append(domain.getDomainConstraint());
			}
		}
		List<Long> violatedConcepts = new ArrayList<>();
		for (Long conceptId : conceptsWithAttribute) {
			if (memberOfLateralizbleRefset.contains(conceptId)) {
				continue;
			}
			// it should be parentOf but the query service doesn't support childOf or parentOf yet.
			List<Long> ancestors = queryService.eclQueryReturnConceptIdentifiers(">" + conceptId, 0, -1).conceptIds();
			if (ancestors.stream().noneMatch(concept -> memberOfLateralizbleRefset.contains(concept))) {
				violatedConcepts.add(conceptId);
			}
		}
		return violatedConcepts;
	}

	private List<Long> processNonNestedDomainConstraintQuery(SnomedQueryService queryService, String attributeId,
			List<Domain> domains, StringBuilder msgBuilder) throws ServiceException {
		List<Long> violatedConcepts;
		String withAttributeButWrongDomainEcl = "(*:" + attributeId + "=*) MINUS ";
		if (domains.size() > 1) {
			withAttributeButWrongDomainEcl += "(";
		}
		int counter = 0;
		for (Domain domain : domains) {
			if (counter++ > 0) {
				withAttributeButWrongDomainEcl += " OR ";
				msgBuilder.append(" OR ");
			}
			withAttributeButWrongDomainEcl += domain.getDomainConstraint();
			msgBuilder.append(domain.getDomainConstraint());
		}
		if (domains.size() > 1) {
			withAttributeButWrongDomainEcl += ")";
		}
		// run ECL query to retrieve failures
		LOGGER.info("Selecting content within domain '{}' with attribute '{}' with any range using expression '{}'", domains.toArray(), attributeId, withAttributeButWrongDomainEcl);
		violatedConcepts = queryService.eclQueryReturnConceptIdentifiers(withAttributeButWrongDomainEcl, 0, -1).conceptIds();
		return violatedConcepts;
	}

	private void filterAttributeDomainByStrength(ValidationRun run, SnomedQueryService queryService, List<Long> precoordinatedTypes, String ruleStrengh,
			Map<String, List<Domain>> attributeDomainMap, Map<String, List<Attribute>> attributesById) throws ServiceException {
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				// There are cases that domain rule is optional e.g 723264001 for Laterality attribute
				if(!ruleStrengh.equals(attribute.getRuleStrengthId())) {
					continue;
				}
				if (precoordinatedTypes.contains(Long.parseLong(attribute.getContentTypeId()))) {
					if (attributesById.containsKey(attribute.getAttributeId())) {
						attributesById.get(attribute.getAttributeId()).add(attribute);
					} else {
						List<Attribute> attributeList = new ArrayList<>();
						attributeList.add(attribute);
						attributesById.put(attribute.getAttributeId(), attributeList);
					}
					if ( attributeDomainMap.containsKey(attribute.getAttributeId())) {
						 attributeDomainMap.get(attribute.getAttributeId()).add(domain);
					} else {
						List<Domain> domainList = new ArrayList<>();
						domainList.add(domain);
						attributeDomainMap.put(attribute.getAttributeId(), domainList);
					}
				} else {
					run.addSkippedAssertion(constructAssertion(queryService, attribute, ValidationType.ATTRIBUTE_DOMAIN,
							" is skipped due to the content type is out of scope:" + attribute.getContentTypeId()));
				}
			}
		}
	}

	private void runAttributeRangeValidation(ValidationRun run, SnomedQueryService queryService, Map<Long, List<DescriptionImpl>> descriptions, Domain domain,
										 List<Long> preCoordinatedTypes, Set<String> validationProcessed) throws ServiceException {

		for (Attribute attribute : domain.getAttributes()) {
			if (domain.getAttributeRanges(attribute.getAttributeId()).isEmpty()) {
				LOGGER.error("No range constraint found with attribute id {} for domain {}.", attribute.getAttributeId(), domain.getDomainId());
				continue;
			}
			for (Attribute attributeRange : domain.getAttributeRanges(attribute.getAttributeId())) {
				String domainConstraint = domain.getDomainConstraint();
				String attributeId = attributeRange.getAttributeId();
				String rangeConstraint = attributeRange.getRangeConstraint();
				String rangeKey = attributeId + "_" + rangeConstraint+ "_" + attributeRange.getContentTypeId();
				if (validationProcessed.contains(rangeKey)) {
					LOGGER.info("Attribute range is done already:{}", attributeRange);
					continue;
				}
				validationProcessed.add(rangeKey);
				if (Strings.isNullOrEmpty(rangeConstraint) || Strings.isNullOrEmpty(attributeRange.getRangeRule())) {
					throw new IllegalStateException("No attribute range constraint or rule is defined in attribute range " +  attributeRange);
				}

				validateConceptsInRange(run, descriptions, queryService, attributeRange, "range constraint", attributeRange.getRangeConstraint());
				validateConceptsInRange(run, descriptions, queryService, attributeRange, "range rule", attributeRange.getRangeRule());

				if (preCoordinatedTypes.contains(Long.parseLong(attributeRange.getContentTypeId()))) {
					String outOfRangeRule = null;
					// check concrete attribute range constraint
					if (isConcreteRangeConstraint(rangeConstraint)) {
						String matchRangeRule = removeCardinality(attributeRange.getRangeRule());
						outOfRangeRule = constructOutOfRangeRule(matchRangeRule);
					} else {
						String baseEcl = domainConstraint;
						baseEcl += baseEcl.contains(":") ? ", " : ": ";
						baseEcl += attributeId;
						outOfRangeRule = baseEcl + " != ";
						outOfRangeRule = containsMultipleConceptIds(rangeConstraint)
								? outOfRangeRule + "(" + rangeConstraint + ")"
								: outOfRangeRule + rangeConstraint;
					}
					LOGGER.info("Selecting content out of range for attribute '{}' with out range constraint expression '{}'", attributeId, outOfRangeRule);
					List<Long> conceptIdsWithInvalidAttributeValue = queryService.eclQueryReturnConceptIdentifiers(outOfRangeRule, 0, -1).conceptIds();
					processValidationResults(run, queryService, attributeRange, conceptIdsWithInvalidAttributeValue, ValidationType.ATTRIBUTE_RANGE, null);
				} else {
					run.addSkippedAssertion(constructAssertion(queryService, attributeRange, ValidationType.ATTRIBUTE_RANGE, "content type:" + attributeRange.getContentTypeId() + " is out of scope."));
				}
			}
		}
	}

	private void validateConceptsInRange(ValidationRun run, Map<Long, List<DescriptionImpl>> descriptions, SnomedQueryService queryService, Attribute attribute,
			String column, String range) throws ServiceException {

		List<ConceptImpl> concepts = getConceptsFromRange(range);
		Set<ConceptImpl> notFoundConcepts = new HashSet<>();
		List<ConceptImpl> inactiveConcepts = new ArrayList<>();
		List<ConceptImpl> invalidTermConcepts = new ArrayList<>();
		concepts.forEach(concept -> {
			ConceptResult existingConcept;
			try {
				existingConcept = queryService.retrieveConcept(concept.getId().toString());
			} catch (ServiceException e) {
				existingConcept = null;
				LOGGER.error("Error while retrieving concept details for concept {}", concept.getId());
			}
			if (existingConcept == null) {
				ConceptImpl foundConcept = notFoundConcepts.stream().filter(c -> concept.getId().equals(c.getId())).findAny().orElse(null);
				if (foundConcept == null) {
					inactiveConcepts.add(concept);
				}
				notFoundConcepts.add(concept);
			} else if (!existingConcept.isActive()) {
				ConceptImpl foundConcept = inactiveConcepts.stream().filter(c -> concept.getId().equals(c.getId())).findAny().orElse(null);
				if (foundConcept == null) {
					inactiveConcepts.add(concept);
				}
			} else {
				boolean termMatched = false;
				List<DescriptionImpl> descriptionList = descriptions.get(concept.getId());
				for (Description description : descriptionList) {
					if (description.isActive() && description.getTerm().equals(concept.getFsn())) {
						termMatched = true;
						break;
					}
				}
				if (!termMatched) {
					ConceptImpl foundConcept = invalidTermConcepts.stream().filter(c -> concept.getId().equals(c.getId())).findAny().orElse(null);
					if (foundConcept == null) {
						invalidTermConcepts.add(concept);
					}
				}
			}
		});

		Assertion assertion;
		String msg;
		if (notFoundConcepts.size() != 0) {
			List<ConceptResult> currentViolatedConcepts = notFoundConcepts.stream()
					.map(concept -> new ConceptResult(concept.getId().toString(), null, "0", null, null, concept.getFsn(), null))
					.collect(Collectors.toList());
			msg = String.format("Concepts used in %s for MRCM attribute range %s do not exist", column, attribute.getUuid().toString());
			assertion = new Assertion(attribute, ValidationType.ATTRIBUTE_RANGE, ValidationSubType.ATTRIBUTE_RANGE_INVALID_CONCEPT, msg, FailureType.ERROR, currentViolatedConcepts, null, null);
			run.addCompletedAssertion(assertion);
		}
		if (inactiveConcepts.size() != 0) {
			List<ConceptResult> currentViolatedConcepts = inactiveConcepts.stream()
					.map(concept -> new ConceptResult(concept.getId().toString(), null, "0", null, null, concept.getFsn(), null))
					.collect(Collectors.toList());
			msg = String.format("Concepts used in %s for MRCM attribute range %s are inactive", column, attribute.getUuid().toString());
			assertion = new Assertion(attribute, ValidationType.ATTRIBUTE_RANGE, ValidationSubType.ATTRIBUTE_RANGE_INACTIVE_CONCEPT, msg, FailureType.ERROR, currentViolatedConcepts, null, null);
			run.addCompletedAssertion(assertion);
		}
		if (invalidTermConcepts.size() != 0) {
			List<ConceptResult> currentViolatedConcepts = invalidTermConcepts.stream()
					.map(concept -> new ConceptResult(concept.getId().toString(), null, "1", null, null, concept.getFsn(), null))
					.collect(Collectors.toList());
			msg = String.format("Terms used in the %s for MRCM attribute range %s are invalid", column, attribute.getUuid().toString());
			assertion = new Assertion(attribute, ValidationType.ATTRIBUTE_RANGE, ValidationSubType.ATTRIBUTE_RANGE_INVALID_TERM, msg, FailureType.ERROR, currentViolatedConcepts, null, null);
			run.addCompletedAssertion(assertion);
		}
	}

	private boolean isConcreteRangeConstraint(String rangeConstraint) {
		for (Relationship.ConcreteValue.Type type : Relationship.ConcreteValue.Type.values()) {
			if (rangeConstraint.startsWith(type.getShorthand())) {
				return true;
			}
		}
		return false;
	}

	private boolean containsMultipleConceptIds(String expression) {
		return expression.matches(".*(\\d){6,18}[^\\d]*(\\d){6,18}.*");
	}
	
	private static String constructOutOfRangeRule(String rangeRule) {
		Map<String, String> replacementMap = new HashMap<>();
		replacementMap.put("<", ">=");
		replacementMap.put(">", "<=");
		replacementMap.put("<=", ">");
		replacementMap.put(">=", "<");
		replacementMap.put("=", "!=");
		replacementMap.put("!=", "=");

		String[] splits = rangeRule.split("\\s+");
		StringBuilder builder = new StringBuilder();
		String previous = null;
		boolean isConcrete = false;
		boolean isInScope = false;
		for (String split : splits) {
			if (split.contains("(")) {
				isInScope = true;
			}
			if (split.contains(")")) {
				isInScope = false;
			}
			if (split.startsWith("#") || split.startsWith("\"")) {
				isConcrete = true;
				if (previous != null && replacementMap.containsKey(previous)) {
					builder.append(replacementMap.get(previous));
					builder.append(" ");
				}
			} else {
				if (previous != null) {
					if ((previous.equals("=") || previous.equals("!=")) && !split.startsWith("*")) {
						// replace = with != for non wildcard range constraint
						builder.append(replacementMap.get(previous));
					} else {
						builder.append(previous);
					}
					builder.append(" ");
				}
			}
			if (isConcrete && isInScope) {
				previous = split.equalsIgnoreCase("AND") ? "OR" : split;
			} else {
				previous = split;
			}
		}
		if (previous != null) {
			builder.append(previous);
		}
		return builder.toString();
	}

	private String removeCardinality(String rangeRule) {
		Pattern cardinalityPattern = Pattern.compile("(.*)(\\[.*\\.\\..*\\])(.*)");
		String[] splits = rangeRule.split("\\s+");
		StringBuilder builder = new StringBuilder();
		for (String split : splits) {
			Matcher matcher = cardinalityPattern.matcher(split);
			if (matcher.matches()) {
				if (!matcher.group(1).isEmpty()) {
					builder.append(matcher.group(1));
				}
				if (!matcher.group(3).isEmpty()) {
					builder.append(matcher.group(3));
				}
			} else {
				builder.append(split);
				builder.append(" ");
			}
		}
		return builder.toString();
	}

	private static List<ConceptImpl> getConceptsFromRange(String range) {
		if (StringUtils.isEmpty(range)) {
			return Collections.emptyList();
		}

		List<ConceptImpl> concepts = new ArrayList<>();
		Matcher matcher = CONCEPT_TERM_PATTERN.matcher(range);
		while (matcher.find()) {
			String parts[] = matcher.group().split(" ", 2);
			String conceptId = parts[0].trim();
			String term = parts[1].trim().substring(1, parts[1].trim().length() -1);
			ConceptImpl concept = new ConceptImpl(conceptId);
			concept.setFsn(term);
			concepts.add(concept);
		}
		return concepts;
	}

	private class MRCMFactory extends ImpotentComponentFactory {

		private static final int proximalPrimitiveConstraint = 2;
		private static final int attDomainIdIndex = 0;
		private static final int ranRangeConstraintIndex = 0;
		private static final int rangeRuleIndex = 1;
		private static final int ranContentTypeIndex = 3;
		public static final int attContentTypeIndex = 5;

		private Map<String, Domain> domains = new HashMap<>();
		private Map<String, List<Attribute>> attributeRangeMap = new HashMap<>();
		private Set<Long> ungroupedAttributes = new HashSet<>();
		private Set<Long> inUseConceptIds = new HashSet<>();
		private Set<ReferenceSetMember> lateralizableRefsets = new HashSet<>();
		private List<ReferenceSetMember> anatomyStructureAndPartRefsets = new ArrayList<>();
		private List<ReferenceSetMember> anatomyStructureAndEntireRefsets = new ArrayList<>();

		@Override
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			synchronized (this) {
				if ("1".equals(active) || LATERALIZABLE_BODY_STRUCTURE_REFSET.equals(refsetId) || ANATOMY_STRUCTURE_AND_PART_REFSET.equals(refsetId) || ANATOMY_STRUCTURE_AND_ENTIRE_REFSET.equals(refsetId)) {
					switch (refsetId) {
						case MRCM_DOMAIN_REFSET:
							// use proximal primitive domain constraint instead. see MRCM doc
							getCreateDomain(referencedComponentId).setDomainConstraint(otherValues[proximalPrimitiveConstraint]);
							break;
						case MRCM_ATTRIBUTE_DOMAIN_REFSET:
							Domain domain = getCreateDomain(otherValues[attDomainIdIndex]);
							Attribute attribute = createAttributeDomain(id, referencedComponentId, otherValues);
							domain.addAttribute(attribute);
							updateAttributeRange(attribute.getAttributeId(), domain);
							loadUngroupedAttributes(active, referencedComponentId, otherValues);
							break;
						case MRCM_ATTRIBUTE_RANGE_REFSET:
							Attribute attributeRange =createAttributeRange(id, referencedComponentId, otherValues);
							addInUseConceptIds(attributeRange.getRangeRule());
							addInUseConceptIds(attributeRange.getRangeConstraint());
							break;
						case LATERALIZABLE_BODY_STRUCTURE_REFSET:
							lateralizableRefsets.add(new ReferenceSetMember(id, effectiveTime, "1".equals(active), moduleId, refsetId, referencedComponentId));
							break;
						case ANATOMY_STRUCTURE_AND_PART_REFSET:
							anatomyStructureAndPartRefsets.add(new ReferenceSetMember(id, effectiveTime, "1".equals(active), moduleId, refsetId, referencedComponentId, otherValues));
							break;
						case ANATOMY_STRUCTURE_AND_ENTIRE_REFSET:
							anatomyStructureAndEntireRefsets.add(new ReferenceSetMember(id, effectiveTime, "1".equals(active), moduleId, refsetId, referencedComponentId, otherValues));
							break;
						default:
							LOGGER.trace("Refset member from refsetId {} not required for MRCM processing", refsetId);
							break;
					}
				}
			}
		}

		private void updateAttributeRange(String attributeId, Domain domain) {
			for (Attribute attribute : domain.getAttributes()) {
				if (attributeRangeMap.get(attribute.getAttributeId()) == null) {
					return;
				}
				for (Attribute range : attributeRangeMap.get(attribute.getAttributeId())) {
					domain.addAttributeRange(range);
				}
			}
		}

		private void updateDomainWithAttributeRange() {
			for (Domain domain : domains.values()) {
				for (Attribute attribute : domain.getAttributes()) {
					if (attributeRangeMap.containsKey(attribute.getAttributeId())) {
						for (Attribute range : attributeRangeMap.get(attribute.getAttributeId())) {
							domain.addAttributeRange(range);
						}
					}
				}
			}
		}

		public Map<String, Domain> getDomains() {
			return domains;
		}

		public Set <ReferenceSetMember> getLateralizableRefsets() {
			return lateralizableRefsets;
		}

		public List<ReferenceSetMember> getAnatomyStructureAndEntireRefsets() {
			return anatomyStructureAndEntireRefsets;
		}

		public List<ReferenceSetMember> getAnatomyStructureAndPartRefsets() {
			return anatomyStructureAndPartRefsets;
		}

		private Domain getCreateDomain(String referencedComponentId) {
			if (!domains.containsKey(referencedComponentId)) {
				domains.put(referencedComponentId, new Domain(referencedComponentId));
			}
			return domains.get(referencedComponentId);
		}

		public Attribute createAttributeDomain(String id, String attributeId, String... otherValues) {
			Attribute attribute = new Attribute(attributeId, otherValues[attContentTypeIndex]);
			if (id != null && !id.isEmpty()) {
				attribute.setUuid(UUID.fromString(id));
			}
			attribute.setGrouped(otherValues[1]);
			attribute.setAttributeCardinality(otherValues[2]);
			attribute.setAttributeInGroupCardinality(otherValues[3]);
			attribute.setRuleStrengthId(otherValues[4]);
			attribute.setType(Attribute.Type.DOMAIN);
			return attribute;
		}

		public Map<String, List<Attribute>> getAttributeRangeMap() {
			return attributeRangeMap;
		}

		public Attribute createAttributeRange(String uuid, String attributeId, String... otherValues) {
			Attribute attribute = new Attribute(attributeId, otherValues[ranContentTypeIndex]);
			if (uuid != null && !uuid.isEmpty()) {
				attribute.setUuid(UUID.fromString(uuid));
			}
			attribute.setRangeConstraint(otherValues[ranRangeConstraintIndex]);
			attribute.setRangeRule(otherValues[rangeRuleIndex]);
			attribute.setRuleStrengthId(otherValues[2]);
			attribute.setType(Type.RANGE);
			if (attributeRangeMap.containsKey(attribute.getAttributeId())) {
				attributeRangeMap.get(attribute.getAttributeId()).add(attribute);
			} else {
				List<Attribute> list = new ArrayList<>();
				list.add(attribute);
				attributeRangeMap.put(attributeId, list);
			}
			updateDomainWithAttributeRange();
			return attribute;
		}

		public Set<Long> getUngroupedAttributes() {
			return ungroupedAttributes;
		}

		private void loadUngroupedAttributes(String active, String referencedComponentId, String... otherValues) {
			if ("1".equals(active)) {
				// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	domainId	grouped	attributeCardinality	attributeInGroupCardinality	ruleStrengthId	contentTypeId
				// 																otherValues .. 	0			1		2						3							4				5
				// Ungrouped attribute
				if ("0".equals(otherValues[1])) {
					ungroupedAttributes.add(parseLong(referencedComponentId));
				}
			}
		}

		private void addInUseConceptIds(String range) {
			List<ConceptImpl> concepts = getConceptsFromRange(range);
			concepts.forEach(concept -> {
				this.inUseConceptIds.add(concept.getId());
			});
		}

		public Set<Long> getInUseConceptIds() {
			return this.inUseConceptIds;
		}
	}

	protected static class OWLExpressionAndDescriptionFactory extends ComponentStoreComponentFactoryImpl {

		private final AxiomRelationshipConversionService axiomConverter;
		private final Logger logger = LoggerFactory.getLogger(getClass());
		private final ComponentStore componentStore;
		private final Set<Long> conceptsUsedInMRCMTemplates;
		private final Map<Long, List<DescriptionImpl>> descriptions;
		private final Map<String, AtomicInteger> relationshipRoleGroupIncrementer;

		public OWLExpressionAndDescriptionFactory(ComponentStore componentStore, Set<Long> ungroupedAttributes, Set<Long> conceptsUsedInMRCMTemplates) {
			super(componentStore);
			this.componentStore = componentStore;
			this.axiomConverter = new AxiomRelationshipConversionService(ungroupedAttributes);
			this.conceptsUsedInMRCMTemplates = conceptsUsedInMRCMTemplates;
			this.descriptions = new Long2ObjectArrayMap<>();
			this.relationshipRoleGroupIncrementer = new HashMap<>();
		}

		@Override
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			synchronized (this) {
				if("1".equals(active) && OWL_AXIOM_REFSET.equals(refsetId)) {
					// OWL OntologyAxiom reference set
					// Fields: id	effectiveTime	active	moduleId	refsetId	referencedComponentId	owlExpression
					String owlExpression = otherValues[0];
					try {
						AtomicInteger groupOffset = getGroupOffset(referencedComponentId);
						AxiomRepresentation axiom = axiomConverter.convertAxiomToRelationships(owlExpression, groupOffset);
						if (axiom != null) {
							if (axiom.getLeftHandSideNamedConcept() != null && axiom.getRightHandSideRelationships() != null) {
								// Regular axiom
								addRelationships(id, axiom.getLeftHandSideNamedConcept(), axiom.getRightHandSideRelationships(), moduleId, effectiveTime);
							} else if (axiom.getRightHandSideNamedConcept() != null && axiom.getLeftHandSideRelationships() != null) {
								// skip GCI axioms
								logger.info("GCI axiom id {}", id);
							}
						}
					} catch (ConversionException | OWLParserException e) {
						logger.error("OntologyAxiom conversion failed for refset member {}", id, e);
					}
				}
			}
		}

		@Override
		public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
			if (conceptsUsedInMRCMTemplates.contains(Long.parseLong(conceptId))) {
				DescriptionImpl description = new DescriptionImpl(id, FactoryUtils.parseActive(active), term, conceptId);
				if (descriptions.containsKey(Long.valueOf(conceptId))) {
					descriptions.get(Long.valueOf(conceptId)).add(description);
				} else {
					List<DescriptionImpl> newDescriptions = new ArrayList<>();
					newDescriptions.add(description);
					descriptions.put(Long.valueOf(conceptId), newDescriptions);
				}
			}
		}

		public Map<Long, List<DescriptionImpl>> getDescriptions() {
			return this.descriptions;
		}

		private void addRelationships(String axiomId, Long namedConcept, Map<Integer, List<Relationship>> groups, String moduleId, String effectiveTime) {
			groups.forEach((group, relationships) -> relationships.forEach(relationship -> {
				String typeId = String.valueOf(relationship.getTypeId());
				String destinationId = String.valueOf(relationship.getDestinationId());

				// Build a composite identifier for this 'relationship' (which is actually a fragment of an axiom expression) because it doesn't have its own component identifier.
				String compositeIdentifier = axiomId + "/Group_" + group + "/Type_" + typeId + "/Destination_" + destinationId;
				newRelationshipState(compositeIdentifier, effectiveTime, "1", moduleId, namedConcept.toString(), destinationId, String.valueOf(group), typeId, ConceptConstants.STATED_RELATIONSHIP,  "900000000000451002");
				logger.debug("Add axiom relationship {}", compositeIdentifier);
				
				this.addStatedConceptAttribute(namedConcept.toString(), typeId, destinationId);
				if(ConceptConstants.isA.equals(typeId)) {
					this.addStatedConceptParent(namedConcept.toString(), destinationId);
					this.addStatedConceptChild(namedConcept.toString(), destinationId);
				}
			}));
		}

		private AtomicInteger getGroupOffset(String conceptId) {
			this.relationshipRoleGroupIncrementer.computeIfAbsent(conceptId, k -> new AtomicInteger(1)); // Skipping to 1 as 0 reserved for non-grouped
			return this.relationshipRoleGroupIncrementer.get(conceptId);
		}

		private ComponentStore getComponentStore() {
			return this.componentStore;
		}
	}

	private static class MRCMValidatorReleaseImportManager extends ReleaseImportManager {

		private final ReleaseImporter releaseImporter;

		public MRCMValidatorReleaseImportManager() {
			releaseImporter = new ReleaseImporter();
		}

		public ReleaseStore loadReleaseFilesToMemoryBasedIndex(Set<String> extractedRF2FilesDirectories, LoadingProfile loadingProfile, OWLExpressionAndDescriptionFactory componentFactory, boolean fullSnapshotRelease) throws ReleaseImportException, IOException {
			return loadReleaseFiledToStore(extractedRF2FilesDirectories, loadingProfile, new RamReleaseStore(), componentFactory, fullSnapshotRelease);
		}

		private ReleaseStore loadReleaseFiledToStore(Set<String> extractedRF2FilesDirectories, LoadingProfile loadingProfile, ReleaseStore releaseStore, OWLExpressionAndDescriptionFactory componentFactory, boolean fullSnapshotRelease) throws ReleaseImportException, IOException {
			if (fullSnapshotRelease) {
				releaseImporter.loadSnapshotReleaseFiles(extractedRF2FilesDirectories.iterator().next(), loadingProfile,
						new HighLevelComponentFactoryAdapterImpl(loadingProfile, componentFactory, componentFactory), false);
			} else {
				boolean loadDelta = RF2ReleaseFilesUtil.anyDeltaFilesPresent(extractedRF2FilesDirectories);
				if (loadDelta) {
					releaseImporter.loadEffectiveSnapshotAndDeltaReleaseFiles(extractedRF2FilesDirectories, loadingProfile,
							new HighLevelComponentFactoryAdapterImpl(loadingProfile, componentFactory, componentFactory), false);
				} else {
					releaseImporter.loadEffectiveSnapshotReleaseFiles(extractedRF2FilesDirectories, loadingProfile,
							new HighLevelComponentFactoryAdapterImpl(loadingProfile, componentFactory, componentFactory), false);
				}
			}
			final Map<Long, ? extends Concept> conceptMap = componentFactory.getComponentStore().getConcepts();
			return writeToIndex(conceptMap, releaseStore, loadingProfile);
		}
	}
}
