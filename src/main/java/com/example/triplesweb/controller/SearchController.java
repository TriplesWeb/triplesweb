package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.repository.ArticleRepository;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.entity.Artist;
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
    @Autowired
    private ArtistRepository artistRepository;

    @GetMapping("/search")
    public String Search(@RequestParam("keyword") String keyword, Model model) {
        keyword = keyword == null ? "" : keyword.trim();
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
                case "sell" -> "거래게시판";
                case "chat" -> "잡담게시판";
                case "info" -> "정보게시판";
                case "notice" -> "공지사항";
                default -> "기타";
            };
            map.put("boardName", boardName);
            return map;
        }).toList();

        List<Map<String, Object>> memberResults = artistRepository.findAll().stream()
                .filter(artist -> containsIgnoreCase(artist.getName(), keyword)
                        || containsIgnoreCase(artist.getEngName(), keyword))
                .sorted((a, b) -> {
                    boolean aExact = keyword.equalsIgnoreCase(a.getName()) || keyword.equalsIgnoreCase(a.getEngName());
                    boolean bExact = keyword.equalsIgnoreCase(b.getName()) || keyword.equalsIgnoreCase(b.getEngName());
                    return Boolean.compare(bExact, aExact);
                })
                .map(artist -> {
                    Map<String, Object> member = new HashMap<>();
                    member.put("artistId", artist.getArtistId());
                    member.put("name", artist.getName());
                    member.put("engName", artist.getEngName());
                    member.put("profileImage", artist.getDisplayProfileImage());
                    member.put("profileUrl", "/triples/artist?tab=member-" + artist.getArtistId());
                    member.put("birthDate", artist.getBirthDate());
                    member.put("debutDate", artist.getDebutDate());
                    return member;
                })
                .toList();

        model.addAttribute("resultList", mappedResult);
        model.addAttribute("memberResults", memberResults);
        model.addAttribute("hasAnyResults", !mappedResult.isEmpty() || !memberResults.isEmpty());
        return "search";
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && keyword != null
                && value.toLowerCase(java.util.Locale.ROOT).contains(keyword.toLowerCase(java.util.Locale.ROOT));
    }

    private String highlight(String text, String keyword) {
        if (text == null || keyword == null) return text;
        String escaped = HtmlUtils.htmlEscape(text);
        return escaped.replaceAll("(?i)(" + Pattern.quote(keyword) + ")", "<b>$1</b>");
    }
}