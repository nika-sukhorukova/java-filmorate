package ru.yandex.practicum.filmorate.storage;

import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.mpa.MpaStorage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Справочник рейтингов MPA в памяти — используется в модульных тестах вместо обращения к базе.
 */
public class InMemoryMpaStorage implements MpaStorage {

    private final Map<Integer, Mpa> ratings = new LinkedHashMap<>();

    public InMemoryMpaStorage() {
        List<String> names = List.of("G", "PG", "PG-13", "R", "NC-17");
        for (int i = 0; i < names.size(); i++) {
            int id = i + 1;
            ratings.put(id, Mpa.builder().id(id).name(names.get(i)).build());
        }
    }

    @Override
    public Collection<Mpa> findAll() {
        return ratings.values();
    }

    @Override
    public Optional<Mpa> findById(Integer id) {
        return Optional.ofNullable(ratings.get(id));
    }
}
