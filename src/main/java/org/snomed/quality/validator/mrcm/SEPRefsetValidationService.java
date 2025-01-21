package org.snomed.quality.validator.mrcm;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.dto.ConceptResult;
import org.ihtsdo.otf.sqs.service.dto.ConceptResults;
import org.ihtsdo.otf.sqs.service.exception.ConceptNotFoundException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.snomed.quality.validator.mrcm.model.ReferenceSetMember;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SEPRefsetValidationService {
    private static final String BODY_STRUCTURE_SEMANTIC_TAG = "body structure";
    private static final String ENTIRE = "Entire";
    private static final String ALL = "All";
    private static final String PART = "Part";
    private static final String STRUCTURE = "structure";

    public enum SEPAssertionType {
        ACTIVE_REFERENCED_COMPONENT_SE_REFSET("fd4fdffa-87dc-4241-ae73-7b8c61284056", "The referenced components in SE refset must be active", "The referenced component id=%s in SE refset must be active."),
        ACTIVE_TARGET_COMPONENT_SE_REFSET("48ad7e95-0e56-4c97-a06f-e8f94773a708", "The target components in SE refset must be active", "The target component id=%s in SE refset must be active."),
        ACTIVE_REFERENCED_COMPONENT_SP_REFSET("640d186b-4420-49e6-a2ae-0aeaffed85f9", "The referenced components in SP refset  must be active", "The referenced component id=%s in SP refset must be active."),
        ACTIVE_TARGET_COMPONENT_SP_REFSET("0a19cbb6-8020-4410-9b07-4dcc820c7919", "The target components in SP refset  must be active", "The target component id=%s in SP refset must be active."),
        DUPLICATE_REFERENCED_COMPONENT_SE_REFSET("4cafa321-0ab0-4b68-8159-39a50c6c26e5", "The referenced components should only appear once in SE refset", "The referenced component id=%s should only appear once per SE refset."),
        DUPLICATE_REFERENCED_COMPONENT_SP_REFSET("ee13b298-b35b-454f-a91c-d38bec618267", "The referenced components should only appear once in SP refset", "The referenced component id=%s should only appear once per SP refset."),
        DUPLICATE_TARGET_COMPONENT_SE_REFSET("44e0ba37-6d6e-4843-9cf1-45cf11b0c8e6", "The target components should only appear once in SE refset", "The target component id=%s should only appear once per SE refset."),
        DUPLICATE_TARGET_COMPONENT_SP_REFSET("a7a42fc4-31d8-4b68-9507-364a25d636f8", "The target components should only appear once in SP refset", "The target component id=%s should only appear once per SP refset."),
        INVALID_STRUCTURE_CONCEPT_SEP_REFSET("b8a616e8-9752-43ce-bb7f-f68d2537dc1b", "The FSN for an S concept must contain the word Structure (case insensitive match) and must not start with the word Entire, All or Part", "A S concept id=%s in SEP refset must contain the word Structure and must not start with the word Entire, All or Part."),
        INVALID_PART_CONCEPT_SP_REFSET("eaea2afa-732c-42d2-a228-f8541b883986", "The FSN for a P concept must start with the word Part (case sensitive match) or contain the word part", "A P concept id=%s in SP refset must contain the word part."),
        INVALID_ALL_OR_ENTIRE_CONCEPT_SE_REFSET("e2267903-5c91-4596-9bdb-662b85f4206b", "The FSN for an E concept must start with the word Entire or the word All (case sensitive match)", "An E concept id=%s in SE refset must start with the word Entire or the word All."),
        MISSING_ALL_OR_ENTIRE_CONCEPTS_FROM_SE_REFSET("4d1fcca3-cb9b-4cc3-92a5-6e99a5b0110e", "All body structure concepts that start with the word 'Entire' or 'All' should appear in the SE refset", "A body structure concept id=%s that starts with the word Entire or All should appear in the SE refset."),
        MISSING_PART_CONCEPTS_FROM_SE_REFSET("6245dda6-fc40-4a0e-bbad-f05fcd9aff36", "All body structure concepts that start with the word 'Part' should appear in the SP refset", "A body structure concept id=%s that starts with the word Part should appear in the SP refset."),
        INVALID_PAIR_OF_TARGET_AND_REFERENCED_COMPONENTS_SEP_REFSET("d8dbdc04-3072-438e-9584-58191520d37d", "For both SE and SP refsets, the 'S' concept should be an inferred parent the targetComponentId (E or P)", "The target component id=%s should have an inferred parent in S concept in SEP refset.");

        final String uuid;
        final String assertionText;
        final String detail;

        SEPAssertionType(String uuid, String assertionText, String detail) {
            this.uuid = uuid;
            this.assertionText = assertionText;
            this.detail = detail;
        }

        public String getUuid() {
            return uuid;
        }

        public String getAssertionText() {
            return assertionText;
        }

        public String getDetail() {
            return detail;
        }

        static SEPAssertionType fromUUID(String uuid) {
            return Arrays.stream(SEPAssertionType.values()).filter(item -> item.uuid.equals(uuid)).findFirst().orElse(null);
        }
    }

    public void validate(SnomedQueryService queryService, ValidationRun run) throws ServiceException, IOException {
        Set<String> exclusionList = getExclusionList(queryService);
        List<ConceptResult> bodyStructureConcepts = getAllBodyStructureConcepts(queryService);

        validateActiveReferenceAndTargetComponents(run, queryService);
        validateDuplicateReferenceComponents(run, queryService);
        validateDuplicateTargetComponents(run, queryService);
        validateAnatomyStructureConceptInSEPRefset(run, queryService, exclusionList);
        validatePartConceptInSPRefset(run, queryService);
        validateAllOrEntireConceptInSERefset(run, queryService, exclusionList);
        validateBodyStructureConcepts(run, queryService, bodyStructureConcepts);
        validateParentConceptsOfTargetComponents(run, queryService);
    }

    // 1. For active members, all referenced components and target components are active
    private void validateActiveReferenceAndTargetComponents(ValidationRun run, SnomedQueryService queryService) throws ServiceException {
        Assertion assertionForReferenceComponentInSERefset = new Assertion(UUID.fromString(SEPAssertionType.ACTIVE_REFERENCED_COMPONENT_SE_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.ACTIVE_REFERENCED_COMPONENT_SE_REFSET.getAssertionText(), Assertion.FailureType.ERROR);
        run.addCompletedAssertion(assertionForReferenceComponentInSERefset);
        Assertion assertionForTargetComponentInSERefset = new Assertion(UUID.fromString(SEPAssertionType.ACTIVE_TARGET_COMPONENT_SE_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.ACTIVE_TARGET_COMPONENT_SE_REFSET.getAssertionText(), Assertion.FailureType.ERROR);
        run.addCompletedAssertion(assertionForTargetComponentInSERefset);
        doValidateActiveReferenceAndTargetComponents(run.getAnatomyStructureAndEntireRefsets(), queryService, assertionForReferenceComponentInSERefset, assertionForTargetComponentInSERefset);

        Assertion assertionForReferenceComponentInSPRefset = new Assertion(UUID.fromString(SEPAssertionType.ACTIVE_REFERENCED_COMPONENT_SP_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.ACTIVE_REFERENCED_COMPONENT_SP_REFSET.getAssertionText(), Assertion.FailureType.ERROR);
        run.addCompletedAssertion(assertionForReferenceComponentInSPRefset);
        Assertion assertionForTargetComponentInSPRefset = new Assertion(UUID.fromString(SEPAssertionType.ACTIVE_TARGET_COMPONENT_SP_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.ACTIVE_TARGET_COMPONENT_SP_REFSET.getAssertionText(), Assertion.FailureType.ERROR);
        run.addCompletedAssertion(assertionForTargetComponentInSPRefset);
        doValidateActiveReferenceAndTargetComponents(run.getAnatomyStructureAndPartRefsets(), queryService, assertionForReferenceComponentInSPRefset, assertionForTargetComponentInSPRefset);
    }

    private void doValidateActiveReferenceAndTargetComponents(List<ReferenceSetMember> referenceSetMembers, SnomedQueryService queryService, Assertion assertionForReferenceComponent, Assertion assertionForTargetComponent) throws ServiceException {
        for (ReferenceSetMember item : referenceSetMembers) {
            if (!item.active()) continue;
            ConceptResult referencedConceptResult = getConceptResultOrNull(queryService, item.referencedComponentId());
            if (referencedConceptResult == null || !referencedConceptResult.isActive()) {
                assertionForReferenceComponent.getCurrentViolatedConceptIds().add(Long.parseLong(item.referencedComponentId()));
                assertionForReferenceComponent.getCurrentViolatedConcepts().add(referencedConceptResult == null ? new ConceptResult(item.referencedComponentId()) : referencedConceptResult);
            }
            ConceptResult targetConceptResult = getConceptResultOrNull(queryService, item.otherValues()[0]);
            if (targetConceptResult == null || !targetConceptResult.isActive()) {
                assertionForTargetComponent.getCurrentViolatedConceptIds().add(Long.parseLong(item.otherValues()[0]));
                assertionForTargetComponent.getCurrentViolatedConcepts().add(targetConceptResult == null ? new ConceptResult(item.otherValues()[0]) : targetConceptResult);
            }
        }
    }

    // 2. For all members (active or inactive) any referencedComponentId (S) should only appear once per refset
    private void validateDuplicateReferenceComponents(ValidationRun run, SnomedQueryService queryService) throws ServiceException {
        Assertion assertionForReferenceComponentInSERefset = new Assertion(UUID.fromString(SEPAssertionType.DUPLICATE_REFERENCED_COMPONENT_SE_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.DUPLICATE_REFERENCED_COMPONENT_SE_REFSET.getAssertionText(), Assertion.FailureType.WARNING);
        run.addCompletedAssertion(assertionForReferenceComponentInSERefset);
        doValidateDuplicateReferenceComponents(queryService, run.getAnatomyStructureAndEntireRefsets(), assertionForReferenceComponentInSERefset);

        Assertion assertionForReferenceComponentInSPRefset = new Assertion(UUID.fromString(SEPAssertionType.DUPLICATE_REFERENCED_COMPONENT_SP_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.DUPLICATE_REFERENCED_COMPONENT_SP_REFSET.getAssertionText(), Assertion.FailureType.WARNING);
        run.addCompletedAssertion(assertionForReferenceComponentInSPRefset);
        doValidateDuplicateReferenceComponents(queryService, run.getAnatomyStructureAndPartRefsets(), assertionForReferenceComponentInSPRefset);
    }

    private void doValidateDuplicateReferenceComponents(SnomedQueryService queryService, List<ReferenceSetMember> referenceSetMembers, Assertion assertionForReferenceComponent) throws ServiceException {
        Map<String, List<ReferenceSetMember>> referencedComponentToEntireRefsetsMap = referenceSetMembers.stream()
                .filter(item -> item.active() || Integer.parseInt(item.effectiveTime()) >= 20210731)
                .collect(Collectors.groupingBy(ReferenceSetMember::referencedComponentId, HashMap::new, Collectors.toCollection(ArrayList::new)));
        for (Map.Entry<String, List<ReferenceSetMember>> entry : referencedComponentToEntireRefsetsMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                assertionForReferenceComponent.getCurrentViolatedConceptIds().add(Long.parseLong(entry.getKey()));
                ConceptResult conceptResult = getConceptResultOrNull(queryService, entry.getKey());
                assertionForReferenceComponent.getCurrentViolatedConcepts().add(conceptResult == null ? new ConceptResult(entry.getKey()) : conceptResult);
            }
        }
    }

    // 3. For all active members, the targetComponentId (E,P) should only appear once
    private void validateDuplicateTargetComponents(ValidationRun run, SnomedQueryService queryService) throws ServiceException {
        Assertion assertionForTargetComponentInSERefset = new Assertion(UUID.fromString(SEPAssertionType.DUPLICATE_TARGET_COMPONENT_SE_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.DUPLICATE_TARGET_COMPONENT_SE_REFSET.getAssertionText(), Assertion.FailureType.WARNING);
        run.addCompletedAssertion(assertionForTargetComponentInSERefset);
        doValidateDuplicateTargetComponents(queryService, run.getAnatomyStructureAndEntireRefsets(), assertionForTargetComponentInSERefset);

        Assertion assertionForTargetComponentInSPRefset = new Assertion(UUID.fromString(SEPAssertionType.DUPLICATE_TARGET_COMPONENT_SP_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.DUPLICATE_TARGET_COMPONENT_SP_REFSET.getAssertionText(), Assertion.FailureType.WARNING);
        run.addCompletedAssertion(assertionForTargetComponentInSPRefset);
        doValidateDuplicateTargetComponents(queryService, run.getAnatomyStructureAndPartRefsets(), assertionForTargetComponentInSPRefset);
    }

    private void doValidateDuplicateTargetComponents(SnomedQueryService queryService, List<ReferenceSetMember> referenceSetMembers, Assertion assertionForTargetComponent) throws ServiceException {
        Map<String, List<ReferenceSetMember>> targetComponentToPartRefsetsMap = referenceSetMembers.stream().filter(ReferenceSetMember::active).collect(
                Collectors.groupingBy(item -> item.otherValues()[0], HashMap::new, Collectors.toCollection(ArrayList::new))
        );
        for (Map.Entry<String, List<ReferenceSetMember>> entry : targetComponentToPartRefsetsMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                assertionForTargetComponent.getCurrentViolatedConceptIds().add(Long.parseLong(entry.getKey()));
                ConceptResult conceptResult = getConceptResultOrNull(queryService, entry.getKey());
                assertionForTargetComponent.getCurrentViolatedConcepts().add(conceptResult == null ? new ConceptResult(entry.getKey()) : conceptResult);
            }
        }
    }

    // 4. The FSN for an S concept must contain the word Structure (case insensitive match) and must not start with the word Entire, All or Part
    private void validateAnatomyStructureConceptInSEPRefset(ValidationRun run, SnomedQueryService queryService, Set<String> exclusionList) throws ServiceException {
        Assertion assertion = new Assertion(UUID.fromString(SEPAssertionType.INVALID_STRUCTURE_CONCEPT_SEP_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.INVALID_STRUCTURE_CONCEPT_SEP_REFSET.getAssertionText(), Assertion.FailureType.ERROR);
        run.addCompletedAssertion(assertion);
        doValidateAnatomyStructureConceptInSEPRefset(run.getAnatomyStructureAndEntireRefsets(), exclusionList, queryService, assertion);
        doValidateAnatomyStructureConceptInSEPRefset(run.getAnatomyStructureAndPartRefsets(), exclusionList, queryService, assertion);
    }

    private void doValidateAnatomyStructureConceptInSEPRefset(List<ReferenceSetMember> referenceSetMembers, Set<String> exclusionList, SnomedQueryService queryService, Assertion assertion) throws ServiceException {
        for (ReferenceSetMember item : referenceSetMembers) {
            if (item.active() && !exclusionList.contains(item.referencedComponentId())) {
                ConceptResult referencedConceptResult = getConceptResultOrNull(queryService, item.referencedComponentId());
                if (referencedConceptResult != null && referencedConceptResult.isActive()) {
                    String fsnWithoutSemanticTag = referencedConceptResult.getFsn().replaceAll("[/(]" + BODY_STRUCTURE_SEMANTIC_TAG + "[/)]$", "");
                    if (!StringUtils.containsAnyIgnoreCase(fsnWithoutSemanticTag, STRUCTURE) || fsnWithoutSemanticTag.startsWith(ALL) || fsnWithoutSemanticTag.startsWith(ENTIRE) || fsnWithoutSemanticTag.startsWith(PART)) {
                        assertion.getCurrentViolatedConceptIds().add(Long.parseLong(item.referencedComponentId()));
                        assertion.getCurrentViolatedConcepts().add(referencedConceptResult);
                    }
                }
            }
        }
    }

    // 5. The FSN for a P concept must start with the word Part (case sensitive match) or contain the word part
    private void validatePartConceptInSPRefset(ValidationRun run, SnomedQueryService queryService) throws ServiceException {
        Assertion assertion = new Assertion(UUID.fromString(SEPAssertionType.INVALID_PART_CONCEPT_SP_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.INVALID_PART_CONCEPT_SP_REFSET.getAssertionText(), Assertion.FailureType.ERROR);
        run.addCompletedAssertion(assertion);
        for (ReferenceSetMember item : run.getAnatomyStructureAndPartRefsets()) {
            if (item.active()) {
                ConceptResult referencedConceptResult = getConceptResultOrNull(queryService, item.otherValues()[0]);
                if (referencedConceptResult != null && referencedConceptResult.isActive()) {
                    String fsn = referencedConceptResult.getFsn();
                    if (!fsn.startsWith(PART) && !fsn.contains("part")) {
                        assertion.getCurrentViolatedConceptIds().add(Long.parseLong(item.otherValues()[0]));
                        assertion.getCurrentViolatedConcepts().add(referencedConceptResult);
                    }
                }
            }
        }
    }

    // 6. The FSN for an E concept must start with the word Entire or the word All (case sensitive match)
    private void validateAllOrEntireConceptInSERefset(ValidationRun run, SnomedQueryService queryService, Set<String> exclusionList) throws ServiceException {
        Assertion assertion = new Assertion(UUID.fromString(SEPAssertionType.INVALID_ALL_OR_ENTIRE_CONCEPT_SE_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.INVALID_ALL_OR_ENTIRE_CONCEPT_SE_REFSET.getAssertionText(), Assertion.FailureType.ERROR);
        run.addCompletedAssertion(assertion);
        for (ReferenceSetMember item : run.getAnatomyStructureAndEntireRefsets()) {
            if (item.active() && !exclusionList.contains(item.otherValues()[0])) {
                ConceptResult referencedConceptResult = getConceptResultOrNull(queryService, item.otherValues()[0]);
                if (referencedConceptResult != null && referencedConceptResult.isActive()) {
                    String fsn = referencedConceptResult.getFsn();
                    if (!fsn.startsWith(ENTIRE) && !fsn.startsWith(ALL)) {
                        assertion.getCurrentViolatedConceptIds().add(Long.parseLong(item.otherValues()[0]));
                        assertion.getCurrentViolatedConcepts().add(referencedConceptResult);
                    }
                }
            }
        }
    }

    // 7. All body structure concepts that start with the word 'Entire' or 'All' should appear in the SE refset
    // Exception: If a S concept has both E concept and All concept, the E concept should be included for the SE refset. But All concept would not be required for the SE refset
    // 8. All body structure concepts that start with the word 'Part' should appear in the SP refset
    private void validateBodyStructureConcepts(ValidationRun run, SnomedQueryService queryService, List<ConceptResult> bodyStructureConcepts) throws ServiceException {
        Assertion assertionForSERefset = new Assertion(UUID.fromString(SEPAssertionType.MISSING_ALL_OR_ENTIRE_CONCEPTS_FROM_SE_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.MISSING_ALL_OR_ENTIRE_CONCEPTS_FROM_SE_REFSET.getAssertionText(), Assertion.FailureType.WARNING);
        run.addCompletedAssertion(assertionForSERefset);

        Assertion assertionForSPRefset = new Assertion(UUID.fromString(SEPAssertionType.MISSING_PART_CONCEPTS_FROM_SE_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.MISSING_PART_CONCEPTS_FROM_SE_REFSET.getAssertionText(), Assertion.FailureType.WARNING);
        run.addCompletedAssertion(assertionForSPRefset);

        Set<String> existingEntires = run.getAnatomyStructureAndEntireRefsets().stream().filter(ReferenceSetMember::active).map(item -> item.otherValues()[0]).collect(Collectors.toSet());
        Set<String> existingParts = run.getAnatomyStructureAndPartRefsets().stream().filter(ReferenceSetMember::active).map(item -> item.otherValues()[0]).collect(Collectors.toSet());
        for (ConceptResult conceptResult : bodyStructureConcepts) {
            if (!conceptResult.isActive()) continue;
            String fsn = conceptResult.getFsn();
            if (fsn.startsWith(ENTIRE)) {
                if (!existingEntires.contains(conceptResult.getId())) {
                    assertionForSERefset.getCurrentViolatedConceptIds().add(Long.parseLong(conceptResult.getId()));
                    assertionForSERefset.getCurrentViolatedConcepts().add(conceptResult);
                }
            } else if (fsn.startsWith(ALL)) {
                if (!existingEntires.contains(conceptResult.getId())) {
                    validateBodyStructureOfAllConcepts(run, queryService, conceptResult, assertionForSERefset);
                }
            } else if (fsn.startsWith(PART) && (!existingParts.contains(conceptResult.getId()))) {
                assertionForSPRefset.getCurrentViolatedConceptIds().add(Long.parseLong(conceptResult.getId()));
                assertionForSPRefset.getCurrentViolatedConcepts().add(conceptResult);
            }
        }
    }

    private void validateBodyStructureOfAllConcepts(ValidationRun run, SnomedQueryService queryService, ConceptResult conceptResult, Assertion assertionForSERefset) throws ServiceException {
        Set<String> parent = conceptResult.getParents();
        boolean existingEntireRefsetFound = false;
        for (String parentConceptId : parent) {
            Set<ReferenceSetMember> members = run.getAnatomyStructureAndEntireRefsets().stream().filter(item -> item.active() && item.referencedComponentId().equals(parentConceptId)).collect(Collectors.toSet());
            for (ReferenceSetMember member : members) {
                ConceptResult concept = getConceptResultOrNull(queryService, member.otherValues()[0]);
                if (concept != null && concept.getFsn().startsWith(ENTIRE)) {
                    existingEntireRefsetFound = true;
                    break;
                }
            }
            if (existingEntireRefsetFound) break;
        }
        if (!existingEntireRefsetFound) {
            assertionForSERefset.getCurrentViolatedConceptIds().add(Long.parseLong(conceptResult.getId()));
            assertionForSERefset.getCurrentViolatedConcepts().add(conceptResult);
        }
    }

    // 9. For both refsets, the 'S' concept should be an inferred parent the targetComponentId (E or P).
    private void validateParentConceptsOfTargetComponents(ValidationRun run, SnomedQueryService queryService) throws ServiceException {
        Assertion assertion = new Assertion(UUID.fromString(SEPAssertionType.INVALID_PAIR_OF_TARGET_AND_REFERENCED_COMPONENTS_SEP_REFSET.getUuid()), ValidationType.SEP_REFSET_TYPE, SEPAssertionType.INVALID_PAIR_OF_TARGET_AND_REFERENCED_COMPONENTS_SEP_REFSET.getAssertionText(), Assertion.FailureType.WARNING);
        run.addCompletedAssertion(assertion);
        doValidateParentConceptsOfTargetComponents(run.getAnatomyStructureAndEntireRefsets(), queryService, assertion);
        doValidateParentConceptsOfTargetComponents(run.getAnatomyStructureAndPartRefsets(), queryService, assertion);
    }

    private void doValidateParentConceptsOfTargetComponents(List<ReferenceSetMember> referenceSetMembers, SnomedQueryService queryService, Assertion assertion) throws ServiceException {
        for (ReferenceSetMember item : referenceSetMembers) {
            if (item.active()) {
                ConceptResult conceptResult = getConceptResultOrNull(queryService, item.otherValues()[0]);
                if (conceptResult != null && !conceptResult.getParents().contains(item.referencedComponentId())) {
                    assertion.getCurrentViolatedConceptIds().add(Long.parseLong(item.otherValues()[0]));
                    assertion.getCurrentViolatedConcepts().add(conceptResult);
                }
            }
        }
    }

    private ConceptResult getConceptResultOrNull(SnomedQueryService queryService, String conceptId) throws ServiceException {
        try {
            return queryService.retrieveConcept(conceptId);
        } catch (ConceptNotFoundException e) {
            return null;
        }
    }

    private Set<String> getExclusionList(SnomedQueryService queryService) {
        List<ConceptResult> exclusionList = new ArrayList<>();
        List<String> conceptIds = List.of("4421005", "122453002", "51576004", "280115004", "91832008", "258331007", "118956008", "278001007", "39801007", "361083003", "21229009", "87100004", "420864000", "698969006", "279228004", "698968003", "244023005", "123957003");
        conceptIds.stream().parallel().forEach(conceptId -> {
            try {
                exclusionList.addAll(queryService.retrieveConceptDescendants(conceptId).items());
            } catch (ServiceException e) {
                throw new RuntimeException("Failed to get the exclusion list for SEP validation. Error: " + e.getMessage());
            }
        });
        Set<String> result = new HashSet<>();
        result.addAll(conceptIds);
        result.addAll(exclusionList.stream().map(ConceptResult::getId).collect(Collectors.toSet()));
        return result;
    }

    private List<ConceptResult> getAllBodyStructureConcepts(SnomedQueryService queryService) throws IOException, ServiceException {
        List<ConceptResult> results = new ArrayList<>();
        long totalConcept = queryService.getConceptCount();
        int offset = 0;
        int limit = 10000;
        while (offset < totalConcept) {
            ConceptResults conceptResults = queryService.listAll(offset, limit);
            conceptResults.items().forEach(item -> {
                if (item.getFsn().endsWith("(" + BODY_STRUCTURE_SEMANTIC_TAG + ")")) {
                    results.add(item);
                }
            });
            offset += limit;
        }
        return results;
    }

}
