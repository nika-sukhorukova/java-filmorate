package ru.yandex.practicum.filmorate.storage.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Event;
import ru.yandex.practicum.filmorate.model.EventOperation;
import ru.yandex.practicum.filmorate.model.EventType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Objects;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EventDbStorage implements EventStorage {

    private static final RowMapper<Event> EVENT_MAPPER = EventDbStorage::mapEvent;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Event create(Event event) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO events (timestamp, user_id, event_type, operation, entity_id)"
                    + " VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, event.getTimestamp());
            statement.setLong(2, event.getUserId());
            statement.setString(3, event.getEventType().name());
            statement.setString(4, event.getOperation().name());
            statement.setLong(5, event.getEntityId());
            return statement;
        }, keyHolder);

        event.setEventId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        log.debug("В базу добавлено событие id={}", event.getEventId());
        return event;
    }

    @Override
    public Collection<Event> findByUserId(Long userId) {
        String sql = """
                SELECT event_id, timestamp, user_id, event_type, operation, entity_id
                FROM events
                WHERE user_id = ?
                ORDER BY timestamp, event_id
                """;

        return jdbcTemplate.query(sql, EVENT_MAPPER, userId);
    }

    private static Event mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return Event.builder()
                .eventId(rs.getLong("event_id"))
                .timestamp(rs.getLong("timestamp"))
                .userId(rs.getLong("user_id"))
                .eventType(EventType.valueOf(rs.getString("event_type")))
                .operation(EventOperation.valueOf(rs.getString("operation")))
                .entityId(rs.getLong("entity_id"))
                .build();
    }
}
