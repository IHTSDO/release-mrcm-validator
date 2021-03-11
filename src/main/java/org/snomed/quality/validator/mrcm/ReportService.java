package org.snomed.quality.validator.mrcm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.snomed.quality.validator.mrcm.Constants.NEW_LINE;
import static org.snomed.quality.validator.mrcm.Constants.TAB;

public class ReportService {

	private final File resultDir;

	private final String releasePackage;

	public ReportService(File resultDir, String releasePackage) {
		this.resultDir = resultDir;
		this.releasePackage = releasePackage;
	}

	public void generateValidationReports(ValidationRun run) throws IOException {
		createSummaryReport(resultDir, releasePackage, run);
		createValidationReport(resultDir, run);
	}

	private void createValidationReport(File resultDir, ValidationRun run) throws IOException {
		createValidationFailureReport(resultDir, run, run.getFailedAssertions(), true);
		createValidationFailureReport(resultDir, run, run.getAssertionsWithWarning(), false);
		createSkippedAssertionsReport(resultDir, run);
		createSuccessfulValidationReport(resultDir, run);
	}

	private void createSuccessfulValidationReport(File resultDir, ValidationRun run) throws IOException {
		if (!run.getCompletedAssertions().isEmpty()) {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(resultDir,"MRCM" + run.getValidationView().getView() + "ValidationPassed.txt")))) {
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
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(resultDir,"MRCM" + run.getValidationView().getView() + "ValidationSkipped.txt")))) {
				for (Assertion skipped : run.getSkippedAssertions()) {
					writer.write(skipped.toString());
					writer.write(NEW_LINE);
				}
			}
		}
	}

	private void createValidationFailureReport(File resultDir, ValidationRun run, Set<Assertion> failures, boolean isErrorReporting) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(resultDir, "MRCM" + run.getValidationView().getView()
				+ "ValidationReport" + (isErrorReporting ? "WithError" : "WithWarning") + ".txt")))) {
			writer.write("Item\tUUID\tAssertion Text\tMessage\tCurrent Total\tViolated Concepts In Current Release\tPrevious Total\tViolated Concepts In Previous Releases");
			writer.write(NEW_LINE);
			int counter = 1;
			for (Assertion failed : failures) {
				writer.write(counter++ + TAB + failed.getUuid().toString() + TAB + failed.getAssertionText() +
						TAB + failed.getMessage() + TAB + failed.getCurrentViolatedConceptIds().size() + TAB +
						extractInstances(failed.getCurrentViolatedConceptIds()) + TAB + failed.getPreviousViolatedConceptIds().size() +
						TAB + extractInstances(failed.getPreviousViolatedConceptIds()) + NEW_LINE);
			}
		}
	}

	private void createSummaryReport(File resultDir, String releasePackage, ValidationRun run) throws IOException {
		final File report = new File(resultDir,"MRCMSummaryReport.txt");
		final int totalAttribute = run.getMRCMDomains().keySet().stream().map(key -> run.getMRCMDomains().get(key))
				.mapToInt(domain -> domain.getAttributes().size()).sum();

		try (final BufferedWriter writer = new BufferedWriter(new FileWriter(report, true))) {
			final StringBuilder reportSummary = new StringBuilder();
			if (report.length() == 0) {
				reportSummary.append("ReleasePackage: ")
						.append(releasePackage)
						.append(NEW_LINE)
						.append("ReportingForReleaseCycle: ")
						.append(run.getReleaseDate())
						.append(NEW_LINE)
						.append(NEW_LINE);
			}
			reportSummary.append(run.getValidationView() == ValidationView.STATED ? "Results on stated view: " : "Results on inferred view: ")
					.append(NEW_LINE)
					.append("\tTotal MRCM domains loaded: ")
					.append(run.getMRCMDomains().size())
					.append(NEW_LINE)
					.append("\tTotal MRCM attributes loaded: ")
					.append(totalAttribute)
					.append(NEW_LINE)
					.append("\tTotal assertions completed: ")
					.append(run.getCompletedAssertions().size())
					.append(NEW_LINE);
			if (run.reportSkippedAssertions()) {
				reportSummary.append("\tTotal assertions skipped: ")
						.append(run.getSkippedAssertions().size())
						.append(NEW_LINE);
			}
			reportSummary.append("\tTotal assertions failed: ").append(run.getFailedAssertions().size())
					.append(NEW_LINE)
					.append(NEW_LINE);
			writer.write(reportSummary.toString());
		}
	}

	private String extractInstances(final List<Long> failed) {
		final StringBuilder conceptList = new StringBuilder();
		IntStream.range(0, failed.size()).forEach(i -> {
			if (i > 0) {
				conceptList.append(",");
			}
			conceptList.append(failed.get(i));
		});
		return conceptList.toString();
	}
}
