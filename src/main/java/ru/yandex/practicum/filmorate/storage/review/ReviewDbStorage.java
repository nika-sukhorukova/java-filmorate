package ru.yandex.practicum.filmorate.storage.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Review;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ReviewDbStorage implements ReviewStorage {

    private static final String SELECT_REVIEW = """
            SELECT r.id,
                   r.content,
                   r.is_positive,
                   r.user_id,
                   r.film_id,
                   COALESCE(SUM(CASE
                                    WHEN rl.review_id IS NULL THEN 0
                                    WHEN rl.is_useful THEN 1
                                    ELSE -1
                                END), 0) AS useful
            FROM reviews AS r
            LEFT JOIN review_likes AS rl ON rl.review_id = r.id
            """;

    private static final String GROUP_BY_REVIEW =
            " GROUP BY r.id, r.content, r.is_positive, r.user_id, r.film_id";

    private static final RowMapper<Review> REVIEW_MAPPER = ReviewDbStorage::mapReview;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Review create(Review review) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("content", review.getContent())
                .addValue("isPositive", review.getIsPositive())
                .addValue("userId", review.getUserId())
                .addValue("filmId", review.getFilmId());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO reviews (content, is_positive, user_id, film_id)"
                        + " VALUES (:content, :isPositive, :userId, :filmId)",
                params, keyHolder);

        review.setReviewId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        review.setUseful(0);
        log.debug("В базу добавлен отзыв id={}", review.getReviewId());
        return review;
    }

    @Override
    public Review update(Review review) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("content", review.getContent())
                .addValue("isPositive", review.getIsPositive())
                .addValue("id", review.getReviewId());

        jdbcTemplate.update("UPDATE reviews SET content = :content, is_positive = :isPositive WHERE id = :id",
                params);
        log.debug("В базе обновлён отзыв id={}", review.getReviewId());
        return review;
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM reviews WHERE id = :id", new MapSqlParameterSource("id", id));
        log.debug("Из базы удалён отзыв id={}", id);
    }

    @Override
    public Optional<Review> findById(Long id) {
        String sql = SELECT_REVIEW + " WHERE r.id = :id" + GROUP_BY_REVIEW;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), REVIEW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Collection<Review> findByFilmId(Long filmId, int count) {
        MapSqlParameterSource params = new MapSqlParameterSource("count", count);

        if (filmId != null) {
            params.addValue("filmId", filmId);
            String sql = SELECT_REVIEW + " WHERE r.film_id = :filmId" + GROUP_BY_REVIEW
                    + " ORDER BY useful DESC, r.id LIMIT :count";
            return jdbcTemplate.query(sql, params, REVIEW_MAPPER);
        }

        String sql = SELECT_REVIEW + GROUP_BY_REVIEW + " ORDER BY useful DESC, r.id LIMIT :count";
        return jdbcTemplate.query(sql, params, REVIEW_MAPPER);
    }

    @Override
    public void addLike(Long reviewId, Long userId) {
        jdbcTemplate.update("MERGE INTO review_likes (review_id, user_id, is_useful) KEY (review_id, user_id)"
                + " VALUES (:reviewId, :userId, TRUE)", likeParams(reviewId, userId));
    }

    @Override
    public void addDislike(Long reviewId, Long userId) {
        jdbcTemplate.update("MERGE INTO review_likes (review_id, user_id, is_useful) KEY (review_id, user_id)"
                + " VALUES (:reviewId, :userId, FALSE)", likeParams(reviewId, userId));
    }

    @Override
    public void removeLike(Long reviewId, Long userId) {
        jdbcTemplate.update("DELETE FROM review_likes WHERE review_id = :reviewId AND user_id = :userId"
                + " AND is_useful = TRUE", likeParams(reviewId, userId));
    }

    @Override
    public void removeDislike(Long reviewId, Long userId) {
        jdbcTemplate.update("DELETE FROM review_likes WHERE review_id = :reviewId AND user_id = :userId"
                + " AND is_useful = FALSE", likeParams(reviewId, userId));
    }

    private static SqlParameterSource likeParams(Long reviewId, Long userId) {
        return new MapSqlParameterSource()
                .addValue("reviewId", reviewId)
                .addValue("userId", userId);
    }

    private static Review mapReview(ResultSet rs, int rowNum) throws SQLException {
        return Review.builder()
                .reviewId(rs.getLong("id"))
                .content(rs.getString("content"))
                .isPositive(rs.getBoolean("is_positive"))
                .userId(rs.getLong("user_id"))
                .filmId(rs.getLong("film_id"))
                .useful(rs.getInt("useful"))
                .build();
    }
}
