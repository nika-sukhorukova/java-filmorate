package ru.yandex.practicum.filmorate.storage.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.FriendshipStatus;
import ru.yandex.practicum.filmorate.model.User;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserDbStorage implements UserStorage {

    private static final String SELECT_USER = "SELECT id, email, login, name, birthday FROM users";

    private static final RowMapper<User> USER_MAPPER = UserDbStorage::mapUser;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Collection<User> findAll() {
        return jdbcTemplate.query(SELECT_USER + " ORDER BY id", USER_MAPPER);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jdbcTemplate.query(SELECT_USER + " WHERE id = ?", USER_MAPPER, id).stream().findFirst();
    }

    @Override
    public User create(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users (email, login, name, birthday) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getLogin());
            statement.setString(3, user.getName());
            statement.setDate(4, Date.valueOf(user.getBirthday()));
            return statement;
        }, keyHolder);

        user.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        log.debug("В базу добавлен пользователь id={}", user.getId());
        return user;
    }

    @Override
    public User update(User user) {
        jdbcTemplate.update("UPDATE users SET email = ?, login = ?, name = ?, birthday = ? WHERE id = ?",
                user.getEmail(), user.getLogin(), user.getName(), Date.valueOf(user.getBirthday()), user.getId());
        log.debug("В базе обновлён пользователь id={}", user.getId());
        return user;
    }

    @Override
    public void addFriend(Long userId, Long friendId) {
        boolean counterRequestExists = hasFriendship(friendId, userId);
        FriendshipStatus status = counterRequestExists ? FriendshipStatus.CONFIRMED : FriendshipStatus.UNCONFIRMED;

        jdbcTemplate.update("MERGE INTO friendships (user_id, friend_id, status_id) KEY (user_id, friend_id)"
                + " VALUES (?, ?, ?)", userId, friendId, status.getId());

        if (counterRequestExists) {
            jdbcTemplate.update("UPDATE friendships SET status_id = ? WHERE user_id = ? AND friend_id = ?",
                    FriendshipStatus.CONFIRMED.getId(), friendId, userId);
            log.debug("Дружба пользователей {} и {} подтверждена", userId, friendId);
        }
    }

    @Override
    public void removeFriend(Long userId, Long friendId) {
        jdbcTemplate.update("DELETE FROM friendships WHERE user_id = ? AND friend_id = ?", userId, friendId);
        jdbcTemplate.update("UPDATE friendships SET status_id = ? WHERE user_id = ? AND friend_id = ?",
                FriendshipStatus.UNCONFIRMED.getId(), friendId, userId);
    }

    @Override
    public Collection<User> findFriends(Long userId) {
        return jdbcTemplate.query(SELECT_USER
                        + " WHERE id IN (SELECT friend_id FROM friendships WHERE user_id = ?) ORDER BY id",
                USER_MAPPER, userId);
    }

    @Override
    public Collection<User> findCommonFriends(Long userId, Long otherId) {
        return jdbcTemplate.query(SELECT_USER
                        + " WHERE id IN (SELECT friend_id FROM friendships WHERE user_id = ?)"
                        + " AND id IN (SELECT friend_id FROM friendships WHERE user_id = ?) ORDER BY id",
                USER_MAPPER, userId, otherId);
    }

    private boolean hasFriendship(Long userId, Long friendId) {
        List<Integer> found = jdbcTemplate.query(
                "SELECT 1 FROM friendships WHERE user_id = ? AND friend_id = ?",
                (rs, rowNum) -> rs.getInt(1), userId, friendId);
        return !found.isEmpty();
    }

    private static User mapUser(ResultSet rs, int rowNum) throws SQLException {
        return User.builder()
                .id(rs.getLong("id"))
                .email(rs.getString("email"))
                .login(rs.getString("login"))
                .name(rs.getString("name"))
                .birthday(rs.getDate("birthday").toLocalDate())
                .build();
    }
}
