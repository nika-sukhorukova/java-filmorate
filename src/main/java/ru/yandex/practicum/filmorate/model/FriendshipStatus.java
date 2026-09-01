package ru.yandex.practicum.filmorate.model;

/**
 * Статус связи «дружба» между двумя пользователями.
 * Идентификаторы соответствуют записям справочника friendship_statuses.
 */
public enum FriendshipStatus {
    UNCONFIRMED(1),
    CONFIRMED(2);

    private final int id;

    FriendshipStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
