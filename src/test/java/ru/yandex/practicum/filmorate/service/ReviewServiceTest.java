package ru.yandex.practicum.filmorate.service;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Event;
import ru.yandex.practicum.filmorate.model.EventOperation;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.event.EventDbStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.review.ReviewDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты бизнес-логики ReviewService на реальных DB-хранилищах: проверяют то, что не покрыто
 * DAO-тестами ReviewDbStorage — проверки существования (404), валидацию (400) и то, что
 * update не даёт подменить автора/фильм отзыва.
 */
@JdbcTest
@AutoConfigureTestDatabase
@Import({ReviewDbStorage.class, FilmDbStorage.class, UserDbStorage.class, EventDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class ReviewServiceTest {

    private final ReviewDbStorage reviewStorage;
    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;
    private final EventDbStorage eventStorage;

    private ReviewService service() {
        EventService eventService = new EventService(eventStorage, userStorage);
        return new ReviewService(reviewStorage, filmStorage, userStorage, eventService);
    }

    private User createUser() {
        return userStorage.create(User.builder()
                .email("author@mail.ru")
                .login("author")
                .name("Имя автора")
                .birthday(LocalDate.of(1990, 1, 1))
                .build());
    }

    private Film createFilm() {
        return filmStorage.create(Film.builder()
                .name("Фильм")
                .description("Описание фильма")
                .releaseDate(LocalDate.of(2014, 11, 6))
                .duration(169)
                .mpa(Mpa.builder().id(1).build())
                .build());
    }

    private Review.ReviewBuilder validReview(Long userId, Long filmId) {
        return Review.builder()
                .content("Отличный фильм")
                .isPositive(true)
                .userId(userId)
                .filmId(filmId);
    }

    @Test
    void create_savesReviewWithZeroUseful() {
        User user = createUser();
        Film film = createFilm();

        Review created = service().create(validReview(user.getId(), film.getId()).build());

        assertThat(created.getReviewId()).isNotNull();
        assertThat(created.getUseful()).isZero();
    }

    @Test
    void create_unknownFilm_throwsNotFoundException() {
        User user = createUser();
        Review review = validReview(user.getId(), 999L).build();

        assertThatThrownBy(() -> service().create(review))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_unknownUser_throwsNotFoundException() {
        Film film = createFilm();
        Review review = validReview(999L, film.getId()).build();

        assertThatThrownBy(() -> service().create(review))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_withoutId_throwsValidationException() {
        Review review = validReview(1L, 1L).build();

        assertThatThrownBy(() -> service().update(review))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_unknownId_throwsNotFoundException() {
        Review review = validReview(1L, 1L).reviewId(999L).build();

        assertThatThrownBy(() -> service().update(review))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_changesOnlyContentAndType_keepsAuthorAndFilm() {
        User user = createUser();
        Film film = createFilm();
        Review created = service().create(validReview(user.getId(), film.getId()).build());

        Review updated = service().update(Review.builder()
                .reviewId(created.getReviewId())
                .content("Пересмотрел — плохо")
                .isPositive(false)
                // подсовываем чужие userId/filmId, они не должны примениться
                .userId(999L)
                .filmId(999L)
                .build());

        assertThat(updated.getContent()).isEqualTo("Пересмотрел — плохо");
        assertThat(updated.getIsPositive()).isFalse();
        assertThat(updated.getUserId()).isEqualTo(user.getId());
        assertThat(updated.getFilmId()).isEqualTo(film.getId());
    }

    @Test
    void delete_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> service().delete(999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_existingReview_removesIt() {
        User user = createUser();
        Film film = createFilm();
        Review created = service().create(validReview(user.getId(), film.getId()).build());

        service().delete(created.getReviewId());

        assertThatThrownBy(() -> service().findById(created.getReviewId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findById_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> service().findById(999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByFilmId_unknownFilm_throwsNotFoundException() {
        assertThatThrownBy(() -> service().findByFilmId(999L, 10))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByFilmId_nonPositiveCount_throwsValidationException() {
        assertThatThrownBy(() -> service().findByFilmId(null, 0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void findByFilmId_nullCount_usesDefaultAndDoesNotThrow() {
        User user = createUser();
        Film film = createFilm();
        service().create(validReview(user.getId(), film.getId()).build());

        assertThat(service().findByFilmId(null, null)).hasSize(1);
    }

    @Test
    void addLike_unknownReview_throwsNotFoundException() {
        User user = createUser();

        assertThatThrownBy(() -> service().addLike(999L, user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addLike_unknownUser_throwsNotFoundException() {
        User author = createUser();
        Film film = createFilm();
        Review created = service().create(validReview(author.getId(), film.getId()).build());

        assertThatThrownBy(() -> service().addLike(created.getReviewId(), 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addDislike_unknownReview_throwsNotFoundException() {
        User user = createUser();

        assertThatThrownBy(() -> service().addDislike(999L, user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeLike_unknownReview_throwsNotFoundException() {
        User user = createUser();

        assertThatThrownBy(() -> service().removeLike(999L, user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeDislike_unknownUser_throwsNotFoundException() {
        User author = createUser();
        Film film = createFilm();
        Review created = service().create(validReview(author.getId(), film.getId()).build());

        assertThatThrownBy(() -> service().removeDislike(created.getReviewId(), 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_addsReviewEvent() {
        User user = createUser();
        Film film = createFilm();

        Review created = service().create(validReview(user.getId(), film.getId()).build());

        List<Event> events = List.copyOf(eventStorage.findByUserId(user.getId()));

        assertThat(events).hasSize(1);

        Event event = events.getFirst();

        assertThat(event.getUserId()).isEqualTo(user.getId());
        assertThat(event.getEventType()).isEqualTo(EventType.REVIEW);
        assertThat(event.getOperation()).isEqualTo(EventOperation.ADD);
        assertThat(event.getEntityId()).isEqualTo(created.getReviewId());
    }

    @Test
    void update_addsReviewEvent() {
        User user = createUser();
        Film film = createFilm();
        Review created = service().create(validReview(user.getId(), film.getId()).build());

        service().update(Review.builder()
                .reviewId(created.getReviewId())
                .content("Новый текст")
                .isPositive(false)
                .build());

        List<Event> events = List.copyOf(eventStorage.findByUserId(user.getId()));

        assertThat(events).hasSize(2);

        Event event = events.get(1);
        assertThat(event.getUserId()).isEqualTo(user.getId());
        assertThat(event.getEventType()).isEqualTo(EventType.REVIEW);
        assertThat(event.getOperation()).isEqualTo(EventOperation.UPDATE);
        assertThat(event.getEntityId()).isEqualTo(created.getReviewId());
    }

    @Test
    void delete_addsReviewEvent() {
        User user = createUser();
        Film film = createFilm();
        Review created = service().create(validReview(user.getId(), film.getId()).build());

        service().delete(created.getReviewId());

        List<Event> events = List.copyOf(eventStorage.findByUserId(user.getId()));

        assertThat(events).hasSize(2);

        Event event = events.get(1);
        assertThat(event.getUserId()).isEqualTo(user.getId());
        assertThat(event.getEventType()).isEqualTo(EventType.REVIEW);
        assertThat(event.getOperation()).isEqualTo(EventOperation.REMOVE);
        assertThat(event.getEntityId()).isEqualTo(created.getReviewId());
    }
}
