package ru.yandex.practicum.filmorate.storage.genre;

import ru.yandex.practicum.filmorate.model.Genre;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Хранилище связей фильмов с жанрами — таблица film_genres.
 */
public interface FilmGenreStorage {

    /**
     * Возвращает жанры сразу для нескольких фильмов, чтобы избежать отдельного запроса на каждый фильм.
     */
    Map<Long, Set<Genre>> findByFilmIds(Collection<Long> filmIds);

    Set<Genre> findByFilmId(Long filmId);

    void save(Long filmId, Collection<Genre> genres);

    void deleteByFilmId(Long filmId);
}
