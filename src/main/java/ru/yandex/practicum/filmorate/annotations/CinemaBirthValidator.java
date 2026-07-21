package ru.yandex.practicum.filmorate.annotations;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class CinemaBirthValidator implements ConstraintValidator<IsAfterCinemaBirth, LocalDate> {
    private static final LocalDate CINEMA_BIRTH_DATE = LocalDate.of(1895, 12, 28);

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Пусть @NotNull проверяет отсутствие данных, если это нужно
        }
        return !value.isBefore(CINEMA_BIRTH_DATE);
    }
}
