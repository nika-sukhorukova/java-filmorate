package ru.yandex.practicum.filmorate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Жанр фильма из справочника genres.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Genre {

    private Integer id;

    private String name;
}
