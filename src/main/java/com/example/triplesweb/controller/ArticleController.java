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
import com.example.triplesweb.service.AboutAdminAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private final AboutAdminAuth aboutAdminAuth;

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
                                  HttpServletRequest request, HttpSession session, Model model) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        aboutAdminAuth.requireAdmin(request, session);
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
                                @RequestParam(value = "selectedObjektIds", required = false) List<String> selectedObjektIds,
                                HttpServletRequest request, HttpSession session) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        aboutAdminAuth.requireAdmin(request, session);

        if (bindingResult.hasErrors() || form.getTitle() == null || form.getTitle().trim().isEmpty() || form.getContent() == null || form.getContent().trim().isEmpty()) {
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            model.addAttribute("errorMessage", "제목과 내용을 모두 입력해주세요.");
            model.addAttribute("articleForm", form);
            addObjektOptions(model, category);
            return "article/article_new";
        }

        Article article = new Article();
        article.setTitle(form.getTitle());
        article.setContent(form.getContent());
        article.setCategory(category);
        article.setUserId("익명"); // 로그인 기능 삭제로 기본 작성자 지정 (또는 폼 입력값 사용)

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

    private static final int OBJEKT_PICKER_PAGE_SIZE = 20;

    private void addObjektOptions(Model model, String category) {
        if ("trade".equals(category) || "sell".equals(category)) {
            List<java.util.Map<String, Object>> all = objektService.findCollections("triples");
            int to = Math.min(OBJEKT_PICKER_PAGE_SIZE, all.size());
            model.addAttribute("showObjektPicker", true);
            model.addAttribute("objektOptions", all.subList(0, to));
            model.addAttribute("objektPickerPage", 0);
            model.addAttribute("objektPickerHasMore", all.size() > to);
            model.addAttribute("objektPickerTotal", all.size());
        }
    }

    /** 게시글 작성 폼의 오브젝트는 처음 20개만 보내고 요청할 때마다 다음 20개를 전달한다. */
    @GetMapping("/objekts")
    @ResponseBody
    public java.util.Map<String, Object> nextObjektOptions(@PathVariable("category") String category,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            HttpServletRequest request, HttpSession session) {
        if (isInvalidCategory(category) || !("trade".equals(category) || "sell".equals(category))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        aboutAdminAuth.requireAdmin(request, session);
        List<java.util.Map<String, Object>> all = objektService.findCollections("triples");
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * OBJEKT_PICKER_PAGE_SIZE, all.size());
        int to = Math.min(from + OBJEKT_PICKER_PAGE_SIZE, all.size());
        return java.util.Map.of(
                "items", all.subList(from, to),
                "page", safePage,
                "nextPage", safePage + 1,
                "hasMore", to < all.size(),
                "total", all.size()
        );
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
    @GetMapping
    public String indexArticle(@PathVariable("category") String category,
                               Model model,
                               @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit,
                               @RequestParam(name = "type", required = false, defaultValue = "title") String type,
                               @RequestParam(name = "keyword", required = false, defaultValue = "") String keyword,
                               HttpServletRequest request, HttpSession session) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        int safeLimit = limit == null ? 10 : Math.min(100, Math.max(1, limit));
        List<Article> articles = (keyword == null || keyword.isBlank())
                ? articleService.findRecentByCategory(category, safeLimit)
                : articleService.searchByCategory(category, type, keyword, safeLimit);

        List<ArticleViewDto> articleViewList = new ArrayList<>();
        int totalSize = articles.size();
        for (int i = 0; i < totalSize; i++) {
            Article art = articles.get(i);
            long displayNo = i + 1;

            ArticleViewDto dto = ArticleViewDto.builder()
                    .id(art.getId())
                    .displayId(displayNo)
                    .title(art.getTitle())
                    .userId(art.getUserId())
                    .category(art.getCategory())
                    .formattedDate(dateTimeFormatService.formatKst(art.getFormattedCreatedAt()))
                    .likesCount(art.getLikesCount())
                    .build();

            articleViewList.add(dto);
        }

        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        model.addAttribute("isNotice", "notice".equals(category));
        model.addAttribute("articleList", articleViewList);
        String normalizedSearchType = type == null ? "title" : type.toLowerCase(java.util.Locale.ROOT);
        model.addAttribute("searchType", normalizedSearchType);
        model.addAttribute("isSearchTitle", "title".equals(normalizedSearchType));
        model.addAttribute("isSearchContent", "content".equals(normalizedSearchType));
        model.addAttribute("isSearchWriter", "writer".equals(normalizedSearchType));
        model.addAttribute("searchKeyword", keyword == null ? "" : keyword);
        model.addAttribute("popularList", articleService.findPopularArticles(category, 5));

        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        model.addAttribute("isLoggedIn", isAdmin);
        model.addAttribute("isAdmin", isAdmin);

        return "article/article";
    }

    /** 상세 */
    @GetMapping("/{id:[0-9]+}")
    public String showArticle(@PathVariable("category") String category,
                              @PathVariable("id") Long id,
                              HttpServletRequest request, HttpSession session, Model model) {

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
        model.addAttribute("isOwner", aboutAdminAuth.isAdmin(request, session));
        model.addAttribute("likeCount", articleService.getLikesCount(id));
        model.addAttribute("liked", false);

        List<ArticleFileDto> fileDtos = article.getFiles().stream().map(af -> {
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
                              HttpServletRequest request, HttpSession session, Model model) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        aboutAdminAuth.requireAdmin(request, session);
        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        model.addAttribute("article", article);
        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));

        List<ArticleFileDto> fileDtos = article.getFiles().stream().map(af -> {
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
                                HttpServletRequest request, HttpSession session, Model model) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        aboutAdminAuth.requireAdmin(request, session);

        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        String validationError = validateArticle(newarticle);
        if (validationError != null) {
            prepareEditModel(article, model, validationError);
            model.addAttribute("category", category);
            return "article/article_edit";
        }

        article.setTitle(newarticle.getTitle());
        article.setContent(newarticle.getContent());

        if (deletedFiles != null) {
            for (Long fid : deletedFiles) {
                articleFileService.deleteFileById(fid);
            }
        }
        if (files != null) {
            for (MultipartFile mf : files) {
                if (mf != null && !mf.isEmpty()) {
                    articleFileService.uploadFile(mf, article);
                }
            }
        }

        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            articleFileService.attachUploadedFiles(uploadedFiles, article);
        }

        articleFileService.extractAndSaveFilesFromContent(article);

        return "redirect:/article/" + category + "/" + id;
    }

    /** 삭제 */
    @PostMapping("/{id}/delete")
    public String deleteArticle(@PathVariable("id") Long id,
                                @PathVariable("category") String category,
                                HttpServletRequest request, HttpSession session) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        aboutAdminAuth.requireAdmin(request, session);
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

        List<ArticleFileDto> fileDtos = article.getFiles().stream().map(f -> {
            ArticleFileDto dto = new ArticleFileDto();
            dto.setId(f.getId());
            dto.setOriginalName(f.getFile().getOriginalName());
            dto.setType(f.getFile().getMediaType());
            dto.setCategory(article.getCategory());
            dto.setArticleId(article.getId());
            return dto;
        }).toList();
        model.addAttribute("files", fileDtos);
        ArticleViewDto view = ArticleViewMapper.toView(article, fileDtos, dateTimeFormatService);
        model.addAttribute("formattedDate", view.getFormattedDate());
    }

    private String validateArticle(Article newArt) {
        if (newArt.getTitle() == null || newArt.getTitle().trim().isEmpty())
            return "제목을 입력해주세요.";
        if (newArt.getContent() == null || newArt.getContent().trim().isEmpty())
            return "내용을 입력해주세요.";
        return null;
    }
}