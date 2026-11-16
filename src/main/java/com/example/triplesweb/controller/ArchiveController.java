package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ChemistryGroupService;
import com.example.triplesweb.service.ChemistryService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.util.ArchiveFolderArt;
import com.example.triplesweb.util.ArchiveFolderClassifier;
import com.example.triplesweb.util.ImageUrls;
import com.example.triplesweb.util.MemberPortraitOptions;
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
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/archive")
public class ArchiveController {

    private final ArchiveService archiveService;
    private final AboutAdminAuth aboutAdminAuth;
    private final ArtistRepository artistRepository;
    private final UnitService unitService;
    private final ArchiveFolderArt archiveFolderArt;
    private final ChemistryService chemistryService;
    private final ChemistryGroupService chemistryGroupService;

    @GetMapping
    public String showArchive(@RequestParam(defaultValue = "image") String type,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "9") int size,
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
                              HttpServletRequest request, HttpSession session, Model model) {
        String jump = pageJumpRedirect(go, request);
        if (jump != null) return jump;
        if (folder == null || folder.isBlank()) {
            return renderFolderHome(request, session, model);
        }
        return renderArchive(type, page, size, q, from, to, on, members, folder, combo, source, chem,
                false, request, session, model);
    }

    @GetMapping("/all")
    public String showAllImages(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "24") int size,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String from,
                                @RequestParam(required = false) String to,
                                @RequestParam(required = false) String on,
                                @RequestParam(required = false) Integer go,
                                @RequestParam(required = false) List<String> members,
                                HttpServletRequest request, HttpSession session, Model model) {
        String jump = pageJumpRedirect(go, request);
        if (jump != null) return jump;
        return renderArchive("image", page, size, q, from, to, on, members, null, null, null, null,
                true, request, session, model);
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
        List<Artist> artists = artistRepository.findAll();
        List<Unit> units = unitService.listVisible();
        List<ArchiveFolderClassifier.UnitMembers> unitMembers = unitSnapshots(units);
        List<Archive> archives = archiveService.index();
        List<Map<String, Object>> folders = folderCards(archives, units, unitMembers, artists);
        long photoCount = 0;
        long videoCount = 0;
        for (Archive archive : archives) {
            if (archive.getType() == MediaType.IMAGE) photoCount++;
            else if (archive.getType() == MediaType.VIDEO) videoCount++;
        }
        model.addAttribute("archiveFolderHome", true);
        model.addAttribute("archiveFolderView", false);
        model.addAttribute("archiveBrowse", false);
        model.addAttribute("archiveFolders", folders);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isAuthenticated", isAdmin);
        model.addAttribute("artistOptions", artists);
        model.addAttribute("memberPickerOptions", memberOptions(artists, List.of()));
        model.addAttribute("imageArchive", true);
        model.addAttribute("videoArchive", false);
        model.addAttribute("archiveTotal", photoCount);
        model.addAttribute("archiveVideoTotal", videoCount);
        model.addAttribute("archiveFolderCount", folders.size());
        model.addAttribute("archivePageCount", folders.size());
        model.addAttribute("archivePageDisplay", 1);
        model.addAttribute("archiveTotalPages", 1);
        model.addAttribute("hasArchives", !folders.isEmpty());
        model.addAttribute("archiveSources", sourceOptions(null));
        addCommonDateFlags(model);
        return "archive/archive";
    }

    private String renderArchive(String typeSlug, int page, int size, String q, String from, String to, String on,
                                 List<String> members, String folder, String combo, String sourceSlug, String chem,
                                 boolean browse, HttpServletRequest request, HttpSession session, Model model) {
        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        boolean isAuthenticated = isAdmin;

        MediaType type = mediaType(typeSlug);
        String folderKey = ArchiveFolderClassifier.canonicalComboFolder(folder == null ? "" : folder.trim());
        List<String> comboMembers = comboMembers(combo);
        ChemistryPick chemistry = resolveChemistry(chem, ArchiveFolderClassifier.comboSize(folderKey));
        if (chemistry != null) comboMembers = chemistry.members();
        List<String> selectedMembers = isSoloFolder(folderKey) ? List.of()
                : (chemistry != null ? chemistry.members() : normalizeMembers(members));
        LocalDate onDate = parseDate(on);
        LocalDate fromDate = onDate != null ? onDate : parseDate(from);
        LocalDate toDate = onDate != null ? onDate : parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        ArchiveSource source = null;
        boolean unclassified = isFanmadeSource(sourceSlug);
        if (!unclassified && sourceSlug != null && !sourceSlug.isBlank()) {
            try {
                source = ArchiveSource.parse(sourceSlug);
            } catch (IllegalArgumentException ignored) {
                source = null;
            }
        }
        List<Unit> visibleUnits = unitService.listVisible();
        List<ArchiveFolderClassifier.UnitMembers> units = unitSnapshots(visibleUnits);
        Page<Archive> archives = browse
                ? archiveService.pageByType(type, page, size, q, selectedMembers, fromDate, toDate)
                : archiveService.pageInFolder(type, page, size, q, selectedMembers, fromDate, toDate,
                    source, unclassified, folderKey, comboMembers, units);

        List<Artist> artists = artistRepository.findAll();
        List<Map<String, Object>> cards = archiveCards(archives, artists);
        model.addAttribute("archives", archives);
        model.addAttribute("archiveCards", cards);
        model.addAttribute("artistOptions", artists);
        model.addAttribute("memberPickerOptions", memberOptions(artists, selectedMembers));
        model.addAttribute("selectedMembers", selectedMembers);
        model.addAttribute("hasSelectedMembers", !selectedMembers.isEmpty());
        model.addAttribute("page", archives);
        model.addAttribute("type", type);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("archiveBrowse", browse);
        model.addAttribute("archiveFolderHome", false);
        model.addAttribute("archiveFolderView", !browse);
        model.addAttribute("showMemberFilter", browse || !isSoloFolder(folderKey));
        model.addAttribute("showChemistryFilter", !browse && isComboFolder(folderKey));
        List<Map<String, Object>> chemistryOptions = chemistryOptions(folderKey, chemistry);
        model.addAttribute("chemistryOptions", chemistryOptions);
        model.addAttribute("hasChemistryOptions", !chemistryOptions.isEmpty());
        model.addAttribute("folderKey", folderKey);
        model.addAttribute("comboValue", combo == null ? "" : combo.trim());
        model.addAttribute("hasCombo", combo != null && !combo.isBlank());
        String chemKey = chemistry == null ? "" : chemistry.key();
        model.addAttribute("chemKey", chemKey);
        model.addAttribute("hasChemFilter", !chemKey.isBlank());
        model.addAttribute("folderTitle", chemistry != null ? chemistry.label() : folderTitle(folderKey, comboMembers, units));
        model.addAttribute("folderAccent", folderAccent(folderKey, visibleUnits, artists));
        model.addAttribute("folderIsSolo", isSoloFolder(folderKey));
        String onText = onDate == null ? "" : onDate.toString();
        String fromText = onDate != null ? "" : (fromDate == null ? "" : fromDate.toString());
        String toText = onDate != null ? "" : (toDate == null ? "" : toDate.toString());
        model.addAttribute("on", onText);
        model.addAttribute("from", fromText);
        model.addAttribute("to", toText);
        model.addAttribute("hasExactDate", onDate != null);
        model.addAttribute("hasDateFilter", onDate != null || fromDate != null || toDate != null);
        model.addAttribute("dateExactEnabled", false);
        String dateQuery = dateQuery(fromText, toText, onText);
        model.addAttribute("dateQuery", dateQuery);
        String sourceQuery = sourceQuery(sourceSlug);
        model.addAttribute("sourceQuery", sourceQuery);
        model.addAttribute("sourceKey", sourceSlug == null ? "" : sourceSlug.trim());
        model.addAttribute("hasSourceFilter", sourceSlug != null && !sourceSlug.isBlank());
        String chemQuery = chemQuery(chemKey);
        model.addAttribute("chemQuery", chemQuery);
        model.addAttribute("archiveSources", sourceOptions(source));
        addFilterSummary(model, q, selectedMembers, artists, fromText, toText, onText);
        addArchivePage(model, archives, type, q, memberQuery(selectedMembers), dateQuery, browse,
                folderQuery(folderKey, combo), sourceQuery, sourceSlug, chemQuery);
        return "archive/archive";
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

    private static List<Map<String, Object>> memberOptions(List<Artist> artists, List<String> selected) {
        Set<String> chosen = Set.copyOf(selected);
        List<Map<String, Object>> options = MemberPortraitOptions.fromArtists(artists, false);
        for (Map<String, Object> option : options) {
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

    private static ArchiveSource requireSource(String source) {
        ArchiveSource parsed = ArchiveSource.parse(source);
        if (parsed == null) throw new IllegalArgumentException("출처를 선택하세요.");
        return parsed;
    }

    private static String memberQuery(List<String> selected) {
        if (selected.isEmpty()) return "";
        StringBuilder query = new StringBuilder();
        for (String member : selected) {
            query.append("&members=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private static List<Map<String, Object>> archiveCards(Page<Archive> archives, List<Artist> artists) {
        Map<String, String> colors = new LinkedHashMap<>();
        for (Artist artist : artists) {
            String color = artist.getColor();
            if (color == null || !color.matches("#[0-9a-fA-F]{6}")) color = "#b9cac4";
            if (artist.getName() != null) colors.put(artist.getName().trim().toLowerCase(Locale.ROOT), color);
            if (artist.getEngName() != null) colors.put(artist.getEngName().trim().toLowerCase(Locale.ROOT), color);
        }
        long offset = archives.getPageable().isPaged() ? archives.getPageable().getOffset() : 0;
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Archive archive : archives) {
            List<Map<String, Object>> badges = new ArrayList<>();
            for (String name : normalizeMembers(archive.getArtistNames() == null ? List.of() : List.of(archive.getArtistNames().split(",")))) {
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
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", archive.getId());
            card.put("title", archive.getTitle());
            card.put("artistNames", archive.getArtistNames());
            card.put("memberOrder", MemberPortraitOptions.earliestMemberIndex(archive.getArtistNames()));
            card.put("file", archive.getFile());
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
            card.put("hasRegisteredOn", !registered.isBlank());
            ArchiveSource source = archive.getSource();
            card.put("sourceValue", source == null ? "" : source.name());
            card.put("sourceLabel", source == null || source == ArchiveSource.FANCAM ? "기타/팬메이드" : source.getLabel());
            card.put("hasSource", source != null);
            cards.add(card);
        }
        return cards;
    }

    private void addFilterSummary(Model model, String q, List<String> selectedMembers, List<Artist> artists,
                                  String fromText, String toText, String onText) {
        List<Map<String, Object>> parts = new ArrayList<>();
        int groups = 0;
        if (!selectedMembers.isEmpty()) {
            groups++;
            Map<String, Object> first = memberFilterPart(selectedMembers.get(0), artists);
            first.put("isMember", true);
            parts.add(first);
            if (selectedMembers.size() > 1) {
                Map<String, Object> more = new LinkedHashMap<>();
                more.put("isMore", true);
                more.put("text", "외 " + (selectedMembers.size() - 1) + "명");
                parts.add(more);
            }
        }
        String query = q == null ? "" : q.trim();
        if (!query.isEmpty()) {
            groups++;
            if (!parts.isEmpty()) parts.add(Map.of("isSep", true));
            parts.add(Map.of("isText", true, "text", query));
        }
        if (onText != null && !onText.isBlank()) {
            groups++;
            if (!parts.isEmpty()) parts.add(Map.of("isSep", true));
            parts.add(Map.of("isText", true, "text", onText));
        } else if ((fromText != null && !fromText.isBlank()) || (toText != null && !toText.isBlank())) {
            groups++;
            if (!parts.isEmpty()) parts.add(Map.of("isSep", true));
            String start = fromText == null || fromText.isBlank() ? "처음" : fromText;
            String end = toText == null || toText.isBlank() ? "최근" : toText;
            parts.add(Map.of("isText", true, "text", start + " ~ " + end));
        }
        model.addAttribute("hasFilters", groups > 0);
        model.addAttribute("filterParts", parts);
        model.addAttribute("filterCount", Math.max(1, groups));
    }

    private static Map<String, Object> memberFilterPart(String name, List<Artist> artists) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", name);
        String color = "#b9cac4";
        for (Artist artist : artists) {
            if (artist.getName() != null && artist.getName().trim().equalsIgnoreCase(name)) {
                color = safeColor(artist.getColor());
                break;
            }
            if (artist.getEngName() != null && artist.getEngName().trim().equalsIgnoreCase(name)) {
                color = safeColor(artist.getColor());
                break;
            }
        }
        part.put("color", color);
        return part;
    }

    private static String safeColor(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : "#b9cac4";
    }

    private void addArchivePage(Model model, Page<Archive> archives, MediaType type, String q, String memberQuery,
                                String dateQuery, boolean browse, String folderQuery, String sourceQuery, String sourceSlug,
                                String chemQuery) {
        int totalPages = Math.max(1, archives.getTotalPages());
        int current = archives.getNumber();
        String query = q == null ? "" : q.trim();
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
        boolean video = type == MediaType.VIDEO;
        String typeQuery = video ? "&type=video" : "";
        String pageQuery = current > 0 ? "&page=" + current : "";
        String qQuery = query.isEmpty() ? "" : "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String keptFolder = folderQuery == null ? "" : folderQuery;
        String keptSource = sourceQuery == null ? "" : sourceQuery;
        String keptChem = chemQuery == null ? "" : chemQuery;
        String filterQuery = qQuery + (memberQuery == null ? "" : memberQuery) + (dateQuery == null ? "" : dateQuery)
                + keptFolder + keptSource + keptChem;
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
        model.addAttribute("dateFromLabel", video ? "영상 날짜 시작" : "사진 날짜 시작");
        model.addAttribute("dateToLabel", video ? "영상 날짜 끝" : "사진 날짜 끝");
        model.addAttribute("dateExactLabel", video ? "이 영상 날짜" : "이 사진 날짜");
        model.addAttribute("imageTabHref", joinHref("/archive", filterQuery + pageQuery));
        model.addAttribute("videoTabHref", joinHref("/archive?type=video", filterQuery + pageQuery));
        model.addAttribute("browseQuery", joinHref("", filterQuery));
        model.addAttribute("sourceTabs", sourceTabs(folderBase, typeQuery + keptFolder
                + qQuery + (memberQuery == null ? "" : memberQuery) + (dateQuery == null ? "" : dateQuery) + keptChem, sourceSlug));
        List<Map<String, Object>> archivePages = new ArrayList<>();
        int start = Math.max(0, current - 2);
        int end = Math.min(totalPages - 1, start + 4);
        start = Math.max(0, end - 4);
        for (int i = start; i <= end; i++) {
            archivePages.add(Map.of("num", i, "label", i + 1, "active", i == current));
        }
        model.addAttribute("archivePages", archivePages);
    }

    private static void addCommonDateFlags(Model model) {
        model.addAttribute("on", "");
        model.addAttribute("from", "");
        model.addAttribute("to", "");
        model.addAttribute("hasExactDate", false);
        model.addAttribute("hasDateFilter", false);
        model.addAttribute("dateExactEnabled", false);
        model.addAttribute("query", "");
        model.addAttribute("hasQuery", false);
        model.addAttribute("mediaDateLabel", "사진 날짜");
        model.addAttribute("dateFilterLabel", "사진 날짜");
        model.addAttribute("dateFromLabel", "사진 날짜 시작");
        model.addAttribute("dateToLabel", "사진 날짜 끝");
        model.addAttribute("dateExactLabel", "이 사진 날짜");
    }

    private static boolean isSoloFolder(String folder) {
        return folder != null && folder.matches("(?i)s([1-9]|1[0-9]|2[0-4])");
    }

    private static boolean isComboFolder(String folder) {
        return folder != null && (folder.equalsIgnoreCase("combo") || ArchiveFolderClassifier.comboSize(folder) != null);
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

    private static String sourceQuery(String sourceSlug) {
        if (sourceSlug == null || sourceSlug.isBlank()) return "";
        return "&source=" + URLEncoder.encode(sourceSlug.trim(), StandardCharsets.UTF_8);
    }

    private static String chemQuery(String chem) {
        if (chem == null || chem.isBlank()) return "";
        return "&chem=" + URLEncoder.encode(chem.trim(), StandardCharsets.UTF_8);
    }

    private static List<Map<String, Object>> sourceOptions(ArchiveSource selected) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (ArchiveSource source : ArchiveSource.values()) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", source.name());
            option.put("slug", source.getSlug());
            option.put("label", source.getLabel());
            option.put("selected", source == selected);
            options.add(option);
        }
        return options;
    }

    private static List<Map<String, Object>> sourceTabs(String path, String extraQuery, String sourceSlug) {
        List<Map<String, Object>> tabs = new ArrayList<>();
        String current = sourceSlug == null ? "" : sourceSlug.trim();
        tabs.add(sourceTab("전체", joinHref(path, extraQuery), current.isBlank()));
        for (ArchiveSource source : ArchiveSource.values()) {
            if (source == ArchiveSource.FANCAM) continue;
            tabs.add(sourceTab(source.getLabel(),
                    joinHref(path, extraQuery + "&source=" + source.getSlug()),
                    current.equalsIgnoreCase(source.getSlug()) || current.equalsIgnoreCase(source.name())));
        }
        tabs.add(sourceTab("기타/팬메이드",
                joinHref(path, extraQuery + "&source=fanmade"),
                isFanmadeSource(current)));
        return tabs;
    }

    private static boolean isFanmadeSource(String slug) {
        if (slug == null || slug.isBlank()) return false;
        String value = slug.trim();
        return value.equalsIgnoreCase("unclassified")
                || value.equalsIgnoreCase("fanmade")
                || value.equalsIgnoreCase("other")
                || value.equalsIgnoreCase("fancam")
                || value.equalsIgnoreCase(ArchiveSource.FANCAM.name());
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
            out.add(new ArchiveFolderClassifier.UnitMembers(unit.getUnitId(), unit.getUnitName(), members));
        }
        return out;
    }

    private String folderTitle(String folder, List<String> comboMembers, List<ArchiveFolderClassifier.UnitMembers> units) {
        if (folder == null || folder.isBlank()) return "";
        if (folder.equalsIgnoreCase("s")) return "전체 멤버";
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

    private String folderAccent(String folder, List<Unit> units, List<Artist> artists) {
        if (folder == null || folder.isBlank()) return "#00f5d4";
        if (folder.equalsIgnoreCase("s")) return "#f5c518";
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

    private List<Map<String, Object>> folderCards(List<Archive> archives, List<Unit> units,
                                                  List<ArchiveFolderClassifier.UnitMembers> unitMembers,
                                                  List<Artist> artists) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> previews = new LinkedHashMap<>();
        Set<String> comboKeys = new LinkedHashSet<>();
        for (Archive archive : archives) {
            ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(archive.getArtistNames(), unitMembers);
            if (match.kind() == ArchiveFolderClassifier.Kind.OTHER) continue;
            String preview = match.kind() == ArchiveFolderClassifier.Kind.SOLO ? "" : archivePreview(archive);
            if (match.kind() == ArchiveFolderClassifier.Kind.COMBO) {
                int n = match.members().size();
                for (int min : ArchiveFolderClassifier.COMBO_FOLDERS) {
                    if (n < min) continue;
                    String key = "combo-" + min;
                    counts.merge(key, 1, Integer::sum);
                    comboKeys.add(key);
                    if (!preview.isBlank()) previews.putIfAbsent(key, preview);
                }
                continue;
            }
            String key = match.key();
            counts.merge(key, 1, Integer::sum);
            if (!preview.isBlank()) previews.putIfAbsent(key, preview);
        }
        List<Map<String, Object>> folders = new ArrayList<>();
        folders.add(folderCard("s", "전체 멤버", "#f5c518", "/archive?folder=s", counts.getOrDefault("s", 0),
                previews.get("s"), ArchiveFolderClassifier.Kind.GROUP, true));
        for (Unit unit : units == null ? List.<Unit>of() : units) {
            if (unit == null || unit.getUnitId() == null) continue;
            String key = "unit-" + unit.getUnitId();
            String href = "/archive?folder=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            String color = unitColor(unit, artists);
            folders.add(folderCard(key, unit.getUnitName(), color, href, counts.getOrDefault(key, 0),
                    firstNonBlank(previews.get(key), registeredUnitImage(unit)),
                    ArchiveFolderClassifier.Kind.UNIT, false));
        }
        String[] comboColors = { "#7dd3fc", "#a5b4fc", "#c4b5fd", "#86efac" };
        for (int i = 0; i < ArchiveFolderClassifier.COMBO_FOLDERS.length; i++) {
            int min = ArchiveFolderClassifier.COMBO_FOLDERS[i];
            String key = "combo-" + min;
            if (!comboKeys.contains(key)) continue;
            folders.add(folderCard(key, ArchiveFolderClassifier.comboLabel(min), comboColors[i],
                    "/archive?folder=" + key, counts.getOrDefault(key, 0), previews.get(key),
                    ArchiveFolderClassifier.Kind.COMBO, false));
        }
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
                    "/archive?folder=" + key, counts.getOrDefault(key, 0), "",
                    ArchiveFolderClassifier.Kind.SOLO, false));
        }
        return folders;
    }

    private static String archivePreview(Archive archive) {
        if (archive.getType() == MediaType.IMAGE && archive.getFile() != null && archive.getFile().getUrl() != null) {
            return archive.getFile().getUrl();
        }
        return archive.getThumbnailUrl() == null ? "" : archive.getThumbnailUrl();
    }

    private static String unitColor(Unit unit, List<Artist> artists) {
        if (unit.getUnitMembers() != null) {
            for (UnitMember membership : unit.getUnitMembers()) {
                if (membership.getArtist() != null) return safeColor(membership.getArtist().getColor());
            }
        }
        return artists.isEmpty() ? "#a78bfa" : safeColor(artists.get(0).getColor());
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
                options.add(chemistryOption(key, label + " · " + String.join(" × ", members), key.equals(selectedKey)));
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
            options.add(chemistryOption(key, label + " · " + String.join(" × ", members), key.equals(selectedKey)));
        }
        return options;
    }

    private static boolean chemistryFitsFolder(int memberCount, Integer comboMin) {
        if (comboMin == null || memberCount < 2) return false;
        if (comboMin <= 4) return memberCount == comboMin;
        return memberCount >= comboMin;
    }

    private static Map<String, Object> chemistryOption(String value, String label, boolean selected) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        option.put("selected", selected);
        return option;
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
        String art = archiveFolderArt.urlFor(kind, key, label);
        String shownPreview = kind == ArchiveFolderClassifier.Kind.SOLO ? "" : (preview == null ? "" : preview);
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
        card.put("hasFolderArt", !art.isBlank());
        card.put("groupFolder", group);
        card.put("soloFolder", kind == ArchiveFolderClassifier.Kind.SOLO);
        return card;
    }

    private String folderRedirect(MediaType type, String artistNames) {
        ArchiveFolderClassifier.Match match = ArchiveFolderClassifier.classify(artistNames, unitSnapshots(unitService.listVisible()));
        String typeQuery = type == MediaType.VIDEO ? "type=video&" : "";
        if (match.kind() == ArchiveFolderClassifier.Kind.OTHER) {
            return type == MediaType.VIDEO ? "/archive?type=video" : "/archive";
        }
        return "/archive?" + typeQuery + "folder=" + URLEncoder.encode(match.key(), StandardCharsets.UTF_8);
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
                                HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        String names = artistNames == null ? "" : String.join(",", artistNames);
        archiveService.save(file, title, type, names, requireDate(registeredOn), requireSource(source));
        return "redirect:" + folderRedirect(type, names);
    }

    @PostMapping("/upload/image")
    public String uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames,
                              @RequestParam("registeredOn") LocalDate registeredOn,
                              @RequestParam("source") String source,
                              HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        String names = artistNames == null ? "" : String.join(",", artistNames);
        archiveService.uploadImageOnly(file, title, MediaType.IMAGE, names, requireDate(registeredOn), requireSource(source));
        return "redirect:" + folderRedirect(MediaType.IMAGE, names);
    }

    @PostMapping("/upload/video")
    public String uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames,
                              @RequestParam("registeredOn") LocalDate registeredOn,
                              @RequestParam("source") String source,
                              HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        String names = artistNames == null ? "" : String.join(",", artistNames);
        archiveService.uploadVideoOnly(file, title, MediaType.VIDEO, names, thumbnail, requireDate(registeredOn), requireSource(source));
        return "redirect:" + folderRedirect(MediaType.VIDEO, names);
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateArchive(@PathVariable long id,
                                           @RequestParam("title") String title,
                                           @RequestParam(value = "file", required = false) MultipartFile file,
                                           @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                                           @RequestParam(defaultValue = "false") boolean clearThumbnail,
                                           @RequestParam(value = "artistNames", required = false) String artistNames,
                                           @RequestParam("registeredOn") LocalDate registeredOn,
                                           @RequestParam(value = "source", required = false) String source,
                                           HttpServletRequest request,
                                           HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            archiveService.update(id, title, file, artistNames, thumbnail, clearThumbnail, requireDate(registeredOn),
                    source == null || source.isBlank() ? null : ArchiveSource.parse(source));
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
