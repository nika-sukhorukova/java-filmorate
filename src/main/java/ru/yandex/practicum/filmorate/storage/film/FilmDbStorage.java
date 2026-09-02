package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;

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
            JOIN mpa_ratings AS m ON m.id = f.mpa_rating_id
            """;

    private static final RowMapper<Film> FILM_MAPPER = FilmDbStorage::mapFilm;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<Film> findAll() {
        return jdbcTemplate.query(SELECT_FILM + " ORDER BY f.id", FILM_MAPPER);
    }

    @Override
    public Optional<Film> findById(Long id) {
        return jdbcTemplate.query(SELECT_FILM + " WHERE f.id = ?", FILM_MAPPER, id).stream().findFirst();
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
            statement.setInt(5, film.getMpa().getId());
            return statement;
        }, keyHolder);

        film.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
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
                film.getMpa().getId(),
                film.getId());
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
        return jdbcTemplate.query(sql, FILM_MAPPER, count);
    }

    private static Film mapFilm(ResultSet rs, int rowNum) throws SQLException {
        return Film.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .releaseDate(rs.getDate("release_date").toLocalDate())
                .duration(rs.getInt("duration"))
                .mpa(Mpa.builder()
                        .id(rs.getInt("mpa_rating_id"))
                        .name(rs.getString("mpa_name"))
                        .build())
                .genres(new LinkedHashSet<>())
                .build();
    }
}
