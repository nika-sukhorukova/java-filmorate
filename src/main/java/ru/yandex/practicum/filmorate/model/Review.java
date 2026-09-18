package ru.yandex.practicum.filmorate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    private Long reviewId;

    @NotBlank(message = "Текст отзыва не может быть пустым")
    private String content;

    @NotNull(message = "Тип отзыва (положительный/отрицательный) должен быть указан")
    private Boolean isPositive;

    @NotNull(message = "Пользователь должен быть указан")
    private Long userId;

    @NotNull(message = "Фильм должен быть указан")
    private Long filmId;

    /**
     * Рейтинг полезности: сумма голосов "полезно" (+1) и "бесполезно" (-1). Хранится
     * не в таблице reviews, а вычисляется по review_likes, чтобы не рассинхронизироваться.
     */
    @Builder.Default
    private int useful = 0;
}
