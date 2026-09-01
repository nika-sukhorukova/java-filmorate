package ru.yandex.practicum.filmorate.storage.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.model.FriendshipStatus;
import ru.yandex.practicum.filmorate.model.User;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class InMemoryUserStorage implements UserStorage {

    private final Map<Long, User> users = new LinkedHashMap<>();

    /**
     * Исходящие заявки в друзья: идентификатор пользователя — заявки, которые он отправил.
     */
    private final Map<Long, Map<Long, FriendshipStatus>> friendships = new HashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Collection<User> findAll() {
        return users.values();
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public User create(User user) {
        user.setId(idGenerator.getAndIncrement());
        users.put(user.getId(), user);
        log.debug("В хранилище добавлен пользователь id={}", user.getId());
        return user;
    }

    @Override
    public User update(User user) {
        users.put(user.getId(), user);
        log.debug("В хранилище обновлён пользователь id={}", user.getId());
        return user;
    }

    @Override
    public void addFriend(Long userId, Long friendId) {
        boolean counterRequestExists = requestsOf(friendId).containsKey(userId);
        FriendshipStatus status = counterRequestExists ? FriendshipStatus.CONFIRMED : FriendshipStatus.UNCONFIRMED;

        requestsOf(userId).put(friendId, status);
        if (counterRequestExists) {
            requestsOf(friendId).put(userId, FriendshipStatus.CONFIRMED);
        }
    }

    @Override
    public void removeFriend(Long userId, Long friendId) {
        requestsOf(userId).remove(friendId);
        if (requestsOf(friendId).containsKey(userId)) {
            requestsOf(friendId).put(userId, FriendshipStatus.UNCONFIRMED);
        }
    }

    @Override
    public Collection<User> findFriends(Long userId) {
        return requestsOf(userId).keySet().stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Collection<User> findCommonFriends(Long userId, Long otherId) {
        Map<Long, FriendshipStatus> otherFriends = requestsOf(otherId);
        return requestsOf(userId).keySet().stream()
                .filter(otherFriends::containsKey)
                .map(users::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, FriendshipStatus> requestsOf(Long userId) {
        return friendships.computeIfAbsent(userId, id -> new LinkedHashMap<>());
    }
}
