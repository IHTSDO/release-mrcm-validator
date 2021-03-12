package org.snomed.quality.validator.mrcm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.snomed.quality.validator.mrcm.Constants.*;

public class ReportService {

	private final File resultDir;

	private final String releasePackage;

	public ReportService(final File resultDir, final String releasePackage) {
		this.resultDir = resultDir;
		this.releasePackage = releasePackage;
	}

	public final void generateValidationReports(final ValidationRun run) throws IOException {
		createSummaryReport(resultDir, releasePackage, run);
		createValidationReport(resultDir, run);
	}

	private void createValidationReport(final File resultDir, final ValidationRun run) throws IOException {
		createValidationFailureReport(resultDir, run, run.getFailedAssertions(), true);
		createValidationFailureReport(resultDir, run, run.getAssertionsWithWarning(), false);
		createSkippedAssertionsReport(resultDir, run);
		createSuccessfulValidationReport(resultDir, run);
	}

	private void createSuccessfulValidationReport(final File resultDir, final ValidationRun run) throws IOException {
		if (!run.getCompletedAssertions().isEmpty()) {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(resultDir,
					MRCM_TITLE_PREFIX + run.getContentType().getType() + VALIDATION_PASSED_FILE_NAME_WITH_TXT_EXTENSION)))) {
				for (Assertion passed : run.getCompletedAssertions()) {
					writer.write(passed.toString());
					writer.write(NEW_LINE);
				}
			}
		}
	}

	private void createSkippedAssertionsReport(final File resultDir, final ValidationRun run) throws IOException {
		// Report skipped assertions and reasons
		if (run.reportSkippedAssertions() && !run.getSkippedAssertions().isEmpty()) {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(resultDir,
					MRCM_TITLE_PREFIX + run.getContentType().getType() + VALIDATION_SKIPPED_FILE_NAME_WITH_TXT_EXTENSION)))) {
				for (Assertion skipped : run.getSkippedAssertions()) {
					writer.write(skipped.toString());
					writer.write(NEW_LINE);
				}
			}
		}
	}

	private void createValidationFailureReport(final File resultDir, final ValidationRun run, final Set<Assertion> failures,
			final boolean isErrorReporting) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(resultDir, MRCM_TITLE_PREFIX +
				run.getContentType().getType() + VALIDATION_REPORT_PREFIX + (isErrorReporting ? WITH_ERROR : WITH_WARNING) +
				TXT_EXTENSION)))) {
			writer.write(VALIDATION_REPORT_HEADINGS);
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

	private void createSummaryReport(final File resultDir, final String releasePackage, final ValidationRun run) throws IOException {
		final File report = new File(resultDir, MRCM_SUMMARY_REPORT);
		final int totalAttribute = run.getMRCMDomains().keySet().stream().map(key -> run.getMRCMDomains().get(key))
				.mapToInt(domain -> domain.getAttributes().size()).sum();

		try (final BufferedWriter writer = new BufferedWriter(new FileWriter(report, true))) {
			final StringBuilder reportSummary = new StringBuilder();
			if (report.length() == 0) {
				reportSummary.append(REPORT_SUMMARY_RELEASE_PACKAGE_LINE)
						.append(releasePackage)
						.append(NEW_LINE)
						.append(REPORT_SUMMARY_REPORTING_FOR_RELEASE_CYCLE_LINE)
						.append(run.getReleaseDate())
						.append(NEW_LINE)
						.append(NEW_LINE);
			}
			reportSummary.append(run.getContentType() == ContentType.STATED ? REPORT_SUMMARY_RESULTS_ON_STATED_VIEW_LINE :
					REPORT_SUMMARY_RESULTS_ON_INFERRED_VIEW_LINE)
					.append(NEW_LINE)
					.append(TAB)
					.append(REPORT_SUMMARY_TOTAL_MRCM_DOMAINS_LOADED_LINE)
					.append(run.getMRCMDomains().size())
					.append(NEW_LINE)
					.append(TAB)
					.append(REPORT_SUMMARY_TOTAL_MRCM_ATTRIBUTES_LOADED_LINE)
					.append(totalAttribute)
					.append(NEW_LINE)
					.append(TAB)
					.append(REPORT_SUMMARY_TOTAL_ASSERTIONS_COMPLETED_LINE)
					.append(run.getCompletedAssertions().size())
					.append(NEW_LINE);
			if (run.reportSkippedAssertions()) {
				reportSummary.append(TAB)
						.append(REPORT_SUMMARY_TOTAL_ASSERTIONS_SKIPPED_LINE)
						.append(run.getSkippedAssertions().size())
						.append(NEW_LINE);
			}
			reportSummary.append(TAB)
					.append(REPORT_SUMMARY_TOTAL_ASSERTIONS_FAILED_LINE)
					.append(run.getFailedAssertions().size())
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
