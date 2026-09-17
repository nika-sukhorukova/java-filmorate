package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.RecommendationService;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.FilmGenreDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreDbStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase
@Import({FilmDbStorage.class, UserDbStorage.class, GenreDbStorage.class,
        FilmGenreDbStorage.class, MpaDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class RecommendationControllerTest {

    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;

    private RecommendationController controller;

    @BeforeEach
    void setUp() {
        controller = new RecommendationController(
                new RecommendationService(userStorage, filmStorage)
        );
    }

    @Test
    void getRecommendations_returnsRecommendedFilms() {
        User target = userStorage.create(validUser("target").build());
        User similar = userStorage.create(validUser("similar").build());

        Film common = filmStorage.create(validFilm("Общий").build());
        Film recommendation = filmStorage.create(validFilm("Рекомендация").build());

        filmStorage.addLike(common.getId(), target.getId());

        filmStorage.addLike(common.getId(), similar.getId());
        filmStorage.addLike(recommendation.getId(), similar.getId());

        assertThat(controller.getRecommendations(target.getId()))
                .extracting(Film::getId)
                .containsExactly(recommendation.getId());
    }

    @Test
    void getRecommendations_unknownUser_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.getRecommendations(999L))
                .isInstanceOf(NotFoundException.class);
    }

    private User.UserBuilder validUser(String login) {
        return User.builder()
                .email(login + "@mail.ru")
                .login(login)
                .name("Имя " + login)
                .birthday(LocalDate.of(1990, 1, 1));
    }

    private Film.FilmBuilder validFilm(String name) {
        return Film.builder()
                .name(name)
                .description("Описание " + name)
                .releaseDate(LocalDate.of(2014, 11, 6))
                .duration(169)
                .mpa(Mpa.builder().id(1).build());
    }
}
