package org.snomed.quality.validator.mrcm;

public final class Constants {

	public static final String ERROR_MESSAGE = "Please specify the java arguments after replacing the { } with actual values. {release_package_unzipped_root} {is_stated_only} {release_date} {result_dir}";

	public static final String RELEASE_PACKAGE_UNZIPPED_ROOT_DIR_HELP_MESSAGE = "{release_package_unzipped_root_dir} is for the release package unzipped file root directory. eg: /Users/Releases/SnomedCT_InternationalRF2_PRODUCTION_20170731T120000Z";

	public static final String IS_STATED_ONLY_HELP_MESSAGE = "{is_stated_only} is to state whether to use stated relationships only. This parameter is optional and the default value is set to true.";

	public static final String RELEASE_DATE_HELP_MESSAGE = "{release_date} is the effective date for the release file that is being validated.The format is yyyyMMdd eg:20170731";

	public static final String RESULT_DIR_HELP_MESSAGE = "{result_dir} is the directory where validation reports will be saved.";

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
