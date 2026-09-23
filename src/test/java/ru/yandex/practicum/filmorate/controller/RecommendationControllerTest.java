package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.EventService;
import ru.yandex.practicum.filmorate.service.FilmService;
import ru.yandex.practicum.filmorate.service.RecommendationService;
import ru.yandex.practicum.filmorate.storage.director.DirectorDbStorage;
import ru.yandex.practicum.filmorate.storage.event.EventDbStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.FilmGenreDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreDbStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase
@Import({
        FilmDbStorage.class,
        UserDbStorage.class,
        GenreDbStorage.class,
        FilmGenreDbStorage.class,
        MpaDbStorage.class,
        DirectorDbStorage.class,
        EventDbStorage.class
})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class RecommendationControllerTest {

    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;
    private final GenreDbStorage genreStorage;
    private final FilmGenreDbStorage filmGenreStorage;
    private final MpaDbStorage mpaStorage;
    private final DirectorDbStorage directorStorage;
    private final EventDbStorage eventStorage;

    private RecommendationController controller;

    @BeforeEach
    void setUp() {
        EventService eventService = new EventService(eventStorage, userStorage);

        FilmService filmService = new FilmService(
                filmStorage,
                userStorage,
                genreStorage,
                filmGenreStorage,
                mpaStorage,
                directorStorage,
                eventService
        );

        controller = new RecommendationController(
                new RecommendationService(userStorage, filmService)
        );
    }

    @Test
    void getRecommendations_returnsRecommendedFilmsWithDetails() {
        User target = userStorage.create(validUser("target").build());
        User similar = userStorage.create(validUser("similar").build());

        Film common = filmStorage.create(validFilm("Общий").build());
        Film recommendation = filmStorage.create(validFilm("Рекомендация").build());

        Director director = directorStorage.create(
                Director.builder()
                        .name("Нолан")
                        .build()
        );

        filmGenreStorage.save(
                recommendation.getId(),
                Set.of(Genre.builder().id(1).build())
        );

        directorStorage.saveFilmDirectors(
                recommendation.getId(),
                Set.of(director)
        );

        filmStorage.addLike(common.getId(), target.getId());

        filmStorage.addLike(common.getId(), similar.getId());
        filmStorage.addLike(recommendation.getId(), similar.getId());

        Collection<Film> recommendations =
                controller.getRecommendations(target.getId());

        assertThat(recommendations).hasSize(1);

        Film result = recommendations.iterator().next();

        assertThat(result.getId()).isEqualTo(recommendation.getId());
        assertThat(result.getGenres()).isNotEmpty();
        assertThat(result.getDirectors()).isNotEmpty();
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
