package ru.yandex.practicum.filmorate.storage.genre;

import ru.yandex.practicum.filmorate.model.Genre;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface GenreStorage {

    Collection<Genre> findAll();

    Optional<Genre> findById(Integer id);

    /**
     * Возвращает жанры с указанными идентификаторами. Используется для проверки того,
     * что все переданные при сохранении фильма жанры существуют.
     */
    Collection<Genre> findAllByIds(Set<Integer> ids);
}
