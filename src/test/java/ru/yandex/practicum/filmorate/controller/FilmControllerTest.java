package ru.yandex.practicum.filmorate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.FilmService;
import ru.yandex.practicum.filmorate.service.UserService;
import ru.yandex.practicum.filmorate.storage.film.InMemoryFilmStorage;
import ru.yandex.practicum.filmorate.storage.user.InMemoryUserStorage;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilmControllerTest {

    private FilmController controller;
    private UserController userController;

    @BeforeEach
    void setUp() {
        InMemoryUserStorage userStorage = new InMemoryUserStorage();
        controller = new FilmController(new FilmService(new InMemoryFilmStorage(), userStorage));
        userController = new UserController(new UserService(userStorage));
    }

    private Film.FilmBuilder validFilm() {
        return Film.builder()
                .name("Интерстеллар")
                .description("Фильм про космос")
                .releaseDate(LocalDate.of(2014, 11, 6))
                .duration(169);
    }

    private User createUser(String login) {
        return userController.create(User.builder()
                .email(login + "@mail.ru")
                .login(login)
                .name("Имя")
                .birthday(LocalDate.of(1990, 1, 1))
                .build());
    }

    @Test
    void create_assignsIdAndStoresFilm() {
        Film created = controller.create(validFilm().build());

        assertThat(created.getId()).isNotNull();
        assertThat(controller.findAll()).containsExactly(created);
    }

    @Test
    void create_assignsIncrementingIds() {
        Film first = controller.create(validFilm().build());
        Film second = controller.create(validFilm().build());

        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(controller.findAll()).hasSize(2);
    }

    @Test
    void findById_existingFilm_isReturned() {
        Film created = controller.create(validFilm().build());

        assertThat(controller.findById(created.getId())).isEqualTo(created);
    }

    @Test
    void findById_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.findById(999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_existingFilm_isUpdated() {
        Film created = controller.create(validFilm().build());

        Film updated = controller.update(validFilm().id(created.getId()).name("Новое имя").build());

        assertThat(updated.getName()).isEqualTo("Новое имя");
        assertThat(controller.findAll()).containsExactly(updated);
    }

    @Test
    void update_withoutId_throwsValidationException() {
        assertThatThrownBy(() -> controller.update(validFilm().id(null).build()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.update(validFilm().id(999L).build()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_keepsExistingLikes() {
        Film created = controller.create(validFilm().build());
        User user = createUser("liker");
        controller.addLike(created.getId(), user.getId());

        Film updated = controller.update(validFilm().id(created.getId()).name("Новое имя").build());

        assertThat(updated.getLikes()).containsExactly(user.getId());
    }

    @Test
    void addLike_isIdempotent() {
        Film created = controller.create(validFilm().build());
        User user = createUser("liker");

        controller.addLike(created.getId(), user.getId());
        controller.addLike(created.getId(), user.getId());

        assertThat(controller.findById(created.getId()).getLikes()).hasSize(1);
    }

    @Test
    void addLike_unknownUser_throwsNotFoundException() {
        Film created = controller.create(validFilm().build());

        assertThatThrownBy(() -> controller.addLike(created.getId(), 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeLike_dropsLike() {
        Film created = controller.create(validFilm().build());
        User user = createUser("liker");
        controller.addLike(created.getId(), user.getId());

        controller.removeLike(created.getId(), user.getId());

        assertThat(controller.findById(created.getId()).getLikes()).isEmpty();
    }

    @Test
    void getPopular_sortsByLikesCountDescending() {
        Film unpopular = controller.create(validFilm().name("Без лайков").build());
        Film popular = controller.create(validFilm().name("С лайками").build());
        User first = createUser("first");
        User second = createUser("second");

        controller.addLike(popular.getId(), first.getId());
        controller.addLike(popular.getId(), second.getId());
        controller.addLike(unpopular.getId(), first.getId());

        assertThat(controller.getPopular(10)).containsExactly(popular, unpopular);
    }

    @Test
    void getPopular_respectsCount() {
        controller.create(validFilm().build());
        controller.create(validFilm().build());

        assertThat(controller.getPopular(1)).hasSize(1);
    }

    @Test
    void getPopular_nonPositiveCount_throwsValidationException() {
        assertThatThrownBy(() -> controller.getPopular(0))
                .isInstanceOf(ValidationException.class);
    }
}
