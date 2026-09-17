package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.service.DirectorService;
import ru.yandex.practicum.filmorate.storage.director.DirectorDbStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase
@Import(DirectorDbStorage.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class DirectorControllerTest {

    private final DirectorDbStorage directorStorage;

    private DirectorController controller;

    @BeforeEach
    void setUp() {
        controller = new DirectorController(new DirectorService(directorStorage));
    }

    private Director.DirectorBuilder validDirector() {
        return Director.builder().name("Кристофер Нолан");
    }

    @Test
    void create_assignsIdAndStoresDirector() {
        Director created = controller.create(validDirector().build());

        assertThat(created.getId()).isNotNull();
        assertThat(controller.findAll()).containsExactly(created);
    }

    @Test
    void create_assignsIncrementingIds() {
        Director first = controller.create(validDirector().name("Первый").build());
        Director second = controller.create(validDirector().name("Второй").build());

        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    void findAll_returnsAllDirectorsOrderedById() {
        controller.create(validDirector().name("Первый").build());
        controller.create(validDirector().name("Второй").build());

        assertThat(controller.findAll())
                .extracting(Director::getName)
                .containsExactly("Первый", "Второй");
    }

    @Test
    void findAll_empty_returnsEmpty() {
        assertThat(controller.findAll()).isEmpty();
    }

    @Test
    void findById_existingDirector_isReturned() {
        Director created = controller.create(validDirector().build());

        assertThat(controller.findById(created.getId())).isEqualTo(created);
    }

    @Test
    void findById_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.findById(999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_existingDirector_isUpdated() {
        Director created = controller.create(validDirector().build());

        Director updated = controller.update(
                validDirector().id(created.getId()).name("Новое имя").build());

        assertThat(updated.getName()).isEqualTo("Новое имя");
        assertThat(controller.findAll()).containsExactly(updated);
    }

    @Test
    void update_withoutId_throwsValidationException() {
        assertThatThrownBy(() -> controller.update(validDirector().id(null).build()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.update(validDirector().id(999L).build()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_removesDirector() {
        Director created = controller.create(validDirector().build());

        controller.delete(created.getId());

        assertThat(controller.findAll()).isEmpty();
    }

    @Test
    void delete_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.delete(999L))
                .isInstanceOf(NotFoundException.class);
    }
}