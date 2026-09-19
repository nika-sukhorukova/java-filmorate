package ru.yandex.practicum.filmorate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.EventOperation;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.director.DirectorStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.genre.FilmGenreStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilmService {

    private final FilmStorage filmStorage;
    private final UserStorage userStorage;
    private final GenreStorage genreStorage;
    private final FilmGenreStorage filmGenreStorage;
    private final MpaStorage mpaStorage;
    private final DirectorStorage directorStorage;

    private final EventService eventService;

    public Collection<Film> findAll() {
        return withDetails(filmStorage.findAll());
    }

    public Film findById(Long id) {
        Film film = filmStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Фильм c id=" + id + " не найден"));
        film.setGenres(filmGenreStorage.findByFilmId(id));
        film.setDirectors(directorStorage.findByFilmId(id));
        return film;
    }

    public Film create(Film film) {
        resolveMpa(film);
        resolveGenres(film);
        resolveDirectors(film);

        Film created = filmStorage.create(film);
        filmGenreStorage.save(created.getId(), created.getGenres());
        directorStorage.saveFilmDirectors(created.getId(), created.getDirectors());
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
        resolveDirectors(film);

        Film updated = filmStorage.update(film);
        filmGenreStorage.deleteByFilmId(updated.getId());
        filmGenreStorage.save(updated.getId(), updated.getGenres());
        directorStorage.deleteFilmDirectors(updated.getId());
        directorStorage.saveFilmDirectors(updated.getId(), updated.getDirectors());
        log.info("Данные фильма с id {} успешно обновлены: {}", updated.getId(), updated);
        return updated;
    }

    public void addLike(Long filmId, Long userId) {
        findById(filmId);
        checkUserExists(userId);

        filmStorage.addLike(filmId, userId);
        eventService.addEvent(userId, EventType.LIKE, EventOperation.ADD, filmId);
        log.info("Пользователь {} поставил лайк фильму {}", userId, filmId);
    }

    public void removeLike(Long filmId, Long userId) {
        findById(filmId);
        checkUserExists(userId);

        filmStorage.removeLike(filmId, userId);
        eventService.addEvent(userId, EventType.LIKE, EventOperation.REMOVE, filmId);
        log.info("Пользователь {} убрал лайк с фильма {}", userId, filmId);
    }

    public Collection<Film> getPopular(int count, Integer genreId, Integer year) {
        if (count <= 0) {
            throw new ValidationException("Параметр count должен быть положительным");
        }
        if (genreId != null) {
            genreStorage.findById(genreId)
                    .orElseThrow(() -> new NotFoundException("Жанр с id=" + genreId + " не найден"));
        }

        log.debug("Запрошены {} самых популярных фильмов (genreId={}, year={})", count, genreId, year);
        return withDetails(filmStorage.findPopular(count, genreId, year));
    }

    private Collection<Film> withDetails(Collection<Film> films) {
        if (films.isEmpty()) {
            return films;
        }

        List<Long> filmIds = films.stream().map(Film::getId).toList();

        Map<Long, Set<Genre>> genresByFilmId = filmGenreStorage.findByFilmIds(filmIds);
        Map<Long, Set<Director>> directorsByFilmId = directorStorage.findByFilmIds(filmIds);

        films.forEach(film -> {
            film.setGenres(genresByFilmId.getOrDefault(film.getId(), new LinkedHashSet<>()));
            film.setDirectors(directorsByFilmId.getOrDefault(film.getId(), new LinkedHashSet<>()));
        });
        return films;
    }

    private void checkUserExists(Long userId) {
        if (userStorage.findById(userId).isEmpty()) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
    }

    private void resolveMpa(Film film) {
        Mpa mpa = film.getMpa();
        if (mpa.getId() == null) {
            throw new ValidationException("У рейтинга MPA должен быть указан id");
        }

        Mpa stored = mpaStorage.findById(mpa.getId())
                .orElseThrow(() -> new NotFoundException("Рейтинг MPA с id=" + mpa.getId() + " не найден"));
        film.setMpa(stored);
    }

    private void resolveGenres(Film film) {
        Set<Genre> genres = film.getGenres();
        if (genres.isEmpty()) {
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

    private void resolveDirectors(Film film) {
        Set<Director> directors = film.getDirectors();
        if (directors.isEmpty()) {
            return;
        }

        boolean hasNullId = directors.stream().anyMatch(director -> director.getId() == null);
        if (hasNullId) {
            throw new ValidationException("У режиссёра должен быть указан id");
        }

        Set<Long> ids = directors.stream()
                .map(Director::getId)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, Director> stored = directorStorage.findAllByIds(ids).stream()
                .collect(Collectors.toMap(Director::getId, Function.identity()));

        Set<Director> resolved = new LinkedHashSet<>();
        for (Long id : ids) {
            Director director = stored.get(id);
            if (director == null) {
                throw new NotFoundException("Режиссёр с id=" + id + " не найден");
            }
            resolved.add(director);
        }

        film.setDirectors(resolved);
    }

    public Collection<Film> findByDirector(Long directorId, String sortBy) {
        directorStorage.findById(directorId)
                .orElseThrow(() -> new NotFoundException(
                        "Режиссёр с id=" + directorId + " не найден"));

        Collection<Film> films = switch (sortBy) {
            case "year" -> filmStorage.findByDirectorSortedByYear(directorId);
            case "likes" -> filmStorage.findByDirectorSortedByLikes(directorId);
            default -> throw new ValidationException(
                    "Параметр sortBy должен быть 'year' или 'likes', получено: " + sortBy);
        };

        return withDetails(films);
    }

    public Collection<Film> findCommonFilms(Long userId, Long friendId) {
        checkUserExists(userId);
        checkUserExists(friendId);
        return withDetails(filmStorage.findCommonFilms(userId, friendId));
    }

    public Collection<Film> searchFilm(String query, String by) {
        if (query == null || query.isBlank() || by == null || by.isBlank()) {
            return List.of();
        }

        return withDetails(filmStorage.searchFilm(query, by.toLowerCase()));
    }
}