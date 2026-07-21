package ru.yandex.practicum.filmorate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserControllerTest {

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController();
    }

    private User.UserBuilder validUser() {
        return User.builder()
                .email("user@mail.ru")
                .login("user-login")
                .name("Имя")
                .birthday(LocalDate.of(1990, 1, 1));
    }

    @Test
    void create_assignsIdAndStoresUser() {
        User created = controller.create(validUser().build());

        assertThat(created.getId()).isNotNull();
        assertThat(controller.findAll()).containsExactly(created);
    }

    @Test
    void create_blankName_usesLogin() {
        User created = controller.create(validUser().name("  ").build());

        assertThat(created.getName()).isEqualTo("user-login");
    }

    @Test
    void create_nullName_usesLogin() {
        User created = controller.create(validUser().name(null).build());

        assertThat(created.getName()).isEqualTo("user-login");
    }

    @Test
    void create_withName_keepsName() {
        User created = controller.create(validUser().name("Пётр").build());

        assertThat(created.getName()).isEqualTo("Пётр");
    }

    @Test
    void update_existingUser_isUpdated() {
        User created = controller.create(validUser().build());

        User updated = controller.update(validUser().id(created.getId()).email("new@mail.ru").build());

        assertThat(updated.getEmail()).isEqualTo("new@mail.ru");
        assertThat(controller.findAll()).containsExactly(updated);
    }

    @Test
    void update_blankName_usesLogin() {
        User created = controller.create(validUser().build());

        User updated = controller.update(validUser().id(created.getId()).name("").login("new-login").build());

        assertThat(updated.getName()).isEqualTo("new-login");
    }

    @Test
    void update_withoutId_throwsValidationException() {
        assertThatThrownBy(() -> controller.update(validUser().id(null).build()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.update(validUser().id(999L).build()))
                .isInstanceOf(NotFoundException.class);
    }
}
