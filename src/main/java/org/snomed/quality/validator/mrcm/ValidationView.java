package org.snomed.quality.validator.mrcm;

import java.util.*;

public enum ValidationView {

	STATED("Stated"),
	INFERRED("Inferred");

	private final String view;

	ValidationView(final String view) {
		this.view = view;
	}

	public final String getView() {
		return view;
	}

	/**
	 * Returns the validation views which are enabled.
	 *
	 * @param views that are enabled before the underlying
	 *              application runs.
	 * @return {@code Map} of {@code ValidationView}s which
	 * are enabled.
	 */
	public static List<ValidationView> getValidationViews(final List<String> views) {
		final List<ValidationView> validationViews = new ArrayList<>();
		views.forEach(view -> Arrays.stream(ValidationView.values()).filter(validationView -> validationView.getView().equalsIgnoreCase(view)).forEach(validationViews::add));
		return validationViews;
	}
}
