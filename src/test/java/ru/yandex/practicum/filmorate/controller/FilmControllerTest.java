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
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.EventOperation;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.EventService;
import ru.yandex.practicum.filmorate.service.FilmService;
import ru.yandex.practicum.filmorate.service.UserService;
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
@Import({FilmDbStorage.class, UserDbStorage.class, GenreDbStorage.class, FilmGenreDbStorage.class,
        MpaDbStorage.class, DirectorDbStorage.class, EventDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class FilmControllerTest {

    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;
    private final GenreDbStorage genreStorage;
    private final FilmGenreDbStorage filmGenreStorage;
    private final MpaDbStorage mpaStorage;
    private final DirectorDbStorage directorStorage;
    private final EventDbStorage eventStorage;

    private FilmController controller;
    private UserController userController;
    private FeedController feedController;

    @BeforeEach
    void setUp() {
        EventService eventService = new EventService(eventStorage, userStorage);

        controller = new FilmController(
                new FilmService(
                        filmStorage,
                        userStorage,
                        genreStorage,
                        filmGenreStorage,
                        mpaStorage,
                        directorStorage,
                        eventService
                )
        );

        userController = new UserController(
                new UserService(userStorage, eventService)
        );

        feedController = new FeedController(eventService);
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

    private Director createDirector(String name) {
        return directorStorage.create(Director.builder().name(name).build());
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

        assertThat(controller.getPopular(10, null, null)).first()
                .extracting(Film::getId)
                .isEqualTo(liked.getId());
        assertThat(controller.getPopular(10, null, null)).last()
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

        assertThat(controller.getPopular(10, null, null)).first()
                .extracting(Film::getId)
                .isEqualTo(likedByTwoUsers.getId());
    }

    @Test
    void addLike_addsEventToUserFeed() {
        Film film = controller.create(validFilm().build());
        User user = createUser("liker");

        controller.addLike(film.getId(), user.getId());

        assertThat(feedController.getFeed(user.getId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getUserId()).isEqualTo(user.getId());
                    assertThat(event.getEntityId()).isEqualTo(film.getId());
                    assertThat(event.getEventType()).isEqualTo(EventType.LIKE);
                    assertThat(event.getOperation()).isEqualTo(EventOperation.ADD);
                });
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

        assertThat(controller.getPopular(10, null, null)).first()
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

        assertThat(controller.getPopular(10, null, null)).containsExactly(popular, unpopular);
    }

    @Test
    void getPopular_respectsCount() {
        controller.create(validFilm().build());
        controller.create(validFilm().build());

        assertThat(controller.getPopular(1, null, null)).hasSize(1);
    }

    @Test
    void getPopular_nonPositiveCount_throwsValidationException() {
        assertThatThrownBy(() -> controller.getPopular(0, null, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void getPopular_filtersByGenre() {
        Film comedy = controller.create(validFilm()
                .name("Комедия")
                .genres(Set.of(Genre.builder().id(1).build()))
                .build());
        controller.create(validFilm().name("Без жанра").build());

        assertThat(controller.getPopular(10, 1, null))
                .extracting(Film::getId)
                .containsExactly(comedy.getId());
    }

    @Test
    void getPopular_filtersByYear() {
        Film recent = controller.create(validFilm().name("Новый").releaseDate(LocalDate.of(2020, 1, 1)).build());
        controller.create(validFilm().name("Старый").releaseDate(LocalDate.of(2000, 1, 1)).build());

        assertThat(controller.getPopular(10, null, 2020))
                .extracting(Film::getId)
                .containsExactly(recent.getId());
    }

    @Test
    void getPopular_unknownGenre_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.getPopular(10, 999, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_withDirectors_savesAndReturnsThem() {
        Director nolan = createDirector("Нолан");

        Film created = controller.create(validFilm()
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());

        assertThat(controller.findById(created.getId()).getDirectors())
                .extracting(Director::getId)
                .containsExactly(nolan.getId());
    }

    @Test
    void create_withUnknownDirector_throwsNotFoundException() {
        Film film = validFilm()
                .directors(Set.of(Director.builder().id(999L).build()))
                .build();

        assertThatThrownBy(() -> controller.create(film))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_deduplicatesDirectors() {
        Director nolan = createDirector("Нолан");
        Director duplicate = Director.builder().id(nolan.getId()).name("Другое имя").build();

        Film created = controller.create(validFilm()
                .directors(Set.of(Director.builder().id(nolan.getId()).build(), duplicate))
                .build());

        assertThat(controller.findById(created.getId()).getDirectors()).hasSize(1);
    }

    @Test
    void update_replacesDirectors() {
        Director nolan = createDirector("Нолан");
        Director tarantino = createDirector("Тарантино");
        Film created = controller.create(validFilm()
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());

        controller.update(validFilm()
                .id(created.getId())
                .directors(Set.of(Director.builder().id(tarantino.getId()).build()))
                .build());

        assertThat(controller.findById(created.getId()).getDirectors())
                .extracting(Director::getId)
                .containsExactly(tarantino.getId());
    }

    @Test
    void update_withEmptyDirectors_clearsThem() {
        Director nolan = createDirector("Нолан");
        Film created = controller.create(validFilm()
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());

        controller.update(validFilm().id(created.getId()).build());

        assertThat(controller.findById(created.getId()).getDirectors()).isEmpty();
    }

    @Test
    void findAll_returnsFilmsWithDirectors() {
        Director nolan = createDirector("Нолан");
        controller.create(validFilm()
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());

        assertThat(controller.findAll())
                .allSatisfy(f -> assertThat(f.getDirectors()).isNotEmpty());
    }

    @Test
    void findByDirector_unknownDirector_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.findByDirector(999L, "year"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByDirector_invalidSortBy_throwsValidationException() {
        Director nolan = createDirector("Нолан");
        controller.create(validFilm()
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());

        assertThatThrownBy(() -> controller.findByDirector(nolan.getId(), "name"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void findByDirector_sortByYear_ordersByReleaseDate() {
        Director nolan = createDirector("Нолан");
        Film older = controller.create(validFilm()
                .name("Старый")
                .releaseDate(LocalDate.of(2000, 1, 1))
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());
        Film newer = controller.create(validFilm()
                .name("Новый")
                .releaseDate(LocalDate.of(2020, 1, 1))
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());

        assertThat(controller.findByDirector(nolan.getId(), "year"))
                .extracting(Film::getId)
                .containsExactly(older.getId(), newer.getId());
    }

    @Test
    void findByDirector_sortByLikes_ordersByLikesDescending() {
        Director nolan = createDirector("Нолан");
        Film unpopular = controller.create(validFilm()
                .name("Без лайков")
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());
        Film popular = controller.create(validFilm()
                .name("С лайками")
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());
        User first = createUser("first");
        User second = createUser("second");
        controller.addLike(popular.getId(), first.getId());
        controller.addLike(popular.getId(), second.getId());
        controller.addLike(unpopular.getId(), first.getId());

        assertThat(controller.findByDirector(nolan.getId(), "likes"))
                .extracting(Film::getId)
                .containsExactly(popular.getId(), unpopular.getId());
    }

    @Test
    void findByDirector_returnsOnlyFilmsOfThatDirector() {
        Director nolan = createDirector("Нолан");
        Director tarantino = createDirector("Тарантино");
        Film nolanFilm = controller.create(validFilm()
                .name("Нолановский")
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());
        controller.create(validFilm()
                .name("Тарантиновский")
                .directors(Set.of(Director.builder().id(tarantino.getId()).build()))
                .build());

        assertThat(controller.findByDirector(nolan.getId(), "year"))
                .extracting(Film::getId)
                .containsExactly(nolanFilm.getId());
    }

    @Test
    void findByDirector_returnsFilmsWithDirectorsAndGenres() {
        Director nolan = createDirector("Нолан");
        controller.create(validFilm()
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());

        assertThat(controller.findByDirector(nolan.getId(), "year"))
                .allSatisfy(f -> assertThat(f.getDirectors()).isNotEmpty());
    }

    @Test
    void findCommonFilms_returnsOnlyFilmsLikedByBoth() {
        Film common = controller.create(validFilm().name("Общий").build());
        Film onlyFirst = controller.create(validFilm().name("Только у первого").build());
        Film onlySecond = controller.create(validFilm().name("Только у второго").build());
        User first = createUser("first");
        User second = createUser("second");

        controller.addLike(common.getId(), first.getId());
        controller.addLike(common.getId(), second.getId());
        controller.addLike(onlyFirst.getId(), first.getId());
        controller.addLike(onlySecond.getId(), second.getId());

        assertThat(controller.findCommonFilms(first.getId(), second.getId()))
                .extracting(Film::getId)
                .containsExactly(common.getId());
    }

    @Test
    void findCommonFilms_sortsByPopularityDescending() {
        Film popular = controller.create(validFilm().name("Популярный").build());
        Film lessPopular = controller.create(validFilm().name("Менее популярный").build());
        User first = createUser("first");
        User second = createUser("second");
        User third = createUser("third");

        controller.addLike(popular.getId(), first.getId());
        controller.addLike(popular.getId(), second.getId());
        controller.addLike(popular.getId(), third.getId());
        controller.addLike(lessPopular.getId(), first.getId());
        controller.addLike(lessPopular.getId(), second.getId());

        assertThat(controller.findCommonFilms(first.getId(), second.getId()))
                .extracting(Film::getId)
                .containsExactly(popular.getId(), lessPopular.getId());
    }

    @Test
    void findCommonFilms_withoutCommonLikes_returnsEmpty() {
        Film firstFilm = controller.create(validFilm().name("Первый").build());
        Film secondFilm = controller.create(validFilm().name("Второй").build());
        User first = createUser("first");
        User second = createUser("second");

        controller.addLike(firstFilm.getId(), first.getId());
        controller.addLike(secondFilm.getId(), second.getId());

        assertThat(controller.findCommonFilms(first.getId(), second.getId())).isEmpty();
    }

    @Test
    void findCommonFilms_noLikesAtAll_returnsEmpty() {
        controller.create(validFilm().build());
        User first = createUser("first");
        User second = createUser("second");

        assertThat(controller.findCommonFilms(first.getId(), second.getId())).isEmpty();
    }

    @Test
    void findCommonFilms_sameUser_returnsFilmsLikedByThatUser() {
        Film film = controller.create(validFilm().build());
        User user = createUser("first");
        controller.addLike(film.getId(), user.getId());

        assertThat(controller.findCommonFilms(user.getId(), user.getId()))
                .extracting(Film::getId)
                .containsExactly(film.getId());
    }

    @Test
    void findCommonFilms_unknownFriend_throwsNotFoundException() {
        User user = createUser("first");

        assertThatThrownBy(() -> controller.findCommonFilms(user.getId(), 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findCommonFilms_unknownUser_throwsNotFoundException() {
        User user = createUser("first");

        assertThatThrownBy(() -> controller.findCommonFilms(999L, user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findCommonFilms_returnsFilmsWithGenresAndDirectors() {
        Director nolan = createDirector("Нолан");
        Film common = controller.create(validFilm()
                .name("Общий")
                .genres(Set.of(Genre.builder().id(1).build()))
                .directors(Set.of(Director.builder().id(nolan.getId()).build()))
                .build());
        User first = createUser("first");
        User second = createUser("second");
        controller.addLike(common.getId(), first.getId());
        controller.addLike(common.getId(), second.getId());

        assertThat(controller.findCommonFilms(first.getId(), second.getId()))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.getDirectors()).isNotEmpty();
                    assertThat(f.getGenres()).isNotEmpty();
                });
    }

    @Test
    void searchFilmKeyWorld_shouldFindMoviesByKeyword() {
        Film matchingByName = controller.create(validFilm()
                .name("Матрица")
                .description("Обычное описание")
                .build());

        Film matchingByDescription = controller.create(validFilm()
                .name("Интерстеллар")
                .description("Фантастика про космос и черные дыры")
                .build());

        Collection<Film> searchResult1 = controller.searchFilm("матриц", "title");

        assertThat(searchResult1)
                .hasSize(1)
                .extracting(Film::getId)
                .containsExactly(matchingByName.getId());

        Collection<Film> searchResult2 = controller.searchFilm("космос", "title");

        assertThat(searchResult2)
                .hasSize(1)
                .extracting(Film::getId)
                .containsExactly(matchingByDescription.getId());
    }

    @Test
    void searchFilmKeyWorld_shouldReturnEmptyList() {
        Collection<Film> result = controller.searchFilm("   ", "title");

        assertThat(result).isEmpty();
    }

    @Test
    void searchFilm_shouldFindMoviesByDirectorOnly() {
        Director director = createDirector("Кристофер Нолан");

        Film nolanFilm = validFilm().name("Начало").build();
        nolanFilm.getDirectors().add(director);
        Film savedNolanFilm = controller.create(nolanFilm);

        controller.create(validFilm().name("Матрица").build());

        Collection<Film> result = controller.searchFilm("нолан", "director");

        assertThat(result)
                .hasSize(1)
                .extracting(Film::getId)
                .containsExactly(savedNolanFilm.getId());
    }

    @Test
    void searchFilm_shouldFindMoviesByBothTitleAndDirector() {
        Director director = createDirector("Кристофер Нолан");

        Film film1 = controller.create(validFilm().name("Начало").build());
        directorStorage.saveFilmDirectors(film1.getId(), Set.of(director));

        Film film2 = controller.create(validFilm().name("Нолана записка").build());

        Film nonMatching = controller.create(validFilm().name("Зеленая миля").build());

        Collection<Film> result = controller.searchFilm("нолан", "title,director");

        assertThat(result)
                .hasSize(2)
                .extracting(Film::getId)
                .contains(film1.getId(), film2.getId())
                .doesNotContain(nonMatching.getId());
    }

    @Test
    void searchFilm_shouldFindMoviesByDescriptionWhenBothTitleAndDirector() {
        Film byDescription = controller.create(validFilm()
                .name("Начало")
                .description("Фильм про космос и черные дыры")
                .build());

        Film nonMatching = controller.create(validFilm()
                .name("Зеленая миля")
                .description("Обычное описание")
                .build());

        Collection<Film> result = controller.searchFilm("космос", "title,director");

        assertThat(result)
                .extracting(Film::getId)
                .containsExactly(byDescription.getId())
                .doesNotContain(nonMatching.getId());
    }

    @Test
    void searchFilm_shouldSortResultsByPopularity() {
        Film greenMile = controller.create(validFilm().name("Зеленая миля").build());
        Film greenZone = controller.create(validFilm().name("Зеленая зона").build());
        Film greenLantern = controller.create(validFilm().name("Зеленый фонарь").build());

        User user1 = createUser("userOne");
        User user2 = createUser("userTwo");
        User user3 = createUser("userTree");

        controller.addLike(greenZone.getId(), user1.getId());
        controller.addLike(greenZone.getId(), user2.getId());
        controller.addLike(greenZone.getId(), user3.getId());

        controller.addLike(greenMile.getId(), user1.getId());

        Collection<Film> result = controller.searchFilm("зелен", "title");

        assertThat(result)
                .hasSize(3)
                .extracting(Film::getId)
                .containsExactly(
                        greenZone.getId(),
                        greenMile.getId(),
                        greenLantern.getId()
                );
    }

    @Test
    void deleteFilm() {
        Film film = controller.create(validFilm().name("первый").build());
        long filmId = film.getId();

        controller.deleteFilm(filmId);

        assertThatThrownBy(() -> controller.findById(filmId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Фильм c id=" + filmId + " не найден");
    }

    @Test
    void deleteFilm_shouldThrowNotFoundException() {
        long nonExistentId = 9999L;

        assertThatThrownBy(() -> controller.deleteFilm(nonExistentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Фильм c id=" + nonExistentId + " не найден");
    }
}