package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Comment;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 댓글({@link Comment}) JPA 저장소.
 * 게시글 ID 조회({@link #findByArticleId}), 작성자별 정렬 목록을 지원한다.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("SELECT c FROM Comment c WHERE c.article.id = :articleId")
    List<Comment> findByArticleId(@Param("articleId") Long articleId);

    List<Comment> findByUserId(String userId, Sort sort);
}
