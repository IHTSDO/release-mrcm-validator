package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.dto.ConceptIdResults;
import org.ihtsdo.otf.sqs.service.dto.ConceptResult;
import org.ihtsdo.otf.sqs.service.exception.ConceptNotFoundException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.quality.validator.mrcm.model.ReferenceSetMember;

import java.util.*;
import java.util.stream.Collectors;

public class LateralizableRefsetValidationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(LateralizableRefsetValidationService.class);
	private static final String ECL_RULE_FOR_MEMBERSHIP = "(<< 423857001 |Structure of half of body lateral to midsagittal plane (body structure)| MINUS ( * : 272741003 | Laterality (attribute) | = (7771000 |Left (qualifier value)| OR 24028007 |Right (qualifier value)| OR 51440002 |Right and left (qualifier value)|) ))";
	private static final String ECL_TO_ADD_MEMBERSHIP = ECL_RULE_FOR_MEMBERSHIP + " MINUS (^ 723264001)";
	private static final String ECL_TO_REMOVE_MEMBERSHIP = "(^ 723264001) MINUS " + ECL_RULE_FOR_MEMBERSHIP;

	public static final String ASSERTION_ID_MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET = "64c8d4e7-4d8e-4f94-a7f0-ee8f2b162fbf";
	public static final String MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET_TEXT = "The refset members need to be inactivated/removed from Lateralizable reference set";

	public static final String ASSERTION_ID_CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET = "64031766-438d-4ad1-ba4c-29c5f22d9108";
	public static final String CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET_TEXT = "The concepts need to be added to Lateralizable reference set";

	public void validate(SnomedQueryService queryService, ValidationRun run) {
		LOGGER.info("Validating 723264001 |Lateralizable body structure reference set|");
		Map<String, List<ReferenceSetMember>> membersByConceptId = mapMembersByConceptId(run);
		Assertion assertionOfMembersToRemove = new Assertion(UUID.fromString(ASSERTION_ID_MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET), ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE, MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET_TEXT, Assertion.FailureType.ERROR);
		try {
			List<ConceptResult> conceptsToRemove = getRelevantConceptsToRemove(queryService, run, membersByConceptId);
			reportConceptsToRemove(run, conceptsToRemove, assertionOfMembersToRemove);
			run.addCompletedAssertion(assertionOfMembersToRemove);
		} catch (Exception e) {
			LOGGER.error("Failed to validate Lateralisable Reference Set; cannot process Concepts to remove.", e);
			assertionOfMembersToRemove.setFailureMessage(e.getMessage());
			run.addIncompleteAssertion(assertionOfMembersToRemove);
		}

		Assertion assertionOfConceptsToAdd = new Assertion(UUID.fromString(ASSERTION_ID_CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET), ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE, CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET_TEXT, Assertion.FailureType.ERROR);
		try {
			List<ConceptResult> conceptsToAdd = getRelevantConceptsToAdd(queryService, run, membersByConceptId);
			reportConceptsToAdd(run, conceptsToAdd, assertionOfConceptsToAdd);
			run.addCompletedAssertion(assertionOfConceptsToAdd);
		} catch (Exception e) {
			LOGGER.error("Failed to validate Lateralisable Reference Set; cannot process Concepts to add.", e);
			assertionOfConceptsToAdd.setFailureMessage(e.getMessage());
			run.addIncompleteAssertion(assertionOfConceptsToAdd);
		}
	}

	private List<ConceptResult> getRelevantConceptsToRemove(SnomedQueryService queryService, ValidationRun run, Map<String, List<ReferenceSetMember>> membersByConceptId) throws ServiceException {
		List<ConceptResult> result = new ArrayList<>();
		Set<Long> conceptsToRemove = new HashSet<>(getAllConceptsByECL(queryService, ECL_TO_REMOVE_MEMBERSHIP));

		for (Long conceptId : conceptsToRemove) {
			ConceptResult conceptResult = queryService.retrieveConcept(conceptId.toString());
			boolean conceptMatches = conceptResult.getEffectiveTime().equals(run.getReleaseDate());
			boolean memberMatches = membersByConceptId.getOrDefault(String.valueOf(conceptId), Collections.emptyList()).stream().filter(ReferenceSetMember::active).anyMatch(r -> Objects.equals(r.effectiveTime(), run.getReleaseDate()));
			if (conceptMatches || memberMatches) {
				result.add(conceptResult);
			}
		}

		// Exclude concepts that have the following semantic tags: cell/Cell structure/Morphologic abnormality
		for (ReferenceSetMember member : run.getLateralizableRefsetMembers()) {
			if (!member.active()) {
				continue;
			}

			try {
				ConceptResult conceptResult = queryService.retrieveConcept(member.referencedComponentId());
				if (conceptResult != null) {
					String fsn = conceptResult.getFsn();
					if (fsn != null && (fsn.endsWith("(cell)") || fsn.endsWith("(cell structure)") || fsn.endsWith("(morphologic abnormality)"))) {
						result.add(conceptResult);
					}
				}
			} catch (ConceptNotFoundException e) {
				result.add(new ConceptResult(member.referencedComponentId()));
			}
		}

		LOGGER.info("{} Concepts IDENTIFIED for removal from lateralisable reference set.", conceptsToRemove.size());
		LOGGER.info("{} Concepts REPORTED for removal from lateralisable reference set.", result.size());
		return result;
	}

	private List<ConceptResult> getRelevantConceptsToAdd(SnomedQueryService queryService, ValidationRun run, Map<String, List<ReferenceSetMember>> membersByConceptId) throws ServiceException {
		List<ConceptResult> result = new ArrayList<>();
		Set<Long> conceptsToAdd = new HashSet<>(getAllConceptsByECL(queryService, ECL_TO_ADD_MEMBERSHIP));

		for (Long conceptId : conceptsToAdd) {
			ConceptResult conceptResult = queryService.retrieveConcept(conceptId.toString());
			boolean conceptMatches = conceptResult.getEffectiveTime().equals(run.getReleaseDate());
			boolean memberMatches = membersByConceptId.getOrDefault(String.valueOf(conceptId), Collections.emptyList()).stream().anyMatch(r -> Objects.equals(r.effectiveTime(), run.getReleaseDate()));
			if (conceptMatches || memberMatches) {
				result.add(conceptResult);
			}
		}

		LOGGER.info("{} Concepts IDENTIFIED for addition to lateralisable reference set.", conceptsToAdd.size());
		LOGGER.info("{} Concepts REPORTED for addition to lateralisable reference set.", result.size());
		return result;
	}

	private List<Long> getAllConceptsByECL(SnomedQueryService queryService, String ecl) throws ServiceException {
		ConceptIdResults results = queryService.eclQueryReturnConceptIdentifiers(ecl, 0, -1);
		return results.conceptIds();
	}

	private void reportConceptsToRemove(ValidationRun run, List<ConceptResult> conceptsToRemove, Assertion assertionOfMembersToRemove) {
		if (!conceptsToRemove.isEmpty()) {
			Set<ReferenceSetMember> referenceSetMembers = run.getLateralizableRefsetMembers();
			Set<String> moduleIds = run.getModuleIds();
			if (moduleIds != null && !moduleIds.isEmpty()) {
				referenceSetMembers.removeIf(referenceSetMember -> !moduleIds.contains(referenceSetMember.moduleId()));
			}

			List<String> referencedComponentIds = referenceSetMembers.stream().filter(ReferenceSetMember::active).map(ReferenceSetMember::referencedComponentId).toList();
			conceptsToRemove.removeIf(conceptResult -> !referencedComponentIds.contains(conceptResult.getId()));

			assertionOfMembersToRemove.setCurrentViolatedConcepts(conceptsToRemove);
			assertionOfMembersToRemove.setCurrentViolatedConceptIds(conceptsToRemove.stream().map(ConceptResult::getId).map(Long::parseLong).toList());
			LOGGER.info("{} Concepts FILTERED for removal from lateralisable reference set.", conceptsToRemove.size());
		}
	}

	private void reportConceptsToAdd(ValidationRun run, List<ConceptResult> conceptsToAdd, Assertion assertionOfConceptsToAdd) {
		if (!conceptsToAdd.isEmpty()) {
			Set<String> allActiveReferencedComponentIds = run.getLateralizableRefsetMembers().stream().filter(ReferenceSetMember::active).map(ReferenceSetMember::referencedComponentId).collect(Collectors.toSet());
			Set<String> moduleIds = run.getModuleIds();
			boolean hasModules = moduleIds != null && !moduleIds.isEmpty();
			conceptsToAdd.removeIf(conceptResult -> {
				boolean duplicate = allActiveReferencedComponentIds.contains(conceptResult.getId());
				boolean outOfScope = hasModules && !moduleIds.contains(conceptResult.getModuleId());
				return duplicate || outOfScope;
			});

			assertionOfConceptsToAdd.setCurrentViolatedConcepts(conceptsToAdd);
			assertionOfConceptsToAdd.setCurrentViolatedConceptIds(conceptsToAdd.stream().map(ConceptResult::getId).map(Long::parseLong).toList());
			LOGGER.info("{} Concepts FILTERED for addition to lateralisable reference set.", conceptsToAdd.size());
		}
	}

	private Map<String, List<ReferenceSetMember>> mapMembersByConceptId(ValidationRun run) {
		if (run == null) {
			return Collections.emptyMap();
		}

		Set<ReferenceSetMember> lateralizableRefsetMembers = run.getLateralizableRefsetMembers();
		if (lateralizableRefsetMembers == null || lateralizableRefsetMembers.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<String, List<ReferenceSetMember>> membersByConceptId = new HashMap<>();
		for (ReferenceSetMember referenceSetMember : lateralizableRefsetMembers) {
			String key = referenceSetMember.referencedComponentId();
			List<ReferenceSetMember> value = membersByConceptId.computeIfAbsent(key, k -> new ArrayList<>());

			value.add(referenceSetMember);
			membersByConceptId.put(key, value);
		}

		return membersByConceptId;
	}
}
