package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ArticleFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleFileRepository extends JpaRepository<ArticleFile,Long> {

    @Query("select af.id from ArticleFile af where af.article.id = :articleId")
    List<Long> findFileIdsByArticleId(@Param("articleId") Long articleId);

    // (선택) savedName만으로 찾고 싶으면, 안전하게 JPQL로 필드명 명시
    @Query("select af from ArticleFile af where af.file.savedName = :savedName")
    Optional<ArticleFile> findBySavedName(@Param("savedName") String savedName);
}
