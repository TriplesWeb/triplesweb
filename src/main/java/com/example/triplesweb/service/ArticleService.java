
package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;

import java.util.List;

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
