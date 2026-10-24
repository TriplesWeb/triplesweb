package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArticleFileDto;
import com.example.triplesweb.dto.ArticleForm;
import com.example.triplesweb.dto.ArticleViewDto;
import com.example.triplesweb.dto.ArticleViewMapper;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.service.ArticleFileServiceImpl;
import com.example.triplesweb.service.ArticleService;
import com.example.triplesweb.service.ArticleObjektService;
import com.example.triplesweb.service.ObjektService;
import com.example.triplesweb.service.CommentService;
import com.example.triplesweb.service.DateTimeFormatService;
import com.example.triplesweb.util.ArticleHtmlCleaner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/article/{category}")
public class ArticleController {

    private final ArticleService articleService;
    private final CommentService commentService;
    private final ArticleFileServiceImpl articleFileService;
    private final DateTimeFormatService dateTimeFormatService;
    private final ArticleObjektService articleObjektService;
    private final ObjektService objektService;

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "free", "chat", "trade", "sell", "info", "notice"
    );
    private static final Set<String> LIKE_ALLOWED_CATEGORIES = Set.of("free", "chat", "info");

    private boolean isInvalidCategory(String category) {
        return !ALLOWED_CATEGORIES.contains(category);
    }

    @InitBinder("article")
    public void disallowFilesBinding(WebDataBinder binder) {
        binder.setDisallowedFields("files");
    }

    /** 작성 폼 */
    @GetMapping("/new")
    public String showArticleForm(@PathVariable("category") String category,
                                  Model model) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        // 로그인 체크 및 관리자 체크 제거 (누구나 작성 가능)
        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        model.addAttribute("articleForm", new ArticleForm());
        addObjektOptions(model, category);
        return "article/article_new";
    }

    /** 생성 */
    @PostMapping("/new")
    public String createArticle(@PathVariable("category") String category,
                                @Valid @ModelAttribute("articleForm") ArticleForm form,
                                BindingResult bindingResult,
                                Model model,
                                @RequestParam(value = "uploadFiles", required = false) List<MultipartFile> files,
                                @RequestParam(value = "uploadedFiles", required = false) List<String> uploadedFiles,
                                @RequestParam(value = "selectedObjektIds", required = false) List<String> selectedObjektIds) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        String cleanedContent = ArticleHtmlCleaner.clean(form.getContent());
        form.setContent(cleanedContent);
        if (bindingResult.hasErrors()
                || form.getTitle() == null || form.getTitle().trim().isEmpty()
                || cleanedContent == null || cleanedContent.isBlank()) {
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            model.addAttribute("errorMessage", "제목과 내용을 모두 입력해주세요.");
            model.addAttribute("articleForm", form);
            addObjektOptions(model, category, selectedObjektIds == null ? Set.of() : Set.copyOf(selectedObjektIds));
            return "article/article_new";
        }

        Article article = new Article();
        article.setTitle(form.getTitle().trim());
        article.setContent(cleanedContent);
        article.setCategory(category);
        article.setUserId("익명"); // 로그인 기능 삭제로 기본 작성자 지정

        // 1) 게시글 생성
        Article savedArticle = articleService.create(article);
        if ("trade".equals(category) || "sell".equals(category)) {
            articleObjektService.attachSelected(savedArticle, selectedObjektIds);
        }

        // 2) 파일 저장 처리
        if (files != null) {
            for (MultipartFile mf : files) {
                if (mf != null && !mf.isEmpty()) {
                    articleFileService.uploadFile(mf, savedArticle);
                }
            }
        }

        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            articleFileService.attachUploadedFiles(uploadedFiles, savedArticle);
        }

        articleFileService.extractAndSaveFilesFromContent(savedArticle);

        return "redirect:/article/" + category + "/" + savedArticle.getId();
    }

    private void addObjektOptions(Model model, String category) {
        addObjektOptions(model, category, Set.of());
    }

    private void addObjektOptions(Model model, String category, Set<String> selectedKeys) {
        if (!("trade".equals(category) || "sell".equals(category))) {
            return;
        }
        Set<String> selected = selectedKeys == null ? Set.of() : selectedKeys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toSet());

        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> members = new LinkedHashSet<>();
        Set<String> seasons = new LinkedHashSet<>();
        Set<String> classes = new LinkedHashSet<>();

        for (Map<String, Object> item : objektService.findCollections("triples")) {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            String id = stringValue(item.get("id"));
            String collectionId = stringValue(item.get("collectionId"));
            String slug = stringValue(item.get("slug"));
            boolean isSelected = selected.contains(id)
                    || selected.contains(collectionId)
                    || selected.contains(slug);
            copy.put("selected", isSelected);
            options.add(copy);

            String member = stringValue(item.get("member"));
            String season = stringValue(item.get("season"));
            String klass = stringValue(item.get("class"));
            if (!member.isBlank()) members.add(member);
            if (!season.isBlank()) seasons.add(season);
            if (!klass.isBlank()) classes.add(klass);
        }

        options.sort(Comparator
                .comparing((Map<String, Object> o) -> !Boolean.TRUE.equals(o.get("selected")))
                .thenComparing(o -> stringValue(o.get("member")), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(o -> stringValue(o.get("collectionId")), String.CASE_INSENSITIVE_ORDER));

        model.addAttribute("showObjektPicker", true);
        model.addAttribute("objektOptions", options);
        model.addAttribute("objektMemberOptions", members.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("objektSeasonOptions", seasons.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("objektClassOptions", classes.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String getCategoryName(String category) {
        return switch (category) {
            case "free" -> "자유";
            case "chat" -> "잡담";
            case "trade" -> "교환";
            case "sell" -> "거래";
            case "info" -> "정보";
            case "notice" -> "공지사항";
            default -> "게시판";
        };
    }

    /** 목록 */

    /** 목록 및 검색 */
    @GetMapping
    public String indexArticle(
            @PathVariable("category") String category,
            Model model,
            @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit,
            @RequestParam(name = "type", required = false, defaultValue = "title") String type,
            @RequestParam(name = "keyword", required = false, defaultValue = "") String keyword) {

        if (isInvalidCategory(category)) {
            return "redirect:/article/trade";
        }

        // 검색 조건 정리
        String searchType = type;
        if (!Set.of("title", "content", "writer").contains(searchType)) {
            searchType = "title";
        }

        String searchKeyword = keyword == null ? "" : keyword.trim();

        // 검색어가 있으면 검색, 없으면 최신 게시글 조회
        List<Article> articles;
        if (searchKeyword.isEmpty()) {
            articles = articleService.findRecentByCategory(category, limit);
        } else {
            articles = articleService.searchByCategory(
                    category, searchType, searchKeyword, limit);
        }

        List<ArticleViewDto> articleViewList = new ArrayList<>();

        for (int i = 0; i < articles.size(); i++) {
            Article art = articles.get(i);

            ArticleViewDto dto = ArticleViewDto.builder()
                    .id(art.getId())
                    .displayId((long) (i + 1))
                    .title(art.getTitle())
                    .userId(art.getUserId())
                    .category(art.getCategory())
                    .formattedDate(
                            dateTimeFormatService.formatKst(
                                    art.getFormattedCreatedAt()))
                    .likesCount(art.getLikesCount())
                    .build();

            articleViewList.add(dto);
        }

        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        model.addAttribute("isNotice", "notice".equals(category));
        model.addAttribute("articleList", articleViewList);
        model.addAttribute("popularList",
                articleService.findPopularArticles(category, 5));

        // 검색 폼에서 사용하는 Mustache 변수
        model.addAttribute("searchKeyword", searchKeyword);
        model.addAttribute("isSearchTitle", "title".equals(searchType));
        model.addAttribute("isSearchContent", "content".equals(searchType));
        model.addAttribute("isSearchWriter", "writer".equals(searchType));

        model.addAttribute("limit", limit);

        // 기존 화면 표시 설정 유지
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("isAdmin", true);

        return "article/article";
    }


    /** 상세 */
    @GetMapping("/{id:[0-9]+}")
    public String showArticle(@PathVariable("category") String category,
                              @PathVariable("id") Long id,
                              Model model) {

        if (isInvalidCategory(category)) return "redirect:/article/trade";
        Article article = articleService.showWithFiles(id);

        if (article == null) return "redirect:/article/" + category;

        model.addAttribute("article", article);
        List<com.example.triplesweb.entity.ArticleObjekt> linkedObjekts = articleObjektService.findForArticle(id);
        model.addAttribute("linkedObjekts", linkedObjekts);
        model.addAttribute("hasLinkedObjekts", !linkedObjekts.isEmpty());
        model.addAttribute("category", category);
        model.addAttribute("commentDtos", commentService.show(id));
        model.addAttribute("categoryName", getCategoryName(category));
        model.addAttribute("isOwner", true); // 로그인 제거: 누구나 수정/삭제 가능
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("userId", "익명");
        model.addAttribute("likeCount", articleService.getLikesCount(id));
        model.addAttribute("liked", false);

        List<ArticleFileDto> fileDtos = toFileDtos(article);

        ArticleViewDto view = ArticleViewMapper.toView(article, fileDtos, dateTimeFormatService);
        model.addAttribute("files", fileDtos);
        model.addAttribute("formattedDate", view.getFormattedDate());
        model.addAttribute("isLikeAllowed", LIKE_ALLOWED_CATEGORIES.contains(category));

        return "article/article_detail";
    }

    @PostMapping("/{id}/like")
    @ResponseBody
    public boolean toggleLike(@PathVariable("category") String category,
                              @PathVariable("id") Long id) {
        if (!LIKE_ALLOWED_CATEGORIES.contains(category)) {
            return false;
        }
        return articleService.toggleLike(id, "anonymous");
    }

    /** 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editArticle(@PathVariable("category") String category,
                              @PathVariable("id") Long id,
                              Model model) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        model.addAttribute("article", article);
        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        addObjektOptionsForArticle(model, category, id);

        List<ArticleFileDto> fileDtos = toFileDtos(article);

        ArticleViewDto view = ArticleViewMapper.toView(article, fileDtos, dateTimeFormatService);
        model.addAttribute("formattedDate", view.getFormattedDate());
        model.addAttribute("files", fileDtos);

        return "article/article_edit";
    }

    /** 수정 저장 */
    @PostMapping("/{id}")
    public String updateArticle(@PathVariable("category") String category,
                                @PathVariable("id") Long id,
                                @ModelAttribute("article") Article newarticle,
                                @RequestParam(value = "uploadFiles", required = false) List<MultipartFile> files,
                                @RequestParam(value = "uploadedFiles", required = false) List<String> uploadedFiles,
                                @RequestParam(value = "deletedFiles", required = false) List<Long> deletedFiles,
                                @RequestParam(value = "selectedObjektIds", required = false) List<String> selectedObjektIds,
                                Model model) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        newarticle.setContent(ArticleHtmlCleaner.clean(newarticle.getContent()));
        String validationError = validateArticle(newarticle);
        if (validationError != null) {
            // 사용자가 입력한 값을 유지해 재표시
            article.setTitle(newarticle.getTitle());
            article.setContent(newarticle.getContent());
            prepareEditModel(article, model, validationError);
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            addObjektOptionsForArticle(model, category, id);
            return "article/article_edit";
        }

        Article saved = articleService.updateWithFiles(
                id,
                newarticle.getTitle().trim(),
                newarticle.getContent(),
                files,
                uploadedFiles,
                deletedFiles);

        if ("trade".equals(category) || "sell".equals(category)) {
            articleObjektService.replaceSelected(saved, selectedObjektIds);
        }

        return "redirect:/article/" + category + "/" + id;
    }

    private void addObjektOptionsForArticle(Model model, String category, Long articleId) {
        Set<String> selected = articleObjektService.findForArticle(articleId).stream()
                .flatMap(link -> java.util.stream.Stream.of(link.getObjektKey(), link.getCollectionId()))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toSet());
        addObjektOptions(model, category, selected);
    }

    /** 삭제 */
    @PostMapping("/{id}/delete")
    public String deleteArticle(@PathVariable("id") Long id,
                                @PathVariable("category") String category) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        List<Long> fileIds = articleFileService.findFileIdsByArticleId(id);
        fileIds.forEach(articleFileService::deleteFileById);
        articleService.delete(id);
        return "redirect:/article/" + category;
    }

    private void prepareEditModel(Article article,
                                  Model model,
                                  String errorMessage) {
        if (errorMessage != null) {
            model.addAttribute("errorMessage", errorMessage);
        }
        model.addAttribute("article", article);

        List<ArticleFileDto> fileDtos = toFileDtos(article);
        model.addAttribute("files", fileDtos);
        ArticleViewDto view = ArticleViewMapper.toView(article, fileDtos, dateTimeFormatService);
        model.addAttribute("formattedDate", view.getFormattedDate());
    }

    private List<ArticleFileDto> toFileDtos(Article article) {
        if (article.getFiles() == null) return List.of();
        return article.getFiles().stream()
                .filter(af -> af.getFile() != null)
                .map(af -> {
                    var m = af.getFile();
                    ArticleFileDto dto = new ArticleFileDto();
                    dto.setId(af.getId());
                    dto.setType(m.getMediaType());
                    dto.setOriginalName(m.getOriginalName());
                    dto.setSavedName(m.getSavedName());
                    dto.setSize(m.getSize());
                    dto.setArticleId(article.getId());
                    dto.setCategory(article.getCategory());
                    return dto;
                }).toList();
    }

    private String validateArticle(Article newArt) {
        if (newArt.getTitle() == null || newArt.getTitle().trim().isEmpty())
            return "제목을 입력해주세요.";
        if (newArt.getContent() == null || newArt.getContent().trim().isEmpty())
            return "내용을 입력해주세요.";
        return null;
    }
}