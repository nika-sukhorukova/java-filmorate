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

class UserValidationTest {

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

    private User.UserBuilder validUser() {
        return User.builder()
                .email("user@mail.ru")
                .login("user-login")
                .name("Имя")
                .birthday(LocalDate.of(1990, 1, 1));
    }

    private boolean hasViolationOn(Set<ConstraintViolation<User>> violations, String property) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void validUser_hasNoViolations() {
        assertThat(validator.validate(validUser().build())).isEmpty();
    }

    // --- email (not empty, must contain @ and be a valid format) ---

    @Test
    void email_null_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().email(null).build()), "email")).isTrue();
    }

    @Test
    void email_empty_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().email("").build()), "email")).isTrue();
    }

    @Test
    void email_withoutAtSign_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().email("mail.ru").build()), "email")).isTrue();
    }

    @Test
    void email_malformed_isInvalid() {
        // пример из доп. задания ТЗ
        assertThat(hasViolationOn(validator.validate(validUser().email("это-неправильный?эмейл@").build()), "email"))
                .isTrue();
    }

    // --- login (not empty, no spaces) ---

    @Test
    void login_null_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().login(null).build()), "login")).isTrue();
    }

    @Test
    void login_empty_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().login("").build()), "login")).isTrue();
    }

    @Test
    void login_blank_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().login("   ").build()), "login")).isTrue();
    }

    @Test
    void login_withSpaceInside_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().login("log in").build()), "login")).isTrue();
    }

    @Test
    void login_valid_hasNoLoginViolation() {
        assertThat(hasViolationOn(validator.validate(validUser().login("login").build()), "login")).isFalse();
    }

    // --- birthday (not in the future) ---

    @Test
    void birthday_inFuture_isInvalid() {
        assertThat(hasViolationOn(validator.validate(validUser().birthday(LocalDate.now().plusDays(1)).build()),
                "birthday")).isTrue();
    }

    @Test
    void birthday_today_isValid() {
        assertThat(hasViolationOn(validator.validate(validUser().birthday(LocalDate.now()).build()), "birthday"))
                .isFalse();
    }

    @Test
    void birthday_inPast_isValid() {
        assertThat(hasViolationOn(validator.validate(validUser().birthday(LocalDate.of(2000, 5, 20)).build()),
                "birthday")).isFalse();
    }

    // --- name может быть пустым: ограничений на уровне модели нет ---

    @Test
    void emptyName_isValidAtModelLevel() {
        assertThat(hasViolationOn(validator.validate(validUser().name("").build()), "name")).isFalse();
        assertThat(hasViolationOn(validator.validate(validUser().name(null).build()), "name")).isFalse();
    }
}
