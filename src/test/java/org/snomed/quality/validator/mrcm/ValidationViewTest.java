package org.snomed.quality.validator.mrcm;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ValidationViewTest {

	@Test
	public void testStatedValidationView() {
		assertEquals("Stated", ValidationView.STATED.getView());
	}

	@Test
	public void testInferredValidationView() {
		assertEquals("Inferred", ValidationView.INFERRED.getView());
	}

	@Test
	public void testGetValidationViewsReturnsResultsWhenOneArgumentIsValid() {
		final List<ValidationView> validationViews = ValidationView.getValidationViews(Collections.singletonList("Stated"));
		assertFalse(validationViews.isEmpty());
		assertTrue(validationViews.contains(ValidationView.STATED));
	}

	@Test
	public void testGetValidationViewsReturnsResultsWhenMultipleArgumentsAreValid() {
		final List<ValidationView> validationViews = ValidationView.getValidationViews(Arrays.asList("Stated", "Inferred"));
		assertFalse(validationViews.isEmpty());
		assertTrue(validationViews.containsAll(Arrays.asList(ValidationView.STATED, ValidationView.INFERRED)));
	}

	@Test
	public void testGetValidationViewsReturnsNoResultsWhenArgumentsAreInvalid() {
		final List<ValidationView> validationViews = ValidationView.getValidationViews(Arrays.asList("Test1", "Test2"));
		assertTrue(validationViews.isEmpty());
	}

	@Test
	public void testGetValidationViewsReturnsNoResultsWhenArgumentsAreNull() {
		final List<ValidationView> validationViews = ValidationView.getValidationViews(Arrays.asList(null, null));
		assertTrue(validationViews.isEmpty());
	}
}
