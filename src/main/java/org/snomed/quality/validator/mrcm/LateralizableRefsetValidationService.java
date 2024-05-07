package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.dto.ConceptIdResults;
import org.ihtsdo.otf.sqs.service.dto.ConceptResult;
import org.ihtsdo.otf.sqs.service.exception.ConceptNotFoundException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.quality.validator.mrcm.model.ReferenceSetMember;
import org.springframework.util.CollectionUtils;

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
		Assertion assertionOfMembersToRemove = new Assertion(UUID.fromString(ASSERTION_ID_MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET), ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE, MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET_TEXT, Assertion.FailureType.ERROR);
		try {
			Set<Long> conceptsToRemove = getRelevantConceptsToRemove(queryService, run);
			reportConceptsToRemove(run, conceptsToRemove, assertionOfMembersToRemove);
			run.addCompletedAssertion(assertionOfMembersToRemove);
		} catch (Exception e) {
			LOGGER.error("Failed to validate Lateralisable Reference Set; cannot process Concepts to remove.", e);
			assertionOfMembersToRemove.setFailureMessage(e.getMessage());
			run.addIncompleteAssertion(assertionOfMembersToRemove);
		}

		Assertion assertionOfConceptsToAdd = new Assertion(UUID.fromString(ASSERTION_ID_CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET), ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE, CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET_TEXT, Assertion.FailureType.ERROR);
		try {
			Set<Long> conceptsToAdd = getRelevantConceptsToAdd(queryService, run);
			reportConceptsToAdd(run, conceptsToAdd, assertionOfConceptsToAdd, queryService);
			run.addCompletedAssertion(assertionOfConceptsToAdd);
		} catch (Exception e) {
			LOGGER.error("Failed to validate Lateralisable Reference Set; cannot process Concepts to add.", e);
			assertionOfConceptsToAdd.setFailureMessage(e.getMessage());
			run.addIncompleteAssertion(assertionOfConceptsToAdd);
		}
	}

	private Set<Long> getRelevantConceptsToRemove(SnomedQueryService queryService, ValidationRun run) throws ServiceException {
        Set<Long> result = new HashSet<>();
        Set<Long> conceptsToRemove = new HashSet<>(getAllConceptsByECL(queryService, ECL_TO_REMOVE_MEMBERSHIP));

		for (Long conceptId : conceptsToRemove) {
			ConceptResult conceptResult = queryService.retrieveConcept(conceptId.toString());
			if (conceptResult.getEffectiveTime().equals(run.getReleaseDate())) {
				result.add(conceptId);
			}
		}

		// Exclude concepts that have the following semantic tags: cell/Cell structure/Morphologic abnormality
		for (ReferenceSetMember member : run.getLateralizableRefsetMembers()) {
			try {
				ConceptResult conceptResult = queryService.retrieveConcept(member.referencedComponentId());
				if (conceptResult != null) {
					String fsn = conceptResult.getFsn();
					if (fsn != null && (fsn.endsWith("(cell)") || fsn.endsWith("(cell structure)") || fsn.endsWith("(morphologic abnormality)"))) {
						result.add(Long.parseLong(member.referencedComponentId()));
					}
				}
			} catch (ConceptNotFoundException e) {
				result.add(Long.parseLong(member.referencedComponentId()));
			}
		}

		LOGGER.info("{} Concepts IDENTIFIED for removal from lateralisable reference set.", conceptsToRemove.size());
		LOGGER.info("{} Concepts REPORTED for removal from lateralisable reference set.", result.size());
		return result;
	}

	private Set<Long> getRelevantConceptsToAdd(SnomedQueryService queryService, ValidationRun run) throws ServiceException {
		Set<Long> result = new HashSet<>();
		Set<Long> conceptsToAdd = new HashSet<>(getAllConceptsByECL(queryService, ECL_TO_ADD_MEMBERSHIP));

		for (Long conceptId : conceptsToAdd) {
			ConceptResult conceptResult = queryService.retrieveConcept(conceptId.toString());
			if (conceptResult.getEffectiveTime().equals(run.getReleaseDate())) {
				result.add(conceptId);
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

	private void reportConceptsToRemove(ValidationRun run, Set<Long> conceptsToRemove, Assertion assertionOfMembersToRemove) {
		if (!conceptsToRemove.isEmpty()) {
			Set<ReferenceSetMember> membersToRemove = run.getLateralizableRefsetMembers().stream().filter(referenceSetMember -> (CollectionUtils.isEmpty(run.getModuleIds()) || !run.getModuleIds().contains(referenceSetMember.moduleId())) && conceptsToRemove.contains(Long.valueOf(referenceSetMember.referencedComponentId())))
					.collect(Collectors.toSet());
			if (!membersToRemove.isEmpty()) {
				assertionOfMembersToRemove.setCurrentViolatedReferenceSetMembers(membersToRemove.stream().map(ReferenceSetMember::memberId).collect(Collectors.toList()));
			}
		}
	}

	private void reportConceptsToAdd(ValidationRun run, Set<Long> conceptsToAdd, Assertion assertionOfConceptsToAdd, SnomedQueryService queryService) throws ServiceException {
		if (!conceptsToAdd.isEmpty()) {
			Set<String> allReferencedComponentIds = run.getLateralizableRefsetMembers().stream().map(ReferenceSetMember::referencedComponentId).collect(Collectors.toSet());
			Set<Long> referencedComponentIdsToAdd = conceptsToAdd.stream().filter(conceptId -> !allReferencedComponentIds.contains(conceptId.toString())).collect(Collectors.toSet());
			if (!referencedComponentIdsToAdd.isEmpty()) {
				Set<Long> filteredReferencedComponentIdsToAdd;
				if (!CollectionUtils.isEmpty(run.getModuleIds())) {
					filteredReferencedComponentIdsToAdd = new HashSet<>();
					for (Long conceptId : referencedComponentIdsToAdd) {
						ConceptResult conceptResult = queryService.retrieveConcept(conceptId.toString());
						if (run.getModuleIds().contains(conceptResult.getModuleId())) {
							filteredReferencedComponentIdsToAdd.add(conceptId);
						}
					}
				} else {
					filteredReferencedComponentIdsToAdd = referencedComponentIdsToAdd;
				}
				if (!filteredReferencedComponentIdsToAdd.isEmpty()) {
					assertionOfConceptsToAdd.setCurrentViolatedConceptIds(new ArrayList<>(referencedComponentIdsToAdd));
				}
			}
		}
	}
}
