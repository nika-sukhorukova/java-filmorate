package ru.yandex.practicum.filmorate.storage.genre;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Genre;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class FilmGenreDbStorage implements FilmGenreStorage {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, Set<Genre>> findByFilmIds(Collection<Long> filmIds) {
        if (filmIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(", ", Collections.nCopies(filmIds.size(), "?"));
        String sql = "SELECT fg.film_id, g.id, g.name FROM film_genres AS fg"
                + " JOIN genres AS g ON g.id = fg.genre_id"
                + " WHERE fg.film_id IN (" + placeholders + ")"
                + " ORDER BY fg.film_id, g.id";

        Map<Long, Set<Genre>> genresByFilmId = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Genre genre = Genre.builder()
                    .id(rs.getInt("id"))
                    .name(rs.getString("name"))
                    .build();
            genresByFilmId.computeIfAbsent(rs.getLong("film_id"), id -> new LinkedHashSet<>()).add(genre);
        }, filmIds.toArray());

        return genresByFilmId;
    }

    @Override
    public Set<Genre> findByFilmId(Long filmId) {
        return findByFilmIds(List.of(filmId)).getOrDefault(filmId, new LinkedHashSet<>());
    }

    @Override
    public void save(Long filmId, Collection<Genre> genres) {
        if (genres.isEmpty()) {
            return;
        }

        List<Object[]> batch = new ArrayList<>();
        for (Genre genre : genres) {
            batch.add(new Object[]{filmId, genre.getId()});
        }

        jdbcTemplate.batchUpdate("INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)", batch);
    }

    @Override
    public void deleteByFilmId(Long filmId) {
        jdbcTemplate.update("DELETE FROM film_genres WHERE film_id = ?", filmId);
    }
}
