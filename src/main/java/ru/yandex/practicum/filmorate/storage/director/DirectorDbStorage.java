package ru.yandex.practicum.filmorate.storage.director;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Director;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DirectorDbStorage implements DirectorStorage {

    private static final RowMapper<Director> DIRECTOR_MAPPER = DirectorDbStorage::mapDirector;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<Director> findAll() {
        return jdbcTemplate.query("SELECT id, name FROM directors ORDER BY id", DIRECTOR_MAPPER);
    }

    @Override
    public Optional<Director> findById(Long id) {
        List<Director> found = jdbcTemplate.query(
                "SELECT id, name FROM directors WHERE id = ?", DIRECTOR_MAPPER, id);
        return found.stream().findFirst();
    }

    @Override
    public Director create(Director director) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO directors (name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, director.getName());
            return statement;
        }, keyHolder);

        director.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        return director;
    }

    @Override
    public Director update(Director director) {
        jdbcTemplate.update("UPDATE directors SET name = ? WHERE id = ?",
                director.getName(), director.getId());
        return director;
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM directors WHERE id = ?", id);
    }

    @Override
    public Collection<Director> findAllByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, name FROM directors WHERE id IN (" + placeholders + ") ORDER BY id";
        return jdbcTemplate.query(sql, DIRECTOR_MAPPER, ids.toArray());
    }

    @Override
    public Map<Long, Set<Director>> findByFilmIds(Collection<Long> filmIds) {
        if (filmIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(filmIds.size(), "?"));
        String sql = "SELECT fd.film_id, d.id, d.name FROM film_directors AS fd"
                + " JOIN directors AS d ON d.id = fd.director_id"
                + " WHERE fd.film_id IN (" + placeholders + ")"
                + " ORDER BY fd.film_id, d.id";

        Map<Long, Set<Director>> directorsByFilmId = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Director director = Director.builder()
                    .id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .build();
            directorsByFilmId.computeIfAbsent(rs.getLong("film_id"), id -> new LinkedHashSet<>())
                    .add(director);
        }, filmIds.toArray());
        return directorsByFilmId;
    }

    @Override
    public Set<Director> findByFilmId(Long filmId) {
        return findByFilmIds(List.of(filmId)).getOrDefault(filmId, new LinkedHashSet<>());
    }

    @Override
    public void saveFilmDirectors(Long filmId, Collection<Director> directors) {
        if (directors.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>();
        for (Director director : directors) {
            batch.add(new Object[]{filmId, director.getId()});
        }
        jdbcTemplate.batchUpdate("INSERT INTO film_directors (film_id, director_id) VALUES (?, ?)", batch);
    }

    @Override
    public void deleteFilmDirectors(Long filmId) {
        jdbcTemplate.update("DELETE FROM film_directors WHERE film_id = ?", filmId);
    }

    private static Director mapDirector(ResultSet rs, int rowNum) throws SQLException {
        return Director.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .build();
    }
}