
package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;

import java.util.List;

/**
 * 게시글({@link Article}) 생성·조회·삭제 및 카테고리별 최신·인기 목록 계약.
 * 첨부 포함 조회, 사용자별 좋아요 여부·토글·집계를 포함한다.
 */
public interface ArticleService {
    Article create(Article article);
    Article show(Long id);
    List<Article> findRecentByCategory(String category, int limit);
    long getLikesCount(Long articleId); // 구현 없으면 0 리턴
    Article delete(Long id);
    boolean isLikedByUser(Long articleId, String userId);
    boolean toggleLike(Long articleId, String userId);

    Article showWithFiles(Long id);
    List<Article> findPopularArticles(String free, int limit);
}
