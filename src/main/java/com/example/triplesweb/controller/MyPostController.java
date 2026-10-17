package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.repository.ArticleRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 로그인 사용자 본인 게시글 목록.
 * URL: {@code GET /myposts}
 * 레이어: Optional — ArticleRepository 직접 사용(Repository 단독 허용).
 */
@Controller
public class MyPostController {
    @Autowired
    private ArticleRepository articleRepository;

    @GetMapping("/myposts")
    public String myArticles(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            model.addAttribute("errorMessage", "로그인 후 이용해주세요.");
            return "redirect:/members/login";
        }

        List<Article> myList = articleRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "formattedCreatedAt"));

        // boardName 같이 넣어야 하므로 map 변환
        List<Map<String, Object>> mappedResult = myList.stream().map(article -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", article.getId());
            map.put("category", article.getCategory());
            map.put("title", article.getTitle());
            map.put("content", article.getContent());
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
        model.addAttribute("keyword", "내가 작성한 글");
        return "members/myposts";
    }

}
