package ru.yandex.practicum.filmorate.storage.director;

import ru.yandex.practicum.filmorate.model.Director;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface DirectorStorage {
    Collection<Director> findAll();

    Optional<Director> findById(Long id);

    Director create(Director director);

    Director update(Director director);

    void delete(Long id);

    Collection<Director> findAllByIds(Set<Long> ids);

    Map<Long, Set<Director>> findByFilmIds(Collection<Long> filmIds);

    Set<Director> findByFilmId(Long filmId);

    void saveFilmDirectors(Long filmId, Collection<Director> directors);

    void deleteFilmDirectors(Long filmId);
}