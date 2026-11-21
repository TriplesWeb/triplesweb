package com.example.triplesweb.controller;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.service.MediaViewCountService;
import com.example.triplesweb.util.MediaOrdering;
import com.example.triplesweb.util.MediaSearch;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.YoutubeIdExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class MediaArchiveController {
    private static final int PAGE_SIZE = 24;
    private static final int SHORTS_LIMIT = 12;

    private final MediaService media;
    private final MediaViewCountService mediaViewCounts;
    private final ArtistRepository artistRepository;
    private final ScheduleRepository scheduleRepository;
    private final AboutAdminAuth aboutAdminAuth;
    private final ObjectMapper objectMapper;

    @GetMapping("/media")
    public String mediaPage(@RequestParam(name = "tab", defaultValue = "all") String tab,
                            @RequestParam(name = "sort", defaultValue = "latest") String sort,
                            @RequestParam(required = false) String artist,
                            @RequestParam(required = false) List<String> members,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to,
                            @RequestParam(required = false) String kind,
                            @RequestParam(name = "page", defaultValue = "1") int page,
                            HttpServletRequest request,
                            HttpSession session,
                            Model model) {
        mediaViewCounts.refreshIfDue();
        tab = normalizeTab(tab);
        boolean all = "all".equals(tab);
        String sortKey = normalizeSort(sort);
        boolean shortsOnly = "shorts".equalsIgnoreCase(kind);

        model.addAttribute("isAdmin", aboutAdminAuth.isAdmin(request, session));
        model.addAttribute("currentTab", tab);
        model.addAttribute("isAll", all);
        model.addAttribute("isMV", "mv".equals(tab));
        model.addAttribute("isSIGNAL", "signal".equals(tab));
        model.addAttribute("isBehind", "behind".equals(tab));
        model.addAttribute("isSecret", "secret".equals(tab));
        model.addAttribute("isContents", "contents".equals(tab));
        model.addAttribute("sort", sortKey);
        model.addAttribute("sortOptions", sortOptions(sortKey));
        model.addAttribute("dateFilterLabel", "전체 기간");
        model.addAttribute("shortsOnly", shortsOnly);

        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        final LocalDate rangeStart = fromDate;
        final LocalDate rangeEnd = toDate;
        List<String> selectedMembers = selectedMembers(members, artist);
        boolean memberFiltered = !selectedMembers.isEmpty();
        String query = q == null ? "" : q.trim();
        boolean dateFiltered = rangeStart != null || rangeEnd != null;
        List<Artist> artists = artistRepository.findAll();
        List<MediaDto> filtered = new ArrayList<>(all ? media.listAll() : media.allByTab(tab));
        if (memberFiltered) {
            filtered.removeIf(v -> !matchesMembers(v.getArtistNames(), selectedMembers));
        }
        if (!query.isEmpty()) {
            filtered.removeIf(v -> !MediaSearch.matches(v.getTitle(), query));
        }
        if (dateFiltered) {
            filtered.removeIf(v -> !matchesPublished(v.getPublishedOn(), rangeStart, rangeEnd));
        }
        MediaOrdering.sort(filtered, sortKey);
        attachMemberBadges(filtered, artists);
        List<MediaDto> longForm = new ArrayList<>();
        List<MediaDto> shortForm = new ArrayList<>();
        for (MediaDto video : filtered) {
            if (MediaOrdering.isShort(video.getDuration())) shortForm.add(video);
            else longForm.add(video);
        }
        List<MediaDto> latestLong = new ArrayList<>(longForm);
        MediaOrdering.sort(latestLong, "latest");
        List<MediaDto> latestShorts = new ArrayList<>(shortForm);
        MediaOrdering.sort(latestShorts, "latest");
        MediaDto featured = shortsOnly || latestLong.isEmpty() ? null : latestLong.get(0);
        List<MediaDto> recommendations = shortsOnly ? List.of() : andlessMusicVideos(longForm);
        List<MediaDto> archive = shortsOnly ? List.of() : longForm;
        List<MediaDto> suggestions = suggestions(archive, featured, recommendations, 4);
        List<MediaDto> shorts = latestShorts.stream().limit(SHORTS_LIMIT).toList();
        int total = archive.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        int current = Math.min(totalPages, Math.max(1, page));
        int fromIdx = total == 0 ? 0 : (current - 1) * PAGE_SIZE;
        int toIdx = Math.min(fromIdx + PAGE_SIZE, total);
        List<MediaDto> videos = total == 0 ? List.of() : archive.subList(fromIdx, toIdx);
        model.addAttribute("featured", featured);
        model.addAttribute("hasFeatured", featured != null);
        model.addAttribute("recommendations", recommendations);
        model.addAttribute("hasRecommendations", !recommendations.isEmpty());
        model.addAttribute("recommendationCount", String.format("%02d", recommendations.size()));
        model.addAttribute("suggestions", suggestions);
        model.addAttribute("hasSuggestions", !suggestions.isEmpty());
        model.addAttribute("shorts", shorts);
        model.addAttribute("hasShorts", !shorts.isEmpty());
        model.addAttribute("mediaCatalogJson", catalogJson(visibleCatalog(featured, recommendations, suggestions, shorts, videos)));
        bindPager(model, total, videos.size(), current, totalPages);
        String filterQuery = mediaQuery(tab, sortKey, query, selectedMembers, rangeStart, rangeEnd, shortsOnly);
        model.addAttribute("mediaQuery", filterQuery);
        model.addAttribute("shortsViewHref", "/media?kind=shorts" + mediaQuery(tab, sortKey, query, selectedMembers, rangeStart, rangeEnd, false));
        model.addAttribute("shortsAllHref", "/media?" + mediaQuery(tab, sortKey, query, selectedMembers, rangeStart, rangeEnd, false).replaceFirst("^&", ""));

        String fromText = rangeStart == null ? "" : rangeStart.toString();
        String toText = rangeEnd == null ? "" : rangeEnd.toString();
        model.addAttribute("from", fromText);
        model.addAttribute("to", toText);
        model.addAttribute("hasDateFilter", dateFiltered);
        model.addAttribute("selectedMembers", selectedMembers);
        model.addAttribute("hasSelectedMembers", memberFiltered);
        model.addAttribute("selectedMemberCount", selectedMembers.size());
        model.addAttribute("memberToggleLabel", memberToggleLabel(selectedMembers));
        model.addAttribute("query", query);
        model.addAttribute("memberPickerOptions", portraitOptions(selectedMembers));
        model.addAttribute("editMemberOptions", portraitOptions(List.of()));
        model.addAttribute("hasActiveFilters", memberFiltered || dateFiltered || !query.isEmpty() || !"latest".equals(sortKey) || shortsOnly);
        model.addAttribute("videos", videos);
        model.addAttribute("hasVideos", total > 0);
        model.addAttribute("total", total);
        return "archive/media";
    }

    @GetMapping("/media/{tab}/{youtubeId}")
    public String mediaDetail(@PathVariable String tab,
                              @PathVariable String youtubeId,
                              Model model) throws IOException {
        mediaViewCounts.refreshIfDue();
        MediaTab t = MediaTab.from(tab);
        MediaDto video = media.findOne(t, youtubeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없습니다."));
        model.addAttribute("currentTab", t.getKey());
        model.addAttribute("tabNameKo", t.getKor());
        model.addAttribute("video", video);
        model.addAttribute("related", media.related(t, youtubeId, 8));

        List<String> names = splitNames(video.getArtistNames());
        List<Schedule> relatedSchedules = names.isEmpty()
                ? List.of()
                : scheduleRepository.findDistinctByArtistNames(names);
        model.addAttribute("relatedSchedules", relatedSchedules);
        model.addAttribute("taggedArtists", artistRepository.findAll().stream()
                .filter(a -> names.contains(a.getName()))
                .toList());
        return "archive/media_detail";
    }

    @GetMapping("/media_new")
    public String newMediaForm(@RequestParam(defaultValue = "mv") String tab,
                               HttpServletRequest request,
                               HttpSession session,
                               Model model) {
        aboutAdminAuth.requireAdmin(request, session);
        MediaDto form = MediaDto.builder()
                .tab(MediaTab.from(tab).getKey())
                .addFirst(true)
                .build();
        bindTabFlags(model, form.normalizedTab());
        model.addAttribute("artistOptions", artistPickerOptions(List.of()));
        model.addAttribute("memberPickerOptions", portraitOptions(List.of()));
        model.addAttribute("form", form);
        return "archive/media_new";
    }

    @PostMapping("/media_new")
    public String createNewMedia(@ModelAttribute("form") MediaDto form,
                                 BindingResult binding,
                                 @RequestParam(value = "artistNames", required = false) List<String> artistNames,
                                 @RequestParam(value = "extraArtists", required = false) String extraArtists,
                                 HttpServletRequest request,
                                 HttpSession session,
                                 Model model) {
        aboutAdminAuth.requireAdmin(request, session);

        List<String> selectedArtists = new ArrayList<>();
        if (artistNames != null) selectedArtists.addAll(artistNames);
        if (extraArtists != null && !extraArtists.isBlank()) {
            selectedArtists.addAll(splitNames(extraArtists));
        }
        String tab = form != null ? form.normalizedTab() : "mv";
        String rawId = form != null && form.getYoutubeId() != null ? form.getYoutubeId() : "";
        String ytId = YoutubeIdExtractor.extract(rawId);
        if (ytId == null || ytId.isBlank() || !ytId.matches("^[a-zA-Z0-9_-]{11}$")) {
            binding.rejectValue("youtubeId", "invalid", "유효한 유튜브 ID 또는 URL을 입력하세요.");
        } else {
            form.setYoutubeId(ytId);
            // 제목·길이·조회수가 비어 있으면 유튜브에서 보강
            Map<String, Object> info = media.makeYoutubeInfo(ytId);
            if (form.getTitle() == null || form.getTitle().isBlank()) {
                Object title = info.get("title");
                if (title != null) form.setTitle(title.toString());
            }
            if (form.getDuration() == null || form.getDuration().isBlank() || "03:00".equals(form.getDuration())) {
                Object duration = info.get("duration");
                if (duration != null) form.setDuration(duration.toString());
            }
            if (form.getViews() == null || form.getViews() == 0L) {
                Object views = info.get("views");
                if (views != null) {
                    try {
                        form.setViews(Long.parseLong(views.toString()));
                    } catch (NumberFormatException ignored) {
                        form.setViews(0L);
                    }
                }
            }
        }

        if (form.getTitle() == null || form.getTitle().isBlank()) {
            binding.rejectValue("title", "required", "영상 제목을 확인한 뒤 다시 저장해 주세요.");
        }

        if (binding.hasErrors()) {
            bindTabFlags(model, tab);
            model.addAttribute("form", form);
            model.addAttribute("artistOptions", artistPickerOptions(selectedArtists));
            model.addAttribute("memberPickerOptions", portraitOptions(selectedArtists));
            return "archive/media_new";
        }

        media.registerMedia(tab, form, String.join(",", selectedArtists));
        return "redirect:/media?tab=" + tab;
    }

    @PostMapping("/media_delete")
    public String deleteMedia(@RequestParam String tab,
                              @RequestParam String youtubeId,
                              HttpServletRequest request,
                              HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        String t = MediaTab.from(tab).getKey();
        media.remove(t, youtubeId);
        return "redirect:/media?tab=" + t;
    }

    @PostMapping("/media_update")
    public String updateMedia(@RequestParam String youtubeId,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) String publishedOn,
                              @RequestParam(value = "artistNames", required = false) List<String> artistNames,
                              @RequestParam(defaultValue = "all") String tab,
                              HttpServletRequest request,
                              HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        String names = artistNames == null ? "" : String.join(",", artistNames);
        media.updateDetails(youtubeId, description, publishedOn, names);
        return "redirect:/media?tab=" + normalizeTab(tab);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDate.parse(raw.trim()); }
        catch (DateTimeParseException ex) { return null; }
    }

    private static boolean matchesPublished(String publishedOn, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        LocalDate day = MediaOrdering.parseDate(publishedOn);
        if (day == null) return false;
        if (from != null && day.isBefore(from)) return false;
        return to == null || !day.isAfter(to);
    }

    private static String normalizeSort(String sort) {
        if (sort == null) return "latest";
        return switch (sort.toLowerCase()) {
            case "oldest", "views", "short", "long" -> sort.toLowerCase();
            case "popular" -> "views";
            default -> "latest";
        };
    }

    private List<Map<String, Object>> sortOptions(String selected) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (String[] row : new String[][]{
                {"latest", "최신순"},
                {"oldest", "오래된순"},
                {"views", "조회순"},
                {"short", "영상 짧은순"},
                {"long", "긴 영상 순"}
        }) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", row[0]);
            option.put("label", row[1]);
            option.put("selected", row[0].equals(selected));
            options.add(option);
        }
        return options;
    }

    private List<Map<String, Object>> portraitOptions(List<String> selectedNames) {
        List<String> selected = selectedNames == null ? List.of() : selectedNames;
        List<Map<String, Object>> options = MemberPortraitOptions.fromArtists(artistRepository.findAll(), false);
        for (Map<String, Object> option : options) {
            option.put("active", selected.contains(String.valueOf(option.get("value"))));
        }
        return options;
    }

    private List<Map<String, Object>> artistPickerOptions(List<String> selectedNames) {
        List<String> selected = selectedNames == null ? List.of() : selectedNames;
        List<Map<String, Object>> options = new ArrayList<>();
        for (Artist artist : artistRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", artist.getName());
            row.put("selected", selected.contains(artist.getName()));
            options.add(row);
        }
        return options;
    }

    private static List<String> selectedMembers(List<String> members, String artist) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (members != null) {
            for (String member : members) {
                if (member != null && !member.isBlank()) names.add(member.trim());
            }
        }
        if (names.isEmpty() && artist != null && !artist.isBlank()) names.add(artist.trim());
        return List.copyOf(names);
    }

    private static String memberToggleLabel(List<String> selected) {
        if (selected == null || selected.isEmpty()) return "멤버 선택";
        if (selected.size() == 1) return selected.get(0);
        return selected.size() + "명";
    }

    /** 재생목록 초기 화면은 ANDLESS 뮤직비디오만 담는다. */
    private static List<MediaDto> andlessMusicVideos(List<MediaDto> videos) {
        List<MediaDto> found = new ArrayList<>();
        for (MediaDto video : videos) {
            if (video == null || MediaOrdering.isShort(video.getDuration())) continue;
            if (!"mv".equalsIgnoreCase(video.getTab())) continue;
            String title = video.getTitle() == null ? "" : video.getTitle().toLowerCase(Locale.ROOT);
            if (!title.contains("andless")) continue;
            found.add(video);
        }
        MediaOrdering.sort(found, "latest");
        return found;
    }

    /** 대표 영상과 멤버·분류가 겹치는 영상을 우선해 아카이브 위에 네 개까지 고른다. */
    private List<MediaDto> suggestions(List<MediaDto> videos, MediaDto anchor, List<MediaDto> reserved, int limit) {
        if (anchor == null || videos == null || videos.isEmpty() || limit < 1) return List.of();
        Set<String> skip = new LinkedHashSet<>();
        if (anchor.getYoutubeId() != null) skip.add(anchor.getYoutubeId().toLowerCase(Locale.ROOT));
        if (reserved != null) {
            for (MediaDto video : reserved) {
                if (video.getYoutubeId() != null) skip.add(video.getYoutubeId().toLowerCase(Locale.ROOT));
            }
        }
        Set<String> members = memberKeySet(anchor.getArtistNames());
        String tab = anchor.getTab() == null ? "" : anchor.getTab();
        List<MediaDto> pool = new ArrayList<>();
        for (MediaDto video : videos) {
            if (video.getYoutubeId() == null || skip.contains(video.getYoutubeId().toLowerCase(Locale.ROOT))) continue;
            pool.add(video);
        }
        pool.sort((left, right) -> {
            int byScore = Integer.compare(similarity(right, members, tab), similarity(left, members, tab));
            if (byScore != 0) return byScore;
            LocalDate rightDay = MediaOrdering.parseDate(right.getPublishedOn());
            LocalDate leftDay = MediaOrdering.parseDate(left.getPublishedOn());
            if (leftDay == null && rightDay == null) return 0;
            if (leftDay == null) return 1;
            if (rightDay == null) return -1;
            return rightDay.compareTo(leftDay);
        });
        List<MediaDto> picked = new ArrayList<>();
        for (MediaDto video : pool) {
            if (picked.size() >= limit) break;
            picked.add(video);
        }
        if (picked.size() < limit && reserved != null) {
            for (MediaDto video : reserved) {
                if (picked.size() >= limit) break;
                if (video.getYoutubeId() == null || video.getYoutubeId().equalsIgnoreCase(anchor.getYoutubeId())) continue;
                picked.add(video);
            }
        }
        return List.copyOf(picked);
    }

    private static int similarity(MediaDto video, Set<String> members, String tab) {
        int score = 0;
        for (String name : memberKeySet(video.getArtistNames())) {
            if (members.contains(name)) score += 3;
        }
        if (tab != null && !tab.isBlank() && tab.equals(video.getTab())) score += 1;
        return score;
    }

    private static Set<String> memberKeySet(String names) {
        Set<String> keys = new LinkedHashSet<>();
        for (String name : splitNames(names)) keys.add(MediaOrdering.memberKey(name));
        return keys;
    }

    private static boolean matchesMembers(String artistNames, List<String> selected) {
        if (selected == null || selected.isEmpty()) return true;
        Set<String> names = new LinkedHashSet<>();
        for (String name : splitNames(artistNames)) names.add(MediaOrdering.memberKey(name));
        for (String member : selected) {
            if (names.contains(MediaOrdering.memberKey(member))) return true;
        }
        return false;
    }

    private void attachMemberBadges(List<MediaDto> videos, List<Artist> artists) {
        for (MediaDto video : videos) {
            video.setMemberBadges(memberBadges(video.getArtistNames(), artists));
        }
    }

    private List<Map<String, Object>> memberBadges(String artistNames, List<Artist> artists) {
        List<Map<String, Object>> badges = new ArrayList<>();
        for (String name : splitNames(artistNames)) {
            Artist matched = null;
            for (Artist artist : artists) {
                if (artist.getName() != null && artist.getName().trim().equalsIgnoreCase(name)) { matched = artist; break; }
                if (artist.getEngName() != null && artist.getEngName().trim().equalsIgnoreCase(name)) { matched = artist; break; }
            }
            String symbol = MemberPortraitOptions.archiveSymbolLogo(name);
            String portrait = matched == null || matched.getDisplayProfileImage() == null ? "" : matched.getDisplayProfileImage();
            String image = !symbol.isBlank() ? symbol : portrait;
            String color = matched == null || matched.getColor() == null || !matched.getColor().matches("#[0-9a-fA-F]{6}")
                    ? "#b9cac4" : matched.getColor();
            Map<String, Object> badge = new LinkedHashMap<>();
            badge.put("name", name);
            badge.put("color", color);
            badge.put("image", image);
            badge.put("hasImage", !image.isBlank());
            badge.put("hasSymbol", !symbol.isBlank());
            badges.add(badge);
        }
        return badges;
    }

    private void bindPager(Model model, int total, int pageCount, int current, int totalPages) {
        model.addAttribute("hasMediaPager", total > PAGE_SIZE);
        model.addAttribute("mediaTotal", total);
        model.addAttribute("mediaPageCount", pageCount);
        model.addAttribute("mediaPageDisplay", current);
        model.addAttribute("mediaTotalPages", totalPages);
        model.addAttribute("hasMediaPrev", current > 1);
        model.addAttribute("hasMediaNext", current < totalPages);
        model.addAttribute("mediaPrevPage", Math.max(1, current - 1));
        model.addAttribute("mediaNextPage", current + 1);
        List<Map<String, Object>> pages = new ArrayList<>();
        int start = Math.max(1, current - 2);
        int end = Math.min(totalPages, start + 4);
        start = Math.max(1, end - 4);
        for (int i = start; i <= end; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("num", i);
            row.put("active", i == current);
            pages.add(row);
        }
        model.addAttribute("mediaPages", pages);
    }

    private static String mediaQuery(String tab, String sort, String query, List<String> members,
                                     LocalDate from, LocalDate to, boolean shortsOnly) {
        StringBuilder queryString = new StringBuilder();
        if (tab != null && !tab.isBlank()) queryString.append("&tab=").append(encode(tab));
        if (sort != null && !sort.isBlank() && !"latest".equals(sort)) queryString.append("&sort=").append(encode(sort));
        if (query != null && !query.isBlank()) queryString.append("&q=").append(encode(query));
        if (members != null) {
            for (String member : members) {
                if (member != null && !member.isBlank()) queryString.append("&members=").append(encode(member));
            }
        }
        if (from != null) queryString.append("&from=").append(from);
        if (to != null) queryString.append("&to=").append(to);
        if (shortsOnly) queryString.append("&kind=shorts");
        return queryString.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static List<MediaDto> visibleCatalog(MediaDto featured, List<MediaDto> recommendations,
                                                 List<MediaDto> suggestions, List<MediaDto> shorts,
                                                 List<MediaDto> videos) {
        List<MediaDto> catalog = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addCatalog(catalog, seen, featured);
        addCatalog(catalog, seen, recommendations);
        addCatalog(catalog, seen, suggestions);
        addCatalog(catalog, seen, shorts);
        addCatalog(catalog, seen, videos);
        return catalog;
    }

    private static void addCatalog(List<MediaDto> catalog, Set<String> seen, List<MediaDto> videos) {
        if (videos == null) return;
        for (MediaDto video : videos) addCatalog(catalog, seen, video);
    }

    private static void addCatalog(List<MediaDto> catalog, Set<String> seen, MediaDto video) {
        if (video == null || video.getYoutubeId() == null || video.getYoutubeId().isBlank()) return;
        if (!seen.add(video.getYoutubeId().toLowerCase(Locale.ROOT))) return;
        catalog.add(video);
    }

    private String catalogJson(List<MediaDto> videos) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MediaDto video : videos) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("youtubeId", video.getYoutubeId());
            row.put("tab", video.getTab());
            row.put("tabLabel", video.getTabLabel());
            row.put("title", video.getTitle() == null ? "" : video.getTitle());
            row.put("description", video.getDescription() == null ? "" : video.getDescription());
            row.put("publishedOn", video.getPublishedOn() == null ? "" : video.getPublishedOn());
            row.put("publishedLabel", video.getPublishedLabel());
            row.put("duration", video.getDuration() == null ? "" : video.getDuration());
            row.put("viewCountFmt", video.getViewCountFmt() == null ? "" : video.getViewCountFmt());
            row.put("artistNames", video.getArtistNames() == null ? "" : video.getArtistNames());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows).replace("<", "\\u003c");
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private static List<String> splitNames(String names) {
        if (names == null || names.isBlank()) return List.of();
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeTab(String tab) {
        return switch (tab.toLowerCase()) {
            case "all", "mv", "signal", "behind", "secret", "contents" -> tab.toLowerCase();
            default -> "mv";
        };
    }

    private void bindTabFlags(Model model, String t) {
        model.addAttribute("tab", t);
        model.addAttribute("tab_mv", "mv".equals(t));
        model.addAttribute("tab_signal", "signal".equals(t));
        model.addAttribute("tab_behind", "behind".equals(t));
        model.addAttribute("tab_secret", "secret".equals(t));
        model.addAttribute("tab_contents", "contents".equals(t));
    }
}
