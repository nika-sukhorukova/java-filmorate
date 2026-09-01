package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FilmDbStorage implements FilmStorage {

    private static final String SELECT_FILM = """
            SELECT f.id,
                   f.name,
                   f.description,
                   f.release_date,
                   f.duration,
                   f.mpa_rating_id,
                   m.name AS mpa_name
            FROM films AS f
            LEFT JOIN mpa_ratings AS m ON m.id = f.mpa_rating_id
            """;

    private static final RowMapper<Film> FILM_MAPPER = FilmDbStorage::mapFilm;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<Film> findAll() {
        List<Film> films = jdbcTemplate.query(SELECT_FILM + " ORDER BY f.id", FILM_MAPPER);
        return loadGenres(films);
    }

    @Override
    public Optional<Film> findById(Long id) {
        List<Film> films = jdbcTemplate.query(SELECT_FILM + " WHERE f.id = ?", FILM_MAPPER, id);
        return loadGenres(films).stream().findFirst();
    }

    @Override
    public Film create(Film film) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO films (name, description, release_date, duration, mpa_rating_id)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, film.getName());
            statement.setString(2, film.getDescription());
            statement.setDate(3, Date.valueOf(film.getReleaseDate()));
            statement.setInt(4, film.getDuration());
            if (film.getMpa() == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, film.getMpa().getId());
            }
            return statement;
        }, keyHolder);

        film.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        saveGenres(film);
        log.debug("В базу добавлен фильм id={}", film.getId());
        return film;
    }

    @Override
    public Film update(Film film) {
        jdbcTemplate.update("UPDATE films SET name = ?, description = ?, release_date = ?, duration = ?,"
                        + " mpa_rating_id = ? WHERE id = ?",
                film.getName(),
                film.getDescription(),
                Date.valueOf(film.getReleaseDate()),
                film.getDuration(),
                film.getMpa() == null ? null : film.getMpa().getId(),
                film.getId());

        jdbcTemplate.update("DELETE FROM film_genres WHERE film_id = ?", film.getId());
        saveGenres(film);
        log.debug("В базе обновлён фильм id={}", film.getId());
        return film;
    }

    @Override
    public void addLike(Long filmId, Long userId) {
        jdbcTemplate.update("MERGE INTO film_likes (film_id, user_id) KEY (film_id, user_id) VALUES (?, ?)",
                filmId, userId);
    }

    @Override
    public void removeLike(Long filmId, Long userId) {
        jdbcTemplate.update("DELETE FROM film_likes WHERE film_id = ? AND user_id = ?", filmId, userId);
    }

    @Override
    public Collection<Film> findPopular(int count) {
        String sql = SELECT_FILM
                + " LEFT JOIN film_likes AS l ON l.film_id = f.id"
                + " GROUP BY f.id, f.name, f.description, f.release_date, f.duration, f.mpa_rating_id, m.name"
                + " ORDER BY COUNT(l.user_id) DESC, f.id"
                + " LIMIT ?";
        return loadGenres(jdbcTemplate.query(sql, FILM_MAPPER, count));
    }

    /**
     * Догружает жанры одним запросом на всю выборку, чтобы не делать отдельный запрос на каждый фильм.
     */
    private List<Film> loadGenres(List<Film> films) {
        if (films.isEmpty()) {
            return films;
        }

        Map<Long, Film> filmsById = films.stream().collect(Collectors.toMap(Film::getId, Function.identity()));
        String placeholders = String.join(", ", Collections.nCopies(filmsById.size(), "?"));
        String sql = "SELECT fg.film_id, g.id, g.name FROM film_genres AS fg"
                + " JOIN genres AS g ON g.id = fg.genre_id"
                + " WHERE fg.film_id IN (" + placeholders + ")"
                + " ORDER BY fg.film_id, g.id";

        jdbcTemplate.query(sql, rs -> {
            Film film = filmsById.get(rs.getLong("film_id"));
            if (film != null) {
                film.getGenres().add(Genre.builder().id(rs.getInt("id")).name(rs.getString("name")).build());
            }
        }, filmsById.keySet().toArray());

        return films;
    }

    private void saveGenres(Film film) {
        Set<Genre> genres = film.getGenres();
        if (genres == null || genres.isEmpty()) {
            film.setGenres(new LinkedHashSet<>());
            return;
        }

        List<Object[]> batch = new ArrayList<>();
        Set<Integer> savedIds = new LinkedHashSet<>();
        for (Genre genre : genres) {
            if (savedIds.add(genre.getId())) {
                batch.add(new Object[]{film.getId(), genre.getId()});
            }
        }

        jdbcTemplate.batchUpdate("INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)", batch);
    }

    private static Film mapFilm(ResultSet rs, int rowNum) throws SQLException {
        Integer mpaId = rs.getObject("mpa_rating_id", Integer.class);
        return Film.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .releaseDate(rs.getDate("release_date").toLocalDate())
                .duration(rs.getInt("duration"))
                .mpa(mpaId == null ? null : Mpa.builder().id(mpaId).name(rs.getString("mpa_name")).build())
                .genres(new LinkedHashSet<>())
                .build();
    }
}
