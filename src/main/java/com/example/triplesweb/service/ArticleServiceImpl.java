package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link ArticleService} 구현체. 게시글 CRUD와 카테고리별 최신·인기 조회를 수행한다.
 * 좋아요는 {@link Article#likesCount} 카운터만 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;

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
        return articleRepository.findByCategoryIgnoreCaseOrderByIdDesc(category, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Article> searchByCategory(String category, String type, String keyword, int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        var pageable = PageRequest.of(0, safeLimit);
        if (keyword == null || keyword.isBlank()) return findRecentByCategory(category, safeLimit);
        String query = keyword.trim();
        return switch (type == null ? "title" : type.toLowerCase(java.util.Locale.ROOT)) {
            case "content" -> articleRepository.findByCategoryIgnoreCaseAndContentContainingIgnoreCaseOrderByIdDesc(category, query, pageable);
            case "writer", "userid", "author" -> articleRepository.findByCategoryIgnoreCaseAndUserIdContainingIgnoreCaseOrderByIdDesc(category, query, pageable);
            default -> articleRepository.findByCategoryIgnoreCaseAndTitleContainingIgnoreCaseOrderByIdDesc(category, query, pageable);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<Article> findPopularArticles(String category, int limit) {
        return articleRepository.findPopularByCategory(category, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public long getLikesCount(Long articleId) {
        return articleRepository.findById(articleId)
                .map(a -> (long) a.getLikesCount())
                .orElse(0L);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLikedByUser(Long articleId, String userId) {
        // 사용자별 좋아요 이력 테이블을 두지 않음
        return false;
    }

    @Override
    public boolean toggleLike(Long articleId, String userId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음"));
        article.incrementLikes();
        articleRepository.save(article);
        return true;
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
