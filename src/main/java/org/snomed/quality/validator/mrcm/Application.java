package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.snomed.quality.validator.mrcm.Constants.*;

public class Application {

	private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

	public static void main(final String[] args) throws Exception {
		if (args == null || args.length < 4) {
			LOGGER.info(ERROR_MESSAGE);
			LOGGER.info(RELEASE_PACKAGE_UNZIPPED_ROOT_DIR_HELP_MESSAGE);
			LOGGER.info(IS_STATED_ONLY_HELP_MESSAGE);
			LOGGER.info(RELEASE_DATE_HELP_MESSAGE);
			LOGGER.info(RESULT_DIR_HELP_MESSAGE);
			throw new IllegalStateException(ERROR_MESSAGE);
		} else {
			final String releaseDate = args[2];
			if (releaseDate != null) {
				new SimpleDateFormat("yyyyMMdd").parse(releaseDate);
			} 
			final File resultDir = new File(args[3]);
			if (!resultDir.exists() && !resultDir.mkdirs()) {
				throw new NotDirectoryException("Result directory '" + resultDir + "' failed to be created automatically.");
			}
			new Application().run(args[0], releaseDate, getValidationViews(args), resultDir);
		}
	}

	private static List<ValidationView> getValidationViews(final String[] args) {
		final String validationViews = args[1];
		return validationViews != null && validationViews.length() != 0 ? ValidationView.getValidationViews((validationViews.contains(",") ?
				Arrays.asList(validationViews.split(",")) : Collections.singletonList(validationViews))) :
				Collections.emptyList();
	}

	private void run(String releasePackage, final String releaseDate, final List<ValidationView> validationViews,
			final File resultDir) throws ReleaseImportException, IOException, ServiceException {
		for (final ValidationView validationView : validationViews) {
			final ValidationService service = new ValidationService();
			final ValidationRun run = new ValidationRun(releaseDate, validationView, true);
			if (releasePackage == null) {
				// No external package specified, using default soft link release path.
				releasePackage = "release";
			}
			service.loadMRCM(new File(releasePackage), run);
			service.validateRelease(new File(releasePackage), run);
			new ReportService(resultDir, releasePackage).generateValidationReports(run);
		}
	}
}
