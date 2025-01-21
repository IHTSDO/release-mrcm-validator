package org.snomed.quality.validator.mrcm;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import com.google.common.collect.Sets;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentStore;
import org.ihtsdo.otf.sqs.service.SnomedQueryService;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.junit.Before;
import org.junit.Test;
import org.snomed.quality.validator.mrcm.Assertion.FailureType;
import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;
import org.springframework.util.Assert;

public class ValidationServiceTest {

	private ValidationService validationService;
	private ValidationRun run;
	private File testReleaseFiles;

	@Before
	public void setUp() throws ReleaseImportException {
		validationService = new ValidationService();
		run = new ValidationRun(null, ContentType.INFERRED, false);
		run.setFullSnapshotRelease(true);
		testReleaseFiles = Paths.get("src/test/resources/rf2TestFiles").toFile();
		validationService.loadMRCM(testReleaseFiles, run);
	}

	@Test
	public void testLoadMRCMRules() {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		assertEquals(17, run.getMRCMDomains().size());
		int totalAttribute = 0;
		int totalAttributeRange = 0;
		for (String key : run.getMRCMDomains().keySet()) {
			Domain domain = run.getMRCMDomains().get(key);
			for (Attribute attribute : domain.getAttributes()) {
				totalAttribute++;
				assertNotNull("Range constraint can't be null for a given attribute.", domain.getAttributeRanges(attribute.getAttributeId()));
				if (domain.getAttributeRanges(attribute.getAttributeId()).isEmpty()) {
                    assertFalse("Attribute must have at least one range constraint.", domain.getAttributeRanges(attribute.getAttributeId()).isEmpty());
				}
				for (Attribute range : domain.getAttributeRanges(attribute.getAttributeId())) {
					totalAttributeRange++;
					assertNotNull(range.getRangeConstraint());
				}
			}
		}
		assertEquals(104,totalAttribute);
		assertEquals(110,totalAttributeRange);
	}

	@Test
	public void testValidationForAll() throws Exception {
		final List<String> expectedFailedMessages = Arrays.asList(
				"272741003 |Laterality (attribute)| must conform to the MRCM attribute cardinality [0..1]",
				"272741003 |Laterality (attribute)| must conform to the MRCM attribute domain << 91723000 |Anatomical structure (body structure)|",
				"3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute in group cardinality [0..1]",
				"363698007 |Finding site (attribute)| must conform to the MRCM attribute in group cardinality [0..1]",
				"Terms used in the range constraint for MRCM attribute range 7d23e837-4e2b-45cd-b5c8-8ea9b51daae0 are invalid",
				"Terms used in the range rule for MRCM attribute range e6bbf042-3c52-4e16-be72-f780d002fb05 are invalid",
				"Terms used in the range rule for MRCM attribute range e6bbf042-3c52-4e16-be72-f780d002fb06 are invalid",
				"Terms used in the range rule for MRCM attribute range e6bbf042-3c52-4e16-be72-f780d002fb07 are invalid",
				"The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute range << 182353008 |Side (qualifier value)|",
				"The attribute value of 3311481003 must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute range dec(#10..#20)",
				"The attribute value of 3311482006 |Has integer presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute range int(#30..#40)",
				"The attribute value of 3311482006 |Has integer presentation strength denominator value concrete (attribute)| must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311482007 |Has string presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute range str(\"test\")",
				"The attribute value of 3311482007 |Has string presentation strength denominator value concrete (attribute)| must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311483000 must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311487004 must conform to the MRCM concrete attribute data type",
				"The attribute value of 363698007 |Finding site (attribute)| must conform to the MRCM attribute range << 442083009 |Anatomical or acquired body structure (body structure)|"
				);
		Assert.notNull(run.getMRCMDomains(),"MRCM Domains should not be null.");

		validationService.validateRelease(testReleaseFiles, run);

		assertEquals(287, run.getCompletedAssertions().size());
		assertEquals(0, run.getSkippedAssertions().size());
		List<String> actualFailedMessages = run.getFailedAssertions().stream().map(Assertion::getAssertionText).sorted().toList();
		assertEquals(expectedFailedMessages.toString(), actualFailedMessages.toString());
	}

	@Test
	public void testLoading() throws ReleaseImportException, IOException, ServiceException {
		final SnomedQueryService queryService = validationService.getSnomedQueryService(Collections.singleton(testReleaseFiles.getPath()), ContentType.INFERRED, new ValidationService.OWLExpressionAndDescriptionFactory(new ComponentStore(),
				Collections.emptySet(), Collections.emptySet()), true);

		assertEquals(Sets.newHashSet(404684003L, 39302008L), new HashSet<>(queryService.eclQueryReturnConceptIdentifiers("> 29857009", 0, 100).conceptIds()));
	}

