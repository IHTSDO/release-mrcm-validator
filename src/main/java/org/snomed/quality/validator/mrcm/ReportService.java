package org.snomed.quality.validator.mrcm;

import org.snomed.quality.validator.mrcm.model.Domain;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.snomed.quality.validator.mrcm.Constants.NEW_LINE;
import static org.snomed.quality.validator.mrcm.Constants.TAB;

public class ReportService {
	private File resultDir;

	private String releasePackage;

	public ReportService(File resultDir, String releasePackage) {
		this.resultDir = resultDir;
		this.releasePackage = releasePackage;
	}

	public void generateValidationReports(ValidationRun run) throws IOException {
		createSummaryReport(resultDir, releasePackage, run);
		createValidationReport(resultDir, run);
	}

	private void createValidationReport(File resultDir, ValidationRun run) throws IOException {
		createValidationFailureReport(resultDir, run.getFailedAssertions(), true);
		createValidationFailureReport(resultDir, run.getAssertionsWithWarning(), false);
		createSkippedAssertionsReport(resultDir, run);
		createSuccessfulValidationReport(resultDir, run);
	}

	private void createSuccessfulValidationReport(File resultDir, ValidationRun run) throws IOException {
		if (!run.getCompletedAssertions().isEmpty()) {
			File successReport = new File(resultDir,"MRCMValidationPassed.txt");
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(successReport))) {
				for (Assertion passed : run.getCompletedAssertions()) {
					writer.write(passed.toString());
					writer.write(NEW_LINE);
				}
			}
		}
	}

	private void createSkippedAssertionsReport(File resultDir, ValidationRun run) throws IOException {
		// Report skipped assertions and reasons
		if (run.reportSkippedAssertions() && !run.getSkippedAssertions().isEmpty()) {
			File skippedReport = new File(resultDir,"MRCMValidationSkipped.txt");
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(skippedReport))) {
				for (Assertion skipped : run.getSkippedAssertions()) {
					writer.write(skipped.toString());
					writer.write(NEW_LINE);
				}
			}
		}
	}

	private void createValidationFailureReport(File resultDir, Set<Assertion> failures, boolean isErrorReporting) throws IOException {
		String type = isErrorReporting ? "WithError" : "WithWarning";
		File report = new File(resultDir,"MRCMValidationReport" + type + ".txt");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(report))) {
			String reportHeader = "Item\tUUID\tAssertion Text\tMessage\tCurrent Total\tViolated Concepts In Current Release\tPrevious Total\tViolated Concepts In Previous Releases";
			writer.write(reportHeader);
			writer.write(NEW_LINE);
			int counter = 1;
			for (Assertion failed : failures) {
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
		}
	}


	private void createSummaryReport(File resultDir, String releasePackage, ValidationRun run) throws IOException {
		File report = new File(resultDir,"MRCMSummaryReport.txt");
		int totalAttribute = 0;
		for (String key: run.getMRCMDomains().keySet()) {
			Domain domain = run.getMRCMDomains().get(key);
			totalAttribute += domain.getAttributes().size();
		}

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(report))) {
			StringBuilder reportSummary = new StringBuilder();
			reportSummary.append("ReleasePackage:" + releasePackage);
			reportSummary.append(NEW_LINE);
			reportSummary.append("ReportingForReleaseCycle:" + run.getReleaseDate());
			reportSummary.append(NEW_LINE);
			reportSummary.append("isStatedViewOnly:" + run.isStatedView());
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total MRCM domains loaded:" + run.getMRCMDomains().size());
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total MRCM attributes loaded:" + totalAttribute);
			reportSummary.append(NEW_LINE);
			reportSummary.append("Total assertions completed:" + run.getCompletedAssertions().size());
			reportSummary.append(NEW_LINE);
			if (run.reportSkippedAssertions()) {
				reportSummary.append("Total assertions skipped:" + run.getSkippedAssertions().size());
				reportSummary.append(NEW_LINE);
			}
			reportSummary.append("Total assertions failed:" + run.getFailedAssertions().size());
			reportSummary.append(NEW_LINE);
			writer.write(reportSummary.toString());
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
