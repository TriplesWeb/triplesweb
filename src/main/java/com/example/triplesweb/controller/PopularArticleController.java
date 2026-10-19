package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
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

    @GetMapping("/popular")
    public String showPopularArticles(Model model) {
        List<Article> popularArticles = articleRepository.findByLikesCountGreaterThanEqualOrderByLikesCountDesc(5, PageRequest.of(0, 10));
        model.addAttribute("articleList", popularArticles);
        return "article/popular";
    }

    /** 좋아요는 Article.likesCount만 증가 (사용자별 이력 없음) */
    @PostMapping("/{category}/{id}/like")
    @ResponseBody
    public String likeArticle(@PathVariable String category, @PathVariable Long id) {
        Optional<Article> optional = articleRepository.findById(id);
        if (optional.isEmpty()) return "fail";

        Article article = optional.get();
        if (!article.getCategory().equalsIgnoreCase(category)) {
            return "fail";
        }

        article.incrementLikes();
        articleRepository.save(article);
        return "ok";
    }
}
