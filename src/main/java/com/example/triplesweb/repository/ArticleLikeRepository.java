package com.example.triplesweb.repository;


import com.example.triplesweb.entity.ArticleLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 게시글 좋아요({@link ArticleLike}) JPA 저장소.
 * 사용자·게시글 쌍 존재/조회 및 게시글별 좋아요 건수 집계 쿼리를 제공한다.
 */
public interface ArticleLikeRepository extends JpaRepository<ArticleLike, Long> {
    boolean existsByArticleIdAndUserId(Long articleId, String userId);

    Optional<ArticleLike> findByArticleIdAndUserId(Long articleId, String userId);

    @Query("select count(al) from ArticleLike al where al.article.id = :articleId")
    long countLikesByArticleId(@Param("articleId")Long articleId);

}