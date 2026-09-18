package ru.yandex.practicum.filmorate.storage.film;

import ru.yandex.practicum.filmorate.model.Film;

import java.util.Collection;
import java.util.Optional;

public interface FilmStorage {

    Collection<Film> findAll();

    Optional<Film> findById(Long id);

    Film create(Film film);

    Film update(Film film);

    void addLike(Long filmId, Long userId);

    void removeLike(Long filmId, Long userId);

    /**
     * Возвращает фильмы, отсортированные по числу лайков по убыванию. genreId и year
     * необязательны и фильтруют выборку по жанру и году выхода соответственно.
     */
    Collection<Film> findPopular(int count, Integer genreId, Integer year);

    Collection<Film> findByDirectorSortedByYear(Long directorId);

    Collection<Film> findByDirectorSortedByLikes(Long directorId);

    /**
     * Возвращает фильмы, рекомендованные пользователю на основе общих лайков
     * с другими пользователями.
     */
    Collection<Film> findRecommendations(Long userId);
}
