package org.snomed.quality.validator.mrcm;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.Before;
import org.junit.Test;
import org.snomed.quality.validator.mrcm.ValidationRun;
import org.snomed.quality.validator.mrcm.ValidationType;
import org.snomed.quality.validator.mrcm.ValidationService;
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
		validationService.loadMRCM(releaseTestFile,run);
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
		assertEquals(101,totalAttribute);
		assertEquals(107,totalAttributeRange);
	}
	
	@Test
	public void testValidRelease() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(231,run.getCompletedAssertions().size());
		assertEquals(146,run.getSkippedAssertions().size());
		assertEquals(4,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
		}
	}

	@Test
	public void testValidReleaseForSpecificAttribute() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
//		String attributeId ="363698007";
		String attributeId = "272741003";
		Map<String, Domain> domainsToValidate = new HashMap<>();
		for (Domain domain : run.getMRCMDomains().values()) {
			Domain selectedDomain = new Domain(domain.getDomainId());
			for (Attribute attribute : domain.getAttributes()) {
				if (attribute.getAttributeId().equals(attributeId)) {
					selectedDomain.addAttribute(attribute);
					domainsToValidate.put(selectedDomain.getDomainId(), selectedDomain);
				}
			}
		}
		run.setMRCMDomains(domainsToValidate);
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals(attributeId, assertion.getAttribute().getAttributeId());
		}
	}
	
	@Test
	public void testAttributeDomainValidation() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_DOMAIN));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(85,run.getCompletedAssertions().size());
		assertEquals(1,run.getSkippedAssertions().size());
		System.out.println(run.getSkippedAssertions().iterator().next().getMessage());
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals("272741003", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(4,assertion.getViolatedConceptIds().size());
			List<Long> expected = Arrays.asList(Long.valueOf("159530000"),Long.valueOf("160959002"),Long.valueOf("161054003"), Long.valueOf("102563003"));
			assertEquals(expected.size(),assertion.getViolatedConceptIds().size());
			assertTrue(expected.containsAll(assertion.getViolatedConceptIds()));
		}
	}
	
	@Test
	public void testAttributeRangeValidation() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_RANGE));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(81,run.getCompletedAssertions().size());
		//skipped assertions
		assertEquals(8, run.getSkippedAssertions().size());
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals("363698007", assertion.getAttribute().getAttributeId());
			assertEquals("<< 442083009 |Anatomical or acquired body structure (body structure)|", assertion.getAttribute().getRangeConstraint());
			assertTrue(assertion.getViolatedConceptIds().contains(new Long("29857009")));
			
		}
	}
	
	@Test
	public void testAttributeCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(1,run.getCompletedAssertions().size());
		assertEquals(100,run.getSkippedAssertions().size());
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals("272741003", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(1,assertion.getViolatedConceptIds().size());
			List<Long> expected = Arrays.asList(Long.valueOf("91723000"));
			assertEquals(expected,assertion.getViolatedConceptIds());
		}
	}
	
	
	
	@Test
	public void testAttributeGroupCardinality() throws Exception {
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_GROUP_CARDINALITY));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(64,run.getCompletedAssertions().size());
		assertEquals(37,run.getSkippedAssertions().size());
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals("363698007", assertion.getAttribute().getAttributeId());
			assertEquals("723597001", assertion.getAttribute().getRuleStrengthId());
			assertEquals(1,assertion.getViolatedConceptIds().size());
			List<Long> expected = Arrays.asList(Long.valueOf("404684003"));
			assertEquals(expected,assertion.getViolatedConceptIds());
		}
	}
	
}