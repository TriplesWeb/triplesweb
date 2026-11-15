package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.entity.Artist;
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
                              HttpServletRequest request, HttpSession session, Model model) {
        String jump = pageJumpRedirect(go, request);
        if (jump != null) return jump;
        return renderArchive(type, page, size, q, from, to, on, members, false, request, session, model);
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
        return renderArchive("image", page, size, q, from, to, on, members, true, request, session, model);
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

    private String renderArchive(String typeSlug, int page, int size, String q, String from, String to, String on,
                                 List<String> members, boolean browse,
                                 HttpServletRequest request, HttpSession session, Model model) {
        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        boolean isAuthenticated = isAdmin;

        MediaType type = mediaType(typeSlug);

        List<String> selectedMembers = normalizeMembers(members);
        LocalDate onDate = parseDate(on);
        LocalDate fromDate = onDate != null ? onDate : parseDate(from);
        LocalDate toDate = onDate != null ? onDate : parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        Page<Archive> archives = (fromDate == null && toDate == null)
                ? (selectedMembers.isEmpty()
                    ? archiveService.pageByType(type, page, size, q)
                    : archiveService.pageByType(type, page, size, q, selectedMembers))
                : archiveService.pageByType(type, page, size, q, selectedMembers, fromDate, toDate);

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
        String onText = onDate == null ? "" : onDate.toString();
        String fromText = onDate != null ? "" : (fromDate == null ? "" : fromDate.toString());
        String toText = onDate != null ? "" : (toDate == null ? "" : toDate.toString());
        model.addAttribute("on", onText);
        model.addAttribute("from", fromText);
        model.addAttribute("to", toText);
        model.addAttribute("hasExactDate", onDate != null);
        model.addAttribute("hasDateFilter", onDate != null || fromDate != null || toDate != null);
        model.addAttribute("dateExactEnabled", true);
        String dateQuery = dateQuery(fromText, toText, onText);
        model.addAttribute("dateQuery", dateQuery);
        addFilterSummary(model, q, selectedMembers, artists, fromText, toText, onText);
        addArchivePage(model, archives, type, q, memberQuery(selectedMembers), dateQuery, browse);
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
        List<Map<String, Object>> options = MemberPortraitOptions.archivePickerOptions(artists);
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
                badges.add(badge);
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", archive.getId());
            card.put("title", archive.getTitle());
            card.put("artistNames", archive.getArtistNames());
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
                                String dateQuery, boolean browse) {
        int totalPages = archives.getTotalPages();
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
        String filterQuery = qQuery + (memberQuery == null ? "" : memberQuery) + (dateQuery == null ? "" : dateQuery);
        model.addAttribute("archiveHomePath", video ? "/archive?type=video" : "/archive");
        model.addAttribute("archivePath", browse ? "/archive/all" : "/archive");
        model.addAttribute("archiveBrowsePath", "/archive/all");
        model.addAttribute("showArchiveBrowse", type == MediaType.IMAGE && !browse);
        model.addAttribute("imageArchive", type == MediaType.IMAGE);
        model.addAttribute("videoArchive", video);
        model.addAttribute("typeQuery", typeQuery);
        model.addAttribute("hasTypeQuery", video);
        model.addAttribute("mediaDateLabel", video ? "영상 날짜" : "사진 날짜");
        model.addAttribute("dateFilterLabel", video ? "영상 날짜" : "사진 날짜");
        model.addAttribute("dateFromLabel", video ? "영상 날짜 시작" : "사진 날짜 시작");
        model.addAttribute("dateToLabel", video ? "영상 날짜 끝" : "사진 날짜 끝");
        model.addAttribute("dateExactLabel", video ? "이 영상 날짜" : "이 사진 날짜");
        model.addAttribute("dateFilterHint", "실제로 올라온 날짜로 찾습니다.");
        model.addAttribute("imageTabHref", joinHref("/archive", filterQuery + pageQuery));
        model.addAttribute("videoTabHref", joinHref("/archive?type=video", filterQuery + pageQuery));
        model.addAttribute("browseQuery", joinHref("", filterQuery));
        List<Map<String, Object>> archivePages = new ArrayList<>();
        if (totalPages > 0) {
            int start = Math.max(0, current - 2);
            int end = Math.min(totalPages - 1, start + 4);
            start = Math.max(0, end - 4);
            for (int i = start; i <= end; i++) {
                archivePages.add(Map.of("num", i, "label", i + 1, "active", i == current));
            }
        }
        model.addAttribute("archivePages", archivePages);
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
                                @RequestParam("registeredOn") LocalDate registeredOn, HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        archiveService.save(file, title, type, artistNames == null ? "" : String.join(",", artistNames), requireDate(registeredOn));
        return (type == MediaType.IMAGE)
                ? "redirect:/archive"
                : "redirect:/archive?type=video";
    }

    @PostMapping("/upload/image")
    public String uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames,
                              @RequestParam("registeredOn") LocalDate registeredOn, HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        archiveService.uploadImageOnly(file, title, MediaType.IMAGE, artistNames == null ? "" : String.join(",", artistNames), requireDate(registeredOn));
        return "redirect:/archive";
    }

    @PostMapping("/upload/video")
    public String uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames,
                              @RequestParam("registeredOn") LocalDate registeredOn, HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        archiveService.uploadVideoOnly(file, title, MediaType.VIDEO, artistNames == null ? "" : String.join(",", artistNames), thumbnail, requireDate(registeredOn));
        return "redirect:/archive?type=video";
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
                                           HttpServletRequest request,
                                           HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            archiveService.update(id, title, file, artistNames, thumbnail, clearThumbnail, requireDate(registeredOn));
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
