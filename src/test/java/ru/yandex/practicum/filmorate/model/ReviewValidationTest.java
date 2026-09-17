package ru.yandex.practicum.filmorate.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Review.ReviewBuilder validReview() {
        return Review.builder()
                .content("Отличный фильм")
                .isPositive(true)
                .userId(1L)
                .filmId(1L);
    }

    private boolean hasViolationOn(Set<ConstraintViolation<Review>> violations, String property) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void validReview_hasNoViolations() {
        assertThat(validator.validate(validReview().build())).isEmpty();
    }

    // --- content ---

    @Test
    void content_null_isInvalid() {
        var violations = validator.validate(validReview().content(null).build());
        assertThat(hasViolationOn(violations, "content")).isTrue();
    }

    @Test
    void content_empty_isInvalid() {
        var violations = validator.validate(validReview().content("").build());
        assertThat(hasViolationOn(violations, "content")).isTrue();
    }

    @Test
    void content_blank_isInvalid() {
        var violations = validator.validate(validReview().content("   ").build());
        assertThat(hasViolationOn(violations, "content")).isTrue();
    }

    // --- isPositive ---

    @Test
    void isPositive_null_isInvalid() {
        var violations = validator.validate(validReview().isPositive(null).build());
        assertThat(hasViolationOn(violations, "isPositive")).isTrue();
    }

    // --- userId ---

    @Test
    void userId_null_isInvalid() {
        var violations = validator.validate(validReview().userId(null).build());
        assertThat(hasViolationOn(violations, "userId")).isTrue();
    }

    // --- filmId ---

    @Test
    void filmId_null_isInvalid() {
        var violations = validator.validate(validReview().filmId(null).build());
        assertThat(hasViolationOn(violations, "filmId")).isTrue();
    }
}
