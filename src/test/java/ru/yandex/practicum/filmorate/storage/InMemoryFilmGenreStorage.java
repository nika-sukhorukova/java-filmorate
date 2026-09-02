package ru.yandex.practicum.filmorate.storage;

import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.storage.genre.FilmGenreStorage;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Связи фильмов с жанрами в памяти — используется в модульных тестах вместо обращения к базе.
 */
public class InMemoryFilmGenreStorage implements FilmGenreStorage {

    private final Map<Long, Set<Genre>> genresByFilmId = new HashMap<>();

    @Override
    public Map<Long, Set<Genre>> findByFilmIds(Collection<Long> filmIds) {
        Map<Long, Set<Genre>> found = new HashMap<>();
        for (Long filmId : filmIds) {
            Set<Genre> genres = genresByFilmId.get(filmId);
            if (genres != null) {
                found.put(filmId, new LinkedHashSet<>(genres));
            }
        }
        return found;
    }

    @Override
    public Set<Genre> findByFilmId(Long filmId) {
        return new LinkedHashSet<>(genresByFilmId.getOrDefault(filmId, new LinkedHashSet<>()));
    }

    @Override
    public void save(Long filmId, Collection<Genre> genres) {
        if (genres.isEmpty()) {
            return;
        }
        genresByFilmId.computeIfAbsent(filmId, id -> new LinkedHashSet<>()).addAll(genres);
    }

    @Override
    public void deleteByFilmId(Long filmId) {
        genresByFilmId.remove(filmId);
    }
}
