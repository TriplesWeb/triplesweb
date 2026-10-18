package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleLike;
import com.example.triplesweb.repository.ArticleLikeRepository;
import com.example.triplesweb.repository.ArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Optional;

@Controller
public class PopularArticleController {
    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleLikeRepository articleLikeRepository;

    @GetMapping("/popular")
    public String showPopularArticles(Model model) {
        List<Article> popularArticles = articleRepository.findByLikesCountGreaterThanEqualOrderByLikesCountDesc(5, PageRequest.of(0, 10));
        model.addAttribute("articleList", popularArticles);
        return "article/popular";
    }

    /** [수정] 로그인 세션 체크 제거 및 익명 좋아요 처리 */
    @PostMapping("/{category}/{id}/like")
    @ResponseBody
    public String likeArticle(@PathVariable String category, @PathVariable Long id) {
        String userId = "anonymous"; // 로그인 기능 삭제로 고정 익명 처리

        Optional<Article> optional = articleRepository.findById(id);
        if (optional.isEmpty()) return "fail";

        if (articleLikeRepository.existsByArticleIdAndUserId(id, userId)) {
            return "already"; // 이미 추천했음
        }

        Article article = optional.get();
        if (!article.getCategory().equalsIgnoreCase(category)) {
            return "fail";
        }

        ArticleLike like = new ArticleLike();
        like.setArticle(article);
        like.setUserId(userId);
        articleLikeRepository.save(like);

        article.setLikesCount(article.getLikesCount() + 1);
        articleRepository.save(article);

        return "ok";
    }
}