package org.snomed.quality.validator.mrcm;

public final class Constants {

	public static final String ERROR_MESSAGE = "Please specify the java arguments after replacing the { } with actual values. {release_package_unzipped_root} {content_type} {release_date} {result_dir}";

	public static final String RELEASE_PACKAGE_UNZIPPED_ROOT_DIR_HELP_MESSAGE = "{release_package_unzipped_root_dir} is for the release package unzipped file root directory. eg: /Users/Releases/SnomedCT_InternationalRF2_PRODUCTION_20170731T120000Z";

	public static final String CONTENT_TYPE_HELP_MESSAGE = "{content_type} is used to specify whether to use stated, inferred or both (stated & inferred) relationships. " +
			"This parameter is optional and the default content type is stated.";

	public static final String RELEASE_DATE_HELP_MESSAGE = "{release_date} is the effective date for the release file that is being validated.The format is yyyyMMdd eg:20170731";

	public static final String RESULT_DIR_HELP_MESSAGE = "{result_dir} is the directory where validation reports will be saved.";

	public static final String MRCM_TITLE_PREFIX = "MRCM";

	public static final String TXT_EXTENSION = ".txt";

	public static final String VALIDATION_PASSED_FILE_NAME_WITH_TXT_EXTENSION = "ValidationPassed" + TXT_EXTENSION;

	public static final String VALIDATION_SKIPPED_FILE_NAME_WITH_TXT_EXTENSION = "ValidationSkipped" + TXT_EXTENSION;

	public static final String VALIDATION_REPORT_PREFIX = "ValidationReport";

	public static final String WITH_ERROR = "WithError";

	public static final String WITH_WARNING = "WithWarning";

	public static final String VALIDATION_REPORT_HEADINGS = "Item\tUUID\tAssertion Text\tMessage\tCurrent Total" +
			"\tViolated Concepts In Current Release\tPrevious Total\tViolated Concepts In Previous Releases";

	public static final String MRCM_SUMMARY_REPORT = MRCM_TITLE_PREFIX + "SummaryReport" + TXT_EXTENSION;

	public static final String REPORT_SUMMARY_RELEASE_PACKAGE_LINE = "ReleasePackage: ";

	public static final String REPORT_SUMMARY_REPORTING_FOR_RELEASE_CYCLE_LINE = "ReportingForReleaseCycle: ";

	public static final String REPORT_SUMMARY_RESULTS_ON_STATED_VIEW_LINE = "Results on stated view: ";

	public static final String REPORT_SUMMARY_RESULTS_ON_INFERRED_VIEW_LINE = "Results on inferred view: ";

	public static final String REPORT_SUMMARY_TOTAL_MRCM_DOMAINS_LOADED_LINE = "\tTotal " + MRCM_TITLE_PREFIX + " domains loaded: ";

	public static final String REPORT_SUMMARY_TOTAL_MRCM_ATTRIBUTES_LOADED_LINE = "\tTotal " + MRCM_TITLE_PREFIX + " attributes loaded: ";

	public static final String REPORT_SUMMARY_TOTAL_ASSERTIONS_COMPLETED_LINE = "\tTotal assertions completed: ";

	public static final String REPORT_SUMMARY_TOTAL_ASSERTIONS_SKIPPED_LINE = "\tTotal assertions skipped: ";

	public static final String REPORT_SUMMARY_TOTAL_ASSERTIONS_FAILED_LINE = "\tTotal assertions failed: ";

	public static final String TAB = "\t";

	public static final String NEW_LINE = "\n";

	public static final String CONTENT_TYPE_IS_OUT_OF_SCOPE = "Content type is out of scope:";

	public static final String NO_CARDINALITY_CONSTRAINT = "0..*";

	public static final String MANDATORY = "723597001";

	public static final String OPTIONAL = "723598006";

	public static final String MRCM_DOMAIN_REFSET = "723560006";

	public static final String MRCM_ATTRIBUTE_DOMAIN_REFSET = "723561005";

	public static final String MRCM_ATTRIBUTE_RANGE_REFSET = "723562003";

	public static final String LATERALIZABLE_BODY_STRUCTURE_REFSET = "723264001";

	public static final String OWL_AXIOM_REFSET = "733073007";

	public static final String ALL_NEW_PRE_COORDINATED_CONTENT_CONCEPT = "723593002";

	public static final String NO_DATA_TYPE_DEFINED_ASSERTION_UUID = "ebc49f5b-2810-4502-b67f-0f6876d82a3c";

	private Constants() {
	}
}
