package org.snomed.quality.validator.mrcm;

import java.util.*;

public enum ContentType {

	STATED("stated"),
	INFERRED("inferred");

	private final String type;

	ContentType(final String type) {
		this.type = type;
	}

	public final String getType() {
		return type;
	}

	/**
	 * Returns the {@code ContentType}s which are enabled.
	 *
	 * @param contentTypesArgs that are enabled before the underlying
	 *                         application runs.
	 * @return {@code List} of {@code ContentType}s which are enabled.
	 */
	public static List<ContentType> getContentTypes(final List<String> contentTypesArgs) {
		final List<ContentType> contentTypes = new ArrayList<>();
		contentTypesArgs.forEach(contentTypesArg -> Arrays.stream(ContentType.values()).filter(contentType ->
				contentType.getType().equalsIgnoreCase(contentTypesArg)).forEach(contentTypes::add));
		return contentTypes;
	}
}
