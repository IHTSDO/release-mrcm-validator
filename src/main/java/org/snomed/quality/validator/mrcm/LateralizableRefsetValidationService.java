package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.dto.ConceptIdResults;
import org.ihtsdo.otf.sqs.service.dto.ConceptResult;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.snomed.quality.validator.mrcm.model.ReferenceSetMember;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class LateralizableRefsetValidationService {

	public static final String ASSERTION_ID_MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET = "64c8d4e7-4d8e-4f94-a7f0-ee8f2b162fbf";
	public static final String MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET_TEXT = "The refset members need to be inactivated/removed from Lateralizable reference set";

	public static final String ASSERTION_ID_CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET = "64031766-438d-4ad1-ba4c-29c5f22d9108";
	public static final String CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET_TEXT = "The concepts need to be added to Lateralizable reference set";

	public void validate(SnomedQueryService queryService, ValidationRun run) throws ServiceException {
		Set<Long> conceptsToRemove = getRelevantConceptsToRemove(queryService);
		Set<Long> conceptsToAdd = getRelevantConceptsToAdd(queryService);

		Assertion assertionOfMembersToRemove = new Assertion(UUID.fromString(ASSERTION_ID_MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET), ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE, MEMBERS_NEED_TO_BE_REMOVED_FROM_LATERALIZABLE_REFSET_TEXT, Assertion.FailureType.ERROR);
		run.addCompletedAssertion(assertionOfMembersToRemove);

		Assertion assertionOfConceptsToAdd = new Assertion(UUID.fromString(ASSERTION_ID_CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET), ValidationType.LATERALIZABLE_BODY_STRUCTURE_REFSET_TYPE, CONCEPTS_NEED_TO_BE_ADDED_TO_LATERALIZABLE_REFSET_TEXT, Assertion.FailureType.ERROR);
		run.addCompletedAssertion(assertionOfConceptsToAdd);

		if (!conceptsToRemove.isEmpty()) {
			Set<ReferenceSetMember> membersToRemove = run.getLateralizableRefsetMembers().stream().filter(referenceSetMember -> (CollectionUtils.isEmpty(run.getModuleIds()) || !run.getModuleIds().contains(referenceSetMember.getModuleId())) && conceptsToRemove.contains(Long.valueOf(referenceSetMember.getReferencedComponentId()).longValue()))
					.collect(Collectors.toSet());
			if (!membersToRemove.isEmpty()) {
				assertionOfMembersToRemove.setCurrentViolatedReferenceSetMembers(membersToRemove.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList()));
			}
		}

		if (!conceptsToAdd.isEmpty()) {
			Set<String> allReferencedComponentIds = run.getLateralizableRefsetMembers().stream().map(ReferenceSetMember::getReferencedComponentId).collect(Collectors.toSet());
			Set<Long> referencedComponentIdsToAdd = conceptsToAdd.stream().filter(conceptId -> !allReferencedComponentIds.contains(conceptId.toString())).collect(Collectors.toSet());
			if (!referencedComponentIdsToAdd.isEmpty()) {
				Set<Long> filteredReferencedComponentIdsToAdd ;
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

	private Set<Long> getRelevantConceptsToRemove(SnomedQueryService queryService) throws ServiceException {
		// Concepts which have been lateralised
		String byLaterality = "(^ 723264001 AND << 91723000) : (272741003 = (7771000 OR 24028007 OR 51440002))";

		// Concepts which no longer have an appropriate prerequisite ancestor
		String byNoPrerequisiteAncestor = "^ 723264001 MINUS (^ 723264001 AND << 423857001)";

		Set<Long> conceptsToRemove = new HashSet<>();
		conceptsToRemove.addAll(getAllConceptsByECL(queryService, byLaterality));
		conceptsToRemove.addAll(getAllConceptsByECL(queryService, byNoPrerequisiteAncestor));

		return conceptsToRemove;
	}

	private Set<Long> getRelevantConceptsToAdd(SnomedQueryService queryService) throws ServiceException {
		// Concepts which have the laterality attribute with an appropriate value
		String byLaterality = "( (<< 91723000 : 272741003 = 182353008) MINUS ( * : 272741003 = (7771000	 OR 24028007 OR 51440002) ) )  MINUS (^ 723264001)";

		Set<Long> conceptsToAdd = new HashSet<>();
		conceptsToAdd.addAll(getAllConceptsByECL(queryService, byLaterality));

		// Concepts which are within a certain hierarchy and have an ancestor within the reference set
		String byHierarchy = "(( << 91723000 MINUS (* : 272741003 = (7771000 OR 24028007 OR 51440002 )))  AND (<  (^ 723264001)))	MINUS (^ 723264001)";
		conceptsToAdd.addAll(getAllConceptsByECL(queryService, byHierarchy));

		return conceptsToAdd;
	}

	private List<Long> getAllConceptsByECL(SnomedQueryService queryService, String ecl) throws ServiceException {
		ConceptIdResults results = queryService.eclQueryReturnConceptIdentifiers(ecl, 0, -1);
		return results.getConceptIds();
	}
}
