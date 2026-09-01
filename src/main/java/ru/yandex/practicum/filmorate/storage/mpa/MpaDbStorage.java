package ru.yandex.practicum.filmorate.storage.mpa;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MpaDbStorage implements MpaStorage {

    private static final RowMapper<Mpa> MPA_MAPPER = MpaDbStorage::mapMpa;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<Mpa> findAll() {
        return jdbcTemplate.query("SELECT id, name FROM mpa_ratings ORDER BY id", MPA_MAPPER);
    }

    @Override
    public Optional<Mpa> findById(Integer id) {
        List<Mpa> found = jdbcTemplate.query("SELECT id, name FROM mpa_ratings WHERE id = ?", MPA_MAPPER, id);
        return found.stream().findFirst();
    }

    private static Mpa mapMpa(ResultSet rs, int rowNum) throws SQLException {
        return Mpa.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .build();
    }
}
