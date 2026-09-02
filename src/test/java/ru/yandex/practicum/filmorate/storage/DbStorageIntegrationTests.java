package ru.yandex.practicum.filmorate.storage;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.FilmGenreDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreDbStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты DAO: каждый тест работает с резидентной базой, созданной по schema.sql
 * и заполненной справочниками из data.sql.
 */
@JdbcTest
@AutoConfigureTestDatabase
@Import({UserDbStorage.class, FilmDbStorage.class, GenreDbStorage.class, FilmGenreDbStorage.class,
        MpaDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class DbStorageIntegrationTests {

    private final UserDbStorage userStorage;
    private final FilmDbStorage filmStorage;
    private final GenreDbStorage genreStorage;
    private final FilmGenreDbStorage filmGenreStorage;
    private final MpaDbStorage mpaStorage;
    private final JdbcTemplate jdbcTemplate;

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
                .mpa(Mpa.builder().id(1).name("G").build());
    }

    @Test
    void createUser_assignsIdAndCanBeFoundById() {
        User created = userStorage.create(validUser("first").build());

        Optional<User> found = userStorage.findById(created.getId());

        assertThat(found)
                .isPresent()
                .hasValueSatisfying(user -> {
                    assertThat(user).hasFieldOrPropertyWithValue("id", created.getId());
                    assertThat(user).hasFieldOrPropertyWithValue("login", "first");
                    assertThat(user).hasFieldOrPropertyWithValue("email", "first@mail.ru");
                });
    }

    @Test
    void findUserById_unknownId_isEmpty() {
        assertThat(userStorage.findById(9999L)).isEmpty();
    }

    @Test
    void findAllUsers_returnsCreatedUsers() {
        userStorage.create(validUser("first").build());
        userStorage.create(validUser("second").build());

        assertThat(userStorage.findAll()).hasSize(2);
    }

    @Test
    void updateUser_changesStoredFields() {
        User created = userStorage.create(validUser("first").build());
        created.setName("Новое имя");
        created.setEmail("new@mail.ru");

        userStorage.update(created);

        assertThat(userStorage.findById(created.getId()))
                .get()
                .hasFieldOrPropertyWithValue("name", "Новое имя")
                .hasFieldOrPropertyWithValue("email", "new@mail.ru");
    }

    @Test
    void addFriend_isOneWayAndUnconfirmed() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());

        userStorage.addFriend(first.getId(), second.getId());

        assertThat(userStorage.findFriends(first.getId()))
                .extracting(User::getId)
                .containsExactly(second.getId());
        assertThat(userStorage.findFriends(second.getId())).isEmpty();
        assertThat(statusOf(first.getId(), second.getId())).isEqualTo("UNCONFIRMED");
    }

    @Test
    void addFriend_counterRequest_confirmsBothDirections() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());

        userStorage.addFriend(first.getId(), second.getId());
        userStorage.addFriend(second.getId(), first.getId());

        assertThat(statusOf(first.getId(), second.getId())).isEqualTo("CONFIRMED");
        assertThat(statusOf(second.getId(), first.getId())).isEqualTo("CONFIRMED");
    }

    @Test
    void addFriend_isIdempotent() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());

        userStorage.addFriend(first.getId(), second.getId());
        userStorage.addFriend(first.getId(), second.getId());

        assertThat(userStorage.findFriends(first.getId())).hasSize(1);
    }

    @Test
    void removeFriend_dropsOnlyOneDirection() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());
        userStorage.addFriend(first.getId(), second.getId());
        userStorage.addFriend(second.getId(), first.getId());

        userStorage.removeFriend(first.getId(), second.getId());

        assertThat(userStorage.findFriends(first.getId())).isEmpty();
        assertThat(userStorage.findFriends(second.getId()))
                .extracting(User::getId)
                .containsExactly(first.getId());
        assertThat(statusOf(second.getId(), first.getId())).isEqualTo("UNCONFIRMED");
    }

    @Test
    void findCommonFriends_returnsIntersection() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());
        User common = userStorage.create(validUser("common").build());
        userStorage.addFriend(first.getId(), common.getId());
        userStorage.addFriend(second.getId(), common.getId());
        userStorage.addFriend(first.getId(), second.getId());

        assertThat(userStorage.findCommonFriends(first.getId(), second.getId()))
                .extracting(User::getId)
                .containsExactly(common.getId());
    }

    @Test
    void createFilm_savesFieldsAndMpa() {
        Film created = filmStorage.create(validFilm("Интерстеллар").build());

        assertThat(filmStorage.findById(created.getId()))
                .isPresent()
                .hasValueSatisfying(stored -> {
                    assertThat(stored.getName()).isEqualTo("Интерстеллар");
                    assertThat(stored.getDescription()).isEqualTo("Описание Интерстеллар");
                    assertThat(stored.getDuration()).isEqualTo(169);
                    assertThat(stored.getMpa().getName()).isEqualTo("G");
                });
    }

    @Test
    void saveFilmGenres_storesAndReadsBack() {
        Film created = filmStorage.create(validFilm("С жанрами").build());

        filmGenreStorage.save(created.getId(), List.of(
                Genre.builder().id(1).build(),
                Genre.builder().id(2).build()));

        assertThat(filmGenreStorage.findByFilmId(created.getId()))
                .extracting(Genre::getId)
                .containsExactly(1, 2);
    }

    @Test
    void findFilmGenresByFilmIds_groupsByFilm() {
        Film first = filmStorage.create(validFilm("Первый").build());
        Film second = filmStorage.create(validFilm("Второй").build());
        filmGenreStorage.save(first.getId(), List.of(Genre.builder().id(1).build()));
        filmGenreStorage.save(second.getId(), List.of(
                Genre.builder().id(2).build(),
                Genre.builder().id(6).build()));

        Map<Long, Set<Genre>> genres = filmGenreStorage.findByFilmIds(List.of(first.getId(), second.getId()));

        assertThat(genres.get(first.getId())).extracting(Genre::getId).containsExactly(1);
        assertThat(genres.get(second.getId())).extracting(Genre::getId).containsExactly(2, 6);
    }

    @Test
    void findFilmGenresByFilmIds_emptyInput_returnsEmptyMap() {
        assertThat(filmGenreStorage.findByFilmIds(List.of())).isEmpty();
    }

    @Test
    void findFilmGenresByFilmId_withoutGenres_isEmpty() {
        Film created = filmStorage.create(validFilm("Без жанров").build());

        assertThat(filmGenreStorage.findByFilmId(created.getId())).isEmpty();
    }

    @Test
    void deleteFilmGenres_dropsAllLinks() {
        Film created = filmStorage.create(validFilm("С жанрами").build());
        filmGenreStorage.save(created.getId(), List.of(Genre.builder().id(1).build()));

        filmGenreStorage.deleteByFilmId(created.getId());

        assertThat(filmGenreStorage.findByFilmId(created.getId())).isEmpty();
    }

    @Test
    void findAllFilms_returnsCreatedFilms() {
        filmStorage.create(validFilm("Первый").build());
        filmStorage.create(validFilm("Второй").build());

        assertThat(filmStorage.findAll()).hasSize(2);
    }

    @Test
    void findFilmById_unknownId_isEmpty() {
        assertThat(filmStorage.findById(9999L)).isEmpty();
    }

    @Test
    void updateFilm_changesFieldsAndMpa() {
        Film created = filmStorage.create(validFilm("Старое название").build());

        created.setName("Новое название");
        created.setMpa(Mpa.builder().id(4).build());
        filmStorage.update(created);

        assertThat(filmStorage.findById(created.getId()))
                .isPresent()
                .hasValueSatisfying(stored -> {
                    assertThat(stored.getName()).isEqualTo("Новое название");
                    assertThat(stored.getMpa().getId()).isEqualTo(4);
                });
    }

    @Test
    void addLike_countsTowardsPopularity() {
        Film unpopular = filmStorage.create(validFilm("Без лайков").build());
        Film popular = filmStorage.create(validFilm("С лайками").build());
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());

        filmStorage.addLike(popular.getId(), first.getId());
        filmStorage.addLike(popular.getId(), second.getId());
        filmStorage.addLike(unpopular.getId(), first.getId());

        assertThat(filmStorage.findPopular(10))
                .extracting(Film::getId)
                .containsExactly(popular.getId(), unpopular.getId());
    }

    @Test
    void addLike_isIdempotent() {
        Film film = filmStorage.create(validFilm("Фильм").build());
        User user = userStorage.create(validUser("first").build());

        filmStorage.addLike(film.getId(), user.getId());
        filmStorage.addLike(film.getId(), user.getId());

        assertThat(countLikes(film.getId())).isEqualTo(1);
    }

    @Test
    void removeLike_dropsLike() {
        Film film = filmStorage.create(validFilm("Фильм").build());
        User user = userStorage.create(validUser("first").build());
        filmStorage.addLike(film.getId(), user.getId());

        filmStorage.removeLike(film.getId(), user.getId());

        assertThat(countLikes(film.getId())).isZero();
    }

    @Test
    void findPopular_respectsCount() {
        filmStorage.create(validFilm("Первый").build());
        filmStorage.create(validFilm("Второй").build());

        assertThat(filmStorage.findPopular(1)).hasSize(1);
    }

    @Test
    void findAllGenres_returnsSixGenresOrderedById() {
        Collection<Genre> genres = genreStorage.findAll();

        assertThat(genres).hasSize(6);
        assertThat(genres).extracting(Genre::getId).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(genres).first().extracting(Genre::getName).isEqualTo("Комедия");
    }

    @Test
    void findGenreById_returnsGenre() {
        assertThat(genreStorage.findById(1))
                .get()
                .hasFieldOrPropertyWithValue("name", "Комедия");
    }

    @Test
    void findGenreById_unknownId_isEmpty() {
        assertThat(genreStorage.findById(9999)).isEmpty();
    }

    @Test
    void findGenresByIds_returnsOnlyRequested() {
        assertThat(genreStorage.findAllByIds(Set.of(2, 4)))
                .extracting(Genre::getId)
                .containsExactly(2, 4);
    }

    @Test
    void findGenresByIds_emptySet_returnsEmptyList() {
        assertThat(genreStorage.findAllByIds(Set.of())).isEmpty();
    }

    @Test
    void findAllMpa_returnsFiveRatingsOrderedById() {
        Collection<Mpa> ratings = mpaStorage.findAll();

        assertThat(ratings).hasSize(5);
        assertThat(ratings).extracting(Mpa::getId).containsExactly(1, 2, 3, 4, 5);
        assertThat(ratings).extracting(Mpa::getName).containsExactly("G", "PG", "PG-13", "R", "NC-17");
    }

    @Test
    void findMpaById_returnsRating() {
        assertThat(mpaStorage.findById(3))
                .get()
                .hasFieldOrPropertyWithValue("name", "PG-13");
    }

    @Test
    void findMpaById_unknownId_isEmpty() {
        assertThat(mpaStorage.findById(9999)).isEmpty();
    }

    private String statusOf(Long userId, Long friendId) {
        return jdbcTemplate.queryForObject(
                "SELECT s.name FROM friendships AS f JOIN friendship_statuses AS s ON s.id = f.status_id"
                        + " WHERE f.user_id = ? AND f.friend_id = ?",
                String.class, userId, friendId);
    }

    private Integer countLikes(Long filmId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM film_likes WHERE film_id = ?", Integer.class, filmId);
    }
}
