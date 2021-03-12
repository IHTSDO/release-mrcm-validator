package org.snomed.quality.validator.mrcm;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ContentTypeTest {

	@Test
	public void testStatedValidationView() {
		assertEquals("Stated", ContentType.STATED.getType());
	}

	@Test
	public void testInferredValidationView() {
		assertEquals("Inferred", ContentType.INFERRED.getType());
	}

	@Test
	public void testGetValidationViewsReturnsResultsWhenOneArgumentIsValid() {
		final List<ContentType> contentTypes = ContentType.getContentTypes(Collections.singletonList("Stated"));
		assertFalse(contentTypes.isEmpty());
		assertTrue(contentTypes.contains(ContentType.STATED));
	}

	@Test
	public void testGetValidationViewsReturnsResultsWhenMultipleArgumentsAreValid() {
		final List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList("Stated", "Inferred"));
		assertFalse(contentTypes.isEmpty());
		assertTrue(contentTypes.containsAll(Arrays.asList(ContentType.STATED, ContentType.INFERRED)));
	}

	@Test
	public void testGetValidationViewsReturnsNoResultsWhenArgumentsAreInvalid() {
		final List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList("Test1", "Test2"));
		assertTrue(contentTypes.isEmpty());
	}

	@Test
	public void testGetValidationViewsReturnsNoResultsWhenArgumentsAreNull() {
		final List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList(null, null));
		assertTrue(contentTypes.isEmpty());
	}
}
