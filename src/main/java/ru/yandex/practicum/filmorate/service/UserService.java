package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;

@Slf4j
@Service
public class UserService {

    private final UserStorage userStorage;

    @Autowired
    public UserService(@Qualifier("userDbStorage") UserStorage userStorage) {
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

        findById(user.getId());
        applyNameFallback(user);

        User updated = userStorage.update(user);
        log.info("Данные пользователя с id {} успешно обновлены: {}", updated.getId(), updated);
        return updated;
    }

    /**
     * Дружба односторонняя: друг попадает в список инициатора, обратная связь не создаётся.
     */
    public void addFriend(Long id, Long friendId) {
        findById(id);
        findById(friendId);

        if (id.equals(friendId)) {
            throw new ValidationException("Нельзя добавить в друзья самого себя");
        }

        userStorage.addFriend(id, friendId);
        log.info("Пользователь {} добавил в друзья пользователя {}", id, friendId);
    }

    public void removeFriend(Long id, Long friendId) {
        findById(id);
        findById(friendId);

        userStorage.removeFriend(id, friendId);
        log.info("Пользователь {} удалил из друзей пользователя {}", id, friendId);
    }

    public Collection<User> getFriends(Long id) {
        findById(id);
        return userStorage.findFriends(id);
    }

    public Collection<User> getCommonFriends(Long id, Long otherId) {
        findById(id);
        findById(otherId);
        return userStorage.findCommonFriends(id, otherId);
    }

    private void applyNameFallback(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
    }
}