	@Test
	public void testValidReleaseForSpecificAttribute() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "MRCM Domains should not be null.");
		String attributeId = "272741003";
		runValidationForAttribute(attributeId, Collections.singletonList(ValidationType.ATTRIBUTE_CARDINALITY));
		assertEquals(1, run.getFailedAssertions().size());
		run.getFailedAssertions().forEach(assertion -> {
			assertEquals(attributeId, assertion.getAttribute().getAttributeId());
			assertEquals(Arrays.asList(91723000L, 113343008L), assertion.getCurrentViolatedConceptIds());
			assertEquals(FailureType.ERROR, assertion.getFailureType());
		});
	}

	private void runValidationForAttribute(String attributeId, List<ValidationType> types) throws ReleaseImportException, IOException, ServiceException {
		Domain domainFound = null;
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (attribute.getAttributeId().equals(attributeId)) {
					domainFound = domain;
					break;
				}
			}
		}
		if (domainFound == null) {
			throw new IllegalStateException("No attribute domain found for attribute id " + attributeId);
		}
		Domain domainToValidate = new Domain(domainFound.getDomainId());
		domainToValidate.setDomainConstraint(domainFound.getDomainConstraint());
		domainFound.getAttributes().stream().filter(attribute -> attribute.getAttributeId().equals(attributeId)).forEach(domainToValidate::addAttribute);
		domainFound.getAttributeRanges(attributeId).forEach(domainToValidate::addAttributeRange);
		assertFalse(domainToValidate.getAttributeRanges(attributeId).isEmpty());
		assertFalse(domainToValidate.getAttributes().isEmpty());
		Map<String, Domain> domainMap = new HashMap<>();
		domainMap.put(domainToValidate.getDomainId(), domainToValidate);
		run.setMRCMDomains(domainMap);
		run.setValidationTypes(types);
		validationService.validateRelease(testReleaseFiles, run);
	}

	@Test
	public void testAttributeDomainValidation() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_DOMAIN));
		validationService.validateRelease(testReleaseFiles, run);
		assertEquals(104, run.getCompletedAssertions().size());
		assertEquals(0, run.getSkippedAssertions().size());
		assertEquals(1, run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			assertEquals("272741003", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(3, assertion.getCurrentViolatedConceptIds().size());
			List<Long> expected = Arrays.asList(160959002L, 161054003L, 102563003L);
			assertEquals(expected.size(), assertion.getCurrentViolatedConceptIds().size());
			assertTrue(expected.containsAll(assertion.getCurrentViolatedConceptIds()));
		}
	}

	@Test
	public void testAttributeRangeValidation() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_RANGE));
		validationService.validateRelease(testReleaseFiles, run);
		assertEquals(92, run.getCompletedAssertions().size());
		// skipped assertions
		assertEquals(0, run.getSkippedAssertions().size());
		assertEquals(9, run.getFailedAssertions().size());
		List<String> expectedFailed = Arrays.asList("363698007", "3311482005", "3311482006", "3311482007", "363702006", "272741003");
		for (Assertion assertion : run.getFailedAssertions()) {
			assertTrue(expectedFailed.contains(assertion.getAttribute().getAttributeId()));
			if ("363698007".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals("<< 442083009 |Anatomical or acquired body structure (body structure)|", assertion.getAttribute().getRangeConstraint());
				assertTrue(assertion.getCurrentViolatedConceptIds().contains(29857009L));
			}
			if ("3311482005".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals(1, assertion.getCurrentViolatedConceptIds().size());
				assertTrue(assertion.getCurrentViolatedConceptIds().contains(375745003L) || assertion.getCurrentViolatedConceptIds().contains(3311482005L));

				if (assertion.getCurrentViolatedConceptIds().contains(3311482005L)) {
					assertEquals("Terms used in the range rule for MRCM attribute range e6bbf042-3c52-4e16-be72-f780d002fb05 are invalid", assertion.getMessage());
				}
			}
		}
	}

	@Test
	public void testAxiomValidation() throws ServiceException, ReleaseImportException, IOException {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE));
		validationService.validateRelease(testReleaseFiles, run);
	}

	@Test
	public void testAttributeCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_CARDINALITY));
		validationService.validateRelease(testReleaseFiles, run);
		assertEquals(2, run.getCompletedAssertions().size());
		assertEquals(1, run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			assertEquals("272741003", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(2, assertion.getCurrentViolatedConceptIds().size());
			assertEquals(2, assertion.getCurrentViolatedConceptIds().size());
			assertTrue(assertion.getCurrentViolatedConceptIds().contains(91723000L));
			assertTrue(assertion.getCurrentViolatedConceptIds().contains(113343008L));
		}
	}

	@Test
	public void testReportWithSkippedAssertions() throws Exception {
		run = new ValidationRun(null, ContentType.INFERRED, true);
		validationService.loadMRCM(testReleaseFiles, run);
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY));
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY));
		validationService.validateRelease(testReleaseFiles, run);
		assertTrue(run.reportSkippedAssertions());
		assertEquals(37, run.getSkippedAssertions().size());
	}

	@Test
	public void testAttributeGroupCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY));
		validationService.validateRelease(testReleaseFiles, run);
		assertEquals(67, run.getCompletedAssertions().size());
		assertEquals(0, run.getSkippedAssertions().size());
		assertEquals(2, run.getFailedAssertions().size());
		List<String> expectedFailed = Arrays.asList("363698007", "3311482005");
		run.getFailedAssertions().forEach(assertion -> {
			assertTrue(expectedFailed.contains(assertion.getAttribute().getAttributeId()));
			if ("363698007".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
				assertEquals(1, assertion.getCurrentViolatedConceptIds().size());
				assertEquals(Collections.singletonList(404684003L), assertion.getCurrentViolatedConceptIds());
				assertEquals(" Content type is for new concept only but there is no current release date specified.", assertion.getMessage());
			}
			assertEquals(FailureType.ERROR, assertion.getFailureType());
			assertTrue(run.getValidationTypes().contains(ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY));
		});
	}

	@Test
	public void testSpecificAttributeDomainValidationWithWarning() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null.");
		String attributeId = "272741003";
		String domainId = "723264001";
		Map<String, Domain> domainsToValidate = new HashMap<>();
		run.getMRCMDomains().values().stream().filter(domain -> domain.getDomainId().equals(domainId))
				.forEach(domain -> domain.getAttributes().stream().filter(attribute -> attribute.getAttributeId().equals(attributeId))
				.forEach(attribute -> domainsToValidate.put(domain.getDomainId(), domain)));
		run.setMRCMDomains(domainsToValidate);
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_DOMAIN));
		validationService.validateRelease(testReleaseFiles, run);
		assertEquals(1, run.getAssertionsWithWarning().size());
		String failureMsg = "272741003 |Laterality (attribute)| must conform to the MRCM attribute domain ^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|";
		run.getAssertionsWithWarning().forEach(assertion -> {
			assertEquals(failureMsg, assertion.getAssertionText());
			assertEquals(attributeId, assertion.getAttribute().getAttributeId());
			assertEquals(FailureType.WARNING, assertion.getFailureType());
			assertEquals(3, assertion.getCurrentViolatedConceptIds().size());
			assertTrue(assertion.getCurrentViolatedConceptIds().containsAll(Arrays.asList(102563003L, 160959002L, 161054003L)));
		});
	}

	@Test
	public void testValidationForConcreteDataType() throws Exception {
		// Has presentation strength denominator value concrete (attribute)
		runValidationForAttribute("3311482005", Collections.singletonList(ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE));
		final List<String> assertionMessages =
				Arrays.asList("The attribute value of 3311483000 must conform to the MRCM concrete attribute data type",
						"The attribute value of 3311487004 must conform to the MRCM concrete attribute data type",
						"The attribute value of 3311482007 must conform to the MRCM concrete attribute data type",
						"The attribute value of 3311481003 must conform to the MRCM concrete attribute data type",
						"The attribute value of 3311482006 must conform to the MRCM concrete attribute data type");
		final List<String> failedAssertionMessages =
				Arrays.asList("Relationship concrete value #100.0 found but no concrete data type is defined in the MRCM range constraint.",
						"Axiom concrete value #1 found but no concrete data type is defined in the MRCM range constraint.",
						"Axiom concrete value of #200 is not a type of STRING as defined in the MRCM.",
						"Relationship concrete value #1.0 found but no concrete data type is defined in the MRCM range constraint.",
						"Axiom concrete value of \"\"500\"\" is not a type of INTEGER as defined in the MRCM.");
		final List<Long> expectedViolatedConceptIds = Arrays.asList(375745003L, 375745004L, 375745005L);
		final Set<Assertion> failedAssertions = run.getFailedAssertions();
		assertEquals(5, failedAssertions.size());
		failedAssertions.forEach(failed -> {
			System.out.println(failed.getAssertionText());
			assertTrue(failed.getAssertionText(), assertionMessages.contains(failed.getAssertionText()));
			assertTrue(failed.getMessage(), failedAssertionMessages.contains(failed.getMessage()));
			assertEquals(FailureType.ERROR, failed.getFailureType());
			assertTrue(expectedViolatedConceptIds.containsAll(failed.getCurrentViolatedConceptIds()));
		});
	}
}
