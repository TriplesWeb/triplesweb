package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ArticleObjekt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface ArticleObjektRepository extends JpaRepository<ArticleObjekt, Long> {
    List<ArticleObjekt> findByArticle_IdOrderByIdAsc(Long articleId);
    void deleteByArticle_Id(Long articleId);

    @Query("""
            SELECT ao FROM ArticleObjekt ao
            JOIN FETCH ao.article
            WHERE ao.article.id IN :ids
            ORDER BY ao.id ASC
            """)
    List<ArticleObjekt> findByArticle_IdInOrderByIdAsc(@Param("ids") Collection<Long> ids);
}