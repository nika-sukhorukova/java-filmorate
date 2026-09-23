package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.model.Event;
import ru.yandex.practicum.filmorate.model.EventOperation;
import ru.yandex.practicum.filmorate.model.EventType;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.EventService;
import ru.yandex.practicum.filmorate.storage.event.EventDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase
@Import({UserDbStorage.class, EventDbStorage.class})
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class FeedControllerTest {

    private final UserDbStorage userStorage;
    private final EventDbStorage eventStorage;

    private FeedController controller;

    @BeforeEach
    void setUp() {
        EventService eventService = new EventService(eventStorage, userStorage);
        controller = new FeedController(eventService);
    }

    @Test
    void getFeed_returnsUserEvents() {
        User user = userStorage.create(validUser("first"));

        Event first = eventStorage.create(event(
                1000L,
                user.getId(),
                EventType.LIKE,
                EventOperation.ADD,
                10L
        ));
        Event second = eventStorage.create(event(
                2000L,
                user.getId(),
                EventType.FRIEND,
                EventOperation.REMOVE,
                20L
        ));

        Collection<Event> feed = controller.getFeed(user.getId());

        assertThat(feed).containsExactly(first, second);
    }

    @Test
    void getFeed_withoutEvents_returnsEmptyCollection() {
        User user = userStorage.create(validUser("first"));

        assertThat(controller.getFeed(user.getId())).isEmpty();
    }

    @Test
    void getFeed_unknownUser_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.getFeed(999L))
                .isInstanceOf(NotFoundException.class);
    }

    private User validUser(String login) {
        return User.builder()
                .email(login + "@mail.ru")
                .login(login)
                .name("Имя " + login)
                .birthday(LocalDate.of(1990, 1, 1))
                .build();
    }

    private Event event(Long timestamp,
                        Long userId,
                        EventType eventType,
                        EventOperation operation,
                        Long entityId) {
        return Event.builder()
                .timestamp(timestamp)
                .userId(userId)
                .eventType(eventType)
                .operation(operation)
                .entityId(entityId)
                .build();
    }
}
