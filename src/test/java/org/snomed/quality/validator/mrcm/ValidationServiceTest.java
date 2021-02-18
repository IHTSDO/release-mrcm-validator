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
		run = new ValidationRun(null,false);
		releaseTestFile = Paths.get("src/test/resources/rf2TestFiles").toFile();
		validationService.loadMRCM(releaseTestFile, run);
	}

	@Test
	public void testLoadMRCMRules() throws ReleaseImportException {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		assertEquals(17, run.getMRCMDomains().size());
		int totalAttribute=0;
		int totalAttributeRange=0;
		for (String key: run.getMRCMDomains().keySet()) {
			Domain domain = run.getMRCMDomains().get(key);
			for (Attribute attribute : domain.getAttributes()) {
				totalAttribute++;
				assertNotNull("Range constraint can't be null for a given attribute.", domain.getAttributeRanges(attribute.getAttributeId()));
				if (domain.getAttributeRanges(attribute.getAttributeId()).isEmpty())  {
					assertEquals("Attribute must have at least one range constraint.", domain.getAttributeRanges(attribute.getAttributeId()).size() >=1);
				}
				for (Attribute range : domain.getAttributeRanges(attribute.getAttributeId())) {
					totalAttributeRange++;
					assertNotNull(range.getRangeConstraint());
				}
			}
		}
		assertEquals(102,totalAttribute);
		assertEquals(108,totalAttributeRange);
	}
	
	@Test
	public void testValidationForAll() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(258,run.getCompletedAssertions().size());
		assertEquals(0,run.getSkippedAssertions().size());
		assertEquals(10,run.getFailedAssertions().size());

		List<String> failedMessages = Arrays.asList(
		"The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute group cardinality [0..1]",
		"The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM concrete attribute data type",
		"The attribute value of 363698007 |Finding site (attribute)| must conform to the MRCM attribute domain range << 442083009 |Anatomical or acquired body structure (body structure)|",
		"The attribute value of 3311487004 must conform to the MRCM concrete attribute data type",
		"The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute domain range << 182353008 |Side (qualifier value)|",
		"The attribute value of 363698007 |Finding site (attribute)| must conform to the MRCM attribute group cardinality [0..1]",
		"The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute cardinality [0..1]",
		"The attribute value of 3311483000 must conform to the MRCM concrete attribute data type",
		"The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| must conform to the MRCM attribute domain range dec(#10..#20)",
		"The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute domain << 91723000 |Anatomical structure (body structure)|");

		for (Assertion assertion : run.getFailedAssertions()) {
			assertTrue(failedMessages.contains(assertion.getAssertionText()));
		}
	}

	@Test
	public void testValidReleaseForSpecificAttribute() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		String attributeId = "272741003";
		runValidationForAttribute(attributeId, Arrays.asList(ValidationType.ATTRIBUTE_CARDINALITY));
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals(attributeId, assertion.getAttribute().getAttributeId());
		}
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
		domainFound.getAttributes().stream().filter(attribute -> attribute.getAttributeId().equals(attributeId))
				.forEach(attribute -> domainToValidate.addAttribute(attribute));
		domainFound.getAttributeRanges(attributeId).stream().forEach(range -> domainToValidate.addAttributeRange(range));
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
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_DOMAIN));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(102,run.getCompletedAssertions().size());
		assertEquals(0,run.getSkippedAssertions().size());
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals("272741003", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(4,assertion.getCurrentViolatedConceptIds().size());
			List<Long> expected = Arrays.asList(Long.valueOf("159530000"),Long.valueOf("160959002"),Long.valueOf("161054003"), Long.valueOf("102563003"));
			assertEquals(expected.size(),assertion.getCurrentViolatedConceptIds().size());
			assertTrue(expected.containsAll(assertion.getCurrentViolatedConceptIds()));
		}
	}
	
	@Test
	public void testAttributeRangeValidation() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_RANGE));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(86,run.getCompletedAssertions().size());
		//skipped assertions
		assertEquals(0, run.getSkippedAssertions().size());
		assertEquals(3,run.getFailedAssertions().size());
		List<String> expectedFailed = Arrays.asList("363698007", "3311482005", "272741003");
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
			if ("3311482005".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals(1, assertion.getCurrentViolatedConceptIds().size());
				assertTrue(assertion.getCurrentViolatedConceptIds().contains(375745003L));
			}
		}
	}
	
	@Test
	public void testAttributeCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(2,run.getCompletedAssertions().size());
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			assertEquals("272741003", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(2,assertion.getCurrentViolatedConceptIds().size());
			List<Long> expected = Arrays.asList(91723000L);
			assertEquals(2, assertion.getCurrentViolatedConceptIds().size());
			assertTrue(assertion.getCurrentViolatedConceptIds().contains(91723000L));
			assertTrue(assertion.getCurrentViolatedConceptIds().contains(113343008L));
		}
	}

	@Test
	public void testReportWithSkippedAssertions() throws Exception {
		run = new ValidationRun(null,false, true);
		validationService.loadMRCM(releaseTestFile, run);
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
		assertTrue(run.reportSkippedAssertions());
		assertEquals(37,run.getSkippedAssertions().size());
	}
	
	@Test
	public void testAttributeGroupCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(65,run.getCompletedAssertions().size());
		assertEquals(0,run.getSkippedAssertions().size());
		assertEquals(2,run.getFailedAssertions().size());
		List<String> expectedFailed = Arrays.asList("363698007", "3311482005");
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertTrue(expectedFailed.contains(assertion.getAttribute().getAttributeId()));
			if ("363698007".equals(assertion.getAttribute().getAttributeId())) {
				assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
				assertEquals(1,assertion.getCurrentViolatedConceptIds().size());
				List<Long> expected = Arrays.asList(404684003L);
				assertEquals(expected,assertion.getCurrentViolatedConceptIds());
			}
		}
	}
	
	
	@Test
	public void testSpecificAttributeDomainValidationWithWarning() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		String attributeId = "272741003";
		String domainId = "723264001";
		Map<String, Domain> domainsToValidate = new HashMap<>();
		for (Domain domain : run.getMRCMDomains().values()) {
			if (!domain.getDomainId().equals(domainId)) {
				continue;
			}
			for (Attribute attribute : domain.getAttributes()) {
				if (attribute.getAttributeId().equals(attributeId)) {
					domainsToValidate.put(domain.getDomainId(), domain);
				}
			}
		}
		run.setMRCMDomains(domainsToValidate);
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_DOMAIN));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(1, run.getAssertionsWithWarning().size());
		String failureMsg = "The attribute value of 272741003 |Laterality (attribute)| must conform to the MRCM attribute domain " +
				"<< ^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|";
		for (Assertion assertion : run.getAssertionsWithWarning()) {
			assertEquals(failureMsg, assertion.getAssertionText());
			assertEquals(attributeId, assertion.getAttribute().getAttributeId());
			assertEquals(FailureType.WARNING, assertion.getFailureType());
			assertEquals(6, assertion.getCurrentViolatedConceptIds().size());
			
		}
	}

	@Test
	public void testValidationForConcreteValue() throws Exception {
		// Has presentation strength denominator value concrete (attribute)
		runValidationForAttribute("3311482005", Arrays.asList(ValidationType.ATTRIBUTE_RANGE, ValidationType.ATTRIBUTE_CARDINALITY));
		String failureMsg = "The attribute value of 3311482005 |Has presentation strength denominator value concrete (attribute)| " +
				"must conform to the MRCM attribute domain range dec(#10..#20)";
		assertEquals(1, run.getFailedAssertions().size());
		for (Assertion failed : run.getFailedAssertions()) {
			assertEquals(failureMsg, failed.getAssertionText());
			assertEquals(375745003, failed.getCurrentViolatedConceptIds().get(0).longValue());
		}
	}


	@Test
	public void testValidationForConcreteDataType() throws Exception {
		// Has presentation strength denominator value concrete (attribute)
		runValidationForAttribute("3311482005", Arrays.asList(ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE));
		List<String> failureMsgs = Arrays.asList(
				"The attribute value of 3311483000 must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311487004 must conform to the MRCM concrete attribute data type",
				"The attribute value of 3311482005 must conform to the MRCM concrete attribute data type"
		);
		assertEquals(3, run.getFailedAssertions().size());
		for (Assertion failed : run.getFailedAssertions()) {
			assertTrue(failureMsgs.contains(failed.getAssertionText()));
			assertEquals(375745003, failed.getCurrentViolatedConceptIds().get(0).longValue());
		}
	}
}