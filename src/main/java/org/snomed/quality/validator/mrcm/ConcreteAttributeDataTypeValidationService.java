package org.snomed.quality.validator.mrcm;

import static org.snomed.otf.owltoolkit.domain.Relationship.ConcreteValue;
import static org.snomed.otf.owltoolkit.domain.Relationship.ConcreteValue.Type;
import static org.snomed.quality.validator.mrcm.Constants.*;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.snomed.quality.validator.mrcm.model.Attribute;

import java.util.*;

public class ConcreteAttributeDataTypeValidationService {

	public void validate(Set<String> extractedRF2FilesDirectories, ValidationRun run) throws ReleaseImportException {
		LoadingProfile profile = run.getContentType() == ContentType.STATED ?
				LoadingProfile.light.withRefsets(OWL_AXIOM_REFSET).withJustRefsets() :
				LoadingProfile.light.withoutStatedRelationships().withoutDescriptions().withRefsets(OWL_AXIOM_REFSET);

		Map<String, Attribute> attributeRangeMap = new HashMap<>();
		Map<String, Type> concreteAttributeDataTypeMap = new HashMap<>();

		run.getAttributeRangesMap().keySet().forEach(attributeId -> run.getAttributeRangesMap().get(attributeId)
				.forEach(range -> Arrays.stream(Type.values()).filter(type -> range.getRangeConstraint()
						.startsWith(type.getShorthand())).forEach(type -> {
			concreteAttributeDataTypeMap.putIfAbsent(range.getAttributeId(), type);
			attributeRangeMap.putIfAbsent(attributeId, range);
		})));

		ReleaseImporter releaseImporter = new ReleaseImporter();
		AxiomRelationshipConversionService conversionService = new AxiomRelationshipConversionService(run.getUngroupedAttributes());
		DataTypeValidationComponentFactory componentFactory = new DataTypeValidationComponentFactory(concreteAttributeDataTypeMap, conversionService);

		if (run.isFullSnapshotRelease()) {
			releaseImporter.loadSnapshotReleaseFiles(extractedRF2FilesDirectories.iterator().next(), profile,componentFactory, false);
		} else {
			boolean loadDelta = RF2ReleaseFilesUtil.anyDeltaFilesPresent(extractedRF2FilesDirectories);
			if (loadDelta) {
				releaseImporter.loadEffectiveSnapshotAndDeltaReleaseFiles(extractedRF2FilesDirectories, profile, componentFactory, false);
			} else {
				releaseImporter.loadEffectiveSnapshotReleaseFiles(extractedRF2FilesDirectories, profile, componentFactory, false);
			}
		}


		// Add assertions for all concrete attributes defined in the MRCM
		attributeRangeMap.values().forEach(attribute -> {
			Assertion assertion = null;
			if (componentFactory.getAttributeToViolatedConceptsMap().containsKey(attribute.getAttributeId())) {
				List<Long> conceptIds = new ArrayList<>(componentFactory.getAttributeToViolatedConceptsMap().get(attribute.getAttributeId()));
				String failureMsg = componentFactory.getAttributeToFailureMsgMap().get(attribute.getAttributeId());
				assertion = new Assertion(attribute, ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE, failureMsg, Assertion.FailureType.ERROR, conceptIds);
			} else {
				assertion = new Assertion(attribute, ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE, null, Assertion.FailureType.ERROR);
			}
			run.addCompletedAssertion(assertion);
		});

		// Add failed assertions for concrete attribute values found but with no data type defined in MRCM
		componentFactory.getAttributeToViolatedConceptsMap().keySet().stream()
				.filter(attributeId -> !attributeRangeMap.containsKey(attributeId)).forEach(attributeId -> {
			Attribute attribute = new Attribute(attributeId, ALL_NEW_PRE_COORDINATED_CONTENT_CONCEPT);
			attribute.setUuid(UUID.fromString(NO_DATA_TYPE_DEFINED_ASSERTION_UUID));
			List<Long> conceptIds = new ArrayList<>(componentFactory.getAttributeToViolatedConceptsMap().get(attributeId));
			String failureMsg = componentFactory.getAttributeToFailureMsgMap().get(attributeId);
			Assertion assertion = new Assertion(attribute, ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE, failureMsg, Assertion.FailureType.ERROR, conceptIds);
			run.addCompletedAssertion(assertion);
		});
	}

	private static class DataTypeValidationComponentFactory extends ImpotentComponentFactory {

		private static final String AXIOM_LABEL = "Axiom";
		private static final String RELATIONSHIP_LABEL = "Relationship";
		private static final String TYPE_NOT_MATCHING_MSG_FORMAT = "%s concrete value of %s is not a type of %s as defined in the MRCM.";
		private static final String NO_DATA_TYPE_DEFINED_MSG_FORMAT = "%s concrete value %s found but no concrete data type is defined in the MRCM range constraint.";
		private static final String INVALID_CONCRETE_VALUE_MSG_FORMAT = "%s concrete value %s found but not starting with # or \".";

		private final Map<String, Type> concreteAttributeDataTypeMap;
		private final Map<String, Set<Long>> attributeToViolatedConceptsMap;
		private final Map<String, String> attributeToFailureMsgMap;

		private final AxiomRelationshipConversionService conversionService;

		public DataTypeValidationComponentFactory(final Map<String, Type> concreteAttributeDataTypeMap, final AxiomRelationshipConversionService conversionService) {
			this.concreteAttributeDataTypeMap = concreteAttributeDataTypeMap;
			this.conversionService = conversionService;
			attributeToViolatedConceptsMap = new HashMap<>();
			attributeToFailureMsgMap = new HashMap<>();
		}

