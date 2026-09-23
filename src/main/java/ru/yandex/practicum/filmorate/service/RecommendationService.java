package ru.yandex.practicum.filmorate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserStorage userStorage;
    private final FilmService filmService;

    public Collection<Film> getRecommendations(Long userId) {
        checkUserExists(userId);
        return filmService.findRecommendations(userId);
    }

    private void checkUserExists(Long userId) {
        if (userStorage.findById(userId).isEmpty()) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
    }
}
