package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.review.ReviewStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;

@Slf4j
@Service
public class ReviewService {

    private static final int DEFAULT_COUNT = 10;

    private final ReviewStorage reviewStorage;
    private final FilmStorage filmStorage;
    private final UserStorage userStorage;

    @Autowired
    public ReviewService(ReviewStorage reviewStorage,
                         @Qualifier("filmDbStorage") FilmStorage filmStorage,
                         @Qualifier("userDbStorage") UserStorage userStorage) {
        this.reviewStorage = reviewStorage;
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
    }

    public Review create(Review review) {
        checkFilmExists(review.getFilmId());
        checkUserExists(review.getUserId());

        Review created = reviewStorage.create(review);
        log.info("Добавлен новый отзыв id={} на фильм {}", created.getReviewId(), created.getFilmId());
        return created;
    }

    public Review update(Review review) {
        if (review.getReviewId() == null) {
            throw new ValidationException("Id отзыва должен быть указан");
        }

        Review existing = findById(review.getReviewId());
        Review toUpdate = existing.toBuilder()
                .content(review.getContent())
                .isPositive(review.getIsPositive())
                .build();

        Review updated = reviewStorage.update(toUpdate);
        log.info("Отзыв с id {} обновлён", updated.getReviewId());
        return updated;
    }

    public void delete(Long id) {
        findById(id);
        reviewStorage.delete(id);
        log.info("Удалён отзыв id={}", id);
    }

    public Review findById(Long id) {
        return reviewStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Отзыв с id=" + id + " не найден"));
    }

    public Collection<Review> findByFilmId(Long filmId, Integer count) {
        int limit = count == null ? DEFAULT_COUNT : count;
        if (limit <= 0) {
            throw new ValidationException("Параметр count должен быть положительным");
        }
        if (filmId != null) {
            checkFilmExists(filmId);
        }

        return reviewStorage.findByFilmId(filmId, limit);
    }

    public void addLike(Long reviewId, Long userId) {
        findById(reviewId);
        checkUserExists(userId);
        reviewStorage.addLike(reviewId, userId);
        log.info("Пользователь {} оценил отзыв {} как полезный", userId, reviewId);
    }

    public void addDislike(Long reviewId, Long userId) {
        findById(reviewId);
        checkUserExists(userId);
        reviewStorage.addDislike(reviewId, userId);
        log.info("Пользователь {} оценил отзыв {} как бесполезный", userId, reviewId);
    }

    public void removeLike(Long reviewId, Long userId) {
        findById(reviewId);
        checkUserExists(userId);
        reviewStorage.removeLike(reviewId, userId);
        log.info("Пользователь {} убрал отметку «полезно» с отзыва {}", userId, reviewId);
    }

    public void removeDislike(Long reviewId, Long userId) {
        findById(reviewId);
        checkUserExists(userId);
        reviewStorage.removeDislike(reviewId, userId);
        log.info("Пользователь {} убрал отметку «бесполезно» с отзыва {}", userId, reviewId);
    }

    private void checkFilmExists(Long filmId) {
        if (filmStorage.findById(filmId).isEmpty()) {
            throw new NotFoundException("Фильм с id=" + filmId + " не найден");
        }
    }

    private void checkUserExists(Long userId) {
        if (userStorage.findById(userId).isEmpty()) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
    }
}
