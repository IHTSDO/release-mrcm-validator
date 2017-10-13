package org.snomed.quality.validator.mrcm;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;
import org.springframework.util.Assert;

public class ValidationServiceTestHarness {

	public static void main(String[] args) throws Exception {
		File releaseTestFile = Paths.get("release").toFile();
		ValidationService validationService = new ValidationService();
		ValidationRun run = new ValidationRun("20180131",true);
		validationService.loadMRCM(releaseTestFile,run);
		Assert.notNull(run.getMRCMDomains(),"Domain should not be null");
		String attributeId = "272741003";
		Map<String, Domain> domainsToValidate = new HashMap<>();
		for (Domain domain : run.getMRCMDomains().values()) {
			for (Attribute attribute : domain.getAttributes()) {
				if (attribute.getAttributeId().equals(attributeId)) {
					domainsToValidate.put(domain.getDomainId(), domain);
				}
			}
		}
		run.setMRCMDomains(domainsToValidate);
		run.setValidationTypes(Arrays.asList(ValidationType.ATTRIBUTE_DOMAIN));
		validationService.validateRelease(releaseTestFile, run);
		assertEquals(1,run.getFailedAssertions().size());
		for (Assertion assertion : run.getFailedAssertions()) {
			System.out.println(assertion);
			assertEquals(attributeId, assertion.getAttribute().getAttributeId());
		}
	}

}
