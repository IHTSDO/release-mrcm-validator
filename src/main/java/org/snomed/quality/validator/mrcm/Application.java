package org.snomed.quality.validator.mrcm;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

	private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
	
	public static void main(String[] args) throws Exception {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		String releasePackage = null;
		String releaseDate = null;
		File resultDir = null;
		boolean isStatedViewOnly = true;
		if ( args == null || args.length < 4) {
			String msg = "Please specify the java arguments after replacing the {} with actual values." + "{release_package_unzipped_root} {is_stated_only} {release_date} {result_dir}";
			System.out.println(msg);
			System.out.println("{release_package_unzipped_root_dir} is for the release package unzipped file root directory. eg: /Users/Releases/SnomedCT_InternationalRF2_PRODUCTION_20170731T120000Z");
			System.out.println("{is_stated_only} is to state whether to use stated relationships only. This parameter is optional and the default value is set to true.");
			System.out.println("{release_date} is the effective date for the release file that is being validated.The format is yyyyMMdd eg:20170731");
			System.out.println("{result_dir} is the directory where validation reports will be saved.");
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

	private void run(String releasePackage, String releaseDate, boolean isStatedViewOnly, File resultDir) throws ReleaseImportException, IOException, ServiceException {
		ValidationService service = new ValidationService();
		ValidationRun run = new ValidationRun(releaseDate, isStatedViewOnly, true);
		if (releasePackage == null) {
			//no external package specified using default soft link release path.
			releasePackage = "release";
			releaseDate = "20180131";
		}
		service.loadMRCM(new File(releasePackage), run);
		service.validateRelease(new File(releasePackage), run);
		ReportService reportService = new ReportService(resultDir, releasePackage);
		reportService.generateValidationReports(run);
	}
}
