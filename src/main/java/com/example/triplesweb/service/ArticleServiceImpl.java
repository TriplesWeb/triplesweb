
package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleLike;
import com.example.triplesweb.repository.ArticleLikeRepository;
import com.example.triplesweb.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

/**
 * {@link ArticleService} 구현체. 게시글 CRUD와 카테고리별 최신·인기 조회를 수행한다.
 * {@link ArticleLikeRepository}로 좋아요 존재 여부, 토글, 건수 조회를 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleLikeRepository articleLikeRepository;

    @Override
    public Article create(Article article) {
        Long maxDisplayId = articleRepository.findMaxDisplayIdByCategory(article.getCategory());
        long nextDisplayId = (maxDisplayId == null) ? 1L : maxDisplayId + 1;
        article.setDisplayId(nextDisplayId);
        return articleRepository.save(article);
    }

    @Override
    @Transactional(readOnly = true)
    public Article showWithFiles(Long id) {
        return articleRepository.findByIdWithFiles(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Article show(Long id) {
        return articleRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Article> findRecentByCategory(String category, int limit) {
        // 레포에 맞춰 하나만 사용하세요.
        return articleRepository.findByCategoryIgnoreCaseOrderByIdDesc(category, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Article> findPopularArticles(String category, int limit) {
        // ⭐ 이미 “추천글 기능 있음”이라고 하셨으니,
        // 여기서는 기존 repository 메서드를 그대로 호출하면 됩니다.
        return articleRepository.findPopularByCategory(category, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public long getLikesCount(Long articleId) {
        // 좋아요 별도 테이블이면 여기서 조회, 없으면 0
        return articleLikeRepository.countLikesByArticleId(articleId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLikedByUser(Long articleId, String userId) {
        return articleLikeRepository.existsByArticleIdAndUserId(articleId, userId);
    }

    @Override
    public boolean toggleLike(Long articleId, String userId) {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음"));

        return articleLikeRepository
                .findByArticleIdAndUserId(articleId, userId)
                .map(like -> {
                    articleLikeRepository.delete(like); // 취소
                    return false;
                }).orElseGet(() -> {
                    ArticleLike like = new ArticleLike();
                    like.setArticle(article);
                    like.setUserId(userId);
                    articleLikeRepository.save(like);   // 추천
                    return true;
                });
    }

    @Override
    public Article delete(Long id) {
        return articleRepository.findById(id)
                .map(a -> {
                    articleRepository.delete(a);
                    return a;
                })
                .orElse(null);
    }
}