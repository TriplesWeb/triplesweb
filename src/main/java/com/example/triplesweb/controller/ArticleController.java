package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArticleFileDto;
import com.example.triplesweb.dto.ArticleForm;
import com.example.triplesweb.dto.ArticleViewDto;
import com.example.triplesweb.dto.ArticleViewMapper;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.service.ArticleFileServiceImpl;
import com.example.triplesweb.service.ArticleService;
import com.example.triplesweb.service.CommentService;
import com.example.triplesweb.service.DateTimeFormatService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
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
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        if ("notice".equals(category) && !isAdmin(session)) {
            redirectAttributes.addFlashAttribute("errorMessage", "공지사항은 관리자만 작성할 수 있습니다.");
            return "redirect:/article/notice";
        }

        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            redirectAttributes.addFlashAttribute("loginRedirectMessage", "게시글 작성은 로그인 후 이용 가능합니다.");
            return "redirect:/login";
        }
        bindLogin(model, session);
        model.addAttribute("userId", userId);
        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        model.addAttribute("articleForm", new ArticleForm());
        return "article/article_new";
    }

    /** 생성 (폼 action="/free" method="post") */
    @PostMapping("/new")
    public String createArticle(@PathVariable("category") String category,
                                @Valid @ModelAttribute("articleForm") ArticleForm form,
                                BindingResult bindingResult,// 제목/내용/기타 바인딩
                                RedirectAttributes redirectAttributes,
                                @RequestParam(value = "uploadFiles", required = false) List<MultipartFile> files, // 파일 input (multiple)
                                @RequestParam(value = "uploadedFiles", required = false) List<String> uploadedFiles, // 에디터 AJAX 업로드
                                HttpSession session,
                                Model model) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors() || form.getTitle() == null || form.getTitle().trim().isEmpty() || form.getContent() == null || form.getContent().trim().isEmpty()) {
            model.addAttribute("userId", userId);
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            model.addAttribute("errorMessage", "제목과 내용을 모두 입력해주세요.");
            model.addAttribute("articleForm", form);
            return "article/article_new";
        }

        Article article = new Article();
        article.setTitle(form.getTitle());
        article.setContent(form.getContent());
        article.setCategory(category);
        article.setUserId(userId);


        // 1) 게시글 생성
        Article savedArticle = articleService.create(article);

        // 2) 사용자가 파일 선택으로 올린 원본들 저장
        if (files != null) {
            for (MultipartFile mf : files) {
                if (mf != null && !mf.isEmpty()) {
                    articleFileService.uploadFile(mf, savedArticle);
                }
            }
        }

        // 3) 에디터에서 AJAX로 미리 올린 파일들(업로드 결과 경로) 연결
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            articleFileService.attachUploadedFiles(uploadedFiles, savedArticle);
        }

        // 4) 본문에 삽입된 <img>/<video> 태그에서 파일 추출해 매핑(사용 중이면 유지)
        articleFileService.extractAndSaveFilesFromContent(savedArticle);

        return "redirect:/article/" + category + "/" + savedArticle.getId();
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
    public String indexArticle(HttpSession session,
                                   @PathVariable("category") String category,
                                   Model model,
                                   @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        bindLogin(model, session);
        String userId = (String) session.getAttribute("userId");

        boolean isAdmin = isAdmin(session);
        boolean isNotice = "notice".equals(category);
        List<Article> articles = articleService.findRecentByCategory(category, limit);

        List<ArticleViewDto> articleViewList = new ArrayList<>();
        int totalSize = articles.size();
        for (int i = 0; i < totalSize; i++) {
            Article art = articles.get(i);

            // 위에서부터 차례대로 1, 2, 3... 증가하는 번호 부여
            long displayNo = i + 1;

            // 기본 엔티티 정보를 담고 displayId를 추가로 세팅하는 변환 로직
            ArticleViewDto dto = ArticleViewDto.builder()
                    .id(art.getId())           // 상세페이지 이동용 실제 DB PK
                    .displayId(displayNo)      // 화면 표출용 카테고리별 독립 순번
                    .title(art.getTitle())
                    .userId(art.getUserId())
                    .category(art.getCategory())
                    .formattedDate(dateTimeFormatService.formatKst(art.getFormattedCreatedAt())) // 날짜 포맷팅 서비스 활용 예시
                    .likesCount(art.getLikes() != null ? art.getLikes().size() : 0)
                    .build();

            articleViewList.add(dto);
        }

        model.addAttribute("userId", userId);
        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isNotice", isNotice);
        model.addAttribute("articleList", articleViewList);
        model.addAttribute("popularList", articleService.findPopularArticles(category, 5));
        return "article/article";
    }

    /** 상세 */
    @GetMapping("/{id:[0-9]+}")
    public String showArticle(    @PathVariable("category") String category,
                                  @PathVariable("id") Long id,
                                  HttpSession session,
                                  Model model) {

        if (isInvalidCategory(category)) return "redirect:/article/trade";
        Article article = articleService.showWithFiles(id);

        if (article == null) return "redirect:/article/" + category;

        bindLogin(model, session);

        String userId = (String) session.getAttribute("userId");
        boolean isOwner = userId != null && userId.equals(article.getUserId());
        model.addAttribute("userId", userId);
        model.addAttribute("article", article);
        model.addAttribute("category", category);
        model.addAttribute("commentDtos", commentService.show(id));
        model.addAttribute("categoryName", getCategoryName(category));
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("likeCount", articleService.getLikesCount(id));

        boolean liked = false;
        if (userId != null) {
            liked = articleService.isLikedByUser(id, userId);
        }

        model.addAttribute("liked", liked);

        List<ArticleFileDto> fileDtos = article.getFiles().stream().map(af -> {
            var m = af.getFile(); // SavedMeta
            ArticleFileDto dto = new ArticleFileDto();
            dto.setId(af.getId());
            dto.setType(m.getMediaType());
            dto.setOriginalName(m.getOriginalName());
            dto.setSavedName(m.getSavedName());
            dto.setSize(m.getSize());
            dto.setArticleId(article.getId());
            dto.setCategory(af.getCategory());
            return dto;
        }).toList();
        ArticleViewDto view = ArticleViewMapper.toView(article, fileDtos, dateTimeFormatService);
        model.addAttribute("files", fileDtos);
        model.addAttribute("formattedDate", view.getFormattedDate());
        boolean isLikeAllowed = LIKE_ALLOWED_CATEGORIES.contains(category);
        model.addAttribute("isLikeAllowed", isLikeAllowed);
        return "article/article_detail";
    }

    @PostMapping("/{id}/like")
    @ResponseBody
    public boolean toggleLike(@PathVariable("category") String category,
            @PathVariable("id") Long id, HttpSession session) {
        if (!LIKE_ALLOWED_CATEGORIES.contains(category)) {
            return false;
        }

        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return false;
        }
        return articleService.toggleLike(id, userId);
    }

    /** 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editArticle(@PathVariable("category") String category,
                                  @PathVariable("id") Long id,
                                  Model model,
                                  HttpSession session,
                                  RedirectAttributes rttr) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        String sessionUserid = (String) session.getAttribute("userId");
        if (sessionUserid == null || !sessionUserid.equals(article.getUserId())) {
            rttr.addFlashAttribute("errorMessage", "수정 권한이 없습니다.");
            return "redirect:/article/" + category + "/" + id;
        }
        bindLogin(model, session);
        model.addAttribute("userId", sessionUserid);
        model.addAttribute("article", article);
        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));

        List<ArticleFileDto> fileDtos = article.getFiles().stream().map(af -> {
            var m = af.getFile(); // SavedMeta
            ArticleFileDto dto = new ArticleFileDto();
            dto.setId(af.getId());
            dto.setType(m.getMediaType());
            dto.setOriginalName(m.getOriginalName());
            dto.setSavedName(m.getSavedName());
            dto.setSize(m.getSize());
            dto.setArticleId(article.getId());
            dto.setCategory(af.getCategory());
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
                                    HttpSession session,
                                    Model model,
                                    RedirectAttributes rttr) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        Article article = articleService.showWithFiles(id);

        if (article == null) return "redirect:/article/" + category;

        String sessionUserid = (String) session.getAttribute("userId");
        if (sessionUserid == null || !sessionUserid.equals(article.getUserId())) {
            rttr.addFlashAttribute("errorMessage", "수정 권한이 없습니다.");
            return "redirect:/article/" + category + "/" + id;
        }

        String validationError = validateArticle(newarticle);
        if (validationError != null) {
            bindLogin(model, session);
            prepareEditModel(article, sessionUserid, model, validationError);
            model.addAttribute("category", category);
            return "article/article_edit";
        }

        // 제목/내용 반영
        article.setTitle(newarticle.getTitle());
        article.setContent(newarticle.getContent());

        // 삭제 요청된 파일 처리
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

        // 에디터 AJAX 업로드 파일 연결
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            articleFileService.attachUploadedFiles(uploadedFiles, article);
        }

        // 본문 내 태그 스캔해 파일 매핑 업데이트
        articleFileService.extractAndSaveFilesFromContent(article);

        return "redirect:/article/" + category + "/" + id;
    }

    /** 삭제 */
    @PostMapping("/{id}/delete")
    public String deleteArticle(@PathVariable("id") Long id,
                                    HttpSession session,
                                    RedirectAttributes rttr, @PathVariable("category") String category) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";
        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        String sessionUserId = (String) session.getAttribute("userId");
        if (sessionUserId == null || !sessionUserId.equals(article.getUserId())) {
            rttr.addFlashAttribute("errorMessage", "삭제 권한이 없습니다.");
            return "redirect:/article/" + category + "/" + id;
        }

        List<Long> fileIds = articleFileService.findFileIdsByArticleId(id);
        fileIds.forEach(articleFileService::deleteFileById);
        articleService.delete(id);
        return "redirect:/article/" + category;
    }

    /** 수정 폼 모델 구성 */
    private void prepareEditModel(Article article,
                                  String userId,
                                  Model model,
                                  String errorMessage) {
        if (errorMessage != null) {
            model.addAttribute("errorMessage", errorMessage);
        }
        model.addAttribute("article", article);
        model.addAttribute("userId", userId);

        List<ArticleFileDto> fileDtos = article.getFiles().stream().map(f -> {
            ArticleFileDto dto = new ArticleFileDto();
            dto.setId(f.getId());
            dto.setOriginalName(f.getFile().getOriginalName());
            dto.setType(f.getFile().getMediaType());
            dto.setCategory(f.getCategory());
            dto.setArticleId(article.getId());
            return dto;
        }).toList();
        model.addAttribute("files", fileDtos);
        ArticleViewDto view = ArticleViewMapper.toView(article, fileDtos, dateTimeFormatService);
        model.addAttribute("formattedDate", view.getFormattedDate());
    }

    /** 제목/내용 기본 검증 */
    private String validateArticle(Article newArt) {
        if (newArt.getTitle() == null || newArt.getTitle().trim().isEmpty())
            return "제목을 입력해주세요.";
        if (newArt.getContent() == null || newArt.getContent().trim().isEmpty())
            return "내용을 입력해주세요.";
        return null;
    }
    private void bindLogin(Model model, HttpSession session) {
        String uid = (String) session.getAttribute("userId");
        model.addAttribute("isLoggedIn", uid != null);        // 템플릿에서 {{#isLoggedIn}} 사용 가능
    }
    private boolean isAdmin(HttpSession session) {
        String role = (String) session.getAttribute("ROLE_ADMIN"); // 또는 "ADMIN"
        String userId = (String) session.getAttribute("userId");

        if (userId == null) return false;

        // 예시 1: role 값이 ADMIN인 경우
        if ("ROLE_ADMIN".equals(role)) {
            return true;
        }
        return "ROLE_ADMIN".equals(role) || "admin".equals(userId);
    }
}
