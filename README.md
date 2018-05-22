Release MRCM Validator
======================

## Overview

MRCM refsets are part of International Releases since 20170731. The Release MRCM Validator offers services to make sure SNOMED releases are compliant with the MRCM rules published.

## Quick Start
Use Maven to build the executable stand-alone jar and run:
```bash
mvn clean package

java -Xmx4g -jar target/mrcm-validator-*-jar-with-dependencies.jar {release_package_unzipped_root_dir} {is_stated_only} {release_date} {result_dir}
```

### Configuration options

* {release_package_unzipped_root_dir} is for the release package unzipped file root directory. eg: /Users/mchu/Releases/SnomedCT_InternationalRF2_PRODUCTION_20170731T120000Z
* {is_stated_only} is to state whether to use stated relationships only. This parameter is optional and the default value is set to TRUE when not specified.
* {release_date} is the effective date for the release file that is being validated.The format is yyyyMMdd eg:20170731
* {result_dir} is the directory where validation reports will be saved.

### Validation results
The following reports will be listed in {result_dir} folder.

* MrcmSummaryReport.txt
* MrcmValidationReportWithError.txt                 
* MrcmValidationReportWithWarning.txt 
* RCMValidationPassed.txt                          
* MRCMValidationSkipped.txt