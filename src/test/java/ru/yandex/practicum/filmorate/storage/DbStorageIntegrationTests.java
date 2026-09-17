package ru.yandex.practicum.filmorate.storage;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Event;
import ru.yandex.practicum.filmorate.model.EventOperation;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.director.DirectorDbStorage;
import ru.yandex.practicum.filmorate.storage.event.EventDbStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.FilmGenreDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreDbStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaDbStorage;
import ru.yandex.practicum.filmorate.storage.review.ReviewDbStorage;
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
        MpaDbStorage.class, ReviewDbStorage.class, DirectorDbStorage.class, EventDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class DbStorageIntegrationTests {

    private final UserDbStorage userStorage;
    private final FilmDbStorage filmStorage;
    private final GenreDbStorage genreStorage;
    private final FilmGenreDbStorage filmGenreStorage;
    private final MpaDbStorage mpaStorage;
    private final ReviewDbStorage reviewStorage;
    private final DirectorDbStorage directorStorage;
    private final EventDbStorage eventStorage;
    private final JdbcTemplate jdbcTemplate;

    private User.UserBuilder validUser(String login) {
        return User.builder()
                .email(login + "@mail.ru")
                .login(login)
                .name("Имя " + login)
                .birthday(LocalDate.of(1990, 1, 1));
    }

    private Review.ReviewBuilder validReview(Long userId, Long filmId) {
        return Review.builder()
                .content("Отличный фильм")
                .isPositive(true)
                .userId(userId)
                .filmId(filmId);
    }

    private Film.FilmBuilder validFilm(String name) {
        return Film.builder()
                .name(name)
                .description("Описание " + name)
                .releaseDate(LocalDate.of(2014, 11, 6))
                .duration(169)
                .mpa(Mpa.builder().id(1).name("G").build());
    }

    private Director.DirectorBuilder validDirector(String name) {
        return Director.builder().name(name);
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

        assertThat(filmStorage.findPopular(10, null, null))
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

        assertThat(filmStorage.findPopular(1, null, null)).hasSize(1);
    }

    @Test
    void findPopular_filtersByGenre() {
        Film comedy = filmStorage.create(validFilm("Комедия").build());
        Film drama = filmStorage.create(validFilm("Драма").build());
        filmGenreStorage.save(comedy.getId(), List.of(Genre.builder().id(1).build()));
        filmGenreStorage.save(drama.getId(), List.of(Genre.builder().id(2).build()));

        assertThat(filmStorage.findPopular(10, 1, null))
                .extracting(Film::getId)
                .containsExactly(comedy.getId());
    }

    @Test
    void findPopular_filtersByYear() {
        filmStorage.create(validFilm("Старый").releaseDate(LocalDate.of(1999, 1, 1)).build());
        Film recent = filmStorage.create(validFilm("Новый").releaseDate(LocalDate.of(2020, 1, 1)).build());

        assertThat(filmStorage.findPopular(10, null, 2020))
                .extracting(Film::getId)
                .containsExactly(recent.getId());
    }

    @Test
    void findPopular_filtersByGenreAndYear() {
        Film moreLiked = filmStorage.create(validFilm("Более популярный").releaseDate(LocalDate.of(2020, 1, 1)).build());
        Film lessLiked = filmStorage.create(validFilm("Менее популярный").releaseDate(LocalDate.of(2020, 1, 1)).build());
        Film wrongYear = filmStorage.create(validFilm("Другой год").releaseDate(LocalDate.of(2010, 1, 1)).build());
        filmGenreStorage.save(moreLiked.getId(), List.of(Genre.builder().id(3).build()));
        filmGenreStorage.save(lessLiked.getId(), List.of(Genre.builder().id(3).build()));
        filmGenreStorage.save(wrongYear.getId(), List.of(Genre.builder().id(3).build()));
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());
        filmStorage.addLike(moreLiked.getId(), first.getId());
        filmStorage.addLike(moreLiked.getId(), second.getId());
        filmStorage.addLike(lessLiked.getId(), first.getId());

        assertThat(filmStorage.findPopular(10, 3, 2020))
                .extracting(Film::getId)
                .containsExactly(moreLiked.getId(), lessLiked.getId());
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

    @Test
    void findRecommendations_returnsFilmsLikedByMostSimilarUser() {
        User target = userStorage.create(validUser("target").build());
        User similar = userStorage.create(validUser("similar").build());
        User other = userStorage.create(validUser("other").build());

        Film first = filmStorage.create(validFilm("Первый").build());
        Film second = filmStorage.create(validFilm("Второй").build());
        Film recommendation = filmStorage.create(validFilm("Рекомендация").build());
        Film otherFilm = filmStorage.create(validFilm("Другой").build());

        addLikes(target, first, second);
        addLikes(similar, first, second, recommendation);
        addLikes(other, first, otherFilm);

        assertThat(filmStorage.findRecommendations(target.getId()))
                .extracting(Film::getId)
                .containsExactly(recommendation.getId());
    }

    @Test
    void findRecommendations_equalSimilarity_returnsFilmsFromAllSimilarUsers() {
        User target = userStorage.create(validUser("target").build());
        User firstSimilar = userStorage.create(validUser("firstSimilar").build());
        User secondSimilar = userStorage.create(validUser("secondSimilar").build());

        Film common = filmStorage.create(validFilm("Общий").build());
        Film firstRecommendation = filmStorage.create(validFilm("Первая рекомендация").build());
        Film secondRecommendation = filmStorage.create(validFilm("Вторая рекомендация").build());

        addLikes(target, common);
        addLikes(firstSimilar, common, firstRecommendation);
        addLikes(secondSimilar, common, secondRecommendation);

        assertThat(filmStorage.findRecommendations(target.getId()))
                .extracting(Film::getId)
                .containsExactly(firstRecommendation.getId(), secondRecommendation.getId());
    }

    @Test
    void findRecommendations_withoutLikes_returnsEmptyCollection() {
        User user = userStorage.create(validUser("target").build());

        assertThat(filmStorage.findRecommendations(user.getId())).isEmpty();
    }

    @Test
    void findRecommendations_withoutCommonLikes_returnsEmptyCollection() {
        User target = userStorage.create(validUser("target").build());
        User other = userStorage.create(validUser("other").build());

        Film targetFilm = filmStorage.create(validFilm("Фильм target").build());
        Film otherFilm = filmStorage.create(validFilm("Фильм другого").build());

        filmStorage.addLike(targetFilm.getId(), target.getId());
        filmStorage.addLike(otherFilm.getId(), other.getId());

        assertThat(filmStorage.findRecommendations(target.getId())).isEmpty();
    }

    @Test
    void createDirector_assignsIdAndCanBeFound() {
        Director created = directorStorage.create(validDirector("Нолан").build());

        assertThat(directorStorage.findById(created.getId()))
                .isPresent()
                .hasValueSatisfying(d -> assertThat(d.getName()).isEqualTo("Нолан"));
    }

    @Test
    void findDirectorById_unknownId_isEmpty() {
        assertThat(directorStorage.findById(9999L)).isEmpty();
    }

    @Test
    void findAllDirectors_orderedById() {
        directorStorage.create(validDirector("Нолан").build());
        directorStorage.create(validDirector("Тарантино").build());

        assertThat(directorStorage.findAll())
                .extracting(Director::getName)
                .containsExactly("Нолан", "Тарантино");
    }

    @Test
    void updateDirector_changesName() {
        Director created = directorStorage.create(validDirector("Нолан").build());
        created.setName("Кристофер Нолан");

        directorStorage.update(created);

        assertThat(directorStorage.findById(created.getId()))
                .get()
                .hasFieldOrPropertyWithValue("name", "Кристофер Нолан");
    }

    @Test
    void deleteDirector_removesIt() {
        Director created = directorStorage.create(validDirector("Нолан").build());

        directorStorage.delete(created.getId());

        assertThat(directorStorage.findById(created.getId())).isEmpty();
    }

    @Test
    void findAllDirectorsByIds_returnsOnlyRequested() {
        Director first = directorStorage.create(validDirector("Нолан").build());
        directorStorage.create(validDirector("Тарантино").build());

        assertThat(directorStorage.findAllByIds(Set.of(first.getId())))
                .extracting(Director::getName)
                .containsExactly("Нолан");
    }

    @Test
    void findAllDirectorsByIds_emptySet_returnsEmpty() {
        assertThat(directorStorage.findAllByIds(Set.of())).isEmpty();
    }

    @Test
    void saveFilmDirectors_storesLinks() {
        Film film = filmStorage.create(validFilm("С режиссёрами").build());
        Director d1 = directorStorage.create(validDirector("Нолан").build());
        Director d2 = directorStorage.create(validDirector("Тарантино").build());

        directorStorage.saveFilmDirectors(film.getId(), List.of(d1, d2));

        assertThat(directorStorage.findByFilmId(film.getId()))
                .extracting(Director::getId)
                .containsExactlyInAnyOrder(d1.getId(), d2.getId());
    }

    @Test
    void findDirectorsByFilmIds_groupsByFilm() {
        Film first = filmStorage.create(validFilm("Первый").build());
        Film second = filmStorage.create(validFilm("Второй").build());
        Director d1 = directorStorage.create(validDirector("Нолан").build());
        Director d2 = directorStorage.create(validDirector("Тарантино").build());
        directorStorage.saveFilmDirectors(first.getId(), List.of(d1));
        directorStorage.saveFilmDirectors(second.getId(), List.of(d2));

        Map<Long, Set<Director>> map =
                directorStorage.findByFilmIds(List.of(first.getId(), second.getId()));

        assertThat(map.get(first.getId())).extracting(Director::getId).containsExactly(d1.getId());
        assertThat(map.get(second.getId())).extracting(Director::getId).containsExactly(d2.getId());
    }

    @Test
    void findDirectorsByFilmIds_emptyInput_returnsEmptyMap() {
        assertThat(directorStorage.findByFilmIds(List.of())).isEmpty();
    }

    @Test
    void deleteFilmDirectors_dropsAllLinks() {
        Film film = filmStorage.create(validFilm("Фильм").build());
        Director d = directorStorage.create(validDirector("Нолан").build());
        directorStorage.saveFilmDirectors(film.getId(), List.of(d));

        directorStorage.deleteFilmDirectors(film.getId());

        assertThat(directorStorage.findByFilmId(film.getId())).isEmpty();
    }

    @Test
    void deleteDirector_cascadesFilmLinks() {
        Film film = filmStorage.create(validFilm("Фильм").build());
        Director d = directorStorage.create(validDirector("Нолан").build());
        directorStorage.saveFilmDirectors(film.getId(), List.of(d));

        directorStorage.delete(d.getId());

        assertThat(directorStorage.findByFilmId(film.getId())).isEmpty();
    }

    @Test
    void createReview_assignsIdAndZeroUseful() {
        User author = userStorage.create(validUser("author").build());
        Film film = filmStorage.create(validFilm("Фильм").build());

        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());

        assertThat(created.getReviewId()).isNotNull();
        assertThat(created.getUseful()).isZero();
        assertThat(reviewStorage.findById(created.getReviewId()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(created);
    }

    @Test
    void findReviewById_unknownId_isEmpty() {
        assertThat(reviewStorage.findById(9999L)).isEmpty();
    }

    @Test
    void updateReview_changesContentAndType() {
        User author = userStorage.create(validUser("author").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());

        created.setContent("Пересмотрел — плохо");
        created.setIsPositive(false);
        reviewStorage.update(created);

        assertThat(reviewStorage.findById(created.getReviewId()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(created);
    }

    @Test
    void deleteReview_removesIt() {
        User author = userStorage.create(validUser("author").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());

        reviewStorage.delete(created.getReviewId());

        assertThat(reviewStorage.findById(created.getReviewId())).isEmpty();
    }

    @Test
    void addLike_increasesUseful() {
        User author = userStorage.create(validUser("author").build());
        User liker = userStorage.create(validUser("liker").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());

        reviewStorage.addLike(created.getReviewId(), liker.getId());

        assertThat(reviewStorage.findById(created.getReviewId())).get().hasFieldOrPropertyWithValue("useful", 1);
    }

    @Test
    void addDislike_decreasesUseful() {
        User author = userStorage.create(validUser("author").build());
        User disliker = userStorage.create(validUser("disliker").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());

        reviewStorage.addDislike(created.getReviewId(), disliker.getId());

        assertThat(reviewStorage.findById(created.getReviewId())).get().hasFieldOrPropertyWithValue("useful", -1);
    }

    @Test
    void addLike_isIdempotentPerUser() {
        User author = userStorage.create(validUser("author").build());
        User liker = userStorage.create(validUser("liker").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());

        reviewStorage.addLike(created.getReviewId(), liker.getId());
        reviewStorage.addLike(created.getReviewId(), liker.getId());

        assertThat(reviewStorage.findById(created.getReviewId())).get().hasFieldOrPropertyWithValue("useful", 1);
    }

    @Test
    void addDislike_afterLike_flipsVote() {
        User author = userStorage.create(validUser("author").build());
        User voter = userStorage.create(validUser("voter").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());

        reviewStorage.addLike(created.getReviewId(), voter.getId());
        reviewStorage.addDislike(created.getReviewId(), voter.getId());

        assertThat(reviewStorage.findById(created.getReviewId())).get().hasFieldOrPropertyWithValue("useful", -1);
    }

    @Test
    void removeLike_dropsVote() {
        User author = userStorage.create(validUser("author").build());
        User liker = userStorage.create(validUser("liker").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());
        reviewStorage.addLike(created.getReviewId(), liker.getId());

        reviewStorage.removeLike(created.getReviewId(), liker.getId());

        assertThat(reviewStorage.findById(created.getReviewId())).get().hasFieldOrPropertyWithValue("useful", 0);
    }

    @Test
    void removeDislike_doesNotDropLike() {
        User author = userStorage.create(validUser("author").build());
        User liker = userStorage.create(validUser("liker").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        Review created = reviewStorage.create(validReview(author.getId(), film.getId()).build());
        reviewStorage.addLike(created.getReviewId(), liker.getId());

        reviewStorage.removeDislike(created.getReviewId(), liker.getId());

        assertThat(reviewStorage.findById(created.getReviewId())).get().hasFieldOrPropertyWithValue("useful", 1);
    }

    @Test
    void findReviewsByFilmId_returnsOnlyThatFilmsReviewsSortedByUseful() {
        User author = userStorage.create(validUser("author").build());
        User voter = userStorage.create(validUser("voter").build());
        Film first = filmStorage.create(validFilm("Первый").build());
        Film second = filmStorage.create(validFilm("Второй").build());
        Review lowRated = reviewStorage.create(validReview(author.getId(), first.getId()).build());
        Review highRated = reviewStorage.create(validReview(author.getId(), first.getId()).build());
        reviewStorage.create(validReview(author.getId(), second.getId()).build());
        reviewStorage.addLike(highRated.getReviewId(), voter.getId());

        assertThat(reviewStorage.findByFilmId(first.getId(), 10))
                .extracting(Review::getReviewId)
                .containsExactly(highRated.getReviewId(), lowRated.getReviewId());
    }

    @Test
    void findReviewsByFilmId_nullFilmId_returnsAllReviews() {
        User author = userStorage.create(validUser("author").build());
        Film first = filmStorage.create(validFilm("Первый").build());
        Film second = filmStorage.create(validFilm("Второй").build());
        reviewStorage.create(validReview(author.getId(), first.getId()).build());
        reviewStorage.create(validReview(author.getId(), second.getId()).build());

        assertThat(reviewStorage.findByFilmId(null, 10)).hasSize(2);
    }

    @Test
    void findReviewsByFilmId_respectsCount() {
        User author = userStorage.create(validUser("author").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        reviewStorage.create(validReview(author.getId(), film.getId()).build());
        reviewStorage.create(validReview(author.getId(), film.getId()).build());

        assertThat(reviewStorage.findByFilmId(film.getId(), 1)).hasSize(1);
    }

    @Test
    void findCommonFilms_returnsOnlyFilmsLikedByBoth() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());

        Film common = filmStorage.create(validFilm("Общий").build());
        Film onlyFirst = filmStorage.create(validFilm("Только первый").build());
        Film onlySecond = filmStorage.create(validFilm("Только второй").build());

        filmStorage.addLike(common.getId(), first.getId());
        filmStorage.addLike(common.getId(), second.getId());
        filmStorage.addLike(onlyFirst.getId(), first.getId());
        filmStorage.addLike(onlySecond.getId(), second.getId());

        assertThat(filmStorage.findCommonFilms(first.getId(), second.getId()))
                .extracting(Film::getId)
                .containsExactly(common.getId());
    }

    @Test
    void findCommonFilms_sortsByLikesCountDescending() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());
        User third = userStorage.create(validUser("third").build());

        Film popular = filmStorage.create(validFilm("Популярный").build());
        Film lessPopular = filmStorage.create(validFilm("Менее популярный").build());

        addLikes(first, popular, lessPopular);
        addLikes(second, popular, lessPopular);
        addLikes(third, popular);

        assertThat(filmStorage.findCommonFilms(first.getId(), second.getId()))
                .extracting(Film::getId)
                .containsExactly(popular.getId(), lessPopular.getId());
    }

    @Test
    void findCommonFilms_withoutCommonLikes_returnsEmpty() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());

        Film firstFilm = filmStorage.create(validFilm("Первый").build());
        Film secondFilm = filmStorage.create(validFilm("Второй").build());

        filmStorage.addLike(firstFilm.getId(), first.getId());
        filmStorage.addLike(secondFilm.getId(), second.getId());

        assertThat(filmStorage.findCommonFilms(first.getId(), second.getId())).isEmpty();
    }

    @Test
    void findCommonFilms_sameUser_returnsThatUsersLikedFilms() {
        User user = userStorage.create(validUser("first").build());
        Film film = filmStorage.create(validFilm("Фильм").build());
        filmStorage.addLike(film.getId(), user.getId());

        assertThat(filmStorage.findCommonFilms(user.getId(), user.getId()))
                .extracting(Film::getId)
                .containsExactly(film.getId());
    }

    @Test
    void findCommonFilms_noLikes_returnsEmpty() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());
        filmStorage.create(validFilm("Фильм").build());

        assertThat(filmStorage.findCommonFilms(first.getId(), second.getId())).isEmpty();
    }

    @Test
    void createEvent_savesFieldsAndAssignsId() {
        User user = userStorage.create(validUser("first").build());

        Event event = Event.builder()
                .timestamp(1000L)
                .userId(user.getId())
                .eventType(EventType.LIKE)
                .operation(EventOperation.ADD)
                .entityId(10L)
                .build();

        Event created = eventStorage.create(event);

        assertThat(created.getEventId()).isNotNull();
        assertThat(eventStorage.findByUserId(user.getId()))
                .containsExactly(created);
    }

    @Test
    void findEventsByUserId_returnsOnlyUserEventsOrderedByTimestamp() {
        User first = userStorage.create(validUser("first").build());
        User second = userStorage.create(validUser("second").build());

        eventStorage.create(event(2000L, first.getId()));
        eventStorage.create(event(3000L, second.getId()));
        eventStorage.create(event(1000L, first.getId()));

        assertThat(eventStorage.findByUserId(first.getId()))
                .extracting(Event::getTimestamp)
                .containsExactly(1000L, 2000L);
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

    private Event event(Long timestamp, Long userId) {
        return Event.builder()
                .timestamp(timestamp)
                .userId(userId)
                .eventType(EventType.LIKE)
                .operation(EventOperation.ADD)
                .entityId(10L)
                .build();
    }

    private void addLikes(User user, Film... films) {
        for (Film film : films) {
            filmStorage.addLike(film.getId(), user.getId());
        }
    }
}
