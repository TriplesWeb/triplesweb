package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ArticleObjekt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleObjektRepository extends JpaRepository<ArticleObjekt, Long> {
    List<ArticleObjekt> findByArticleIdOrderByIdAsc(Long articleId);
    void deleteByArticleId(Long articleId);
}
