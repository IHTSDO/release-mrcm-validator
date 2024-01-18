package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import static org.snomed.quality.validator.mrcm.Constants.*;
import static org.snomed.quality.validator.mrcm.ContentType.INFERRED;
import static org.snomed.quality.validator.mrcm.ContentType.STATED;

@SpringBootApplication
public class Application {

	public static void main(final String[] args) throws Exception {
		if (args == null || args.length != 4) {
			System.out.println(ERROR_MESSAGE);
			System.out.println(RELEASE_PACKAGE_UNZIPPED_ROOT_DIR_HELP_MESSAGE);
			System.out.println(CONTENT_TYPE_HELP_MESSAGE);
			System.out.println(RELEASE_DATE_HELP_MESSAGE);
			System.out.println(RESULT_DIR_HELP_MESSAGE);
			throw new IllegalStateException(ERROR_MESSAGE);
		} else {
			final String releaseDate = args[2];
			if (releaseDate != null) {
				validateReleaseDate(releaseDate);
			}
			final File resultDir = new File(args[3]);
			if (!resultDir.exists() && !resultDir.mkdirs()) {
				throw new NotDirectoryException("Result directory '" + resultDir + "' failed to be created automatically.");
			}
			new Application().run(args[0], releaseDate, getContentTypes(args[1]), resultDir);
		}
	}

	private static void validateReleaseDate(String releaseDate) {
		// make sure the release date is in correct format
		try {
			DateFormat formatter = new SimpleDateFormat(RELEASE_DATE_FORMAT);
			formatter.setLenient(false);
			formatter.parse(releaseDate);
		} catch (ParseException e) {
			String msg = String.format("The date format for release date is %s but should be in %s", releaseDate, RELEASE_DATE_FORMAT);
			throw new IllegalArgumentException(msg);
		}
	}

	private static List<ContentType> getContentTypes(String contentTypeArg) {
		if (contentTypeArg == null || contentTypeArg.isEmpty()) {
			// default to run both
			return Arrays.asList(STATED, INFERRED);
		}
		List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList(contentTypeArg.split(",")));
		if (contentTypes.isEmpty()) {
			// default to run both
			return Arrays.asList(STATED, INFERRED);
		}
		return contentTypes;
	}

	private void run(String releasePackage, final String releaseDate, final List<ContentType> contentTypes,
			final File resultDir) throws ReleaseImportException, IOException, ServiceException {
		for (final ContentType contentType : contentTypes) {
			final ValidationService service = new ValidationService();
			final ValidationRun run = new ValidationRun(releaseDate, contentType, true);
			run.setFullSnapshotRelease(true);
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
