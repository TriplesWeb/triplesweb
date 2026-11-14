package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArticleFileDto;
import com.example.triplesweb.dto.ArticleForm;
import com.example.triplesweb.dto.ArticleViewDto;
import com.example.triplesweb.dto.ArticleViewMapper;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.service.ArticleFileServiceImpl;
import com.example.triplesweb.service.ArticleService;
import com.example.triplesweb.service.ArticleObjektService;
import com.example.triplesweb.service.ObjektService;
import com.example.triplesweb.service.CommentService;
import com.example.triplesweb.service.DateTimeFormatService;
import com.example.triplesweb.util.ArticleHtmlCleaner;
import com.example.triplesweb.util.ArticleMemberSwap;
import com.example.triplesweb.util.ObjektCatalogSort;
import com.example.triplesweb.util.ObjektFilterOrder;
import com.example.triplesweb.util.ObjektSeasonCodes;
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
import java.util.Locale;
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
    private final ArtistSnapshotService artistSnapshot;

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "trade", "sell", "info", "notice"
    );
    private static final Set<String> LIKE_ALLOWED_CATEGORIES = Set.of("info");

    private static boolean isObjektBoard(String category) {
        return Set.of("trade", "sell").contains(category);
    }

    private static boolean isTradeBoard(String category) {
        return "trade".equals(category);
    }

    private static boolean isSellBoard(String category) {
        return "sell".equals(category);
    }

    private boolean isInvalidCategory(String category) {
        return !ALLOWED_CATEGORIES.contains(category);
    }

    @InitBinder("article")
    public void disallowFilesBinding(WebDataBinder binder) {
        binder.setDisallowedFields("files");
    }

    /** 작성 폼 */
    public String showArticleForm(String category, Model model) {
        return showArticleForm(category, null, null, null, null, null, model);
    }

    /** 테스트·deep-link 호환: selectedObjektIds는 trade에서 Have로 매핑된다. */
    public String showArticleForm(String category, List<String> selectedObjektIds, Model model) {
        return showArticleForm(category, selectedObjektIds, null, null, null, null, model);
    }

    @GetMapping("/new")
    public String showArticleForm(@PathVariable("category") String category,
                                  @RequestParam(value = "selectedObjektIds", required = false) List<String> selectedObjektIds,
                                  @RequestParam(value = "selectedHaveObjektIds", required = false) List<String> selectedHaveObjektIds,
                                  @RequestParam(value = "selectedWantObjektIds", required = false) List<String> selectedWantObjektIds,
                                  @RequestParam(value = "sellListingSide", required = false) String sellListingSide,
                                  @RequestParam(value = "objektSide", required = false) String objektSide,
                                  Model model) {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        // 로그인 체크 및 관리자 체크 제거 (누구나 작성 가능)
        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        addCategoryFlags(model, category);
        model.addAttribute("articleForm", new ArticleForm());
        // COSMO deep link selectedObjektIds → trade Have / 거래 판매 사전선택
        List<String> have = selectedHaveObjektIds;
        if ((have == null || have.isEmpty()) && selectedObjektIds != null && !selectedObjektIds.isEmpty()) {
            have = selectedObjektIds;
        }
        addObjektOptions(model, category, have, selectedWantObjektIds, firstNonBlank(sellListingSide, objektSide));
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
                                @RequestParam(value = "selectedHaveObjektIds", required = false) List<String> selectedHaveObjektIds,
                                @RequestParam(value = "selectedWantObjektIds", required = false) List<String> selectedWantObjektIds,
                                @RequestParam(value = "sellListingSide", required = false) String sellListingSide) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        String cleanedContent = ArticleHtmlCleaner.clean(form.getContent());
        form.setContent(cleanedContent);
        LinkedSides sides = resolveLinkedSides(
                category, selectedObjektIds, selectedHaveObjektIds, selectedWantObjektIds, sellListingSide);
        List<String> haveKeys = sides.have();
        List<String> wantKeys = sides.want();
        String validationError = validateTitleAndContent(form.getTitle(), cleanedContent);
        if (validationError == null) {
            validationError = validateObjektSelection(category, haveKeys, wantKeys);
        }
        if (bindingResult.hasErrors() || validationError != null) {
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            addCategoryFlags(model, category);
            model.addAttribute("errorMessage", validationError != null ? validationError : "제목과 내용을 모두 입력해주세요.");
            model.addAttribute("articleForm", form);
            addObjektOptions(model, category, haveKeys, wantKeys, sellListingSide);
            return "article/article_new";
        }

        Article article = new Article();
        article.setTitle(form.getTitle().trim());
        article.setContent(cleanedContent);
        article.setCategory(category);
        article.setUserId("익명"); // 로그인 기능 삭제로 기본 작성자 지정

        try {
            Article savedArticle = articleService.create(article);
            if (isObjektBoard(category)) {
                articleObjektService.attachSelected(savedArticle, haveKeys, wantKeys);
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
            addCategoryFlags(model, category);
            addObjektOptions(model, category, haveKeys, wantKeys, sellListingSide);
            return "article/article_new";
        }
    }

    private void addObjektOptions(Model model, String category) {
        addObjektOptions(model, category, null, null, null);
    }

    private void addObjektOptions(Model model, String category, List<String> haveKeys, List<String> wantKeys) {
        addObjektOptions(model, category, haveKeys, wantKeys, null);
    }

    private void addObjektOptions(Model model, String category, List<String> haveKeys, List<String> wantKeys,
                                  String sellListingSide) {
        if (!(isObjektBoard(category))) {
            return;
        }
        boolean tradeSplit = isTradeBoard(category);
        boolean sellWant = isSellBoard(category) && "want".equals(normalizeSellListingSide(sellListingSide));
        Set<String> haveSelected = keySet(haveKeys);
        Set<String> wantSelected = tradeSplit ? keySet(wantKeys) : Set.of();
        if (isSellBoard(category) && sellWant && haveSelected.isEmpty()) {
            haveSelected = keySet(wantKeys);
        }

        List<Map<String, Object>> selectedItems = new ArrayList<>();
        List<Map<String, Object>> haveItems = new ArrayList<>();
        List<Map<String, Object>> wantItems = new ArrayList<>();
        Set<String> members = new LinkedHashSet<>();
        Set<String> seasons = new LinkedHashSet<>();
        Set<String> classes = new LinkedHashSet<>();
        Set<String> kinds = new LinkedHashSet<>();

        for (Map<String, Object> item : objektService.findCollections("triples")) {
            String member = stringValue(item.get("member"));
            String season = stringValue(item.get("season"));
            String klass = stringValue(item.get("class"));
            String kind = stringValue(item.get("collectionNo"));
            if (!member.isBlank()) members.add(member);
            if (!season.isBlank()) seasons.add(season);
            if (!klass.isBlank()) classes.add(klass);
            if (!kind.isBlank()) kinds.add(kind);

            String id = stringValue(item.get("id"));
            String collectionId = stringValue(item.get("collectionId"));
            String slug = stringValue(item.get("slug"));
            boolean inHave = matchesKey(haveSelected, id, collectionId, slug);
            boolean inWant = matchesKey(wantSelected, id, collectionId, slug);
            if (!inHave && !inWant) continue;
            if (selectedItems.size() >= ArticleObjektService.MAX_LINKED) continue;
            Map<String, Object> row = pickerItem(item);
            selectedItems.add(row);
            if (inHave) haveItems.add(row);
            else wantItems.add(row);
        }

        List<String> soloMembers = MemberPortraitOptions.soloMemberNames(members);
        boolean hasOther = members.stream().anyMatch(MemberPortraitOptions::isOtherMember);
        model.addAttribute("showObjektPicker", true);
        model.addAttribute("isTradeObjektSplit", tradeSplit);
        model.addAttribute("isSellListingHave", isSellBoard(category) && !sellWant);
        model.addAttribute("isSellListingWant", isSellBoard(category) && sellWant);
        model.addAttribute("objektSelected", selectedItems);
        model.addAttribute("objektHaveSelected", haveItems);
        model.addAttribute("objektWantSelected", wantItems);
        model.addAttribute("objektInitialSearch", selectedItems.size() == 1 ? selectedItems.get(0).getOrDefault("collectionId", "") : "");
        model.addAttribute("objektMemberOptions", soloMembers);
        model.addAttribute("hasOtherObjektOption", hasOther);
        model.addAttribute("objektSeasonOptions", ObjektFilterOrder.sortedSeasons(seasons));
        model.addAttribute("objektClassOptions", classes.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("objektKindOptions", kinds.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private static Set<String> keySet(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Set.of();
        return keys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .limit(ArticleObjektService.MAX_LINKED)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean matchesKey(Set<String> selected, String id, String collectionId, String slug) {
        return selected.contains(id) || selected.contains(collectionId) || selected.contains(slug);
    }

    @GetMapping("/objekt-options")
    @ResponseBody
    public Map<String, Object> objektOptions(@RequestParam(defaultValue = "") String member,
                                             @RequestParam(defaultValue = "") String season,
                                             @RequestParam(name = "class", defaultValue = "") String objektClass,
                                             @RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "all") String group,
                                             @RequestParam(defaultValue = "0") int offset,
                                             @RequestParam(defaultValue = "24") int limit,
                                             @RequestParam(defaultValue = "") String kind,
                                             @RequestParam(defaultValue = "latest") String sort) {
        int size = Math.min(48, Math.max(1, limit));
        int start = Math.max(0, offset);
        String seasonKey = season.trim().toLowerCase(Locale.ROOT);
        String classKey = objektClass.trim().toLowerCase(Locale.ROOT);
        String query = q.trim().toLowerCase(Locale.ROOT);
        String selectedGroup = "other".equalsIgnoreCase(group) ? "other"
                : ("members".equalsIgnoreCase(group) || !member.isBlank() ? "members" : "all");
        List<String> selectedMembers = member.isBlank() ? List.of() : List.of(member.trim());
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> item : objektService.findCollections("triples")) {
            if (!MemberPortraitOptions.matchesMemberGroup(selectedGroup, selectedMembers, stringValue(item.get("member")))) continue;
            if (!seasonKey.isEmpty() && !stringValue(item.get("season")).toLowerCase(Locale.ROOT).equals(seasonKey)) continue;
            if (!classKey.isEmpty() && !stringValue(item.get("class")).toLowerCase(Locale.ROOT).equals(classKey)) continue;
            if (!ObjektCatalogSort.matchesKind(kind, item)) continue;
            if (!query.isEmpty()) {
                String haystack = (stringValue(item.get("collectionId")) + " " + stringValue(item.get("member"))
                        + " " + stringValue(item.get("collectionNo")) + " " + stringValue(item.get("season"))
                        + " " + stringValue(item.get("class"))).toLowerCase(Locale.ROOT);
                if (!ObjektSeasonCodes.matches(query, haystack,
                        stringValue(item.get("season")), stringValue(item.get("collectionNo")))) continue;
            }
            matched.add(item);
        }
        matched.sort(ObjektCatalogSort.comparator(sort));
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
        row.put("cardTitle", ObjektSeasonCodes.cardTitle(
                stringValue(item.get("collectionId")), stringValue(item.get("season"))));
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
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(name = "member", required = false, defaultValue = "") String member,
            @RequestParam(name = "objektSide", required = false, defaultValue = "have") String objektSide,
            @RequestParam(name = "season", required = false, defaultValue = "") String season,
            @RequestParam(name = "class", required = false, defaultValue = "") String objektClass) {

        if (isInvalidCategory(category)) {
            return "redirect:/article/trade";
        }

        boolean marketBoard = isObjektBoard(category);

        // 검색 조건 정리 (마켓 피드는 작성자 검색을 기본값으로)
        String searchType = type == null ? "" : type.trim();
        if (!Set.of("title", "content", "writer").contains(searchType)) {
            searchType = marketBoard ? "writer" : "title";
        }

        String searchKeyword = keyword == null ? "" : keyword.trim();
        String selectedMember = member == null ? "" : member.trim();
        String selectedSide = normalizeObjektSide(category, objektSide);
        String selectedSeason = season == null ? "" : season.trim();
        String selectedClass = objektClass == null ? "" : objektClass.trim();
        int safeLimit = Math.min(100, Math.max(1, limit));
        List<Article> articles;
        Map<Long, List<ArticleObjekt>> linkedByArticle;
        boolean filterByAttrs = marketBoard && (!selectedMember.isEmpty()
                || !selectedSeason.isEmpty()
                || !selectedClass.isEmpty()
                || isSellBoard(category));
        if (filterByAttrs) {
            // Keyset batches preserve exact member/unit matching while searching beyond the newest page.
            articles = new ArrayList<>();
            linkedByArticle = new LinkedHashMap<>();
            long beforeId = Long.MAX_VALUE;
            int batchSize = 100;
            while (articles.size() < safeLimit) {
                List<Article> batch = articleService.findCategoryBatchBefore(category, searchType, searchKeyword, beforeId, batchSize);
                if (batch == null || batch.isEmpty()) break;
                Map<Long, List<ArticleObjekt>> batchLinks = articleObjektService.groupByArticleId(
                        batch.stream().map(Article::getId).filter(Objects::nonNull).toList());
                if (batchLinks == null) batchLinks = Map.of();
                for (Article art : batch) {
                    List<ArticleObjekt> links = batchLinks.getOrDefault(art.getId(), List.of());
                    if (matchesMarketListing(category, links, selectedMember, selectedSide, selectedSeason, selectedClass)) {
                        articles.add(art);
                        linkedByArticle.put(art.getId(), links);
                        if (articles.size() == safeLimit) break;
                    }
                }
                if (batch.size() < batchSize) break;
                beforeId = batch.get(batch.size() - 1).getId();
            }
        } else {
            articles = searchKeyword.isEmpty()
                    ? articleService.findRecentByCategory(category, safeLimit)
                    : articleService.searchByCategory(category, searchType, searchKeyword, safeLimit);
            linkedByArticle = articleObjektService.groupByArticleId(
                    articles.stream().map(Article::getId).filter(Objects::nonNull).toList());
            if (linkedByArticle == null) linkedByArticle = Map.of();
        }
        List<Artist> artists = artistSnapshot.findAll();

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
            if (marketBoard) {
                List<ArticleObjekt> links = linkedByArticle.getOrDefault(art.getId(), List.of());
                List<ArticleObjekt> have = links.stream()
                        .filter(row -> ArticleObjektService.SIDE_HAVE.equalsIgnoreCase(
                                row.getSide() == null ? ArticleObjektService.SIDE_HAVE : row.getSide()))
                        .toList();
                List<ArticleObjekt> want = links.stream()
                        .filter(row -> ArticleObjektService.SIDE_WANT.equalsIgnoreCase(row.getSide()))
                        .toList();
                ArticleMemberSwap.copyOnto(
                        ArticleMemberSwap.summarize(have, want, artists, isTradeBoard(category)), dto);
                ArticleMemberSwap.copyPreviewsOnto(dto, have, want, 6);
            }

            articleViewList.add(dto);
        }

        model.addAttribute("category", category);
        model.addAttribute("categoryName", getCategoryName(category));
        addCategoryFlags(model, category);
        model.addAttribute("isGroupOrders", "group-orders".equals(category));
        if (marketBoard) {
            addMarketMemberFilter(model, selectedMember, selectedSide, artists);
            addMarketSeasonClassFilter(model, selectedSeason, selectedClass);
        }
        model.addAttribute("articleList", articleViewList);
        // 교환/거래/분철: 추천 컬럼·인기글 조회 제거. popularList는 템플릿 미사용.
        model.addAttribute("hideRecommendations", isObjektBoard(category));
        model.addAttribute("popularList", List.of());

        // 검색 폼에서 사용하는 Mustache 변수
        model.addAttribute("searchKeyword", searchKeyword);
        model.addAttribute("isSearchTitle", "title".equals(searchType));
        model.addAttribute("isSearchContent", "content".equals(searchType));
        model.addAttribute("isSearchWriter", "writer".equals(searchType));
        model.addAttribute("selectedMember", selectedMember);
        model.addAttribute("selectedObjektSide", selectedSide);
        model.addAttribute("selectedSeason", selectedSeason);
        model.addAttribute("selectedClass", selectedClass);
        model.addAttribute("hasSelectedMember", !selectedMember.isEmpty());
        model.addAttribute("hasSearchKeyword", !searchKeyword.isEmpty());
        model.addAttribute("hasMarketAttrFilter",
                !selectedMember.isEmpty() || !selectedSeason.isEmpty() || !selectedClass.isEmpty());

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
        List<ArticleObjekt> linkedObjekts = articleObjektService.findForArticle(id);
        List<Artist> artists = artistSnapshot.findAll();
        List<Map<String, Object>> linkedViews = linkedObjekts.stream()
                .map(link -> linkedObjektView(link, artists)).toList();
        model.addAttribute("linkedObjekts", linkedViews);
        model.addAttribute("hasLinkedObjekts", !linkedObjekts.isEmpty());
        boolean tradeSplit = isTradeBoard(category);
        model.addAttribute("isTradeObjektSplit", tradeSplit);
        if (tradeSplit) {
            List<ArticleObjekt> haveLinks = articleObjektService.findHaveForArticle(id);
            List<ArticleObjekt> wantLinks = articleObjektService.findWantForArticle(id);
            List<Map<String, Object>> haveViews = haveLinks.stream()
                    .map(link -> linkedObjektView(link, artists)).toList();
            List<Map<String, Object>> wantViews = wantLinks.stream()
                    .map(link -> linkedObjektView(link, artists)).toList();
            model.addAttribute("linkedHaveObjekts", haveViews);
            model.addAttribute("linkedWantObjekts", wantViews);
            model.addAttribute("hasLinkedHaveObjekts", !haveViews.isEmpty());
            model.addAttribute("hasLinkedWantObjekts", !wantViews.isEmpty());
            model.addAllAttributes(ArticleMemberSwap.summarize(haveLinks, wantLinks, artists, true));
        } else if (isObjektBoard(category)) {
            List<ArticleObjekt> haveLinks = articleObjektService.findHaveForArticle(id);
            List<ArticleObjekt> wantLinks = articleObjektService.findWantForArticle(id);
            model.addAllAttributes(ArticleMemberSwap.summarize(haveLinks, wantLinks, artists, false));
        }
        model.addAttribute("category", category);
        addCategoryFlags(model, category);
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
        addCategoryFlags(model, category);
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
                                @RequestParam(value = "selectedHaveObjektIds", required = false) List<String> selectedHaveObjektIds,
                                @RequestParam(value = "selectedWantObjektIds", required = false) List<String> selectedWantObjektIds,
                                @RequestParam(value = "sellListingSide", required = false) String sellListingSide,
                                Model model) throws IOException {
        if (isInvalidCategory(category)) return "redirect:/article/trade";

        Article article = articleService.showWithFiles(id);
        if (article == null) return "redirect:/article/" + category;

        newarticle.setContent(ArticleHtmlCleaner.clean(newarticle.getContent()));
        LinkedSides sides = resolveLinkedSides(
                category, selectedObjektIds, selectedHaveObjektIds, selectedWantObjektIds, sellListingSide);
        List<String> haveKeys = sides.have();
        List<String> wantKeys = sides.want();
        String validationError = validateArticleUpdate(article, newarticle, category, files, uploadedFiles, deletedFiles, haveKeys, wantKeys);
        if (validationError != null) {
            // 사용자가 입력한 값을 유지해 재표시
            article.setTitle(newarticle.getTitle());
            article.setContent(newarticle.getContent());
            prepareEditModel(article, model, validationError);
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            addCategoryFlags(model, category);
            addObjektOptions(model, category, haveKeys, wantKeys, sellListingSide);
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

            if (isObjektBoard(category)) {
                articleObjektService.replaceSelected(saved, haveKeys, wantKeys);
            }
            return "redirect:/article/" + category + "/" + id;
        } catch (RuntimeException | IOException ex) {
            article.setTitle(newarticle.getTitle());
            article.setContent(newarticle.getContent());
            prepareEditModel(article, model, "글을 수정하지 못했습니다. 첨부 파일과 내용을 확인한 뒤 다시 시도해 주세요.");
            model.addAttribute("category", category);
            model.addAttribute("categoryName", getCategoryName(category));
            addCategoryFlags(model, category);
            addObjektOptions(model, category, haveKeys, wantKeys, sellListingSide);
            return "article/article_edit";
        }
    }

    private void addObjektOptionsForArticle(Model model, String category, Long articleId) {
        List<com.example.triplesweb.entity.ArticleObjekt> links = articleObjektService.findForArticle(articleId);
        if (isTradeBoard(category)) {
            List<String> have = new ArrayList<>();
            List<String> want = new ArrayList<>();
            for (com.example.triplesweb.entity.ArticleObjekt link : links) {
                String side = link.getSide();
                if (side == null || side.isBlank() || ArticleObjektService.SIDE_HAVE.equalsIgnoreCase(side)) {
                    have.add(link.getObjektKey());
                } else if (ArticleObjektService.SIDE_WANT.equalsIgnoreCase(side)) {
                    want.add(link.getObjektKey());
                } else {
                    have.add(link.getObjektKey());
                }
            }
            addObjektOptions(model, category, have, want, null);
            return;
        }
        String listingSide = sellListingSideFrom(links);
        boolean wantListing = "want".equals(listingSide);
        List<String> selected = links.stream()
                .filter(link -> wantListing
                        ? ArticleObjektService.SIDE_WANT.equalsIgnoreCase(link.getSide())
                        : !ArticleObjektService.SIDE_WANT.equalsIgnoreCase(
                                link.getSide() == null ? "" : link.getSide()))
                .map(com.example.triplesweb.entity.ArticleObjekt::getObjektKey)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();
        addObjektOptions(model, category, selected, null, listingSide);
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
                                         List<String> haveKeys,
                                         List<String> wantKeys) {
        String invalid = validateArticle(incoming);
        if (invalid != null) return invalid;
        String objektError = validateObjektSelection(category, haveKeys, wantKeys);
        if (objektError != null) return objektError;
        boolean sameText = existing.getTitle() != null
                && existing.getTitle().trim().equals(incoming.getTitle().trim())
                && ArticleHtmlCleaner.fingerprint(existing.getContent())
                .equals(ArticleHtmlCleaner.fingerprint(incoming.getContent()));
        if (!sameText || hasFileChanges(files, uploadedFiles, deletedFiles)) return null;
        if (isObjektBoard(category) && !sameObjektSelection(existing.getId(), category, haveKeys, wantKeys)) {
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

    private boolean sameObjektSelection(Long articleId,
                                        String category,
                                        List<String> haveKeys,
                                        List<String> wantKeys) {
        List<com.example.triplesweb.entity.ArticleObjekt> currentLinks = articleObjektService.findForArticle(articleId);
        if (isTradeBoard(category) || isSellBoard(category)) {
            Set<String> currentHave = currentLinks.stream()
                    .filter(link -> {
                        String side = link.getSide();
                        return side == null || side.isBlank() || ArticleObjektService.SIDE_HAVE.equalsIgnoreCase(side);
                    })
                    .map(com.example.triplesweb.entity.ArticleObjekt::getObjektKey)
                    .filter(key -> key != null && !key.isBlank())
                    .collect(Collectors.toSet());
            Set<String> currentWant = currentLinks.stream()
                    .filter(link -> ArticleObjektService.SIDE_WANT.equalsIgnoreCase(
                            link.getSide() == null ? "" : link.getSide()))
                    .map(com.example.triplesweb.entity.ArticleObjekt::getObjektKey)
                    .filter(key -> key != null && !key.isBlank())
                    .collect(Collectors.toSet());
            return currentHave.equals(Set.copyOf(ArticleObjektService.cleanKeys(haveKeys)))
                    && currentWant.equals(Set.copyOf(ArticleObjektService.cleanKeys(wantKeys)));
        }
        Set<String> current = currentLinks.stream()
                .map(com.example.triplesweb.entity.ArticleObjekt::getObjektKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toSet());
        return current.equals(Set.copyOf(ArticleObjektService.cleanKeys(haveKeys)));
    }

    private static String validateTitleAndContent(String title, String content) {
        if (title == null || title.trim().isEmpty()) return "제목을 입력해주세요.";
        if (!ArticleHtmlCleaner.hasVisibleContent(content)) return "내용을 입력해주세요.";
        return null;
    }

    private static Map<String, Object> linkedObjektView(ArticleObjekt link, List<Artist> artists) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("objektKey", link.getObjektKey());
        row.put("collectionId", link.getCollectionId());
        row.put("cardTitle", ObjektSeasonCodes.cardTitle(link.getCollectionId(), link.getSeason()));
        row.put("season", link.getSeason());
        row.put("memberName", MemberPortraitOptions.displayMember(link.getMemberName()));
        row.put("memberColor", MemberPortraitOptions.artistColor(link.getMemberName(), artists));
        row.put("thumbnailImage", link.getThumbnailImage());
        row.put("frontImage", link.getFrontImage());
        row.put("collectionNo", link.getCollectionNo());
        return row;
    }

    private void addMarketMemberFilter(Model model, String selectedMember, String selectedSide, List<Artist> artists) {
        List<String> names = artists == null ? List.of() : artists.stream()
                .map(Artist::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        List<Map<String, Object>> picker = MemberPortraitOptions.forObjektFilter(
                MemberPortraitOptions.soloMemberNames(names), artists);
        for (Map<String, Object> option : picker) {
            option.put("active", !selectedMember.isBlank()
                    && MemberPortraitOptions.sameMember(selectedMember, String.valueOf(option.get("value"))));
        }
        model.addAttribute("memberPickerOptions", picker);
        model.addAttribute("isObjektSideAll", "all".equals(selectedSide));
        model.addAttribute("isObjektSideHave", "have".equals(selectedSide));
        model.addAttribute("isObjektSideWant", "want".equals(selectedSide));
    }

    private void addMarketSeasonClassFilter(Model model, String selectedSeason, String selectedClass) {
        Set<String> seasons = new LinkedHashSet<>();
        Set<String> classes = new LinkedHashSet<>();
        List<Map<String, Object>> catalog = objektService.findCollections("triples");
        if (catalog == null) catalog = List.of();
        for (Map<String, Object> item : catalog) {
            if (item == null) continue;
            String season = stringValue(item.get("season"));
            String klass = stringValue(item.get("class"));
            if (!season.isBlank()) seasons.add(season);
            if (!klass.isBlank()) classes.add(klass);
        }
        // 전체는 드롭다운의 all 체크박스가 담당 — 옵션 목록에는 실제 값만 둔다.
        List<Map<String, Object>> seasonOptions = new ArrayList<>();
        for (String value : ObjektFilterOrder.sortedSeasons(seasons)) {
            seasonOptions.add(marketFilterOption(value, value, selectedSeason.equalsIgnoreCase(value)));
        }
        List<Map<String, Object>> classOptions = new ArrayList<>();
        for (String value : classes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            classOptions.add(marketFilterOption(value, value, selectedClass.equalsIgnoreCase(value)));
        }
        model.addAttribute("marketSeasonOptions", seasonOptions);
        model.addAttribute("marketClassOptions", classOptions);
        model.addAttribute("hasSelectedSeason", !selectedSeason.isBlank());
        model.addAttribute("hasSelectedClass", !selectedClass.isBlank());
    }

    private static Map<String, Object> marketFilterOption(String value, String label, boolean selected) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("label", label);
        row.put("selected", selected);
        return row;
    }

    private static String normalizeObjektSide(String category, String objektSide) {
        if (!isObjektBoard(category)) return "have";
        if (objektSide == null) return "have";
        String normalized = objektSide.trim().toLowerCase();
        if (isTradeBoard(category) && "all".equals(normalized)) return "all";
        if ("want".equals(normalized)) return "want";
        return "have";
    }

    private static String normalizeSellListingSide(String side) {
        return "want".equalsIgnoreCase(side == null ? "" : side.trim()) ? "want" : "have";
    }

    private static String sellListingSideFrom(List<ArticleObjekt> links) {
        boolean have = false;
        boolean want = false;
        if (links != null) {
            for (ArticleObjekt link : links) {
                if (link == null) continue;
                if (ArticleObjektService.SIDE_WANT.equalsIgnoreCase(link.getSide())) want = true;
                else have = true;
            }
        }
        return (want && !have) ? "want" : "have";
    }

    private static boolean matchesMarketListing(String category,
                                                List<ArticleObjekt> links,
                                                String member,
                                                String side,
                                                String season,
                                                String objektClass) {
        boolean hasAttr = (member != null && !member.isBlank())
                || (season != null && !season.isBlank())
                || (objektClass != null && !objektClass.isBlank());
        if (hasAttr) {
            return ArticleMemberSwap.matchesListing(links, member, side, season, objektClass);
        }
        if (isSellBoard(category)) {
            return ArticleMemberSwap.hasSide(links, side);
        }
        return true;
    }

    private void addCategoryFlags(Model model, String category) {
        model.addAttribute("isMarketBoard", isObjektBoard(category));
        model.addAttribute("isTrade", isTradeBoard(category));
        model.addAttribute("isSell", isSellBoard(category));
    }

    private static LinkedSides resolveLinkedSides(String category,
                                                  List<String> selectedObjektIds,
                                                  List<String> selectedHaveObjektIds,
                                                  List<String> selectedWantObjektIds,
                                                  String sellListingSide) {
        if (isTradeBoard(category)) {
            return new LinkedSides(selectedHaveObjektIds, selectedWantObjektIds);
        }
        if (isSellBoard(category) && "want".equals(normalizeSellListingSide(sellListingSide))) {
            return new LinkedSides(List.of(), selectedObjektIds);
        }
        return new LinkedSides(selectedObjektIds, List.of());
    }

    private static String validateObjektSelection(String category, List<String> haveKeys, List<String> wantKeys) {
        if (isTradeBoard(category)) {
            return ArticleObjektService.validateTradeSides(haveKeys, wantKeys);
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    private record LinkedSides(List<String> have, List<String> want) {}
}
