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

import java.io.File;
import java.util.*;


public class ConcreteAttributeDataTypeValidationService {

	public void validate(File file, ValidationRun run) throws ReleaseImportException {
		LoadingProfile profile = run.isStatedView() ?
				LoadingProfile.light.withFullRelationshipObjects().withFullConcreteRelationshipObjects().withStatedRelationships()
						.withStatedAttributeMapOnConcept().withFullRefsetMemberObjects().withRefsets(LATERALIZABLE_BODY_STRUCTURE_REFSET, OWL_AXIOM_REFSET).withoutInferredAttributeMapOnConcept()
				: LoadingProfile.light.withFullRelationshipObjects().withFullConcreteRelationshipObjects()
				.withFullRefsetMemberObjects().withRefsets(LATERALIZABLE_BODY_STRUCTURE_REFSET, OWL_AXIOM_REFSET);

		Map<String, Attribute> attributeRangeMap = new HashMap<>();
		Map<String, Type> concreteAttributeDataTypeMap = new HashMap<>();

		for (String attributeId : run.getAttributeRangesMap().keySet()) {
			for (Attribute range : run.getAttributeRangesMap().get(attributeId)) {
				for (Type type : Type.values()) {
					if (range.getRangeConstraint().startsWith(type.getShorthand())) {
						concreteAttributeDataTypeMap.putIfAbsent(range.getAttributeId(), type);
						attributeRangeMap.putIfAbsent(attributeId, range);
					}
				}
			}
		}

		ReleaseImporter releaseImporter = new ReleaseImporter();
		AxiomRelationshipConversionService conversionService = new AxiomRelationshipConversionService(run.getUngroupedAttributes());
		DataTypeValidationComponentFactory componentFactory = new DataTypeValidationComponentFactory(concreteAttributeDataTypeMap, conversionService);
		releaseImporter.loadSnapshotReleaseFiles(file.getAbsolutePath(), profile, componentFactory);

		for (String attributeId : componentFactory.getAttributeToViolatedConceptsMap().keySet()) {
			Attribute attribute = attributeRangeMap.get(attributeId);
			if (attribute == null) {
				attribute = new Attribute(attributeId, ALL_NEW_PRE_COORDINATED_CONTENT_CONCEPT);
				attribute.setUuid(UUID.fromString(NO_DATA_TYPE_DEFINED_ASSERTION_UUID));
			}
			List<Long> conceptIds = new ArrayList<>(componentFactory.getAttributeToViolatedConceptsMap().get(attributeId));
			String failureMsg = componentFactory.getAttributeToFailureMsgMap().get(attributeId);
			Assertion assertion = new Assertion(attribute, ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE, failureMsg, Assertion.FailureType.ERROR, conceptIds);
			run.addCompletedAssertion(assertion);
		}
	}

	private static class DataTypeValidationComponentFactory extends ImpotentComponentFactory {

		private Map<String, Type> concreteAttributeDataTypeMap;
		private Map<String, Set<Long>> attributeToViolatedConceptsMap;
		private Map<String, String> attributeToFailureMsgMap;
		private final String TYPE_NOT_MATCHING_MSG_FORMAT = "Concrete value of %s is not a type of %s as defined in the MRCM";
		private final String NO_DATA_TYPE_DEFINED_MSG_FORMAT = "Concrete value %s found but no concrete data type is defined in the MRCM range constraint";
		private final String INVALID_CONCRETE_VALUE_MSG_FORMAT = "Concrete value %s found but not starting with # or \"";
		private AxiomRelationshipConversionService conversionService;

		public DataTypeValidationComponentFactory(Map<String, Type> concreteAttributeDataTypeMap, AxiomRelationshipConversionService conversionService) {
			this.concreteAttributeDataTypeMap = concreteAttributeDataTypeMap;
			attributeToViolatedConceptsMap = new HashMap<>();
			attributeToFailureMsgMap = new HashMap<>();
			this.conversionService = conversionService;
		}

