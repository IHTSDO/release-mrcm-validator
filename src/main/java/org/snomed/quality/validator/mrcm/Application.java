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
import java.util.List;
import java.util.Set;

public class Application {

	private static final String TAB = "\t";

	private static final String NEW_LINE = "\n";

	private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
	
	public static void main(String[] args) throws Exception {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		String releasePackage = null;
		String releaseDate = null;
		File resultDir = null;
		boolean isStatedViewOnly = true;
		if ( args == null || args.length < 4) {
			String msg = "Please specifiy the java arguments after replacing the {} with actual values." + "{releasePackageFullPathName} {true of false} {20180131} {result_dir}";
			System.out.println(msg);
			System.out.println("Java argument 1 is for the release package unzipped full file path. (required):" + " eg:/Users/mchu/Releases/international/SnomedCT_InternationalRF2_PRODUCTION_20170731T120000Z");
			System.out.println("Java argument 2 is to state whether to use stated relationships. (optional default to true):" + "e.g false (implies to use inferred instead.)");
			System.out.println("Java argument 3 is the release date for current new content(optional):" + " e.g 20170731.(Note: Don't specify this argument when testing for all failures.)");
			System.out.println("Java argument 4 is the directory where validation reports will be saved.");
			throw new IllegalStateException(msg);
		} else {
			releasePackage = args[0];
			isStatedViewOnly =  Boolean.parseBoolean(args[1]);
			releaseDate = args[2];
			if (releaseDate != null) {
				dateFormat.parse(releaseDate);
			} 
			resultDir = new File (args[3]);
			if (!resultDir.exists()) {
				resultDir.mkdirs();
			}
			new Application().run(releasePackage, releaseDate, isStatedViewOnly, resultDir);
		}
	}

	private void run(String releasePackage, String releaseDate, boolean isStatedViewOnly, File resultDir) throws ReleaseImportException, IOException, ServiceException, ParseException {
		ValidationService service = new ValidationService();
		ValidationRun run = new ValidationRun(releaseDate, isStatedViewOnly);
		if (releasePackage == null) {
			//no external package specified using default soft link release path.
			releasePackage = "release";
			releaseDate = "20180131";
		}
		service.loadMRCM(new File(releasePackage), run);
		service.validateRelease(new File(releasePackage), run);
		int totalAttribute = 0; 
		for (String key: run.getMRCMDomains().keySet()) {
			Domain domain = run.getMRCMDomains().get(key);
			totalAttribute += domain.getAttributes().size();
		}
		File report = new File(resultDir,"MrcmValidationReport.txt");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(report))) {
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
			writer.write(reportSummary.toString());
			writer.write(NEW_LINE);
			writer.write("Failed assertions:");
			writer.write(NEW_LINE);
			Set<Assertion> failedAssertions = run.getFailedAssertions();
			String reportHeader = "Item\tUUID\tAssertion Text\t Failure Message\tCurrent Total\tCurrent Violated Concepts\tPrevious Total\tPrevious Release Violated Concepts";
			writer.write(reportHeader);
			writer.write(NEW_LINE);
			int counter = 1;
			for (Assertion failed : failedAssertions) {
				StringBuilder builder = new StringBuilder();
				builder.append(counter++);
				builder.append(TAB);
				builder.append(failed.getUuid().toString());
				builder.append(TAB);
				builder.append(failed.getAssertionText());
				builder.append(TAB);
				builder.append(failed.getMessage());
				builder.append(TAB);
				builder.append(failed.getCurrentViolatedConceptIds().size());
				builder.append(TAB);
				builder.append(extractInstances(failed.getCurrentViolatedConceptIds()).toString());
				builder.append(TAB);
				builder.append(failed.getPreviousViolatedConceptIds().size());
				builder.append(TAB);
				builder.append(extractInstances(failed.getPreviousViolatedConceptIds()).toString());
				builder.append(NEW_LINE);
				writer.write(builder.toString());
			}
			System.out.println("Please see validation report in " + report.getAbsolutePath());
		}
		//Report skipped assertions and reasons
		if (!run.getSkippedAssertions().isEmpty()) {
			File skippedReport = new File(resultDir,"MRCMValidationSkipped.txt");
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(skippedReport))) {
				for (Assertion skipped : run.getSkippedAssertions()) {
					writer.write(skipped.toString());
					writer.write(NEW_LINE);
				}
			}
			System.out.println("Please see validations skipped report in " + skippedReport.getAbsolutePath());
		}
	}

	private StringBuilder extractInstances(List<Long> failed) {
		StringBuilder conceptList = new StringBuilder();
		for (int i=0; i < failed.size(); i++) {
			if (i > 0) {
				conceptList.append(",");
			}
			conceptList.append(failed.get(i));
		}
		return conceptList;
	}
}
