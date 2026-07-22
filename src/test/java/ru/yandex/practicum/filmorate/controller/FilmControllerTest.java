package ru.yandex.practicum.filmorate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilmControllerTest {

    private FilmController controller;

    @BeforeEach
    void setUp() {
        controller = new FilmController();
    }

    private Film.FilmBuilder validFilm() {
        return Film.builder()
                .name("Интерстеллар")
                .description("Фильм про космос")
                .releaseDate(LocalDate.of(2014, 11, 6))
                .duration(169);
    }

    @Test
    void create_assignsIdAndStoresFilm() {
        Film created = controller.create(validFilm().build());

        assertThat(created.getId()).isNotNull();
        assertThat(controller.findAll()).containsExactly(created);
    }

    @Test
    void create_assignsIncrementingIds() {
        Film first = controller.create(validFilm().build());
        Film second = controller.create(validFilm().build());

        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(controller.findAll()).hasSize(2);
    }

    @Test
    void update_existingFilm_isUpdated() {
        Film created = controller.create(validFilm().build());

        Film updated = controller.update(validFilm().id(created.getId()).name("Новое имя").build());

        assertThat(updated.getName()).isEqualTo("Новое имя");
        assertThat(controller.findAll()).containsExactly(updated);
    }

    @Test
    void update_withoutId_throwsValidationException() {
        assertThatThrownBy(() -> controller.update(validFilm().id(null).build()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_unknownId_throwsNotFoundException() {
        assertThatThrownBy(() -> controller.update(validFilm().id(999L).build()))
                .isInstanceOf(NotFoundException.class);
    }
}
