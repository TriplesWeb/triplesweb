package com.example.triplesweb.repository;


import com.example.triplesweb.entity.ArticleLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArticleLikeRepository extends JpaRepository<ArticleLike, Long> {
    boolean existsByArticleIdAndUserId(Long articleId, String userId);

    Optional<ArticleLike> findByArticleIdAndUserId(Long articleId, String userId);

    @Query("select count(al) from ArticleLike al where al.article.id = :articleId")
    long countLikesByArticleId(@Param("articleId")Long articleId);

}