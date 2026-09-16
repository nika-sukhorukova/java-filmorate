package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.FilmService;
import ru.yandex.practicum.filmorate.service.UserService;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.FilmGenreDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreDbStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты контроллера через реальные DB-хранилища: каждый тест работает с резидентной базой,
 * созданной по schema.sql и заполненной справочниками из data.sql, и откатывается после теста.
 */
@JdbcTest
@AutoConfigureTestDatabase
@Import({FilmDbStorage.class, UserDbStorage.class, GenreDbStorage.class, FilmGenreDbStorage.class,
        MpaDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class FilmControllerTest {

    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;
    private final GenreDbStorage genreStorage;
    private final FilmGenreDbStorage filmGenreStorage;
    private final MpaDbStorage mpaStorage;

    private FilmController controller;
    private UserController userController;

    @BeforeEach
    void setUp() {
        controller = new FilmController(
                new FilmService(filmStorage, userStorage, genreStorage, filmGenreStorage, mpaStorage));
        userController = new UserController(new UserService(userStorage));
    }

    private Film.FilmBuilder validFilm() {
        return Film.builder()
                .name("Интерстеллар")
                .description("Фильм про космос")
                .releaseDate(LocalDate.of(2014, 11, 6))
                .duration(169)
                .mpa(Mpa.builder().id(1).build());
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
        Film liked = controller.create(validFilm().name("С лайком").build());
        Film withoutLikes = controller.create(validFilm().name("Без лайков").build());
        User user = createUser("liker");
        controller.addLike(liked.getId(), user.getId());

        controller.update(validFilm().id(liked.getId()).name("Новое имя").build());

        assertThat(controller.getPopular(10)).first()
                .extracting(Film::getId)
                .isEqualTo(liked.getId());
        assertThat(controller.getPopular(10)).last()
                .extracting(Film::getId)
                .isEqualTo(withoutLikes.getId());
    }

    @Test
    void addLike_isIdempotent() {
        Film twiceLikedBySameUser = controller.create(validFilm().name("Один лайкнул дважды").build());
        Film likedByTwoUsers = controller.create(validFilm().name("Двое лайкнули").build());
        User first = createUser("first");
        User second = createUser("second");

        controller.addLike(twiceLikedBySameUser.getId(), first.getId());
        controller.addLike(twiceLikedBySameUser.getId(), first.getId());
        controller.addLike(likedByTwoUsers.getId(), first.getId());
        controller.addLike(likedByTwoUsers.getId(), second.getId());

        assertThat(controller.getPopular(10)).first()
                .extracting(Film::getId)
                .isEqualTo(likedByTwoUsers.getId());
    }

    @Test
    void addLike_unknownUser_throwsNotFoundException() {
        Film created = controller.create(validFilm().build());

        assertThatThrownBy(() -> controller.addLike(created.getId(), 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeLike_dropsLike() {
        Film unliked = controller.create(validFilm().name("Лайк снят").build());
        Film liked = controller.create(validFilm().name("Лайк остался").build());
        User user = createUser("liker");
        controller.addLike(unliked.getId(), user.getId());
        controller.addLike(liked.getId(), user.getId());

        controller.removeLike(unliked.getId(), user.getId());

        assertThat(controller.getPopular(10)).first()
                .extracting(Film::getId)
                .isEqualTo(liked.getId());
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
