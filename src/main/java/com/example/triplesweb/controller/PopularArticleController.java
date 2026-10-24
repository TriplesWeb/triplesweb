package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArticleViewDto;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.service.ArticleService;
import com.example.triplesweb.service.DateTimeFormatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * 인기글 목록. 좋아요 수 기준으로 전체 카테고리 게시글을 보여 준다.
 */
@Controller
@RequiredArgsConstructor
public class PopularArticleController {

    private final ArticleService articleService;
    private final DateTimeFormatService dateTimeFormatService;

    @GetMapping("/popular")
    public String showPopularArticles(Model model) {
        List<Article> popularArticles = articleService.findPopularArticles(null, 30);
        List<ArticleViewDto> articleViewList = new ArrayList<>();
        for (int i = 0; i < popularArticles.size(); i++) {
            Article art = popularArticles.get(i);
            articleViewList.add(ArticleViewDto.builder()
                    .id(art.getId())
                    .displayId((long) (i + 1))
                    .title(art.getTitle())
                    .userId(art.getUserId())
                    .category(art.getCategory())
                    .formattedDate(dateTimeFormatService.formatKst(art.getFormattedCreatedAt()))
                    .likesCount(art.getLikesCount())
                    .build());
        }
        model.addAttribute("category", "popular");
        model.addAttribute("categoryName", "인기글");
        model.addAttribute("isNotice", false);
        model.addAttribute("articleList", articleViewList);
        model.addAttribute("searchKeyword", "");
        model.addAttribute("isSearchTitle", true);
        model.addAttribute("isSearchContent", false);
        model.addAttribute("isSearchWriter", false);
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("isAdmin", true);
        model.addAttribute("hideWriteButton", true);
        return "article/popular";
    }
}
