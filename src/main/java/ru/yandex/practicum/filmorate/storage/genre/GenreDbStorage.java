package ru.yandex.practicum.filmorate.storage.genre;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Genre;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class GenreDbStorage implements GenreStorage {

    private static final RowMapper<Genre> GENRE_MAPPER = GenreDbStorage::mapGenre;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<Genre> findAll() {
        return jdbcTemplate.query("SELECT id, name FROM genres ORDER BY id", GENRE_MAPPER);
    }

    @Override
    public Optional<Genre> findById(Integer id) {
        List<Genre> found = jdbcTemplate.query("SELECT id, name FROM genres WHERE id = ?", GENRE_MAPPER, id);
        return found.stream().findFirst();
    }

    @Override
    public Collection<Genre> findAllByIds(Set<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, name FROM genres WHERE id IN (" + placeholders + ") ORDER BY id";
        return jdbcTemplate.query(sql, GENRE_MAPPER, ids.toArray());
    }

    private static Genre mapGenre(ResultSet rs, int rowNum) throws SQLException {
        return Genre.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .build();
    }
}
