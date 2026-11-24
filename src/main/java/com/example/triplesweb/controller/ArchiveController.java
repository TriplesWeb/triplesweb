package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArchiveSourceService;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.service.ChemistryGroupService;
import com.example.triplesweb.service.ChemistryService;
import com.example.triplesweb.service.IdempotencyService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.util.ArchiveFolderArt;
import com.example.triplesweb.util.ArchiveFolderClassifier;
import com.example.triplesweb.util.ArchiveCardImages;
import com.example.triplesweb.util.ArchiveImages;
import com.example.triplesweb.util.ArchiveSort;
import com.example.triplesweb.util.ImageUrls;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/archive")
public class ArchiveController {

    private static final String FILTER_HEADER = "X-Archive-Filter";
    private static final int DEFAULT_ARCHIVE_SIZE = 10;
    private static final List<Integer> ARCHIVE_SIZE_OPTIONS = List.of(10, 20, 30, 40, 50);

    private final ArchiveService archiveService;
    private final ArchiveSourceService archiveSourceService;
    private final AboutAdminAuth aboutAdminAuth;
    private final ArtistSnapshotService artistSnapshot;
    private final UnitService unitService;
    private final ArchiveFolderArt archiveFolderArt;
    private final ChemistryService chemistryService;
    private final ChemistryGroupService chemistryGroupService;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public String showArchive(@RequestParam(defaultValue = "image") String type,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) String from,
                              @RequestParam(required = false) String to,
                              @RequestParam(required = false) String on,
                              @RequestParam(required = false) Integer go,
                              @RequestParam(required = false) List<String> members,
                              @RequestParam(required = false) String folder,
                              @RequestParam(required = false) String combo,
                              @RequestParam(required = false) String source,
                              @RequestParam(required = false) String chem,
                              @RequestParam(required = false) String sort,
                              HttpServletRequest request, HttpServletResponse response, HttpSession session, Model model) {
        response.addHeader(HttpHeaders.VARY, FILTER_HEADER);
        String jump = pageJumpRedirect(go, request);
        if (jump != null) return jump;
        if (folder == null || folder.isBlank()) {
            return renderFolderHome(request, session, model);
        }
        String folderKey = ArchiveFolderClassifier.canonicalComboFolder(folder.trim());
        if (isUnitIndex(folderKey)) {
            return renderFolderIndex(request, session, model);
        }
        List<String> legacyMembers = legacyFolderMembers(folderKey, combo, chem);
        if (legacyMembers != null) {
            return redirectToGroupFolder(request, legacyMembers);
        }
        return renderArchive(type, page, size, q, from, to, on, members, folder, combo, source, chem, sort,
                false, request, session, model);
    }

    @GetMapping("/all")
    public String showAllImages(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String from,
                                @RequestParam(required = false) String to,
                                @RequestParam(required = false) String on,
                                @RequestParam(required = false) Integer go,
                                @RequestParam(required = false) List<String> members,
                                @RequestParam(required = false) String sort,
                                HttpServletRequest request, HttpServletResponse response, HttpSession session, Model model) {
        response.addHeader(HttpHeaders.VARY, FILTER_HEADER);
        String jump = pageJumpRedirect(go, request);
        if (jump != null) return jump;
        return renderArchive("image", page, size, q, from, to, on, members, null, null, null, null, sort,
                true, request, session, model);
    }

    @GetMapping("/admin-forms")
    public String archiveAdminForms(@RequestParam(defaultValue = "image") String type,
                                    @RequestParam(defaultValue = "false") boolean folderHome,
                                    HttpServletRequest request, HttpSession session, Model model) {
        if (!aboutAdminAuth.isAdmin(request, session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        boolean video = !folderHome && mediaType(type) == MediaType.VIDEO;
        model.addAttribute("archiveFolderHome", folderHome);
        model.addAttribute("imageArchive", !video);
        model.addAttribute("videoArchive", video);
        model.addAttribute("memberPickerOptions", memberOptions(artistSnapshot.findAll(), List.of()));
        model.addAttribute("archiveSources", sourceOptions(null));
        addUnitCheckboxOptions(model);
        addCommonDateFlags(model);
        if (video) model.addAttribute("mediaDateLabel", "영상 날짜");
        return "archive/admin";
    }

    @GetMapping("/image_archive")
    public String redirectImageArchive(HttpServletRequest request) {
        return redirectWithQuery("/archive", request);
    }

    @GetMapping("/video_archive")
    public String redirectVideoArchive(HttpServletRequest request) {
        return redirectWithQuery("/archive?type=video", request);
    }

    @GetMapping({"/image_archive/all", "/video_archive/all"})
    public String redirectBrowseArchive(HttpServletRequest request) {
        return redirectWithQuery("/archive/all", request);
    }

    private static String redirectWithQuery(String path, HttpServletRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        request.getParameterMap().forEach((name, values) -> {
            if (name == null || values == null) return;
            for (String value : values) builder.queryParam(name, value);
        });
        return "redirect:" + builder.build().encode().toUriString();
    }

    private static String pageJumpRedirect(Integer go, HttpServletRequest request) {
        if (go == null) return null;
        int target = Math.max(1, go) - 1;
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        request.getParameterMap().forEach((name, values) -> {
            if (name == null || values == null) return;
            if ("go".equals(name) || "page".equals(name)) return;
            for (String value : values) builder.queryParam(name, value);
        });
        if (target > 0) builder.queryParam("page", target);
        return "redirect:" + builder.build().encode().toUriString();
    }

    private String renderFolderHome(HttpServletRequest request, HttpSession session, Model model) {
        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        List<Artist> artists = artistSnapshot.findAll();
        // 홈 카드는 번들 아이콘·멤버 색만 필요. 유닛/아카이브 전체 스캔은 Tailscale RTT만 키운다.
        List<Map<String, Object>> folders = folderCards(List.of(), List.of(), List.of(), artists);
        ArchiveService.TypeTotals totals = archiveService.countTotals();
        long photoCount = totals.images();
        long videoCount = totals.videos();
        model.addAttribute("archiveFolderHome", true);
        model.addAttribute("archiveFolderView", false);
        model.addAttribute("archiveBrowse", false);
        folders.stream().limit(2).forEach(card -> card.put("eagerFolderArt", true));
        model.addAttribute("archiveFolders", folders);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isAuthenticated", isAdmin);
        model.addAttribute("imageArchive", true);
        model.addAttribute("videoArchive", false);
        model.addAttribute("archiveTotal", photoCount);
        model.addAttribute("archiveVideoTotal", videoCount);
        model.addAttribute("archiveFolderCount", folders.size());
        model.addAttribute("archivePageCount", folders.size());
        model.addAttribute("archivePageDisplay", 1);
        model.addAttribute("archiveTotalPages", 1);
        model.addAttribute("hasArchives", !folders.isEmpty());
        model.addAttribute("defaultUploadArtists", "");
        model.addAttribute("hasArchiveBack", false);
        model.addAttribute("archiveBackHref", "/archive");
        model.addAttribute("archiveFolderIndex", false);
        addCommonDateFlags(model);
        return "archive/archive";
    }

    private String renderFolderIndex(HttpServletRequest request, HttpSession session, Model model) {
        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        List<Artist> artists = artistSnapshot.findAll();
        List<Unit> units = unitService.listVisibleForArchive();
        // 유닛 카드도 번들 아트·등록 이미지를 쓰고 배지는 숨기므로 전체 아카이브 스캔을 생략한다.
        FolderCounts counted = new FolderCounts(Map.of(), Map.of());
        List<Map<String, Object>> folders = unitFolderCards(units, artists, counted);
        ArchiveService.TypeTotals totals = archiveService.countTotals();
        long photoCount = totals.images();
        long videoCount = totals.videos();
        String title = "디멘션";
        String accent = "#a78bfa";
        model.addAttribute("archiveFolderHome", true);
        model.addAttribute("archiveFolderIndex", true);
        model.addAttribute("archiveFolderView", false);
        model.addAttribute("archiveBrowse", false);
        folders.stream().limit(2).forEach(card -> card.put("eagerFolderArt", true));
        model.addAttribute("archiveFolders", folders);
        model.addAttribute("folderTitle", title);
        model.addAttribute("folderAccent", accent);
        model.addAttribute("hasArchiveBack", true);
        model.addAttribute("archiveBackHref", "/archive");
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isAuthenticated", isAdmin);
        model.addAttribute("imageArchive", true);
        model.addAttribute("videoArchive", false);
        model.addAttribute("archiveTotal", photoCount);
        model.addAttribute("archiveVideoTotal", videoCount);
        model.addAttribute("archiveFolderCount", folders.size());
        model.addAttribute("archivePageCount", folders.size());
        model.addAttribute("archivePageDisplay", 1);
        model.addAttribute("archiveTotalPages", 1);
        model.addAttribute("hasArchives", !folders.isEmpty());
        model.addAttribute("defaultUploadArtists", "");
        addCommonDateFlags(model);
        return "archive/archive";
    }

    private String renderArchive(String typeSlug, int page, int size, String q, String from, String to, String on,
                                 List<String> members, String folder, String combo, String sourceSlug, String chem,
                                 String sortRaw, boolean browse, HttpServletRequest request, HttpSession session, Model model) {
        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        boolean isAuthenticated = isAdmin;
        boolean filterOnly = "1".equals(request.getHeader(FILTER_HEADER));

        MediaType type = mediaType(typeSlug);
        String folderKey = ArchiveFolderClassifier.canonicalComboFolder(folder == null ? "" : folder.trim());
        List<String> selectedMembers = canonicalMembers(members);
        boolean memberSuperset = folderKey.equalsIgnoreCase("s") && !selectedMembers.isEmpty();
        LocalDate onDate = parseDate(on);
        LocalDate fromDate = onDate != null ? onDate : parseDate(from);
        LocalDate toDate = onDate != null ? onDate : parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        ArchiveSource sourceEntity = parseSourceFilter(sourceSlug);
        String source = sourceEntity == null ? null : sourceEntity.getCode();
        String sourceKey = sourceEntity == null ? sourceSlug : sourceEntity.getSlug();
        boolean externalView = sourceEntity != null && "external".equalsIgnoreCase(sourceEntity.getSlug());
        int safeSize = normalizeArchiveSize(size);
        ArchiveSort sort = ArchiveSort.from(sortRaw);
        if (sort == ArchiveSort.TITLE && type != MediaType.VIDEO) sort = ArchiveSort.NEWEST;
        if (sort.isMemberSort() && isSoloFolder(folderKey)) sort = ArchiveSort.NEWEST;
        // 카드 배지·유닛 폴더 완전일치 판별용. 멤버 없는 s 전체 목록 스캔에는 비워 둔다.
        List<Unit> allVisibleUnits = unitService.listVisibleForArchive();
        boolean needsBrowseUnits = !browse && (!folderKey.equalsIgnoreCase("s") || !selectedMembers.isEmpty());
        List<ArchiveFolderClassifier.UnitMembers> units = needsBrowseUnits
                ? unitSnapshots(allVisibleUnits) : List.of();
        ArchiveService.FolderBrowse folderBrowse = null;
        Page<Archive> archives;
        if (browse) {
            archives = archiveService.pageByType(type, page, safeSize, q, selectedMembers, fromDate, toDate,
                    null, false, false, sort);
        } else {
            folderBrowse = archiveService.browseFolder(type, page, safeSize, q,
                    memberSuperset ? List.of() : selectedMembers, fromDate, toDate,
                    source, false, folderKey, memberSuperset ? selectedMembers : List.of(), units, sort);
            archives = folderBrowse.page();
        }

        List<Artist> artists = artistSnapshot.findAll();
        List<Map<String, Object>> cards = archiveCards(archives, artists, allVisibleUnits, selectedMembers);
        model.addAttribute("archives", archives);
        model.addAttribute("archiveCards", cards);
        if (!filterOnly) model.addAttribute("memberPickerOptions", memberOptions(artists, selectedMembers));
        model.addAttribute("selectedMembers", selectedMembers);
        model.addAttribute("hasSelectedMembers", !selectedMembers.isEmpty());
        model.addAttribute("defaultUploadArtists", String.join(",", selectedMembers));
        model.addAttribute("page", archives);
        model.addAttribute("type", type);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("archiveBrowse", browse);
        model.addAttribute("archiveFolderHome", false);
        model.addAttribute("archiveFolderIndex", false);
        model.addAttribute("archiveFolderView", !browse);
        model.addAttribute("hasArchiveBack", !browse);
        model.addAttribute("archiveBackHref", folderBackHref(folderKey, selectedMembers, allVisibleUnits));
        // 솔로·유닛·콤보 폴더는 이미 멤버가 고정되어 있어 멤버 필터가 필요 없다.
        boolean lockedFolderMembers = memberSuperset
                || isSoloFolder(folderKey)
                || isUnitFolder(folderKey)
                || isComboFolder(folderKey);
        boolean showMemberFilter = browse || !lockedFolderMembers;
        model.addAttribute("showMemberFilter", showMemberFilter);
        model.addAttribute("showChemistryFilter", false);
        model.addAttribute("chemistryOptions", List.of());
        model.addAttribute("hasChemistryOptions", false);
        if (!filterOnly) model.addAttribute("chemistryNamesJson", chemistryNamesJson());
        model.addAttribute("folderKey", folderKey);
        model.addAttribute("comboValue", "");
        model.addAttribute("hasCombo", false);
        model.addAttribute("chemKey", "");
        model.addAttribute("hasChemFilter", false);
        String baseTitle = folderTitle(folderKey, selectedMembers, units);
        model.addAttribute("folderBaseTitle", baseTitle);
        model.addAttribute("folderTitle", baseTitle);
        model.addAttribute("hasFolderTitle", baseTitle != null && !baseTitle.isBlank());
        model.addAttribute("folderAccent", folderAccent(folderKey, selectedMembers, allVisibleUnits, artists));
        String soloNumber = "";
        String soloName = "";
        boolean soloFolder = isSoloFolder(folderKey);
        if (soloFolder) {
            int index = Integer.parseInt(folderKey.substring(1)) - 1;
            soloNumber = "S" + (index + 1);
            soloName = MemberPortraitOptions.koreanOrder().get(index);
        } else if (folderKey != null && folderKey.equalsIgnoreCase("s")
                && selectedMembers != null && selectedMembers.size() == 1) {
            ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(
                    String.join(",", selectedMembers), units);
            if (match.kind() == ArchiveFolderClassifier.Kind.SOLO) {
                soloFolder = true;
                String name = selectedMembers.get(0);
                List<String> order = MemberPortraitOptions.koreanOrder();
                int index = -1;
                for (int i = 0; i < order.size(); i++) {
                    if (order.get(i).equalsIgnoreCase(name)) {
                        index = i;
                        break;
                    }
                }
                if (index >= 0) {
                    soloNumber = "S" + (index + 1);
                    soloName = order.get(index);
                } else {
                    soloName = name;
                }
            }
        }
        model.addAttribute("folderIsSolo", soloFolder);
        model.addAttribute("folderTitleNumber", soloNumber);
        model.addAttribute("folderTitleName", soloName);
        String onText = onDate == null ? "" : onDate.toString();
        String fromText = onDate != null ? "" : (fromDate == null ? "" : fromDate.toString());
        String toText = onDate != null ? "" : (toDate == null ? "" : toDate.toString());
        model.addAttribute("on", onText);
        model.addAttribute("from", fromText);
        model.addAttribute("to", toText);
        model.addAttribute("hasExactDate", onDate != null);
        model.addAttribute("hasDateFilter", onDate != null || fromDate != null || toDate != null);
        model.addAttribute("hasDateStat", onDate != null || fromDate != null || toDate != null);
        model.addAttribute("dateStatLabel", dateStatLabel(onDate, fromDate, toDate));
        model.addAttribute("dateExactEnabled", false);
        String dateQuery = dateQuery(fromText, toText, onText);
        model.addAttribute("dateQuery", dateQuery);
        String sourceQuery = sourceQuery(sourceKey);
        model.addAttribute("sourceQuery", sourceQuery);
        model.addAttribute("sourceKey", sourceKey == null ? "" : sourceKey.trim());
        model.addAttribute("hasSourceFilter", sourceSlug != null && !sourceSlug.isBlank());
        model.addAttribute("chemQuery", "");
        model.addAttribute("hasActiveArchiveFilters", (q != null && !q.isBlank())
                || (showMemberFilter && !selectedMembers.isEmpty())
                || fromDate != null || toDate != null || onDate != null
                || (sourceSlug != null && !sourceSlug.isBlank()));
        String sizeQuery = archiveSizeQuery(safeSize);
        String sortQuery = archiveSortQuery(sort);
        model.addAttribute("sortKey", sort.key());
        model.addAttribute("sortQuery", sortQuery);
        model.addAttribute("sortNewest", sort == ArchiveSort.NEWEST);
        model.addAttribute("sortOldest", sort == ArchiveSort.OLDEST);
        model.addAttribute("sortTitle", sort == ArchiveSort.TITLE);
        model.addAttribute("sortFirstMember", sort == ArchiveSort.FIRST_MEMBER);
        model.addAttribute("sortLastMember", sort == ArchiveSort.LAST_MEMBER);
        String membersQuery = memberQuery(selectedMembers);
        addArchivePage(model, archives, type, q, membersQuery, dateQuery, browse,
                folderQuery(folderKey, ""), sourceQuery, sourceKey, "", safeSize, sizeQuery, sortQuery);
        // 고정 멤버 폴더에서 검색·날짜·출처만 초기화해도 같은 폴더에 남긴다.
        if (!browse && lockedFolderMembers && !selectedMembers.isEmpty()) {
            String typeQuery = type == MediaType.VIDEO ? "&type=video" : "";
            model.addAttribute("archiveHomePath",
                    joinHref("/archive", folderQuery(folderKey, "") + membersQuery + typeQuery));
        }
        // 출처 탭: 폴더는 한 번 스캔한 결과, 전체 보기는 source 컬럼만 distinct 조회.
        Set<String> availableSources = browse
                ? archiveService.availableSources(type, q, selectedMembers, fromDate, toDate)
                : folderBrowse.availableSources();
        String keptFilters = (type == MediaType.VIDEO ? "&type=video" : "") + folderQuery(folderKey, "")
                + (q == null || q.isBlank() ? "" : "&q=" + URLEncoder.encode(q.trim(), StandardCharsets.UTF_8))
                + memberQuery(selectedMembers) + dateQuery;
        model.addAttribute("sourceTabs", sourceTabs(browse ? "/archive/all" : "/archive",
                keptFilters + sizeQuery + sortQuery, sourceKey, availableSources));
        model.addAttribute("externalSource", externalView);
        return filterOnly ? "archive/filter" : "archive/archive";
    }

    private static MediaType mediaType(String typeSlug) {
        if (typeSlug != null && typeSlug.trim().equalsIgnoreCase("video")) return MediaType.VIDEO;
        return MediaType.IMAGE;
    }

    private static List<String> normalizeMembers(List<String> members) {
        if (members == null) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String member : members) {
            if (member != null && !member.isBlank()) unique.add(member.trim());
        }
        return List.copyOf(unique);
    }

    private static List<String> canonicalMembers(List<String> members) {
        return ArchiveFolderClassifier.membersOf(String.join(",", normalizeMembers(members)));
    }

    private List<String> legacyFolderMembers(String folderKey, String combo, String chem) {
        if (folderKey == null || folderKey.isBlank() || folderKey.equalsIgnoreCase("s")) return null;
        if (isSoloFolder(folderKey)) {
            int index = Integer.parseInt(folderKey.substring(1)) - 1;
            return List.of(MemberPortraitOptions.koreanOrder().get(index));
        }
        // unit-N 은 unitId로 직접 조회한다. 멤버 폴더로 바꾸면 출연진·unitId가 어긋날 때 빈 목록이 된다.
        if (folderKey.toLowerCase(Locale.ROOT).startsWith("unit-")) {
            return null;
        }
        if (isComboIndex(folderKey) || isComboFolder(folderKey)) {
            List<String> fromChemistry = chemistryMembers(chem, ArchiveFolderClassifier.comboSize(folderKey));
            if (!fromChemistry.isEmpty()) return fromChemistry;
            return ArchiveFolderClassifier.membersOf(combo == null ? "" : combo);
        }
        return null;
    }

    private List<String> chemistryMembers(String chem, Integer comboMin) {
        if (chem == null || chem.isBlank()) return List.of();
        if (comboMin != null) {
            ChemistryPick picked = resolveChemistry(chem, comboMin);
            if (picked != null) return picked.members();
        }
        for (int size : new int[] {2, 3, 4, 5}) {
            ChemistryPick picked = resolveChemistry(chem, size);
            if (picked != null) return picked.members();
        }
        return List.of();
    }

    private static String redirectToGroupFolder(HttpServletRequest request, List<String> members) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/archive");
        String type = request.getParameter("type");
        if (type != null && type.trim().equalsIgnoreCase("video")) builder.queryParam("type", "video");
        builder.queryParam("folder", "s");
        copyQuery(builder, request, "source");
        copyQuery(builder, request, "q");
        copyQuery(builder, request, "from");
        copyQuery(builder, request, "to");
        copyQuery(builder, request, "on");
        for (String member : members) builder.queryParam("members", member);
        return "redirect:" + builder.build().encode().toUriString();
    }

    private static void copyQuery(UriComponentsBuilder builder, HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value != null && !value.isBlank()) builder.queryParam(name, value);
    }

    private static List<Map<String, Object>> memberOptions(List<Artist> artists, List<String> selected) {
        Set<String> chosen = Set.copyOf(selected);
        List<Map<String, Object>> options = MemberPortraitOptions.fromArtists(artists, false);
        for (Map<String, Object> option : options) {
            option.put("keepOption", true);
            option.put("active", chosen.contains(String.valueOf(option.get("value"))));
        }
        return options;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDate.parse(raw.trim()); }
        catch (DateTimeParseException ex) { return null; }
    }

    private static String dateQuery(String from, String to, String on) {
        StringBuilder query = new StringBuilder();
        if (on != null && !on.isBlank()) {
            query.append("&on=").append(URLEncoder.encode(on, StandardCharsets.UTF_8));
            return query.toString();
        }
        if (from != null && !from.isBlank()) query.append("&from=").append(URLEncoder.encode(from, StandardCharsets.UTF_8));
        if (to != null && !to.isBlank()) query.append("&to=").append(URLEncoder.encode(to, StandardCharsets.UTF_8));
        return query.toString();
    }

    private static LocalDate requireDate(LocalDate registeredOn) {
        if (registeredOn == null) throw new IllegalArgumentException("실제로 올라온 날짜를 선택하세요.");
        return registeredOn;
    }

    private String requireSource(String source) {
        return archiveSourceService.requireVisible(source).getCode();
    }

    private static String memberQuery(List<String> selected) {
        if (selected.isEmpty()) return "";
        StringBuilder query = new StringBuilder();
        for (String member : selected) {
            query.append("&members=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private List<Map<String, Object>> archiveCards(Page<Archive> archives, List<Artist> artists, List<Unit> units, List<String> selectedMembers) {
        Map<String, ArchiveSource> sourceCatalog = archiveSourceService.byCode();
        Map<String, String> colors = new LinkedHashMap<>();
        for (Artist artist : artists) {
            String color = artist.getColor();
            if (color == null || !color.matches("#[0-9a-fA-F]{6}")) color = "#b9cac4";
            if (artist.getName() != null) colors.put(artist.getName().trim().toLowerCase(Locale.ROOT), color);
            if (artist.getEngName() != null) colors.put(artist.getEngName().trim().toLowerCase(Locale.ROOT), color);
        }
        List<ArchiveFolderClassifier.UnitMembers> unitMembers = unitSnapshots(units);
        Map<Long, String> unitColors = new LinkedHashMap<>();
        for (Unit unit : units == null ? List.<Unit>of() : units) {
            if (unit.getUnitId() == null) continue;
            unitColors.put(unit.getUnitId(), unitColor(unit, artists));
        }
        long offset = archives.getPageable().isPaged() ? archives.getPageable().getOffset() : 0;
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Archive archive : archives) {
            List<String> imageUrls = archive.getType() == MediaType.IMAGE
                    ? ArchiveImages.resolve(archive, objectMapper)
                    : List.of();
            if (imageUrls.isEmpty() && archive.getFile() != null && archive.getFile().getUrl() != null) {
                imageUrls = List.of(archive.getFile().getUrl());
            }
            List<String> artistRows = imageArtistRows(archive);
            List<Integer> visible = new ArrayList<>();
            for (int i = 0; i < imageUrls.size(); i++) {
                String castNames = castNamesAt(archive, artistRows, i);
                if (selectedMembers == null || selectedMembers.isEmpty() || membersOverlap(castNames, selectedMembers)) {
                    visible.add(i);
                }
            }
            if (visible.isEmpty()) {
                for (int i = 0; i < imageUrls.size(); i++) visible.add(i);
            }
            List<String> viewUrls = new ArrayList<>();
            List<Map<String, Object>> imageCasts = new ArrayList<>();
            for (int i : visible) {
                viewUrls.add(imageUrls.get(i));
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("memberBadges", memberBadges(castNamesAt(archive, artistRows, i), artists, colors));
                imageCasts.add(frame);
            }
            Map<String, Object> card = new LinkedHashMap<>();
            String title = archive.getTitle() == null ? "" : archive.getTitle().trim();
            String description = archive.getDescription() == null ? "" : archive.getDescription().trim();
            String headline = !description.isEmpty() ? description : title;
            card.put("id", archive.getId());
            card.put("title", title);
            card.put("description", description);
            card.put("hasDescription", !description.isEmpty());
            card.put("headline", headline);
            card.put("hasHeadline", !headline.isEmpty());
            card.put("artistNames", archive.getArtistNames());
            card.put("memberOrder", MemberPortraitOptions.earliestMemberIndex(archive.getArtistNames()));
            card.put("file", archive.getFile());
            String imagesJson = ArchiveImages.write(imageUrls, objectMapper);
            String viewJson = ArchiveImages.write(viewUrls, objectMapper);
            // 멤버 필터로 가려진 대표 사진은 쓰지 않고, 보이는 장 중 첫 장을 표지로 쓴다.
            String coverUrl = archive.getFile() == null || archive.getFile().getUrl() == null
                    ? ""
                    : archive.getFile().getUrl().trim();
            boolean coverVisible = !coverUrl.isEmpty() && viewUrls.stream()
                    .anyMatch(url -> url != null && coverUrl.equals(url.trim()));
            String previewSource = coverVisible
                    ? coverUrl
                    : (viewUrls.isEmpty() ? "" : viewUrls.get(0));
            // 표지에 보이는 장의 출연진으로 배지를 맞춘다.
            List<Map<String, Object>> badges = imageCasts.isEmpty()
                    ? memberBadges(archive.getArtistNames(), artists, colors)
                    : castBadges(imageCasts.get(previewCastIndex(viewUrls, previewSource, imageCasts.size())));
            card.put("imageUrls", imageUrls);
            card.put("imageUrlsJson", imagesJson == null ? "[]" : imagesJson);
            card.put("viewUrlsJson", viewJson == null ? "[]" : viewJson);
            card.put("imageArtistsJson", archive.getImageArtistsJson() == null ? "[]" : archive.getImageArtistsJson());
            card.put("imageCasts", imageCasts);
            card.put("previewUrl", ArchiveCardImages.previewUrl(previewSource));
            card.put("eagerPreview", cards.size() < 4);
            card.put("imageCount", viewUrls.size());
            card.put("hasMultipleImages", viewUrls.size() > 1);
            card.put("thumbnailUrl", archive.getThumbnailUrl());
            card.put("displayNumber", offset + cards.size() + 1);
            card.put("memberBadges", badges);
            card.put("memberCount", badges.size());
            List<String> memberNames = new ArrayList<>();
            for (Map<String, Object> badge : badges) memberNames.add(String.valueOf(badge.get("name")));
            card.put("memberNames", String.join(", ", memberNames));
            if (!badges.isEmpty()) card.put("firstMemberBadge", badges.get(0));
            int extra = Math.max(0, badges.size() - 1);
            card.put("otherMemberCount", extra);
            card.put("hasOtherMembers", extra > 0);
            if (extra > 0) card.put("otherMemberNames", String.join(", ", memberNames.subList(1, memberNames.size())));
            String registered = archive.getRegisteredOn() == null ? "" : archive.getRegisteredOn().toString();
            card.put("registeredOn", registered);
            card.put("registeredOnDisplay", registered.replace('-', '/'));
            card.put("hasRegisteredOn", !registered.isBlank());
            String sourceCode = archive.getSource();
            ArchiveSource sourceMeta = sourceCode == null || sourceCode.isBlank()
                    ? null : sourceCatalog.get(sourceCode.toUpperCase(Locale.ROOT));
            if (sourceMeta == null && sourceCode != null && !sourceCode.isBlank()) {
                sourceMeta = archiveSourceService.find(sourceCode);
            }
            card.put("sourceValue", sourceMeta == null ? (sourceCode == null ? "" : sourceCode) : sourceMeta.getCode());
            card.put("sourceLabel", sourceMeta == null
                    ? (sourceCode == null ? "" : sourceCode)
                    : sourceMeta.getLabel());
            card.put("sourceColor", sourceMeta == null || sourceMeta.getColor() == null
                    ? "#00f5d4"
                    : sourceMeta.getColor());
            card.put("hasSource", sourceCode != null && !sourceCode.isBlank());
            Long storedUnitId = archive.getUnitId();
            card.put("unitId", storedUnitId == null ? "" : String.valueOf(storedUnitId));
            // 유닛 배지는 저장된 unitId만. 목록에 없는 유닛이어도 id가 있으면 배지를 남긴다.
            ArchiveFolderClassifier.UnitMembers storedUnit = storedUnitId == null ? null : unitMembers.stream()
                    .filter(unit -> unit != null && storedUnitId.equals(unit.unitId()))
                    .findFirst()
                    .orElse(null);
            boolean unitBadge = storedUnitId != null;
            card.put("hasUnitBadge", unitBadge);
            if (storedUnit != null) {
                String label = storedUnit.unitName() == null || storedUnit.unitName().isBlank()
                        ? "유닛" : storedUnit.unitName();
                card.put("unitBadgeLabel", label);
                card.put("unitBadgeColor", unitColors.getOrDefault(storedUnit.unitId(), "#a78bfa"));
                card.put("unitBadgeKey", "unit-" + storedUnit.unitId());
            } else if (storedUnitId != null) {
                card.put("unitBadgeLabel", "유닛");
                card.put("unitBadgeColor", unitColors.getOrDefault(storedUnitId, "#a78bfa"));
                card.put("unitBadgeKey", "unit-" + storedUnitId);
            } else {
                card.put("unitBadgeLabel", "");
                card.put("unitBadgeColor", "");
                card.put("unitBadgeKey", "");
            }
            card.put("hasBadgeRow", (sourceCode != null && !sourceCode.isBlank()) || unitBadge);
            cards.add(card);
        }
        return cards;
    }

    private static String safeColor(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : "#b9cac4";
    }

    private static String koreanDay(LocalDate date) {
        if (date == null) return "";
        return date.getYear() + "년 " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    private static String dateStatLabel(LocalDate on, LocalDate from, LocalDate to) {
        if (on != null) return koreanDay(on);
        if (from != null && to != null) {
            if (from.equals(to)) return koreanDay(from);
            return koreanDay(from) + " ~ " + koreanDay(to);
        }
        if (from != null) return koreanDay(from) + " ~";
        if (to != null) return "~ " + koreanDay(to);
        return "전체";
    }

    private void addArchivePage(Model model, Page<Archive> archives, MediaType type, String q, String memberQuery,
                                String dateQuery, boolean browse, String folderQuery, String sourceQuery, String sourceSlug,
                                String chemQuery, int size, String sizeQuery) {
        addArchivePage(model, archives, type, q, memberQuery, dateQuery, browse, folderQuery, sourceQuery, sourceSlug,
                chemQuery, size, sizeQuery, "");
    }

    private void addArchivePage(Model model, Page<Archive> archives, MediaType type, String q, String memberQuery,
                                String dateQuery, boolean browse, String folderQuery, String sourceQuery, String sourceSlug,
                                String chemQuery, int size, String sizeQuery, String sortQuery) {
        int totalPages = Math.max(1, archives.getTotalPages());
        int current = archives.getNumber();
        String query = q == null ? "" : q.trim();
        String keptSizeQuery = sizeQuery == null ? "" : sizeQuery;
        String keptSortQuery = sortQuery == null ? "" : sortQuery;
        model.addAttribute("archiveTotal", archives.getTotalElements());
        model.addAttribute("archivePageCount", archives.getNumberOfElements());
        model.addAttribute("archiveOffset", archives.getPageable().isPaged() ? archives.getPageable().getOffset() : 0);
        model.addAttribute("archivePageDisplay", current + 1);
        model.addAttribute("archiveTotalPages", totalPages);
        model.addAttribute("hasArchivePrev", archives.hasPrevious());
        model.addAttribute("hasArchiveNext", archives.hasNext());
        model.addAttribute("archivePrevPage", Math.max(0, current - 1));
        model.addAttribute("archiveNextPage", current + 1);
        model.addAttribute("hasArchives", !archives.isEmpty());
        model.addAttribute("memberQuery", memberQuery == null ? "" : memberQuery);
        model.addAttribute("query", query);
        model.addAttribute("hasQuery", !query.isEmpty());
        model.addAttribute("archiveSize", size);
        model.addAttribute("sizeQuery", keptSizeQuery);
        model.addAttribute("sortQuery", keptSortQuery);
        model.addAttribute("hasCustomArchiveSize", size != DEFAULT_ARCHIVE_SIZE);
        model.addAttribute("archiveSizeOptions", archiveSizeOptions(size));
        boolean video = type == MediaType.VIDEO;
        String typeQuery = video ? "&type=video" : "";
        String pageQuery = current > 0 ? "&page=" + current : "";
        String qQuery = query.isEmpty() ? "" : "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String keptFolder = folderQuery == null ? "" : folderQuery;
        String keptSource = sourceQuery == null ? "" : sourceQuery;
        String keptChem = chemQuery == null ? "" : chemQuery;
        String filterQuery = qQuery + (memberQuery == null ? "" : memberQuery) + (dateQuery == null ? "" : dateQuery)
                + keptFolder + keptSource + keptChem + keptSizeQuery + keptSortQuery;
        String folderBase = browse ? "/archive/all" : "/archive";
        model.addAttribute("archiveHomePath", browse ? folderBase : joinHref("/archive", keptFolder + typeQuery));
        model.addAttribute("archiveFoldersPath", "/archive");
        model.addAttribute("archivePath", folderBase);
        model.addAttribute("archiveBrowsePath", "/archive/all");
        model.addAttribute("showArchiveBrowse", false);
        model.addAttribute("folderQuery", keptFolder);
        model.addAttribute("imageArchive", type == MediaType.IMAGE);
        model.addAttribute("videoArchive", video);
        model.addAttribute("typeQuery", typeQuery);
        model.addAttribute("hasTypeQuery", video);
        model.addAttribute("mediaDateLabel", video ? "영상 날짜" : "사진 날짜");
        model.addAttribute("dateFilterLabel", video ? "영상 날짜" : "사진 날짜");
        model.addAttribute("dateFromLabel", "시작");
        model.addAttribute("dateToLabel", "끝");
        model.addAttribute("dateExactLabel", "이날");
        model.addAttribute("imageTabHref", joinHref("/archive", filterQuery + pageQuery));
        model.addAttribute("videoTabHref", joinHref("/archive?type=video", filterQuery + pageQuery));
        model.addAttribute("browseQuery", joinHref("", filterQuery));
        List<Map<String, Object>> archivePages = new ArrayList<>();
        int start = Math.max(0, current - 2);
        int end = Math.min(totalPages - 1, start + 4);
        start = Math.max(0, end - 4);
        for (int i = start; i <= end; i++) {
            archivePages.add(Map.of("num", i, "label", i + 1, "active", i == current));
        }
        model.addAttribute("archivePages", archivePages);
    }

    private static String archiveSortQuery(ArchiveSort sort) {
        if (sort == null || sort == ArchiveSort.NEWEST) return "";
        return "&sort=" + sort.key();
    }

    private static int normalizeArchiveSize(int size) {
        return ARCHIVE_SIZE_OPTIONS.contains(size) ? size : DEFAULT_ARCHIVE_SIZE;
    }

    private static String archiveSizeQuery(int size) {
        return size == DEFAULT_ARCHIVE_SIZE ? "" : "&size=" + size;
    }

    private static List<Map<String, Object>> archiveSizeOptions(int selected) {
        List<Map<String, Object>> options = new ArrayList<>(ARCHIVE_SIZE_OPTIONS.size());
        for (int value : ARCHIVE_SIZE_OPTIONS) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", value);
            option.put("label", value + "개");
            option.put("selected", value == selected);
            options.add(option);
        }
        return options;
    }

    private void addUnitCheckboxOptions(Model model) {
        model.addAttribute("unitCheckboxOptions", unitService.listUnitCheckboxOptions());
    }

    private static void addCommonDateFlags(Model model) {
        model.addAttribute("on", "");
        model.addAttribute("from", "");
        model.addAttribute("to", "");
        model.addAttribute("hasExactDate", false);
        model.addAttribute("hasDateFilter", false);
        model.addAttribute("dateExactEnabled", false);
        model.addAttribute("hasDateStat", false);
        model.addAttribute("dateStatLabel", "전체");
        model.addAttribute("folderIsSolo", false);
        model.addAttribute("folderTitleNumber", "");
        model.addAttribute("folderTitleName", "");
        model.addAttribute("query", "");
        model.addAttribute("hasQuery", false);
        model.addAttribute("mediaDateLabel", "사진 날짜");
        model.addAttribute("dateFilterLabel", "사진 날짜");
        model.addAttribute("dateFromLabel", "시작");
        model.addAttribute("dateToLabel", "끝");
        model.addAttribute("dateExactLabel", "이날");
    }

    private ArchiveSource parseSourceFilter(String sourceSlug) {
        if (sourceSlug == null || sourceSlug.isBlank()) {
            return null;
        }
        return archiveSourceService.find(sourceSlug);
    }

    private static boolean isSoloFolder(String folder) {
        return folder != null && folder.matches("(?i)s([1-9]|1[0-9]|2[0-4])");
    }

    private static boolean isUnitFolder(String folder) {
        return folder != null && folder.toLowerCase(Locale.ROOT).startsWith("unit-");
    }

    private static boolean isComboFolder(String folder) {
        return ArchiveFolderClassifier.comboSize(folder) != null;
    }

    private static boolean isUnitIndex(String folder) {
        return folder != null && (folder.equalsIgnoreCase("units") || folder.equalsIgnoreCase("unit"));
    }

    private static boolean isComboIndex(String folder) {
        return folder != null && (folder.equalsIgnoreCase("combos") || folder.equalsIgnoreCase("combo"));
    }

    private static boolean isFolderIndex(String folder) {
        return isUnitIndex(folder) || isComboIndex(folder);
    }

    private String folderBackHref(String folder, List<String> selectedMembers, List<Unit> units) {
        if (folder != null && folder.toLowerCase(Locale.ROOT).startsWith("unit-")) {
            return "/archive?folder=units";
        }
        if (isComboFolder(folder)) return "/archive?folder=combos";
        if (folder != null && folder.equalsIgnoreCase("s")
                && selectedMembers != null && !selectedMembers.isEmpty()) {
            List<ArchiveFolderClassifier.UnitMembers> snapshots = unitSnapshots(units);
            Set<String> wanted = new LinkedHashSet<>(
                    ArchiveFolderClassifier.membersOf(String.join(",", selectedMembers)));
            // 디멘션에서 연 유닛 그룹은 유닛 목록으로, 솔로는 홈 카드로, 그 외 멤버 선택은 전체 폴더로.
            if (ArchiveFolderClassifier.isExactUnitMemberSet(wanted, snapshots)) {
                return "/archive?folder=units";
            }
            ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(
                    String.join(",", selectedMembers), snapshots);
            if (match.kind() == ArchiveFolderClassifier.Kind.SOLO) return "/archive";
            return "/archive?folder=s";
        }
        return "/archive";
    }

    private static List<String> comboMembers(String combo) {
        if (combo == null || combo.isBlank()) return List.of();
        return normalizeMembers(List.of(combo.split(",")));
    }

    private static String folderQuery(String folder, String combo) {
        if (folder == null || folder.isBlank()) return "";
        String query = "&folder=" + URLEncoder.encode(folder.trim(), StandardCharsets.UTF_8);
        if (folder.equalsIgnoreCase("combo") && combo != null && !combo.isBlank()) {
            query += "&combo=" + URLEncoder.encode(combo.trim(), StandardCharsets.UTF_8);
        }
        return query;
    }


    private List<String> imageArtistRows(Archive archive) {
        String json = archive.getImageArtistsJson();
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> rows = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return rows == null ? List.of() : rows;
        } catch (Exception error) {
            return List.of();
        }
    }

    private static String castNamesAt(Archive archive, List<String> rows, int index) {
        if (rows != null && index >= 0 && index < rows.size() && rows.get(index) != null && !rows.get(index).isBlank()) {
            return rows.get(index);
        }
        return archive.getArtistNames() == null ? "" : archive.getArtistNames();
    }

    private static boolean membersOverlap(String castNames, List<String> selected) {
        if (selected == null || selected.isEmpty()) return true;
        for (String name : normalizeMembers(castNames == null || castNames.isBlank() ? List.of() : List.of(castNames.split(",")))) {
            for (String picked : selected) {
                if (name.equalsIgnoreCase(picked)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castBadges(Map<String, Object> frame) {
        Object badges = frame.get("memberBadges");
        return badges instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    /** viewUrls 안에서 표지 URL과 같은 장의 인덱스. 없으면 0. */
    private static int previewCastIndex(List<String> viewUrls, String previewSource, int castCount) {
        if (castCount <= 0) return 0;
        if (previewSource == null || previewSource.isBlank() || viewUrls == null || viewUrls.isEmpty()) {
            return 0;
        }
        String wanted = previewSource.trim();
        for (int i = 0; i < viewUrls.size() && i < castCount; i++) {
            String url = viewUrls.get(i);
            if (url != null && wanted.equals(url.trim())) return i;
        }
        return 0;
    }

    private static List<Map<String, Object>> memberBadges(String names, List<Artist> artists, Map<String, String> colors) {
        List<Map<String, Object>> badges = new ArrayList<>();
        for (String name : normalizeMembers(names == null || names.isBlank() ? List.of() : List.of(names.split(",")))) {
            Artist matched = null;
            String keyName = name.toLowerCase(Locale.ROOT);
            for (Artist artist : artists) {
                if (artist.getName() != null && artist.getName().trim().equalsIgnoreCase(name)) { matched = artist; break; }
                if (artist.getEngName() != null && artist.getEngName().trim().equalsIgnoreCase(name)) { matched = artist; break; }
            }
            String symbol = MemberPortraitOptions.archiveSymbolLogo(name);
            String portrait = matched == null || matched.getDisplayProfileImage() == null ? "" : matched.getDisplayProfileImage();
            String image = !symbol.isBlank() ? symbol : portrait;
            Map<String, Object> badge = new LinkedHashMap<>();
            badge.put("name", name);
            badge.put("color", colors.getOrDefault(keyName, "#b9cac4"));
            badge.put("image", image);
            badge.put("hasImage", !image.isBlank());
            badge.put("hasSymbol", !symbol.isBlank());
            badge.put("portrait", portrait);
            badge.put("hasPortrait", !portrait.isBlank());
            badge.put("artistId", matched == null ? "" : matched.getArtistId());
            badge.put("hasArtist", matched != null && matched.getArtistId() != null);
            badges.add(badge);
        }
        return badges;
    }

    private String unionArtistNames(String fallback, String imageArtistsJson) {
        if (imageArtistsJson == null || imageArtistsJson.isBlank()) return fallback == null ? "" : fallback;
        List<String> rows;
        try {
            rows = objectMapper.readValue(imageArtistsJson, new TypeReference<List<String>>() {});
        } catch (Exception error) {
            return fallback == null ? "" : fallback;
        }
        if (rows == null || rows.isEmpty()) return fallback == null ? "" : fallback;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String row : rows) {
            if (row == null || row.isBlank()) continue;
            for (String name : normalizeMembers(List.of(row.split(",")))) names.add(name);
        }
        if (names.isEmpty()) return fallback == null ? "" : fallback;
        return String.join(",", names);
    }

    private static String sourceQuery(String sourceSlug) {
        if (sourceSlug == null || sourceSlug.isBlank()) return "";
        return "&source=" + URLEncoder.encode(sourceSlug.trim(), StandardCharsets.UTF_8);
    }

    private static String chemQuery(String chem) {
        if (chem == null || chem.isBlank()) return "";
        return "&chem=" + URLEncoder.encode(chem.trim(), StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> sourceOptions(ArchiveSource selected) {
        List<Map<String, Object>> options = new ArrayList<>();
        String selectedCode = selected == null ? null : selected.getCode();
        Set<String> seen = new LinkedHashSet<>();
        for (var dto : archiveSourceService.listVisible()) {
            options.add(sourceOption(dto, selectedCode, false));
            seen.add(dto.code().toUpperCase(Locale.ROOT));
        }
        // 숨긴 출처도 편집 폼에서 기존 값을 고를 수 있게 남긴다(업로드는 requireVisible로 거절).
        for (var dto : archiveSourceService.listAll()) {
            if (seen.contains(dto.code().toUpperCase(Locale.ROOT))) continue;
            options.add(sourceOption(dto, selectedCode, true));
        }
        return options;
    }

    private static Map<String, Object> sourceOption(ArchiveSourceDto dto, String selectedCode, boolean hidden) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", dto.code());
        option.put("slug", dto.slug());
        option.put("label", hidden ? dto.label() + " (숨김)" : dto.label());
        option.put("color", dto.color());
        option.put("selected", selectedCode != null && selectedCode.equalsIgnoreCase(dto.code()));
        return option;
    }

    private List<Map<String, Object>> sourceTabs(String path, String extraQuery, String sourceSlug, Set<String> availableSources) {
        List<Map<String, Object>> tabs = new ArrayList<>();
        String current = sourceSlug == null ? "" : sourceSlug.trim();
        tabs.add(sourceTab("전체", joinHref(path, extraQuery), current.isBlank()));
        Set<String> available = new LinkedHashSet<>();
        if (availableSources != null) {
            for (String code : availableSources) {
                if (code != null && !code.isBlank()) available.add(code.toUpperCase(Locale.ROOT));
            }
        }
        for (var dto : archiveSourceService.listVisible()) {
            if (!available.contains(dto.code().toUpperCase(Locale.ROOT))) continue;
            tabs.add(sourceTab(dto.label(),
                    joinHref(path, extraQuery + "&source=" + dto.slug()),
                    current.equalsIgnoreCase(dto.slug()) || current.equalsIgnoreCase(dto.code())));
        }
        return tabs;
    }

    private static Map<String, Object> sourceTab(String label, String href, boolean active) {
        Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("label", label);
        tab.put("href", href);
        tab.put("active", active);
        return tab;
    }

    private List<ArchiveFolderClassifier.UnitMembers> unitSnapshots(List<Unit> units) {
        List<ArchiveFolderClassifier.UnitMembers> out = new ArrayList<>();
        if (units == null) return out;
        for (Unit unit : units) {
            if (unit == null || unit.getUnitId() == null) continue;
            Set<String> members = new LinkedHashSet<>();
            if (unit.getUnitMembers() != null) {
                for (UnitMember membership : unit.getUnitMembers()) {
                    if (membership == null || membership.getArtist() == null) continue;
                    members.addAll(ArchiveFolderClassifier.membersOf(membership.getArtist().getName()));
                }
            }
            String formation = unit.getFormationDate();
            out.add(new ArchiveFolderClassifier.UnitMembers(
                    unit.getUnitId(),
                    unit.getUnitName(),
                    members,
                    formation == null || formation.isBlank() ? null : formation.trim()));
        }
        return out;
    }

    private String folderTitle(String folder, List<String> comboMembers, List<ArchiveFolderClassifier.UnitMembers> units) {
        if (folder == null || folder.isBlank()) return "";
        if (folder.equalsIgnoreCase("s")) {
            if (comboMembers == null || comboMembers.isEmpty()) return "";
            Set<String> wanted = new LinkedHashSet<>(ArchiveFolderClassifier.membersOf(String.join(",", comboMembers)));
            for (ArchiveFolderClassifier.UnitMembers unit : units == null ? List.<ArchiveFolderClassifier.UnitMembers>of() : units) {
                if (unit != null && unit.members() != null && unit.members().equals(wanted)) {
                    return unit.unitName() == null || unit.unitName().isBlank() ? "유닛" : unit.unitName();
                }
            }
            ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(
                    String.join(",", comboMembers), units);
            if (match.kind() == ArchiveFolderClassifier.Kind.SOLO) {
                return match.label();
            }
            return "";
        }
        if (isUnitIndex(folder)) return "디멘션";
        if (isComboIndex(folder)) return "케미";
        Integer comboSize = ArchiveFolderClassifier.comboSize(folder);
        if (comboSize != null) return ArchiveFolderClassifier.comboLabel(comboSize);
        if (isComboFolder(folder)) {
            List<String> names = ArchiveFolderClassifier.membersOf(String.join(",", comboMembers));
            return names.isEmpty() ? "2명 케미" : String.join(" × ", names);
        }
        if (isSoloFolder(folder)) {
            int index = Integer.parseInt(folder.substring(1)) - 1;
            return "S" + (index + 1) + " " + MemberPortraitOptions.koreanOrder().get(index);
        }
        if (folder.toLowerCase(Locale.ROOT).startsWith("unit-")) {
            for (ArchiveFolderClassifier.UnitMembers unit : units) {
                if (("unit-" + unit.unitId()).equalsIgnoreCase(folder)) return unit.unitName();
            }
        }
        return folder;
    }

    private String folderAccent(String folder, List<String> selectedMembers, List<Unit> units, List<Artist> artists) {
        if (folder == null || folder.isBlank()) return "#00f5d4";
        if (folder.equalsIgnoreCase("s")) {
            if (selectedMembers != null && !selectedMembers.isEmpty()) {
                List<ArchiveFolderClassifier.UnitMembers> snapshots = unitSnapshots(units);
                Set<String> wanted = new LinkedHashSet<>(
                        ArchiveFolderClassifier.membersOf(String.join(",", selectedMembers)));
                for (Unit unit : units == null ? List.<Unit>of() : units) {
                    if (unit.getUnitId() == null) continue;
                    for (ArchiveFolderClassifier.UnitMembers snap : snapshots) {
                        if (snap != null && unit.getUnitId().equals(snap.unitId())
                                && snap.members() != null && snap.members().equals(wanted)) {
                            return unitColor(unit, artists);
                        }
                    }
                }
                ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(
                        String.join(",", selectedMembers), snapshots);
                if (match.kind() == ArchiveFolderClassifier.Kind.SOLO && !match.members().isEmpty()) {
                    int index = MemberPortraitOptions.memberIndex(match.members().get(0));
                    if (index < 24) {
                        for (Artist artist : artists) {
                            if (MemberPortraitOptions.memberIndex(artist.getName()) == index
                                    || MemberPortraitOptions.memberIndex(artist.getEngName()) == index) {
                                return safeColor(artist.getColor());
                            }
                        }
                    }
                }
            }
            return "#f5c518";
        }
        if (isUnitIndex(folder)) return "#a78bfa";
        if (isComboIndex(folder)) return "#7dd3fc";
        Integer comboSize = ArchiveFolderClassifier.comboSize(folder);
        if (comboSize != null) {
            String[] comboColors = { "#7dd3fc", "#a5b4fc", "#c4b5fd", "#86efac" };
            int[] folders = ArchiveFolderClassifier.COMBO_FOLDERS;
            for (int i = 0; i < folders.length; i++) {
                if (folders[i] == comboSize) return comboColors[i];
            }
            return "#a5b4fc";
        }
        if (isSoloFolder(folder)) {
            int index = Integer.parseInt(folder.substring(1)) - 1;
            for (Artist artist : artists) {
                if (MemberPortraitOptions.memberIndex(artist.getName()) == index
                        || MemberPortraitOptions.memberIndex(artist.getEngName()) == index) {
                    return safeColor(artist.getColor());
                }
            }
            return "#b9cac4";
        }
        if (folder.toLowerCase(Locale.ROOT).startsWith("unit-")) {
            for (Unit unit : units == null ? List.<Unit>of() : units) {
                if (unit.getUnitId() != null && ("unit-" + unit.getUnitId()).equalsIgnoreCase(folder)) {
                    return unitColor(unit, artists);
                }
            }
        }
        return "#00f5d4";
    }

    private record FolderCounts(Map<String, Integer> counts, Map<String, String> previews) {}

    private FolderCounts folderCounts(List<Archive> archives, List<ArchiveFolderClassifier.UnitMembers> unitMembers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> previews = new LinkedHashMap<>();
        for (Archive archive : archives) {
            counts.merge("s", 1, Integer::sum);
            ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(
                    archive.getArtistNames(), unitMembers, archive.getRegisteredOn(), archive.getUnitId());
            if (match.kind() == ArchiveFolderClassifier.Kind.OTHER) continue;
            List<String> cast = match.members();
            for (String name : cast) {
                int index = MemberPortraitOptions.memberIndex(name);
                if (index < 24) counts.merge("s" + (index + 1), 1, Integer::sum);
            }
            if (unitMembers == null) continue;
            String preview = archivePreview(archive);
            Long storedUnitId = archive.getUnitId();
            // 유닛 폴더 수는 저장된 unitId만. 출연진만 겹치면 세지 않는다.
            if (storedUnitId == null) continue;
            for (ArchiveFolderClassifier.UnitMembers unit : unitMembers) {
                if (unit == null || !storedUnitId.equals(unit.unitId())) continue;
                String key = "unit-" + unit.unitId();
                counts.merge(key, 1, Integer::sum);
                if (!preview.isBlank()) previews.putIfAbsent(key, preview);
            }
        }
        return new FolderCounts(counts, previews);
    }

    private List<Map<String, Object>> folderCards(List<Archive> archives, List<Unit> units,
                                                  List<ArchiveFolderClassifier.UnitMembers> unitMembers,
                                                  List<Artist> artists) {
        FolderCounts counted = folderCounts(archives, unitMembers);
        int unitPhotos = 0;
        for (Unit unit : units == null ? List.<Unit>of() : units) {
            if (unit.getUnitId() == null) continue;
            unitPhotos += counted.counts().getOrDefault("unit-" + unit.getUnitId(), 0);
        }
        List<Map<String, Object>> folders = new ArrayList<>();
        folders.add(folderCard("s", "tripleS", "#2f8eb4", "/archive?folder=s", counted.counts().getOrDefault("s", 0),
                "", ArchiveFolderClassifier.Kind.GROUP, true));
        folders.add(parentCard("units", "디멘션", "#7c4dff", "/archive?folder=units", unitPhotos));
        for (int i = 0; i < MemberPortraitOptions.koreanOrder().size(); i++) {
            String name = MemberPortraitOptions.koreanOrder().get(i);
            String key = "s" + (i + 1);
            String color = "#b9cac4";
            for (Artist artist : artists) {
                if (MemberPortraitOptions.memberIndex(artist.getName()) == i
                        || MemberPortraitOptions.memberIndex(artist.getEngName()) == i) {
                    color = safeColor(artist.getColor());
                    break;
                }
            }
            folders.add(folderCard(key, "S" + (i + 1) + " " + name, color,
                    "/archive?folder=s&members=" + URLEncoder.encode(name, StandardCharsets.UTF_8),
                    counted.counts().getOrDefault(key, 0), "",
                    ArchiveFolderClassifier.Kind.SOLO, false));
        }
        return folders;
    }

    private List<Map<String, Object>> unitFolderCards(List<Unit> units, List<Artist> artists, FolderCounts counted) {
        List<Map<String, Object>> folders = new ArrayList<>();
        for (Unit unit : units == null ? List.<Unit>of() : units) {
            if (unit.getUnitId() == null) continue;
            String key = "unit-" + unit.getUnitId();
            String href = "/archive?folder=unit-" + unit.getUnitId();
            String color = unitColor(unit, artists);
            folders.add(folderCard(key, unit.getUnitName(), color, href, counted.counts().getOrDefault(key, 0),
                    firstNonBlank(counted.previews().get(key), registeredUnitImage(unit)),
                    ArchiveFolderClassifier.Kind.UNIT, false));
        }
        return folders;
    }

    private Map<String, Object> parentCard(String key, String label, String color, String href, int count) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("key", key);
        card.put("label", label);
        card.put("color", color);
        card.put("href", href);
        card.put("count", count);
        card.put("hasCount", count > 0);
        card.put("preview", "");
        card.put("hasPreview", false);
        card.put("folderArt", archiveFolderArt.dimensionUrl());
        card.put("hasFolderArt", true);
        card.put("hasBundledFolderArt", true);
        card.put("illustratedFolder", ArchiveFolderArt.DIMENSION_ART.equals(card.get("folderArt")));
        card.put("groupFolder", false);
        card.put("soloFolder", false);
        card.put("comboFolder", false);
        card.put("parentFolder", true);
        card.put("selectable", false);
        card.put("memberName", "");
        return card;
    }

    private static String archivePreview(Archive archive) {
        if (archive.getType() == MediaType.IMAGE && archive.getFile() != null && archive.getFile().getUrl() != null) {
            return archive.getFile().getUrl();
        }
        return archive.getThumbnailUrl() == null ? "" : archive.getThumbnailUrl();
    }

    private static String unitColor(Unit unit, List<Artist> artists) {
        if (unit != null) {
            String folderColor = ArchiveFolderArt.unitColor(unit.getUnitName());
            if (!folderColor.isBlank()) return folderColor;
            if (unit.getUnitMembers() != null) {
                for (UnitMember membership : unit.getUnitMembers()) {
                    if (membership.getArtist() != null) return safeColor(membership.getArtist().getColor());
                }
            }
        }
        return artists == null || artists.isEmpty() ? "#a78bfa" : safeColor(artists.get(0).getColor());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null ? "" : second;
    }

    private static String registeredUnitImage(Unit unit) {
        if (unit == null) return "";
        String image = unit.getUnitImage();
        if (image == null || image.isBlank()) return "";
        String trimmed = image.trim();
        if (trimmed.equals(ImageUrls.PLACEHOLDER) || trimmed.endsWith("image-upload-pending.png")) return "";
        return unit.getDisplayUnitImage();
    }

    private record ChemistryPick(String key, String label, List<String> members) {}

    private ChemistryPick resolveChemistry(String chem, Integer comboMin) {
        if (chem == null || chem.isBlank()) return null;
        String value = chem.trim();
        if (value.regionMatches(true, 0, "pair-", 0, 5)) {
            if (comboMin != null && comboMin != 2) return null;
            Long id = parseId(value.substring(5));
            if (id == null) return null;
            for (ChemistryDto item : safeChemistries()) {
                if (id.equals(item.getId())) {
                    List<String> members = ArchiveFolderClassifier.membersOf(item.getMemberA() + "," + item.getMemberB());
                    if (members.size() != 2) return null;
                    String label = item.getLabel() == null || item.getLabel().isBlank()
                            ? String.join(" × ", members) : item.getLabel().trim();
                    return new ChemistryPick("pair-" + id, label, members);
                }
            }
            return null;
        }
        if (value.regionMatches(true, 0, "group-", 0, 6)) {
            Long id = parseId(value.substring(6));
            if (id == null) return null;
            for (ChemistryGroupDto item : safeChemistryGroups()) {
                if (!id.equals(item.getId())) continue;
                List<String> members = ArchiveFolderClassifier.membersOf(String.join(",",
                        item.getMembers() == null ? List.of() : item.getMembers()));
                if (!chemistryFitsFolder(members.size(), comboMin)) return null;
                String label = item.getLabel() == null || item.getLabel().isBlank()
                        ? String.join(" × ", members) : item.getLabel().trim();
                return new ChemistryPick("group-" + id, label, members);
            }
        }
        return null;
    }

    private List<Map<String, Object>> chemistryOptions(String folder, ChemistryPick selected) {
        Integer min = ArchiveFolderClassifier.comboSize(folder);
        if (min == null) return List.of();
        List<Map<String, Object>> options = new ArrayList<>();
        String selectedKey = selected == null ? "" : selected.key();
        if (min == 2) {
            for (ChemistryDto item : safeChemistries()) {
                if (item.getId() == null) continue;
                List<String> members = ArchiveFolderClassifier.membersOf(item.getMemberA() + "," + item.getMemberB());
                if (members.size() != 2) continue;
                String key = "pair-" + item.getId();
                String label = item.getLabel() == null || item.getLabel().isBlank()
                        ? String.join(" × ", members) : item.getLabel().trim();
                options.add(chemistryOption(key, label, members, key.equals(selectedKey)));
            }
            return options;
        }
        for (ChemistryGroupDto item : safeChemistryGroups()) {
            if (item.getId() == null) continue;
            List<String> members = ArchiveFolderClassifier.membersOf(String.join(",",
                    item.getMembers() == null ? List.of() : item.getMembers()));
            if (!chemistryFitsFolder(members.size(), min)) continue;
            String key = "group-" + item.getId();
            String label = item.getLabel() == null || item.getLabel().isBlank()
                    ? String.join(" × ", members) : item.getLabel().trim();
            options.add(chemistryOption(key, label, members, key.equals(selectedKey)));
        }
        return options;
    }

    private static boolean chemistryFitsFolder(int memberCount, Integer comboMin) {
        if (comboMin == null || memberCount < 2) return false;
        if (comboMin <= 4) return memberCount == comboMin;
        return memberCount >= comboMin;
    }

    private static Map<String, Object> chemistryOption(String value, String label, List<String> members, boolean selected) {
        Map<String, Object> option = new LinkedHashMap<>();
        List<String> names = members == null ? List.of() : members;
        option.put("value", value);
        option.put("shortLabel", label);
        option.put("members", String.join(",", names));
        option.put("label", names.isEmpty() ? label : label + " · " + String.join(" × ", names));
        option.put("selected", selected);
        return option;
    }

    private String chemistryNamesJson() {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        Set<String> seen = new LinkedHashSet<>();
        for (ChemistryDto item : safeChemistries()) {
            String rawMembers = (item.getMemberA() == null ? "" : item.getMemberA())
                    + "," + (item.getMemberB() == null ? "" : item.getMemberB());
            first = appendChemistryName(json, first, seen, ArchiveFolderClassifier.membersOf(rawMembers), item.getLabel(),
                    "pair", item.getId());
        }
        for (ChemistryGroupDto item : safeChemistryGroups()) {
            List<String> members = item.getMembers() == null ? List.of() : item.getMembers();
            first = appendChemistryName(json, first, seen,
                    ArchiveFolderClassifier.membersOf(String.join(",", members)), item.getLabel(), "group", item.getId());
        }
        return json.append(']').toString();
    }

    private static boolean appendChemistryName(StringBuilder json, boolean first, Set<String> seen,
                                               List<String> members, String rawLabel, String type, Long id) {
        if (members == null || members.size() < 2) return first;
        String label = chemistryDisplayLabel(rawLabel);
        if (label.isEmpty()) return first;
        if (!seen.add(String.join("\u0000", members))) return first;
        if (!first) json.append(',');
        json.append("{\"members\":[");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) json.append(',');
            json.append(jsonString(members.get(i)));
        }
        json.append("],\"label\":").append(jsonString(label));
        if (id != null) {
            json.append(",\"href\":").append(jsonString("/triples/chemistry?" + type + "=" + id));
        }
        json.append('}');
        return false;
    }

    private static String chemistryDisplayLabel(String label) {
        if (label == null || label.isBlank()) return "";
        List<String> parts = new ArrayList<>();
        for (String line : label.split("\\R+")) {
            String trimmed = line.trim().replaceAll("\\s+", " ");
            if (!trimmed.isEmpty()) parts.add(trimmed);
        }
        return String.join(" · ", parts);
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '<' -> out.append("\\u003c");
                default -> out.append(ch);
            }
        }
        return out.append('"').toString();
    }

    private List<ChemistryDto> safeChemistries() {
        try {
            List<ChemistryDto> items = chemistryService.findAll();
            return items == null ? List.of() : items;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<ChemistryGroupDto> safeChemistryGroups() {
        try {
            List<ChemistryGroupDto> items = chemistryGroupService.findAll();
            return items == null ? List.of() : items;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> folderCard(String key, String label, String color, String href,
                                           int count, String preview, ArchiveFolderClassifier.Kind kind, boolean group) {
        Map<String, Object> card = new LinkedHashMap<>();
        boolean hideArt = kind == ArchiveFolderClassifier.Kind.COMBO;
        String art = hideArt ? "" : archiveFolderArt.urlFor(kind, key, label);
        String shownPreview = hideArt || kind == ArchiveFolderClassifier.Kind.SOLO ? "" : (preview == null ? "" : preview);
        boolean customArt = !art.isBlank();
        card.put("key", key);
        card.put("label", label);
        card.put("color", color == null || color.isBlank() ? "#b9cac4" : color);
        card.put("href", href);
        card.put("count", count);
        card.put("hasCount", count > 0);
        card.put("preview", shownPreview);
        card.put("hasPreview", !customArt && !shownPreview.isBlank());
        card.put("folderArt", art);
        card.put("hasFolderArt", customArt);
        card.put("hasBundledFolderArt", archiveFolderArt.isBundled(art));
        card.put("illustratedFolder", ArchiveFolderArt.GROUP_ART.equals(art) || ArchiveFolderArt.DIMENSION_ART.equals(art));
        card.put("groupFolder", group);
        card.put("soloFolder", kind == ArchiveFolderClassifier.Kind.SOLO);
        card.put("comboFolder", kind == ArchiveFolderClassifier.Kind.COMBO);
        card.put("parentFolder", false);
        card.put("selectable", false);
        card.put("memberName", "");
        return card;
    }

    private static Long parseUnitId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            long id = Long.parseLong(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private void applyUploadedUnitId(Archive saved, String unitId) {
        if (saved == null || saved.getId() == null) return;
        archiveService.assignUnitId(saved.getId(), parseUnitId(unitId));
    }

    private String folderRedirect(MediaType type, String artistNames) {
        return folderRedirect(type, artistNames, null);
    }

    private String folderRedirect(MediaType type, String artistNames, Long assignedUnitId) {
        String typeQuery = type == MediaType.VIDEO ? "type=video&" : "";
        if (assignedUnitId != null && assignedUnitId > 0) {
            return "/archive?" + typeQuery + "folder=unit-" + assignedUnitId;
        }
        ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(
                artistNames, unitSnapshots(unitService.listVisibleForArchive()), null, null);
        if (match.kind() == ArchiveFolderClassifier.Kind.OTHER || match.members().isEmpty()) {
            return type == MediaType.VIDEO ? "/archive?type=video" : "/archive";
        }
        if (match.kind() == ArchiveFolderClassifier.Kind.GROUP) {
            return "/archive?" + typeQuery + "folder=s";
        }
        return "/archive?" + typeQuery + groupFolderQuery(match.members());
    }

    private static List<String> unitMembersOf(Unit unit) {
        List<String> names = new ArrayList<>();
        if (unit.getUnitMembers() == null) return names;
        for (UnitMember membership : unit.getUnitMembers()) {
            if (membership == null || membership.getArtist() == null) continue;
            names.addAll(ArchiveFolderClassifier.membersOf(membership.getArtist().getName()));
        }
        return ArchiveFolderClassifier.membersOf(String.join(",", names));
    }

    private static String groupFolderHref(List<String> members) {
        return "/archive?" + groupFolderQuery(members);
    }

    private static String groupFolderQuery(List<String> members) {
        StringBuilder query = new StringBuilder("folder=s");
        for (String member : members == null ? List.<String>of() : members) {
            if (member == null || member.isBlank()) continue;
            query.append("&members=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private static String joinHref(String path, String extraQuery) {
        if (extraQuery == null || extraQuery.isBlank()) return path;
        String extra = extraQuery.startsWith("&") || extraQuery.startsWith("?") ? extraQuery.substring(1) : extraQuery;
        if (extra.isBlank()) return path;
        return path.contains("?") ? path + "&" + extra : path + "?" + extra;
    }

    @PostMapping("/upload")
    public String uploadArchive(@RequestParam("title") String title,
                                @RequestParam("type") MediaType type,
                                @RequestParam("file") MultipartFile file, @RequestParam(value="artistNames", required=false) List<String> artistNames,
                                @RequestParam("registeredOn") LocalDate registeredOn,
                                @RequestParam("source") String source,
                                @RequestParam(value = "unitId", required = false) String unitId,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        var replay = idempotencyService.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey);
        if (replay.isPresent()) {
            return "redirect:" + replay.get();
        }
        try {
            String names = artistNames == null ? "" : String.join(",", artistNames);
            Archive saved = archiveService.save(file, title, type, names, requireDate(registeredOn), requireSource(source));
            applyUploadedUnitId(saved, unitId);
            String location = folderRedirect(type, names, parseUnitId(unitId));
            idempotencyService.complete(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey, location);
            return "redirect:" + location;
        } catch (RuntimeException | IOException error) {
            idempotencyService.abandon(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey);
            throw error;
        }
    }

    @PostMapping("/upload/image")
    public String uploadImage(@RequestParam("file") List<MultipartFile> files,
                              @RequestParam(value = "title", required = false) String title,
                              @RequestParam(value = "description", required = false) String description,
                              @RequestParam(value="artistNames", required=false) List<String> artistNames,
                              @RequestParam(value = "imageArtistsJson", required = false) String imageArtistsJson,
                              @RequestParam("registeredOn") LocalDate registeredOn,
                              @RequestParam("source") String source,
                              @RequestParam(value = "unitId", required = false) String unitId,
                              @RequestParam(defaultValue = "0") int coverIndex,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                              HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        var replay = idempotencyService.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey);
        if (replay.isPresent()) {
            return "redirect:" + replay.get();
        }
        try {
            String names = artistNames == null ? "" : String.join(",", artistNames);
            LocalDate date = requireDate(registeredOn);
            String archiveSource = requireSource(source);
            String safeTitle = title == null ? "" : title;
            List<MultipartFile> uploads = files == null ? List.of() : files.stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .toList();
            if (uploads.isEmpty()) {
                throw new IllegalArgumentException("사진 파일을 선택해 주세요.");
            }
            String castNames = unionArtistNames(names, imageArtistsJson);
            Archive saved;
            if (imageArtistsJson == null || imageArtistsJson.isBlank()) {
                saved = archiveService.uploadImages(uploads, safeTitle, castNames, date, archiveSource, description, coverIndex);
            } else {
                saved = archiveService.uploadImages(uploads, safeTitle, castNames, date, archiveSource, description, coverIndex, imageArtistsJson);
            }
            applyUploadedUnitId(saved, unitId);
            String location = folderRedirect(MediaType.IMAGE, names, parseUnitId(unitId));
            idempotencyService.complete(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey, location);
            return "redirect:" + location;
        } catch (RuntimeException | IOException error) {
            idempotencyService.abandon(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey);
            throw error;
        }
    }

    @PostMapping("/upload/video")
    public String uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames,
                              @RequestParam("registeredOn") LocalDate registeredOn,
                              @RequestParam("source") String source,
                              @RequestParam(value = "unitId", required = false) String unitId,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                              HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        var replay = idempotencyService.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey);
        if (replay.isPresent()) {
            return "redirect:" + replay.get();
        }
        try {
            String names = artistNames == null ? "" : String.join(",", artistNames);
            Archive saved = archiveService.uploadVideoOnly(file, title, MediaType.VIDEO, names, thumbnail, requireDate(registeredOn), requireSource(source));
            applyUploadedUnitId(saved, unitId);
            String location = folderRedirect(MediaType.VIDEO, names, parseUnitId(unitId));
            idempotencyService.complete(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey, location);
            return "redirect:" + location;
        } catch (RuntimeException | IOException error) {
            idempotencyService.abandon(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, idempotencyKey);
            throw error;
        }
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateArchive(@PathVariable long id,
                                           @RequestParam(value = "title", required = false) String title,
                                           @RequestParam(value = "description", required = false) String description,
                                           @RequestParam(value = "file", required = false) List<MultipartFile> files,
                                           @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                                           @RequestParam(defaultValue = "false") boolean clearThumbnail,
                                           @RequestParam(value = "artistNames", required = false) String artistNames,
                                           @RequestParam(value = "imageArtistsJson", required = false) String imageArtistsJson,
                                           @RequestParam("registeredOn") LocalDate registeredOn,
                                           @RequestParam(value = "source", required = false) String source,
                                           @RequestParam(value = "keepImageUrlsJson", required = false) String keepImageUrlsJson,
                                           @RequestParam(value = "imageOrderJson", required = false) String imageOrderJson,
                                           @RequestParam(required = false) Integer coverIndex,
                                           @RequestParam(value = "unitId", required = false) String unitId,
                                           HttpServletRequest request,
                                           HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            String castNames = unionArtistNames(artistNames, imageArtistsJson);
            String sourceCode = source == null || source.isBlank()
                    ? null
                    : archiveSourceService.requireExisting(source).getCode();
            if (imageArtistsJson == null || imageArtistsJson.isBlank()) {
                archiveService.update(id, title == null ? "" : title, files, castNames, thumbnail, clearThumbnail,
                        requireDate(registeredOn),
                        sourceCode,
                        description,
                        keepImageUrlsJson,
                        imageOrderJson,
                        coverIndex);
            } else {
                archiveService.update(id, title == null ? "" : title, files, castNames, thumbnail, clearThumbnail,
                        requireDate(registeredOn),
                        sourceCode,
                        description,
                        keepImageUrlsJson,
                        imageOrderJson,
                        coverIndex,
                        imageArtistsJson);
            }
            archiveService.assignUnitId(id, parseUnitId(unitId));
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteArchive(@PathVariable long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        boolean ok = archiveService.delete(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<String> invalidUpload(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
