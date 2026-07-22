package ru.yandex.practicum.filmorate.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FilmValidationTest {

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

    private Film.FilmBuilder validFilm() {
        return Film.builder()
                .name("Интерстеллар")
                .description("Фильм про космос")
                .releaseDate(LocalDate.of(2014, 11, 6))
                .duration(169);
    }

    private boolean hasViolationOn(Set<ConstraintViolation<Film>> violations, String property) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void validFilm_hasNoViolations() {
        assertThat(validator.validate(validFilm().build())).isEmpty();
    }

    // --- name ---

    @Test
    void name_null_isInvalid() {
        var violations = validator.validate(validFilm().name(null).build());
        assertThat(hasViolationOn(violations, "name")).isTrue();
    }

    @Test
    void name_empty_isInvalid() {
        var violations = validator.validate(validFilm().name("").build());
        assertThat(hasViolationOn(violations, "name")).isTrue();
    }

    @Test
    void name_blank_isInvalid() {
        var violations = validator.validate(validFilm().name("   ").build());
        assertThat(hasViolationOn(violations, "name")).isTrue();
    }

    // --- description (max 200) ---

    @Test
    void description_exactly200_isValid() {
        var violations = validator.validate(validFilm().description("a".repeat(200)).build());
        assertThat(hasViolationOn(violations, "description")).isFalse();
    }

    @Test
    void description_201_isInvalid() {
        var violations = validator.validate(validFilm().description("a".repeat(201)).build());
        assertThat(hasViolationOn(violations, "description")).isTrue();
    }

    @Test
    void description_empty_isValid() {
        var violations = validator.validate(validFilm().description("").build());
        assertThat(hasViolationOn(violations, "description")).isFalse();
    }

    // --- releaseDate (1895-12-28) ---

    @Test
    void releaseDate_cinemaBirthday_isValid() {
        var violations = validator.validate(validFilm().releaseDate(LocalDate.of(1895, 12, 28)).build());
        assertThat(hasViolationOn(violations, "releaseDate")).isFalse();
    }

    @Test
    void releaseDate_dayBeforeCinemaBirthday_isInvalid() {
        var violations = validator.validate(validFilm().releaseDate(LocalDate.of(1895, 12, 27)).build());
        assertThat(hasViolationOn(violations, "releaseDate")).isTrue();
    }

    @Test
    void releaseDate_dayAfterCinemaBirthday_isValid() {
        var violations = validator.validate(validFilm().releaseDate(LocalDate.of(1895, 12, 29)).build());
        assertThat(hasViolationOn(violations, "releaseDate")).isFalse();
    }

    // --- duration (positive) ---

    @Test
    void duration_positive_isValid() {
        assertThat(hasViolationOn(validator.validate(validFilm().duration(1).build()), "duration")).isFalse();
    }

    @Test
    void duration_zero_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validFilm().duration(0).build()), "duration")).isTrue();
    }

    @Test
    void duration_negative_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validFilm().duration(-1).build()), "duration")).isTrue();
    }
}
