package org.snomed.quality.validator.mrcm;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.snomed.quality.validator.mrcm.ContentType.INFERRED;

public class ContentTypeTest {

	@Test
	public void testStatedContentType() {
		assertEquals("stated", ContentType.STATED.getType());
	}

	@Test
	public void testInferredContentType() {
		assertEquals("inferred", INFERRED.getType());
	}

	@Test
	public void testGetContentTypeReturnsResultsWhenOneArgumentIsValid() {
		List<ContentType> contentTypes = ContentType.getContentTypes(Collections.singletonList("Stated"));
		assertFalse(contentTypes.isEmpty());
		assertTrue(contentTypes.contains(ContentType.STATED));

		contentTypes = ContentType.getContentTypes(Collections.singletonList("stated"));
		assertFalse(contentTypes.isEmpty());
		assertTrue(contentTypes.contains(ContentType.STATED));
	}

	@Test
	public void testReturnsResultsWhenMultipleArgumentsAreValid() {
		List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList("Stated", "Inferred"));
		assertFalse(contentTypes.isEmpty());
		assertTrue(contentTypes.containsAll(Arrays.asList(ContentType.STATED, INFERRED)));

		contentTypes = ContentType.getContentTypes(Arrays.asList("stated", "inferred"));
		assertFalse(contentTypes.isEmpty());
		assertTrue(contentTypes.containsAll(Arrays.asList(ContentType.STATED, INFERRED)));
	}

	@Test
	public void testReturnsNoResultsWhenArgumentsAreInvalid() {
		final List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList("Test1", "Test2"));
		assertTrue(contentTypes.isEmpty());
	}

	@Test
	public void testReturnsNoResultsWhenArgumentsAreNull() {
		final List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList(null, null));
		assertTrue(contentTypes.isEmpty());
	}

	@Test
	public void testReturnOneValidResult() {
		final List<ContentType> contentTypes = ContentType.getContentTypes(Arrays.asList(null, "inferred"));
		assertFalse(contentTypes.isEmpty());
		assertEquals(1, contentTypes.size());
		assertEquals(INFERRED, contentTypes.get(0));
	}
}
