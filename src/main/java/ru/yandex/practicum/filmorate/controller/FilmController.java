package ru.yandex.practicum.filmorate.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.service.FilmService;

import java.util.Collection;

@Slf4j
@RestController
@RequestMapping("/films")
public class FilmController {

    private static final String DEFAULT_POPULAR_COUNT = "10";

    private final FilmService filmService;

    @Autowired
    public FilmController(FilmService filmService) {
        this.filmService = filmService;
    }

    @GetMapping
    public Collection<Film> findAll() {
        return filmService.findAll();
    }

    @GetMapping("/popular")
    public Collection<Film> getPopular(@RequestParam(defaultValue = DEFAULT_POPULAR_COUNT) int count,
                                        @RequestParam(required = false) Integer genreId,
                                        @RequestParam(required = false) Integer year) {
        log.debug("GET /films/popular?count={}&genreId={}&year={}", count, genreId, year);
        return filmService.getPopular(count, genreId, year);
    }

    @GetMapping("/{id}")
    public Film findById(@PathVariable Long id) {
        log.debug("GET /films/{}", id);
        return filmService.findById(id);
    }

    @PostMapping
    public Film create(@Valid @RequestBody Film film) {
        return filmService.create(film);
    }

    @PutMapping
    public Film update(@Valid @RequestBody Film film) {
        return filmService.update(film);
    }

    @PutMapping("/{id}/like/{userId}")
    public void addLike(@PathVariable Long id, @PathVariable Long userId) {
        log.debug("PUT /films/{}/like/{}", id, userId);
        filmService.addLike(id, userId);
    }

    @DeleteMapping("/{id}/like/{userId}")
    public void removeLike(@PathVariable Long id, @PathVariable Long userId) {
        log.debug("DELETE /films/{}/like/{}", id, userId);
        filmService.removeLike(id, userId);
    }

    @GetMapping("/director/{directorId}")
    public Collection<Film> findByDirector(
            @PathVariable Long directorId,
            @RequestParam(defaultValue = "year") String sortBy) {
        log.debug("GET /films/director/{}?sortBy={}", directorId, sortBy);
        return filmService.findByDirector(directorId, sortBy);
    }

    @GetMapping("/common")
    public Collection<Film> findCommonFilms(@RequestParam Long userId, @RequestParam Long friendId) {
        log.debug("GET /films/common?userId={}&friendId={}", userId, friendId);
        return filmService.findCommonFilms(userId, friendId);
    }

    @DeleteMapping("/{id}")
    public void deleteFilm( @PathVariable long id) {
        log.debug("DELETE /films/{}", id);
        filmService.deleteFilm(id);
    }
}