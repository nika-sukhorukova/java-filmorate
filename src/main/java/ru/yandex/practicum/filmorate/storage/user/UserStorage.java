package ru.yandex.practicum.filmorate.storage.user;

import ru.yandex.practicum.filmorate.model.User;

import java.util.Collection;
import java.util.Optional;

public interface UserStorage {

    Collection<User> findAll();

    Optional<User> findById(Long id);

    User create(User user);

    User update(User user);

    /**
     * Добавляет одностороннюю связь: пользователь добавляет друга в свой список.
     * Если встречная заявка уже существует, обе связи получают статус CONFIRMED.
     */
    void addFriend(Long userId, Long friendId);

    /**
     * Удаляет связь только в одну сторону — список друзей второго пользователя не меняется.
     */
    void removeFriend(Long userId, Long friendId);

    Collection<User> findFriends(Long userId);

    Collection<User> findCommonFriends(Long userId, Long otherId);
}
