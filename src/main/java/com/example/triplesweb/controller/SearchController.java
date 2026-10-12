package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.repository.ArticleRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class SearchController {
    @Autowired
    private ArticleRepository articleRepository;

    @GetMapping("/search")
    public String Search (@RequestParam("keyword") String keyword, HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        model.addAttribute("userId", userId);
        model.addAttribute("keyword", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            model.addAttribute("errorMessage", "검색어를 입력해주세요.");
            return "search";
        }

        List<Article> result = articleRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrUserIdContainingIgnoreCase(
                keyword, keyword, keyword, Sort.by(Sort.Direction.DESC, "formattedCreatedAt"));

        List<Map<String, Object>> mappedResult = result.stream().map(article -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", article.getId());
            map.put("category", article.getCategory());
            map.put("title", highlight(article.getTitle(), keyword));
            map.put("content", highlight(article.getContent(), keyword));
            map.put("userId", article.getUserId());

            String boardName = switch (article.getCategory()) {
                case "free" -> "자유게시판";
                case "notice" -> "공지게시판";
                case "trade" -> "교환게시판";
                default -> "기타";
            };
            map.put("boardName", boardName);
            return map;
        }).toList();

        model.addAttribute("resultList", mappedResult);
        return "search";
    }
    private String highlight(String text, String keyword) {
        if (text == null || keyword == null) return text;
        String escaped = HtmlUtils.htmlEscape(text);
        return escaped.replaceAll("(?i)(" + Pattern.quote(keyword) + ")", "<b>$1</b>");
    }
}
