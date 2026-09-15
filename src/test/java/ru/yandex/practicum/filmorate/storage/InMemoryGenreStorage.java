package ru.yandex.practicum.filmorate.storage;

import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.storage.genre.GenreStorage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Справочник жанров в памяти — используется в модульных тестах вместо обращения к базе.
 */
public class InMemoryGenreStorage implements GenreStorage {

    private final Map<Integer, Genre> genres = new LinkedHashMap<>();

    public InMemoryGenreStorage() {
        List<String> names = List.of("Комедия", "Драма", "Мультфильм", "Триллер", "Документальный", "Боевик");
        for (int i = 0; i < names.size(); i++) {
            int id = i + 1;
            genres.put(id, Genre.builder().id(id).name(names.get(i)).build());
        }
    }

    @Override
    public Collection<Genre> findAll() {
        return genres.values();
    }

    @Override
    public Optional<Genre> findById(Integer id) {
        return Optional.ofNullable(genres.get(id));
    }

    @Override
    public Collection<Genre> findAllByIds(Set<Integer> ids) {
        return ids.stream()
                .map(genres::get)
                .filter(genre -> genre != null)
                .toList();
    }
}
