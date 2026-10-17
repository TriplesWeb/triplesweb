package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ArticleFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 첨부({@link ArticleFile}) JPA 저장소.
 * 게시글별 파일 ID 목록, savedName 단건 조회 JPQL을 제공한다.
 */
public interface ArticleFileRepository extends JpaRepository<ArticleFile,Long> {

    @Query("select af.id from ArticleFile af where af.article.id = :articleId")
    List<Long> findFileIdsByArticleId(@Param("articleId") Long articleId);

    // (선택) savedName만으로 찾고 싶으면, 안전하게 JPQL로 필드명 명시
    @Query("select af from ArticleFile af where af.file.savedName = :savedName")
    Optional<ArticleFile> findBySavedName(@Param("savedName") String savedName);
}
