package org.snomed.quality.validator.mrcm;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
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
	private File releaseTestFile;

	@Before
	public void setUp() throws ReleaseImportException {
		validationService = new ValidationService();
		run = new ValidationRun(null, false);
		releaseTestFile = Paths.get("src/test/resources/rf2TestFiles").toFile();
		validationService.loadMRCM(releaseTestFile, run);
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
					assertTrue("Attribute must have at least one range constraint.", domain.getAttributeRanges(attribute.getAttributeId()).size() >= 1);
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
		Assert.notNull(run.getMRCMDomains(),"MRCM Domains should not be null.");
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(267, run.getCompletedAssertions().size());
		assertEquals(0, run.getSkippedAssertions().size());
		assertEquals(14, run.getFailedAssertions().size());

		final List<String> failedMessages = Arrays.asList(
				"The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute group cardinality [0..1]",
				"The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM concrete attribute data type",
				"The attribute value of 363698007 |Finding site (attribute)| must conform to the MRCM attribute domain range << 442083009 |Anatomical or acquired body structure (body structure)|",
				"The attribute value of 3311487004 must conform to the MRCM concrete attribute data type",
				"The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute domain range << 182353008 |Side (qualifier value)|",
				"The attribute value of 363698007 |Finding site (attribute)| must conform to the MRCM attribute group cardinality [0..1]",
				"The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute cardinality [0..1]",
				"The attribute value of 3311483000 must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311481003 must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute domain range dec(#10..#20)",
				"The attribute value of 3311482006 |Has integer presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute domain range int(#30..#40)",
				"The attribute value of 3311482007 |Has string presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute domain range str(\"test\")",
				"The attribute value of 3311482007 |Has string presentation strength denominator value concrete (attribute)| must conform to the MRCM concrete attribute data type",
				"The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute domain << 91723000 |Anatomical structure (body structure)|",
				"The attribute value of 3311482006 |Has integer presentation strength denominator value concrete (attribute)| must conform to the MRCM concrete attribute data type");

		run.getFailedAssertions().stream().map(assertion -> failedMessages.contains(assertion.getAssertionText())).forEach(org.junit.Assert::assertTrue);
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
		validationService.validateRelease(releaseTestFile, run);
	}

	@Test
	public void testAttributeDomainValidation() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_DOMAIN));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(104, run.getCompletedAssertions().size());
		assertEquals(0, run.getSkippedAssertions().size());
		assertEquals(1, run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals("272741003", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(4, assertion.getCurrentViolatedConceptIds().size());
			List<Long> expected = Arrays.asList(Long.valueOf("159530000"), Long.valueOf("160959002"), Long.valueOf("161054003"), Long.valueOf("102563003"));
			assertEquals(expected.size(), assertion.getCurrentViolatedConceptIds().size());
			assertTrue(expected.containsAll(assertion.getCurrentViolatedConceptIds()));
		}
	}

	@Test
	public void testAttributeRangeValidation() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_RANGE));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(88, run.getCompletedAssertions().size());
		//skipped assertions
		assertEquals(0, run.getSkippedAssertions().size());
		assertEquals(5, run.getFailedAssertions().size());
		List<String> expectedFailed = Arrays.asList("363698007", "3311482005", "272741003", "3311482006", "3311482007");
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertTrue(expectedFailed.contains(assertion.getAttribute().getAttributeId()));
			if ("363698007".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals("<< 442083009 |Anatomical or acquired body structure (body structure)|", assertion.getAttribute().getRangeConstraint());
				assertTrue(assertion.getCurrentViolatedConceptIds().contains(29857009L));
			}
			if ("272741003".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals("<< 182353008 |Side (qualifier value)|", assertion.getAttribute().getRangeConstraint());
				assertEquals(2, assertion.getCurrentViolatedConceptIds().size());
				assertTrue(assertion.getCurrentViolatedConceptIds().contains(91723000L));
				assertTrue(assertion.getCurrentViolatedConceptIds().contains(113343008L));
			}
			if ("3311482005".equals(assertion.getAttribute().getAttributeId()) || "3311482006".equals(assertion.getAttribute().getAttributeId())
					|| "3311482007".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals(1, assertion.getCurrentViolatedConceptIds().size());
				assertTrue(assertion.getCurrentViolatedConceptIds().contains(375745003L));
			}
		}
	}

	@Test
	public void testAxiomValidation() throws ServiceException, ReleaseImportException, IOException {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE));
		validationService.validateRelease(releaseTestFile, run);
	}

	@Test
	public void testAttributeCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
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
		run = new ValidationRun(null, false, true);
		validationService.loadMRCM(releaseTestFile, run);
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
		assertTrue(run.reportSkippedAssertions());
		assertEquals(37, run.getSkippedAssertions().size());
	}

	@Test
	public void testAttributeGroupCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(), "Domain should not be null");
		run.setValidationTypes(Collections.singletonList(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
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
			assertTrue(run.getValidationTypes().contains(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
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
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(1, run.getAssertionsWithWarning().size());
		String failureMsg = "The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute domain " +
				"<< ^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|";
		run.getAssertionsWithWarning().forEach(assertion -> {
			assertEquals(failureMsg, assertion.getAssertionText());
			assertEquals(attributeId, assertion.getAttribute().getAttributeId());
			assertEquals(FailureType.WARNING, assertion.getFailureType());
			assertEquals(6, assertion.getCurrentViolatedConceptIds().size());
			assertTrue(assertion.getCurrentViolatedConceptIds().containsAll(Arrays.asList(159530000L, 91723000L, 102563003L, 160959002L, 161054003L, 113343008L)));
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
			assertTrue(assertionMessages.contains(failed.getAssertionText()));
			assertTrue(failedAssertionMessages.contains(failed.getMessage()));
			assertEquals(FailureType.ERROR, failed.getFailureType());
			assertTrue(expectedViolatedConceptIds.containsAll(failed.getCurrentViolatedConceptIds()));
		});
	}
}
