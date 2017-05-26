package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;

public class Application {

	private static final String NEW_LINE = "\n";

	public static void main(String[] args) throws ReleaseImportException, IOException, ServiceException {
		String releasePackage = null;
		if ( args!= null && args.length > 0) {
			releasePackage = args[0];
		}
		new Application().run(releasePackage);
	}

	private void run(String releasePackage) throws ReleaseImportException, IOException, ServiceException {
		ValidationService service = new ValidationService();
		ValidationRun run = new ValidationRun();
		if (releasePackage == null) {
			//no external package specified using default soft link release path.
			releasePackage = "release";
//			releasePackage = Paths.get("src/test/resources/rf2TestFiles").toString();
		}
		service.loadMRCM(new File(releasePackage), run);
		service.validateRelease(new File(releasePackage), run);
		int totalAttribute = 0;
		for (String key: run.getMRCMDomains().keySet()) {
			Domain domain = run.getMRCMDomains().get(key);
			for (Attribute attribute : domain.getAttributes()) {
				totalAttribute++;
				if (domain.getAttributeRanges(attribute.getAttributeId()).isEmpty())  {
				}
			}
		}
		StringBuilder reportSummary = new StringBuilder();
		reportSummary.append("Total MRCM domains loaded:" + run.getMRCMDomains().size());
		reportSummary.append(NEW_LINE);
		reportSummary.append("Total MRCM attribute loaded:" + totalAttribute);
		reportSummary.append(NEW_LINE);
		reportSummary.append("Total assertions completed:" + run.getCompletedAssertions().size());
		reportSummary.append(NEW_LINE);
		reportSummary.append("Total assertions skipped:" + run.getSkippedAssertions().size());
		reportSummary.append(NEW_LINE);
		reportSummary.append("Total assertions failed:" + run.getFailedAssertions().size());
		System.out.println(reportSummary.toString());
		for (Assertion skipped : run.getSkippedAssertions()) {
			System.out.println("Assertion skipped:" + skipped.getAttribute() + " Reason: " + skipped.getAssertionText());
		}
		Set<Assertion> failedAssertions = run.getFailedAssertions();
		for (Assertion failedAssertion : failedAssertions) {
			System.out.println("Assertion failed: " + failedAssertion);
		}
	}
}
