package ru.yandex.practicum.filmorate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.UserService;
import ru.yandex.practicum.filmorate.storage.user.InMemoryUserStorage;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserControllerTest {

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(new UserService(new InMemoryUserStorage()));
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
    void findById_existingUser_isReturned() {
        User created = controller.create(validUser().build());

        assertThat(controller.findById(created.getId())).isEqualTo(created);
    }

    @Test
    void findById_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.findById(999L))
                .isInstanceOf(NotFoundException.class);
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

    @Test
    void update_keepsExistingFriends() {
        User first = controller.create(validUser().build());
        User second = controller.create(validUser().email("other@mail.ru").login("other").build());
        controller.addFriend(first.getId(), second.getId());

        User updated = controller.update(validUser().id(first.getId()).email("new@mail.ru").build());

        assertThat(updated.getFriends()).containsOnlyKeys(second.getId());
    }

    @Test
    void addFriend_isMutual() {
        User first = controller.create(validUser().build());
        User second = controller.create(validUser().email("other@mail.ru").login("other").build());

        controller.addFriend(first.getId(), second.getId());

        assertThat(controller.getFriends(first.getId())).containsExactly(second);
        assertThat(controller.getFriends(second.getId())).containsExactly(first);
    }

    @Test
    void addFriend_isIdempotent() {
        User first = controller.create(validUser().build());
        User second = controller.create(validUser().email("other@mail.ru").login("other").build());

        controller.addFriend(first.getId(), second.getId());
        controller.addFriend(first.getId(), second.getId());

        assertThat(controller.getFriends(first.getId())).hasSize(1);
    }

    @Test
    void addFriend_self_throwsValidationException() {
        User created = controller.create(validUser().build());

        assertThatThrownBy(() -> controller.addFriend(created.getId(), created.getId()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void addFriend_unknownUser_throwsNotFoundException() {
        User created = controller.create(validUser().build());

        assertThatThrownBy(() -> controller.addFriend(created.getId(), 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeFriend_isMutual() {
        User first = controller.create(validUser().build());
        User second = controller.create(validUser().email("other@mail.ru").login("other").build());
        controller.addFriend(first.getId(), second.getId());

        controller.removeFriend(first.getId(), second.getId());

        assertThat(controller.getFriends(first.getId())).isEmpty();
        assertThat(controller.getFriends(second.getId())).isEmpty();
    }

    @Test
    void getFriends_withoutFriends_isEmpty() {
        User created = controller.create(validUser().build());

        assertThat(controller.getFriends(created.getId())).isEmpty();
    }

    @Test
    void getCommonFriends_returnsIntersection() {
        User first = controller.create(validUser().build());
        User second = controller.create(validUser().email("second@mail.ru").login("second").build());
        User common = controller.create(validUser().email("common@mail.ru").login("common").build());

        controller.addFriend(first.getId(), common.getId());
        controller.addFriend(second.getId(), common.getId());

        assertThat(controller.getCommonFriends(first.getId(), second.getId())).containsExactly(common);
    }

    @Test
    void getCommonFriends_withoutIntersection_isEmpty() {
        User first = controller.create(validUser().build());
        User second = controller.create(validUser().email("second@mail.ru").login("second").build());

        assertThat(controller.getCommonFriends(first.getId(), second.getId())).isEmpty();
    }
}
