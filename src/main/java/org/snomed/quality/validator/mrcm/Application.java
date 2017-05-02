package org.snomed.quality.validator.mrcm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.sqs.service.exception.ServiceException;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class Application {

	public static void main(String[] args) throws ReleaseImportException, IOException, ServiceException {
		new Application().run();
	}

	private void run() throws ReleaseImportException, IOException, ServiceException {
		ValidationService service = new ValidationService();

		ValidationRun run = new ValidationRun();
		service.loadMRCM(new File("src/main/resources/mrcm-rf2"), run);

		service.validateRelease(new File("release"), run);

		Set<Assertion> failedAssertions = run.getFailedAssertions();
		for (Assertion failedAssertion : failedAssertions) {
			System.out.println("Assertion failed: " + failedAssertion);
		}
		if (failedAssertions.isEmpty()) {
			System.out.println("No failed assertions");
		}
	}

}
