package ru.yandex.practicum.filmorate.storage.event;

import ru.yandex.practicum.filmorate.model.Event;

import java.util.Collection;

public interface EventStorage {

    Event create(Event event);

    Collection<Event> findByUserId(Long userId);
}
