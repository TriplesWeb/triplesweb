
package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArticleRepository;
import com.example.triplesweb.repository.ArtistRepository;

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
    public String search(
            @RequestParam("keyword") String keyword,
            Model model
    ) {
        // 검색어를 별도 변수에 저장해 람다에서 안전하게 사용
        final String searchKeyword =
                keyword == null ? "" : keyword.trim();

        model.addAttribute("keyword", searchKeyword);

        if (searchKeyword.isEmpty()) {
            model.addAttribute("errorMessage", "검색어를 입력해주세요.");
            model.addAttribute("resultList", List.of());
            model.addAttribute("memberResults", List.of());
            model.addAttribute("hasAnyResults", false);
            return "search";
        }

        // 게시글 검색
        List<Article> result =
                articleRepository
                        .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrUserIdContainingIgnoreCase(
                                searchKeyword,
                                searchKeyword,
                                searchKeyword,
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "formattedCreatedAt"
                                )
                        );

        List<Map<String, Object>> mappedResult =
                result.stream()
                        .map(article -> {
                            Map<String, Object> map =
                                    new HashMap<>();

                            map.put("id", article.getId());
                            map.put(
                                    "category",
                                    article.getCategory()
                            );
                            map.put(
                                    "title",
                                    highlight(
                                            article.getTitle(),
                                            searchKeyword
                                    )
                            );
                            map.put(
                                    "content",
                                    highlight(
                                            article.getContent(),
                                            searchKeyword
                                    )
                            );
                            map.put(
                                    "userId",
                                    article.getUserId()
                            );

                            String boardName =
                                    switch (article.getCategory()) {
                                        case "free" -> "자유게시판";
                                        case "trade" -> "교환게시판";
                                        case "sell" -> "거래게시판";
                                        case "chat" -> "잡담게시판";
                                        case "info" -> "정보게시판";
                                        case "notice" -> "공지사항";
                                        default -> "기타";
                                    };

                            map.put("boardName", boardName);

                            return map;
                        })
                        .toList();

        // 멤버 이름 검색: 한국어 이름과 영어 이름 모두 검색
        List<Map<String, Object>> memberResults =
                artistRepository.findAll()
                        .stream()
                        .filter(artist ->
                                containsIgnoreCase(
                                        artist.getName(),
                                        searchKeyword
                                )
                                        || containsIgnoreCase(
                                        artist.getEngName(),
                                        searchKeyword
                                )
                        )
                        // 검색어와 이름이 정확히 일치하는 멤버 우선
                        .sorted((a, b) -> {
                            boolean aExact =
                                    searchKeyword.equalsIgnoreCase(
                                            a.getName()
                                    )
                                            || searchKeyword.equalsIgnoreCase(
                                            a.getEngName()
                                    );

                            boolean bExact =
                                    searchKeyword.equalsIgnoreCase(
                                            b.getName()
                                    )
                                            || searchKeyword.equalsIgnoreCase(
                                            b.getEngName()
                                    );

                            return Boolean.compare(
                                    bExact,
                                    aExact
                            );
                        })
                        .map(artist -> {
                            Map<String, Object> member =
                                    new HashMap<>();

                            member.put(
                                    "artistId",
                                    artist.getArtistId()
                            );
                            member.put("name", artist.getName());
                            member.put(
                                    "engName",
                                    artist.getEngName()
                            );
                            member.put(
                                    "profileImage",
                                    artist.getDisplayProfileImage()
                            );
                            member.put(
                                    "profileUrl",
                                    "/triples/artist?tab=member-"
                                            + artist.getArtistId()
                            );
                            member.put(
                                    "birthDate",
                                    artist.getBirthDate()
                            );
                            member.put(
                                    "debutDate",
                                    artist.getDebutDate()
                            );

                            return member;
                        })
                        .toList();

        // 검색 결과 전달
        model.addAttribute("resultList", mappedResult);
        model.addAttribute("memberResults", memberResults);
        model.addAttribute(
                "hasAnyResults",
                !mappedResult.isEmpty()
                        || !memberResults.isEmpty()
        );

        return "search";
    }

    /**
     * 대소문자를 구분하지 않고 문자열 포함 여부를 확인한다.
     */
    private boolean containsIgnoreCase(
            String value,
            String keyword
    ) {
        return value != null
                && keyword != null
                && value.toLowerCase(java.util.Locale.ROOT)
                .contains(
                        keyword.toLowerCase(
                                java.util.Locale.ROOT
                        )
                );
    }

    /**
     * 검색어와 일치하는 부분을 강조 표시한다.
     */
    private String highlight(
            String text,
            String keyword
    ) {
        if (text == null || keyword == null) {
            return text;
        }

        String escaped = HtmlUtils.htmlEscape(text);

        return escaped.replaceAll(
                "(?i)(" + Pattern.quote(keyword) + ")",
                "<b>$1</b>"
        );
    }
}
