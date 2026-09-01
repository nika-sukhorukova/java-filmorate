package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FilmService {

    private final FilmStorage filmStorage;
    private final UserStorage userStorage;
    private final GenreStorage genreStorage;
    private final MpaStorage mpaStorage;

    @Autowired
    public FilmService(@Qualifier("filmDbStorage") FilmStorage filmStorage,
                       @Qualifier("userDbStorage") UserStorage userStorage,
                       GenreStorage genreStorage,
                       MpaStorage mpaStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
        this.genreStorage = genreStorage;
        this.mpaStorage = mpaStorage;
    }

    public Collection<Film> findAll() {
        return filmStorage.findAll();
    }

    public Film findById(Long id) {
        return filmStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Фильм c id=" + id + " не найден"));
    }

    public Film create(Film film) {
        resolveMpa(film);
        resolveGenres(film);

        Film created = filmStorage.create(film);
        log.info("Добавлен новый фильм: {} {}", created.getId(), created.getName());
        return created;
    }

    public Film update(Film film) {
        if (film.getId() == null) {
            log.warn("Ошибка валидации при обновлении: Id не указан");
            throw new ValidationException("Id должен быть указан");
        }

        findById(film.getId());
        resolveMpa(film);
        resolveGenres(film);

        Film updated = filmStorage.update(film);
        log.info("Данные фильма с id {} успешно обновлены: {}", updated.getId(), updated);
        return updated;
    }

    public void addLike(Long filmId, Long userId) {
        findById(filmId);
        checkUserExists(userId);

        filmStorage.addLike(filmId, userId);
        log.info("Пользователь {} поставил лайк фильму {}", userId, filmId);
    }

    public void removeLike(Long filmId, Long userId) {
        findById(filmId);
        checkUserExists(userId);

        filmStorage.removeLike(filmId, userId);
        log.info("Пользователь {} убрал лайк с фильма {}", userId, filmId);
    }

    public Collection<Film> getPopular(int count) {
        if (count <= 0) {
            throw new ValidationException("Параметр count должен быть положительным");
        }

        log.debug("Запрошены {} самых популярных фильмов", count);
        return filmStorage.findPopular(count);
    }

    private void checkUserExists(Long userId) {
        if (userStorage.findById(userId).isEmpty()) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
    }

    /**
     * Проверяет существование рейтинга и подставляет его название, чтобы в ответе
     * возвращался полный объект, а не только идентификатор.
     */
    private void resolveMpa(Film film) {
        Mpa mpa = film.getMpa();
        if (mpa == null || mpa.getId() == null) {
            return;
        }

        Mpa stored = mpaStorage.findById(mpa.getId())
                .orElseThrow(() -> new NotFoundException("Рейтинг MPA с id=" + mpa.getId() + " не найден"));
        film.setMpa(stored);
    }

    /**
     * Проверяет, что все переданные жанры существуют, убирает дубликаты и подставляет названия.
     */
    private void resolveGenres(Film film) {
        Set<Genre> genres = film.getGenres();
        if (genres == null || genres.isEmpty()) {
            film.setGenres(new LinkedHashSet<>());
            return;
        }

        Set<Integer> ids = genres.stream()
                .map(Genre::getId)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Integer, Genre> stored = genreStorage.findAllByIds(ids).stream()
                .collect(Collectors.toMap(Genre::getId, Function.identity()));

        Set<Genre> resolved = new LinkedHashSet<>();
        for (Integer id : ids) {
            Genre genre = stored.get(id);
            if (genre == null) {
                throw new NotFoundException("Жанр с id=" + id + " не найден");
            }
            resolved.add(genre);
        }

        film.setGenres(resolved);
    }
}
