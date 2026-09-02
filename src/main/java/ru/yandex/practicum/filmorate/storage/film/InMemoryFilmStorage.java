package ru.yandex.practicum.filmorate.storage.film;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.model.Film;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class InMemoryFilmStorage implements FilmStorage {

    private final Map<Long, Film> films = new LinkedHashMap<>();

    /**
     * Лайки фильмов: идентификатор фильма — идентификаторы поставивших лайк пользователей.
     */
    private final Map<Long, Set<Long>> likes = new HashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Collection<Film> findAll() {
        return films.values();
    }

    @Override
    public Optional<Film> findById(Long id) {
        return Optional.ofNullable(films.get(id));
    }

    @Override
    public Film create(Film film) {
        film.setId(idGenerator.getAndIncrement());
        films.put(film.getId(), film);
        log.debug("В хранилище добавлен фильм id={}", film.getId());
        return film;
    }

    @Override
    public Film update(Film film) {
        films.put(film.getId(), film);
        log.debug("В хранилище обновлён фильм id={}", film.getId());
        return film;
    }

    @Override
    public void addLike(Long filmId, Long userId) {
        likesOf(filmId).add(userId);
    }

    @Override
    public void removeLike(Long filmId, Long userId) {
        likesOf(filmId).remove(userId);
    }

    @Override
    public Collection<Film> findPopular(int count) {
        return films.values().stream()
                .sorted(Comparator.comparingInt((Film film) -> likesOf(film.getId()).size()).reversed()
                        .thenComparing(Film::getId))
                .limit(count)
                .toList();
    }

    private Set<Long> likesOf(Long filmId) {
        return likes.computeIfAbsent(filmId, id -> new LinkedHashSet<>());
    }
}
