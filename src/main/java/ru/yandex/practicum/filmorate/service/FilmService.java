package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;
import java.util.Comparator;

@Slf4j
@Service
public class FilmService {

    private final FilmStorage filmStorage;
    private final UserStorage userStorage;

    @Autowired
    public FilmService(FilmStorage filmStorage, UserStorage userStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
    }

    public Collection<Film> findAll() {
        return filmStorage.findAll();
    }

    public Film findById(Long id) {
        return filmStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Фильм c id=" + id + " не найден"));
    }

    public Film create(Film film) {
        Film created = filmStorage.create(film);
        log.info("Добавлен новый фильм: {} {}", created.getId(), created.getName());
        return created;
    }

    public Film update(Film film) {
        if (film.getId() == null) {
            log.warn("Ошибка валидации при обновлении: Id не указан");
            throw new ValidationException("Id должен быть указан");
        }

        Film stored = findById(film.getId());
        film.getLikes().addAll(stored.getLikes());

        Film updated = filmStorage.update(film);
        log.info("Данные фильма с id {} успешно обновлены: {}", updated.getId(), updated);
        return updated;
    }

    public void addLike(Long filmId, Long userId) {
        Film film = findById(filmId);
        checkUserExists(userId);

        film.getLikes().add(userId);
        log.info("Пользователь {} поставил лайк фильму {}", userId, filmId);
    }

    public void removeLike(Long filmId, Long userId) {
        Film film = findById(filmId);
        checkUserExists(userId);

        film.getLikes().remove(userId);
        log.info("Пользователь {} убрал лайк с фильма {}", userId, filmId);
    }

    public Collection<Film> getPopular(int count) {
        if (count <= 0) {
            throw new ValidationException("Параметр count должен быть положительным");
        }

        log.debug("Запрошены {} самых популярных фильмов", count);
        return filmStorage.findAll().stream()
                .sorted(Comparator.comparingInt((Film film) -> film.getLikes().size()).reversed())
                .limit(count)
                .toList();
    }

    private void checkUserExists(Long userId) {
        if (userStorage.findById(userId).isEmpty()) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
    }
}