		@Override
		public void newConcreteRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String value, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
			if (active.equals("1")) {
				if (concreteAttributeDataTypeMap.containsKey(typeId)) {
					Type dataTypeInMRCM = concreteAttributeDataTypeMap.get(typeId);
					ConcreteValue concreteValue = new ConcreteValue(value);
					if (Type.DECIMAL == dataTypeInMRCM) {
						if (Type.DECIMAL != concreteValue.getType() || Type.INTEGER != concreteValue.getType()) {
							if (!attributeToFailureMsgMap.containsKey(typeId)) {
								attributeToFailureMsgMap.put(typeId, constructFailureMessage(sourceId, typeId, value, dataTypeInMRCM));
							}
							attributeToViolatedConceptsMap.computeIfAbsent(typeId, k -> new HashSet<>()).add(Long.parseLong(sourceId));
						}
					} else {
						if (dataTypeInMRCM != concreteValue.getType()) {
							if (!attributeToFailureMsgMap.containsKey(typeId)) {
								attributeToFailureMsgMap.put(typeId, constructFailureMessage(sourceId, typeId, value, dataTypeInMRCM));
							}
							attributeToViolatedConceptsMap.computeIfAbsent(typeId, k -> new HashSet<>()).add(Long.parseLong(sourceId));
						}
					}
				} else {
					// check concrete value
					if (value.startsWith("#") || value.startsWith("\"")) {
						if (!attributeToFailureMsgMap.containsKey(typeId)) {
							attributeToFailureMsgMap.put(typeId, String.format(NO_DATA_TYPE_DEFINED_MSG_FORMAT, value));
						}
						attributeToViolatedConceptsMap.computeIfAbsent(typeId, k -> new HashSet<>()).add(Long.parseLong(sourceId));
					} else {
						// report as invalid
						attributeToFailureMsgMap.put(typeId, String.format(INVALID_CONCRETE_VALUE_MSG_FORMAT, value));
					}
				}

			}
		}

		private String constructFailureMessage(String conceptId, String attributeId, String value, Type dataType) {
			return String.format(TYPE_NOT_MATCHING_MSG_FORMAT, value, dataType);

		}
		@Override
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			if (OWL_AXIOM_REFSET.equals(refsetId)) {
				try {
					AxiomRepresentation axiom = conversionService.convertAxiomToRelationships(otherValues[0]);
					if (axiom == null) {
						// not an axiom so skip and do nothing
						return;
					}
					Set<Relationship> relationships = new HashSet<>();
					if (axiom.getLeftHandSideRelationships() != null) {
						axiom.getLeftHandSideRelationships().values().stream().forEach(values -> relationships.addAll(values));
					}
					if (axiom.getRightHandSideRelationships() != null) {
						axiom.getRightHandSideRelationships().values().stream().forEach(values -> relationships.addAll(values));
					}
					for (Relationship relationship : relationships) {
						if (relationship.isConcrete()) {
							Type dataTypeInMRCM = concreteAttributeDataTypeMap.get(relationship.getTypeId());
							if (!attributeToFailureMsgMap.containsKey(relationship.getTypeId())) {
								attributeToFailureMsgMap.put(String.valueOf(relationship.getTypeId()), String.format(NO_DATA_TYPE_DEFINED_MSG_FORMAT, relationship.getValue().getRF2Value()));
							} else if (relationship.getValue().getType() != dataTypeInMRCM) {
								attributeToFailureMsgMap.put(String.valueOf(relationship.getTypeId()),
										constructFailureMessage(String.valueOf(axiom.getLeftHandSideNamedConcept()), String.valueOf(relationship.getTypeId()), relationship.getValue().getRF2Value(), dataTypeInMRCM));
							}
						}
					}
				} catch (ConversionException e) {
					throw new IllegalStateException(String.format("Failed to convert axiom %s to relationships", id), e);
				}
			}
		}

		public Map<String, Set<Long>> getAttributeToViolatedConceptsMap() {
			return attributeToViolatedConceptsMap;
		}

		public Map<String, String> getAttributeToFailureMsgMap() {
			return attributeToFailureMsgMap;
		}
	}
}
