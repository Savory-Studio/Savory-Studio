package com.apg.promociones.apg_api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromocionRequestTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void rejectsMissingRestaurant() {
        PromocionRequest request = validRequest();
        request.setRestaurantId(null);

        assertEquals("restaurantId", validator.validate(request).iterator().next().getPropertyPath().toString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankRequiredText(String value) {
        PromocionRequest request = validRequest();
        request.setName(value);
        request.setApplicableProducts(value);
        request.setType(value);

        assertEquals(3, validator.validate(request).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsLimitsBelowOne(int value) {
        PromocionRequest request = validRequest();
        request.setMaxUses(value);
        request.setMaxUseAmountPerUser(value);

        assertEquals(2, validator.validate(request).size());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1, 100})
    void acceptsOptionalOrPositiveLimits(Integer value) {
        PromocionRequest request = validRequest();
        request.setMaxUses(value);
        request.setMaxUseAmountPerUser(value);

        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsEndAtOrBeforeStart(int hoursAfterStart) {
        PromocionRequest request = validRequest();
        request.setEndTimestamp(START.plusHours(hoursAfterStart));

        var violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals("La fecha de inicio debe ser anterior a la fecha de vencimiento.",
                violations.iterator().next().getMessage());
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void acceptsOrderedOrOmittedDates(boolean includeStart, boolean includeEnd) {
        PromocionRequest request = validRequest();
        request.setStartTimestamp(includeStart ? START : null);
        request.setEndTimestamp(includeEnd ? START.plusHours(1) : null);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void doesNotExposeDateValidationAsJsonField() {
        var json = JsonMapper.builder().build().valueToTree(validRequest());

        assertFalse(json.has("dateRangeValid"));
        assertTrue(json.has("startTimestamp"));
        assertTrue(json.has("endTimestamp"));
    }

    private PromocionRequest validRequest() {
        PromocionRequest request = new PromocionRequest();
        request.setRestaurantId(1);
        request.setName("Lunch promotion");
        request.setApplicableProducts("Lunch menu");
        request.setType("PERCENTAGE");
        request.setStartTimestamp(START);
        request.setEndTimestamp(START.plusHours(1));
        return request;
    }
}
