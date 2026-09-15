package ru.yandex.practicum.filmorate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Рейтинг Ассоциации кинокомпаний (MPA) — возрастное ограничение фильма.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mpa {

    private Integer id;

    private String name;
}