		@Override
		public void newConcreteRelationshipState(final String id, final String effectiveTime, final String active, final String moduleId, final String sourceId,
				final String value, final String relationshipGroup, final String typeId, final String characteristicTypeId, final String modifierId) {
			if ("1".equals(active)) {
				if (concreteAttributeDataTypeMap.containsKey(typeId)) {
					final Type dataTypeInMRCM = concreteAttributeDataTypeMap.get(typeId);
					final ConcreteValue concreteValue = new ConcreteValue(value);
					if (Type.DECIMAL == dataTypeInMRCM) {
						// In the current implementation of the ConcreteValue constructor above,
						// it cannot differentiate between Decimal values which do not contain a
						// decimal point. It automatically classifies it as an Integer which is
						// incorrect as a Decimal value can either be #10.0/#10 for example.
						if (Type.STRING == concreteValue.getType()) {
							addAttributeToViolatedConceptsMap(sourceId, typeId, constructFailureMessage(sourceId, typeId, RELATIONSHIP_LABEL, value, dataTypeInMRCM));
						}
					} else {
						if (dataTypeInMRCM != concreteValue.getType()) {
							addAttributeToViolatedConceptsMap(sourceId, typeId, constructFailureMessage(sourceId, typeId, RELATIONSHIP_LABEL, value, dataTypeInMRCM));
						}
					}
				} else {
					// check concrete value
					if (value.startsWith("#") || value.startsWith("\"")) {
						addAttributeToViolatedConceptsMap(sourceId, typeId, String.format(NO_DATA_TYPE_DEFINED_MSG_FORMAT, RELATIONSHIP_LABEL, value));
					} else {
						// report as invalid
						addAttributeToViolatedConceptsMap(sourceId, typeId, String.format(INVALID_CONCRETE_VALUE_MSG_FORMAT, RELATIONSHIP_LABEL, value));

					}
				}
			}
		}

		private void addAttributeToViolatedConceptsMap(final String sourceId, final String typeId, final String failureMsg) {
			if (!attributeToFailureMsgMap.containsKey(typeId)) {
				attributeToFailureMsgMap.put(typeId, failureMsg);
			}
			attributeToViolatedConceptsMap.computeIfAbsent(typeId, k -> new HashSet<>()).add(Long.parseLong(sourceId));
		}

		private String constructFailureMessage(String conceptId, String attributeId, final String prefix, final String value, final Type dataType) {
			return String.format(TYPE_NOT_MATCHING_MSG_FORMAT, prefix, value, dataType, conceptId);
		}

		@Override
		public void newReferenceSetMemberState(final String[] fieldNames, final String id, final String effectiveTime, final String active, final String moduleId,
				final String refsetId, final String referencedComponentId, final String... otherValues) {
			if (OWL_AXIOM_REFSET.equals(refsetId)) {
				try {
					final AxiomRepresentation axiom = conversionService.convertAxiomToRelationships(otherValues[0]);
					if (axiom == null) {
						// not an axiom so skip and do nothing
						return;
					}
					processFailureMessagesForAxiomRelationships(axiom, processRelationships(axiom.getRightHandSideRelationships(),
							processRelationships(axiom.getLeftHandSideRelationships(), new HashSet<>())));
				} catch (ConversionException e) {
					throw new IllegalStateException(String.format("Failed to convert axiom %s to relationships", id), e);
				}
			}
		}

		private void processFailureMessagesForAxiomRelationships(final AxiomRepresentation axiom, final Set<Relationship> relationships) {
			relationships.stream().filter(Relationship::isConcrete).forEach(relationship -> {
				final String typeId = String.valueOf(relationship.getTypeId());
				final Type dataTypeInMRCM = concreteAttributeDataTypeMap.get(typeId);
				if (dataTypeInMRCM == null) {
					if (!attributeToFailureMsgMap.containsKey(typeId)) {
						attributeToFailureMsgMap.put(typeId, String.format(NO_DATA_TYPE_DEFINED_MSG_FORMAT, AXIOM_LABEL,
								relationship.getValue().getRF2Value(), axiom.getLeftHandSideNamedConcept()));
						attributeToViolatedConceptsMap.computeIfAbsent(typeId, k -> new HashSet<>()).add(Long.parseLong(String.valueOf(axiom.getLeftHandSideNamedConcept())));
					}
				} else if (relationship.getValue().getType() != dataTypeInMRCM) {
					attributeToFailureMsgMap.put(typeId, constructFailureMessage(String.valueOf(axiom.getLeftHandSideNamedConcept()),
							typeId, AXIOM_LABEL, relationship.getValue().getRF2Value(), dataTypeInMRCM));
					attributeToViolatedConceptsMap.computeIfAbsent(typeId, k -> new HashSet<>()).add(Long.parseLong(String.valueOf(axiom.getLeftHandSideNamedConcept())));
				}
			});
		}

		private Set<Relationship> processRelationships(final Map<Integer, List<Relationship>> axiomRelationships, final Set<Relationship> relationships) {
			if (axiomRelationships != null) {
				axiomRelationships.values().forEach(relationships::addAll);
			}
			return relationships;
		}

		public Map<String, Set<Long>> getAttributeToViolatedConceptsMap() {
			return attributeToViolatedConceptsMap;
		}

		public Map<String, String> getAttributeToFailureMsgMap() {
			return attributeToFailureMsgMap;
		}
	}
}
