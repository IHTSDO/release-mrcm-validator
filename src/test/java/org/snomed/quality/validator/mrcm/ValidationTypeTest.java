package org.snomed.quality.validator.mrcm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ValidationTypeTest {

	@Test
	public void testAttributeDomainValidationType() {
		assertEquals("Attribute domain", ValidationType.ATTRIBUTE_DOMAIN.getName());
	}

	@Test
	public void testAttributeRangeValidationType() {
		assertEquals("Attribute range", ValidationType.ATTRIBUTE_RANGE.getName());
	}

	@Test
	public void testAttributeCardinalityValidationType() {
		assertEquals("Attribute cardinality", ValidationType.ATTRIBUTE_CARDINALITY.getName());
	}

	@Test
	public void testAttributeInGroupCardinalityValidationType() {
		assertEquals("Attribute in group cardinality", ValidationType.ATTRIBUTE_IN_GROUP_CARDINALITY.getName());
	}

	@Test
	public void testConcreteAttributeDataTypeValidationType() {
		assertEquals("Concrete attribute data type", ValidationType.CONCRETE_ATTRIBUTE_DATA_TYPE.getName());
	}
}
