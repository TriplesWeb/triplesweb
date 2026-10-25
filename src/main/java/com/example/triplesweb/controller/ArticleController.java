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
import com.example.triplesweb.util.ObjektFilterOrder;
import com.example.triplesweb.util.MemberPortraitOptions;
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
        String validationError = validateTitleAndContent(form.getTitle(), cleanedContent);
        if (bindingResult.hasErrors() || validationError != null) {
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            model.addAttribute("errorMessage", validationError != null ? validationError : "제목과 내용을 모두 입력해주세요.");
            model.addAttribute("articleForm", form);
            addObjektOptions(model, category, selectedObjektIds == null ? Set.of() : Set.copyOf(selectedObjektIds));
            return "article/article_new";
        }

        Article article = new Article();
        article.setTitle(form.getTitle().trim());
        article.setContent(cleanedContent);
        article.setCategory(category);
        article.setUserId("익명"); // 로그인 기능 삭제로 기본 작성자 지정

        try {
            Article savedArticle = articleService.create(article);
            if ("trade".equals(category) || "sell".equals(category)) {
                articleObjektService.attachSelected(savedArticle, selectedObjektIds);
            }
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
        } catch (RuntimeException | IOException ex) {
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            model.addAttribute("errorMessage", "글을 저장하지 못했습니다. 첨부 파일과 내용을 확인한 뒤 다시 시도해 주세요.");
            model.addAttribute("articleForm", form);
            addObjektOptions(model, category, selectedObjektIds == null ? Set.of() : Set.copyOf(selectedObjektIds));
            return "article/article_new";
        }
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

        List<Map<String, Object>> selectedItems = new ArrayList<>();
        Set<String> members = new LinkedHashSet<>();
        Set<String> seasons = new LinkedHashSet<>();
        Set<String> classes = new LinkedHashSet<>();

        for (Map<String, Object> item : objektService.findCollections("triples")) {
            String member = stringValue(item.get("member"));
            String season = stringValue(item.get("season"));
            String klass = stringValue(item.get("class"));
            if (!member.isBlank()) members.add(member);
            if (!season.isBlank()) seasons.add(season);
            if (!klass.isBlank()) classes.add(klass);

            String id = stringValue(item.get("id"));
            String collectionId = stringValue(item.get("collectionId"));
            String slug = stringValue(item.get("slug"));
            boolean isSelected = selected.contains(id)
                    || selected.contains(collectionId)
                    || selected.contains(slug);
            if (!isSelected || selectedItems.size() >= 20) continue;
            selectedItems.add(pickerItem(item));
        }

        List<String> soloMembers = MemberPortraitOptions.soloMemberNames(members);
        boolean hasOther = members.stream().anyMatch(MemberPortraitOptions::isOtherMember);
        model.addAttribute("showObjektPicker", true);
        model.addAttribute("objektSelected", selectedItems);
        model.addAttribute("objektMemberOptions", soloMembers);
        model.addAttribute("hasOtherObjektOption", hasOther);
        model.addAttribute("objektSeasonOptions", ObjektFilterOrder.sortedSeasons(seasons));
        model.addAttribute("objektClassOptions", classes.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    @GetMapping("/objekt-options")
    @ResponseBody
    public Map<String, Object> objektOptions(@RequestParam(defaultValue = "") String member,
                                             @RequestParam(defaultValue = "") String season,
                                             @RequestParam(name = "class", defaultValue = "") String objektClass,
                                             @RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "all") String group,
                                             @RequestParam(defaultValue = "0") int offset,
                                             @RequestParam(defaultValue = "24") int limit) {
        int size = Math.min(48, Math.max(1, limit));
        int start = Math.max(0, offset);
        String seasonKey = season.trim().toLowerCase();
        String classKey = objektClass.trim().toLowerCase();
        String query = q.trim().toLowerCase();
        String selectedGroup = "other".equalsIgnoreCase(group) ? "other"
                : ("members".equalsIgnoreCase(group) || !member.isBlank() ? "members" : "all");
        List<String> selectedMembers = member.isBlank() ? List.of() : List.of(member.trim());
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> item : objektService.findCollections("triples")) {
            if (!MemberPortraitOptions.matchesMemberGroup(selectedGroup, selectedMembers, stringValue(item.get("member")))) continue;
            if (!seasonKey.isEmpty() && !stringValue(item.get("season")).toLowerCase().equals(seasonKey)) continue;
            if (!classKey.isEmpty() && !stringValue(item.get("class")).toLowerCase().equals(classKey)) continue;
            if (!query.isEmpty()) {
                String haystack = (stringValue(item.get("collectionId")) + " " + stringValue(item.get("member"))
                        + " " + stringValue(item.get("collectionNo")) + " " + stringValue(item.get("season"))
                        + " " + stringValue(item.get("class"))).toLowerCase();
                if (!haystack.contains(query)) continue;
            }
            matched.add(item);
        }
        int end = Math.min(matched.size(), start + size);
        List<Map<String, Object>> page = new ArrayList<>();
        if (start < matched.size()) {
            for (Map<String, Object> item : matched.subList(start, end)) page.add(pickerItem(item));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", matched.size());
        body.put("offset", start);
        body.put("items", page);
        return body;
    }

    private static Map<String, Object> pickerItem(Map<String, Object> item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", stringValue(item.get("id")));
        row.put("collectionId", stringValue(item.get("collectionId")));
        row.put("member", com.example.triplesweb.util.MemberPortraitOptions.displayMember(stringValue(item.get("member"))));
        row.put("season", stringValue(item.get("season")));
        row.put("class", stringValue(item.get("class")));
        row.put("collectionNo", stringValue(item.get("collectionNo")));
        row.put("thumbnailImage", stringValue(item.get("thumbnailImage")));
        return row;
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
                    .displayId(art.getDisplayId() != null ? art.getDisplayId() : art.getId())
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
                              @RequestParam(required = false) String notice,
                              Model model) {

        if (isInvalidCategory(category)) return "redirect:/article/trade";
        Article article = articleService.showWithFiles(id);

        if (article == null) return "redirect:/article/" + category;

        model.addAttribute("article", article);
        List<com.example.triplesweb.entity.ArticleObjekt> linkedObjekts = articleObjektService.findForArticle(id);
        model.addAttribute("linkedObjekts", linkedObjekts.stream().map(ArticleController::linkedObjektView).toList());
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
        if ("delete-failed".equals(notice)) {
            model.addAttribute("errorMessage", "글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

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
        String validationError = validateArticleUpdate(article, newarticle, category, files, uploadedFiles, deletedFiles, selectedObjektIds);
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

        try {
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
        } catch (RuntimeException | IOException ex) {
            article.setTitle(newarticle.getTitle());
            article.setContent(newarticle.getContent());
            prepareEditModel(article, model, "글을 수정하지 못했습니다. 첨부 파일과 내용을 확인한 뒤 다시 시도해 주세요.");
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            addObjektOptionsForArticle(model, category, id);
            return "article/article_edit";
        }
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

        try {
            List<Long> fileIds = articleFileService.findFileIdsByArticleId(id);
            fileIds.forEach(articleFileService::deleteFileById);
            articleService.delete(id);
            return "redirect:/article/" + category;
        } catch (RuntimeException ex) {
            return "redirect:/article/" + category + "/" + id + "?notice=delete-failed";
        }
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
        return validateTitleAndContent(newArt.getTitle(), newArt.getContent());
    }

    private String validateArticleUpdate(Article existing,
                                         Article incoming,
                                         String category,
                                         List<MultipartFile> files,
                                         List<String> uploadedFiles,
                                         List<Long> deletedFiles,
                                         List<String> selectedObjektIds) {
        String invalid = validateArticle(incoming);
        if (invalid != null) return invalid;
        boolean sameText = existing.getTitle() != null
                && existing.getTitle().trim().equals(incoming.getTitle().trim())
                && ArticleHtmlCleaner.fingerprint(existing.getContent())
                .equals(ArticleHtmlCleaner.fingerprint(incoming.getContent()));
        if (!sameText || hasFileChanges(files, uploadedFiles, deletedFiles)) return null;
        if (("trade".equals(category) || "sell".equals(category))
                && !sameObjektSelection(existing.getId(), selectedObjektIds)) {
            return null;
        }
        return "게시글을 수정해주세요.";
    }

    private static boolean hasFileChanges(List<MultipartFile> files,
                                          List<String> uploadedFiles,
                                          List<Long> deletedFiles) {
        boolean uploaded = files != null && files.stream().anyMatch(file -> file != null && !file.isEmpty());
        return uploaded
                || (uploadedFiles != null && uploadedFiles.stream().anyMatch(name -> name != null && !name.isBlank()))
                || (deletedFiles != null && !deletedFiles.isEmpty());
    }

    private boolean sameObjektSelection(Long articleId, List<String> selectedObjektIds) {
        Set<String> current = articleObjektService.findForArticle(articleId).stream()
                .map(com.example.triplesweb.entity.ArticleObjekt::getObjektKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toSet());
        Set<String> incoming = selectedObjektIds == null ? Set.of() : selectedObjektIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        return current.equals(incoming);
    }

    private static String validateTitleAndContent(String title, String content) {
        if (title == null || title.trim().isEmpty()) return "제목을 입력해주세요.";
        if (!ArticleHtmlCleaner.hasVisibleContent(content)) return "내용을 입력해주세요.";
        return null;
    }

    private static Map<String, Object> linkedObjektView(com.example.triplesweb.entity.ArticleObjekt link) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("objektKey", link.getObjektKey());
        row.put("collectionId", link.getCollectionId());
        row.put("memberName", com.example.triplesweb.util.MemberPortraitOptions.displayMember(link.getMemberName()));
        row.put("thumbnailImage", link.getThumbnailImage());
        row.put("frontImage", link.getFrontImage());
        row.put("collectionNo", link.getCollectionNo());
        return row;
    }
}
