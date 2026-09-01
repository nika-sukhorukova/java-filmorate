package ru.yandex.practicum.filmorate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.yandex.practicum.filmorate.annotations.IsAfterCinemaBirth;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Film {
    private Long id;

    @NotBlank(message = "Название фильма не может быть пустым")
    private String name;

    @Size(max = 200, message = "Максимальная длина описания - 200 символов")
    private String description;

    @IsAfterCinemaBirth
    @NotNull
    private LocalDate releaseDate;

    @Positive
    @NotNull
    private Integer duration;

    /**
     * Жанры фильма. При создании и обновлении достаточно передать идентификаторы.
     */
    @Builder.Default
    private Set<Genre> genres = new LinkedHashSet<>();

    /**
     * Возрастной рейтинг MPA. При создании и обновлении достаточно передать идентификатор.
     */
    private Mpa mpa;
}
