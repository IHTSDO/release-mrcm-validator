package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.quality.validator.mrcm.model.Attribute;
import org.snomed.quality.validator.mrcm.model.Domain;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Set;

public class Application {

	private static final String NEW_LINE = "\n";

	private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
	
	public static void main(String[] args) throws Exception {
		
		System.out.println("Java argument 1 is for the release package unzipped full file path. (required):" + " eg:/Users/mchu/Releases/international/SnomedCT_InternationalRF2_PRODUCTION_20170731T120000Z");
		System.out.println("Java argument 2 is to state whether to use stated relationships. (optional default to true):" + "e.g false (implies to use inferred instead.)");
		System.out.println("Java argument 3 is the release date for current new content(optional):" + " e.g 20170731.(Note: Don't specify this argument when testing for all failures.)");
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		String releasePackage = null;
		String releaseDate = null;
		boolean isStatedViewOnly = true;
		if ( args!= null && args.length > 0) {
			releasePackage = args[0];
			if (args.length == 2) {
				isStatedViewOnly =  Boolean.getBoolean(args[1]);
			} else if (args.length == 3) {
				isStatedViewOnly = Boolean.getBoolean(args[1]);
				releaseDate = args[2];
			} else {
				System.out.println("Usage: Replace the {} with actual values." + "{releasePackageFullPathName} {true of false} {20170731}");
			}
			if (releaseDate != null) {
				dateFormat.parse(releaseDate);
			}
		} 
		new Application().run(releasePackage, releaseDate, isStatedViewOnly);
	}

	private void run(String releasePackage, String releaseDate, boolean isStatedViewOnly) throws ReleaseImportException, IOException, ServiceException, ParseException {
		ValidationService service = new ValidationService();
		ValidationRun run = new ValidationRun(releaseDate, isStatedViewOnly);
		if (releasePackage == null) {
			//no external package specified using default soft link release path.
			releasePackage = "release";
			releaseDate = "20170731";
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
		File tempReport = Files.createTempFile("MRCM_ValidationReport",".txt").toFile();
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempReport))) {
			StringBuilder reportSummary = new StringBuilder();
			reportSummary.append("ReleasePackage:" + releasePackage);
			reportSummary.append(NEW_LINE);
			reportSummary.append("ReportingForReleaseCycle:" + releaseDate);
			reportSummary.append(NEW_LINE);
			reportSummary.append("isStatedViewOnly:" + isStatedViewOnly);
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total MRCM domains loaded:" + run.getMRCMDomains().size());
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total MRCM attributes loaded:" + totalAttribute);
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total assertions completed:" + run.getCompletedAssertions().size());
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total assertions skipped:" + run.getSkippedAssertions().size());
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total assertions failed:" + run.getFailedAssertions().size());
			reportSummary.append(NEW_LINE);
			LOGGER.info("Report summary: {}", reportSummary.toString());
			writer.write(reportSummary.toString());
			writer.write(NEW_LINE);
			writer.write("Failed assertions:");
			writer.write(NEW_LINE);
			Set<Assertion> failedAssertions = run.getFailedAssertions();
			for (Assertion failedAssertion : failedAssertions) {
				LOGGER.info("Assertion failed:: {}", failedAssertion);
				writer.write(failedAssertion.toString());
				writer.write(NEW_LINE);
				writer.write(failedAssertion.getAssertionText());
				LOGGER.info("Assertion text {}", failedAssertion.getAssertionText());
				writer.write(NEW_LINE);
				writer.write(" Total:" + failedAssertion.getViolatedConceptIds().size());
				writer.write(NEW_LINE);
			}
			System.out.println("Please see validation report in " + tempReport.getAbsolutePath());
		}
		//Report skipped assertions and reasons
		if (!run.getSkippedAssertions().isEmpty()) {
			File skippedReport = Files.createTempFile("MRCM_ValidationSkipped",".txt").toFile();
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(skippedReport))) {
				for (Assertion skipped : run.getSkippedAssertions()) {
					writer.write(skipped.toString());
					writer.write(NEW_LINE);
				}
			}
			System.out.println("Please see validations skipped report in " + skippedReport.getAbsolutePath());
		}
	}
}
