package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.FriendshipStatus;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class UserService {

    private final UserStorage userStorage;

    @Autowired
    public UserService(UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    public Collection<User> findAll() {
        return userStorage.findAll();
    }

    public User findById(Long id) {
        return userStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + id + " не найден"));
    }

    public User create(User user) {
        applyNameFallback(user);
        User created = userStorage.create(user);
        log.info("Добавлен новый пользователь: {} {}", created.getId(), created.getLogin());
        return created;
    }

    public User update(User user) {
        if (user.getId() == null) {
            log.warn("Ошибка валидации при обновлении: Id не указан");
            throw new ValidationException("Id должен быть указан");
        }

        User stored = findById(user.getId());
        applyNameFallback(user);
        user.getFriends().putAll(stored.getFriends());

        User updated = userStorage.update(user);
        log.info("Данные пользователя с id {} успешно обновлены: {}", updated.getId(), updated);
        return updated;
    }

    public void addFriend(Long id, Long friendId) {
        User user = findById(id);
        User friend = findById(friendId);

        if (id.equals(friendId)) {
            throw new ValidationException("Нельзя добавить в друзья самого себя");
        }

        user.getFriends().put(friendId, FriendshipStatus.CONFIRMED);
        friend.getFriends().put(id, FriendshipStatus.CONFIRMED);
        log.info("Пользователи {} и {} теперь друзья", id, friendId);
    }

    public void removeFriend(Long id, Long friendId) {
        User user = findById(id);
        User friend = findById(friendId);

        user.getFriends().remove(friendId);
        friend.getFriends().remove(id);
        log.info("Пользователи {} и {} больше не друзья", id, friendId);
    }

    public Collection<User> getFriends(Long id) {
        Set<Long> friendIds = findById(id).getFriends().keySet();
        log.debug("У пользователя {} найдено {} друзей", id, friendIds.size());
        return friendIds.stream()
                .map(this::findById)
                .toList();
    }

    public Collection<User> getCommonFriends(Long id, Long otherId) {
        Set<Long> friendIds = findById(id).getFriends().keySet();
        Set<Long> otherFriendIds = findById(otherId).getFriends().keySet();

        List<User> common = friendIds.stream()
                .filter(otherFriendIds::contains)
                .map(this::findById)
                .toList();
        log.debug("У пользователей {} и {} найдено {} общих друзей", id, otherId, common.size());
        return common;
    }

    private void applyNameFallback(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
    }
}
