package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;
import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.media.MediaSignalEra;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.media.MediaTabClassifier;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArtistAdminService;
import com.example.triplesweb.service.ChemistryGroupService;
import com.example.triplesweb.service.ChemistryService;
import com.example.triplesweb.service.MediaChannelSyncService;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.service.MediaViewCountService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.util.ImageUrls;
import com.example.triplesweb.util.MediaOrdering;
import com.example.triplesweb.util.MediaSearch;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.MemberSetCatalogJson;
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
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final int SHORTS_PREVIEW = 8;

    private final MediaService media;
    private final MediaViewCountService mediaViewCounts;
    private final MediaChannelSyncService youtubeSync;
    private final ArtistRepository artistRepository;
    private final ArtistAdminService artistAdmin;
    private final ScheduleRepository scheduleRepository;
    private final AboutAdminAuth aboutAdminAuth;
    private final ObjectMapper objectMapper;
    private final UnitService unitService;
    private final ChemistryService chemistryService;
    private final ChemistryGroupService chemistryGroupService;

    @GetMapping("/media")
    public String mediaPage(@RequestParam(name = "tab", defaultValue = "all") String tab,
                            @RequestParam(name = "sort", defaultValue = "latest") String sort,
                            @RequestParam(required = false) String artist,
                            @RequestParam(required = false) List<String> members,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to,
                            @RequestParam(required = false) String kind,
                            @RequestParam(required = false) Integer go,
                            @RequestParam(required = false) String add,
                            @RequestParam(required = false) String era,
                            @RequestParam(name = "page", defaultValue = "1") int page,
                            HttpServletRequest request,
                            HttpSession session,
                            Model model) {
        if (go != null) return mediaPageJump(go, request);
        youtubeSync.ensureExternalSearch();
        mediaViewCounts.refreshIfDue();
        tab = normalizeTab(tab);
        boolean all = "all".equals(tab);
        boolean shortsTab = "shorts".equals(tab);
        String sortKey = normalizeSort(sort);
        boolean shortsOnly = shortsTab || "shorts".equalsIgnoreCase(kind);

        model.addAttribute("isAdmin", aboutAdminAuth.isAdmin(request, session));
        model.addAttribute("currentTab", tab);
        model.addAttribute("isAll", all);
        model.addAttribute("isMV", "mv".equals(tab));
        model.addAttribute("isSIGNAL", "signal".equals(tab));
        model.addAttribute("isBehind", "behind".equals(tab));
        model.addAttribute("isSecret", "secret".equals(tab));
        model.addAttribute("isShorts", shortsTab);
        model.addAttribute("isMusic", "music".equals(tab));
        model.addAttribute("isFancam", "fancam".equals(tab));
        model.addAttribute("isContents", "contents".equals(tab));
        model.addAttribute("isExternal", "external".equals(tab));
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
        List<MediaDto> filtered = new ArrayList<>((all || shortsTab) ? media.listAll() : media.allByTab(tab));
        if ("signal".equals(tab)) {
            filtered.removeIf(v -> !MediaTab.SIGNAL.getKey().equals(
                    MediaTabClassifier.tabFor(v.getTitle(), v.getDuration())));
        }
        if (memberFiltered) {
            filtered.removeIf(v -> !matchesMembers(v.getArtistNames(), selectedMembers));
        }
        if (!query.isEmpty()) {
            filtered.removeIf(v -> !MediaSearch.matches(v.getTitle(), query));
        }
        if (dateFiltered) {
            filtered.removeIf(v -> !matchesPublished(v.getPublishedOn(), rangeStart, rangeEnd));
        }
        MediaSignalEra selectedEra = "signal".equals(tab) ? MediaSignalEra.fromKey(era) : null;
        if (selectedEra != null) {
            filtered.removeIf(v -> !MediaSignalEra.matches(v.getPublishedOn(), v.getTitle(), selectedEra.getKey()));
        }
        MediaOrdering.sort(filtered, sortKey);
        attachMemberBadges(filtered, artists);
        List<MediaDto> longForm = new ArrayList<>();
        List<MediaDto> shortForm = new ArrayList<>();
        for (MediaDto video : filtered) {
            if (shortForm(video)) shortForm.add(video);
            else longForm.add(video);
        }
        MediaDto featured = null;
        List<MediaDto> recommendations = List.of();
        List<MediaDto> archive = shortsOnly ? shortForm : longForm;
        List<MediaDto> suggestions = List.of();
        int total = archive.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        int current = Math.min(totalPages, Math.max(1, page));
        int fromIdx = total == 0 ? 0 : (current - 1) * PAGE_SIZE;
        int toIdx = Math.min(fromIdx + PAGE_SIZE, total);
        List<MediaDto> pageItems = total == 0 ? List.of() : archive.subList(fromIdx, toIdx);
        List<MediaDto> videos = shortsOnly ? List.of() : pageItems;
        List<MediaDto> shorts = List.of();
        if (shortsOnly) {
            shorts = pageItems;
        } else if (all) {
            MediaOrdering.sort(shortForm, "latest");
            shorts = shortForm.stream().limit(SHORTS_PREVIEW).toList();
        }
        model.addAttribute("featured", featured);
        model.addAttribute("hasFeatured", featured != null);
        model.addAttribute("recommendations", recommendations);
        model.addAttribute("hasRecommendations", !recommendations.isEmpty());
        model.addAttribute("recommendationCount", String.format("%02d", recommendations.size()));
        model.addAttribute("suggestions", suggestions);
        model.addAttribute("hasSuggestions", !suggestions.isEmpty());
        model.addAttribute("shorts", shorts);
        model.addAttribute("hasShorts", !shorts.isEmpty());
        model.addAttribute("shortsPreview", all && !shortsOnly && !shorts.isEmpty());
        model.addAttribute("mediaCatalogJson", catalogJson(visibleCatalog(featured, recommendations, suggestions, shorts, videos), true));
        model.addAttribute("mediaPickCatalogJson", "[]");
        model.addAttribute("pickDateFilter", Map.of("from", "", "to", "", "hasDateFilter", false));
        List<Map<String, Object>> unitOptions = safeUnitOptions();
        List<ChemistryDto> chemistries = safeChemistries();
        List<ChemistryGroupDto> chemistryGroups = safeChemistryGroups();
        model.addAttribute("unitCheckboxOptions", unitOptions);
        model.addAttribute("chemistryNamesJson", MemberSetCatalogJson.chemistry(chemistries, chemistryGroups));
        model.addAttribute("unitRosterJson", MemberSetCatalogJson.units(unitOptions));
        model.addAttribute("openMediaAdd", add != null && (add.equals("1") || add.equalsIgnoreCase("true")));
        bindPager(model, total, shortsOnly ? shorts.size() : videos.size(), current, totalPages);
        String eraKey = selectedEra == null ? null : selectedEra.getKey();
        String filterQuery = mediaQuery(tab, sortKey, query, selectedMembers, rangeStart, rangeEnd, shortsOnly && !shortsTab, eraKey);
        model.addAttribute("mediaQuery", filterQuery);
        model.addAttribute("shortsTabHref", "/media?tab=shorts" + mediaQuery("", sortKey, query, selectedMembers, rangeStart, rangeEnd, false, null));
        model.addAttribute("signalEra", selectedEra == null ? "" : selectedEra.getKey());
        model.addAttribute("hasSignalEra", selectedEra != null);
        model.addAttribute("signalEras", signalEraTabs(sortKey, query, selectedMembers, rangeStart, rangeEnd, selectedEra));

        String fromText = rangeStart == null ? "" : rangeStart.toString();
        String toText = rangeEnd == null ? "" : rangeEnd.toString();
        model.addAttribute("from", fromText);
        model.addAttribute("to", toText);
        model.addAttribute("hasDateFilter", dateFiltered);
        model.addAttribute("selectedMembers", selectedMembers);
        model.addAttribute("hasSelectedMembers", memberFiltered);
        model.addAttribute("selectedMemberCount", selectedMembers.size());
        model.addAttribute("memberToggleLabel", MemberSetCatalogJson.toggleLabel(
                selectedMembers,
                MemberSetCatalogJson.chemistrySets(chemistries, chemistryGroups),
                MemberSetCatalogJson.unitSets(unitOptions)));
        model.addAttribute("query", query);
        model.addAttribute("memberPickerOptions", portraitOptions(selectedMembers));
        model.addAttribute("editMemberOptions", portraitOptions(List.of()));
        model.addAttribute("hasActiveFilters", memberFiltered || dateFiltered || !query.isEmpty() || !"latest".equals(sortKey) || (shortsOnly && !shortsTab));
        model.addAttribute("videos", videos);
        model.addAttribute("hasVideos", total > 0);
        model.addAttribute("total", total);
        return "archive/media";
    }

    @GetMapping(value = "/media/pick-catalog.json", produces = "application/json")
    @ResponseBody
    public String pickCatalogJson(@RequestParam(defaultValue = "false") boolean includeShorts) {
        List<Artist> artists = artistRepository.findAll();
        List<MediaDto> videos = new ArrayList<>();
        for (MediaDto video : media.listAll()) {
            if (shortForm(video)) {
                if (!includeShorts) continue;
                video.setTab(MediaTab.SHORTS.getKey());
            }
            videos.add(video);
        }
        attachMemberBadges(videos, artists);
        return catalogJson(videos, false);
    }

    @GetMapping("/media/{tab}/{youtubeId}")
    public String mediaDetail(@PathVariable String tab,
                              @PathVariable String youtubeId,
                              Model model) throws IOException {
        mediaViewCounts.refreshIfDue();
        MediaTab t = MediaTab.from(tab);
        MediaDto video = media.findOne(t, youtubeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없습니다."));
        MediaTab videoTab = MediaTab.from(video.getTab());
        model.addAttribute("currentTab", videoTab.getKey());
        model.addAttribute("tabNameKo", videoTab.getKor());
        model.addAttribute("shortsVideo", videoTab == MediaTab.SHORTS);
        model.addAttribute("video", video);
        model.addAttribute("related", media.related(videoTab, youtubeId, 8));

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
                               HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        String key = MediaTab.from(tab).getKey();
        return "redirect:/media?tab=" + key + "&add=1";
    }

    @PostMapping("/media_new")
    public Object createNewMedia(@ModelAttribute("form") MediaDto form,
                                 BindingResult binding,
                                 @RequestParam(value = "artistNames", required = false) List<String> artistNames,
                                 HttpServletRequest request,
                                 HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);

        List<String> selectedArtists = artistNames == null ? List.of() : artistNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        String tab = form != null ? form.normalizedTab() : "mv";
        String rawId = form != null && form.getYoutubeId() != null ? form.getYoutubeId() : "";
        String ytId = YoutubeIdExtractor.extract(rawId);
        if (ytId == null || ytId.isBlank() || !ytId.matches("^[a-zA-Z0-9_-]{11}$")) {
            binding.rejectValue("youtubeId", "invalid", "유효한 유튜브 ID 또는 URL을 입력하세요.");
        } else {
            form.setYoutubeId(ytId);
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
            String message = firstBindingMessage(binding, "영상을 등록하지 못했습니다.");
            if (wantsAjax(request)) {
                return org.springframework.http.ResponseEntity.badRequest()
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                        .body(message);
            }
            return "redirect:/media?tab=" + tab + "&add=1";
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
        media.updateDetails(youtubeId, description, publishedOn, names, tab);
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
            case "oldest", "views", "short", "long", "registered" -> sort.toLowerCase();
            case "popular" -> "views";
            default -> "latest";
        };
    }

    private List<Map<String, Object>> sortOptions(String selected) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (String[] row : new String[][]{
                {"latest", "최신순"},
                {"registered", "등록순"},
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
        List<String> names = splitNames(artistNames);
        if (MemberPortraitOptions.coversFullLineup(names)) {
            return List.of(fullLineupBadge());
        }
        List<Map<String, Object>> badges = new ArrayList<>();
        for (String name : names) {
            Artist matched = null;
            for (Artist artist : artists) {
                if (artist.getName() != null && artist.getName().trim().equalsIgnoreCase(name)) { matched = artist; break; }
                if (artist.getEngName() != null && artist.getEngName().trim().equalsIgnoreCase(name)) { matched = artist; break; }
            }
            String symbol = MemberPortraitOptions.archiveSymbolLogo(name);
            String portrait = portraitUrl(matched);
            String image = !symbol.isBlank() ? symbol : portrait;
            String color = matched == null || matched.getColor() == null || !matched.getColor().matches("#[0-9a-fA-F]{6}")
                    ? "#b9cac4" : matched.getColor();
            Map<String, Object> badge = new LinkedHashMap<>();
            badge.put("name", name);
            if (matched != null && matched.getArtistId() != null) badge.put("artistId", matched.getArtistId());
            badge.put("hasArtist", matched != null && matched.getArtistId() != null);
            badge.put("color", color);
            badge.put("image", image);
            badge.put("hasImage", !image.isBlank());
            badge.put("portrait", portrait);
            badge.put("hasPortrait", !portrait.isBlank());
            badge.put("hasSymbol", !symbol.isBlank());
            badges.add(badge);
        }
        return badges;
    }

    private Map<String, Object> fullLineupBadge() {
        GroupIntroDto group = artistAdmin.getGroupIntro();
        String image = usableImage(group == null ? null : group.getImageUrl());
        String title = group == null || group.getTitle() == null || group.getTitle().isBlank()
                ? "tripleS" : group.getTitle();
        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put("name", MemberSetCatalogJson.GROUP_LABEL);
        badge.put("title", title);
        badge.put("color", "#00f5d4");
        badge.put("image", image);
        badge.put("hasImage", !image.isBlank());
        badge.put("portrait", image);
        badge.put("hasPortrait", !image.isBlank());
        badge.put("hasSymbol", false);
        badge.put("fullLineup", true);
        return badge;
    }

    private static String portraitUrl(Artist artist) {
        return artist == null ? "" : usableImage(artist.getProfileImage());
    }

    private static String usableImage(String raw) {
        if (raw == null) return "";
        String image = raw.trim();
        if (image.isEmpty() || image.equals(ImageUrls.PLACEHOLDER) || image.endsWith("/images/image-upload-pending.png")) {
            return "";
        }
        return image;
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
                                     LocalDate from, LocalDate to, boolean shortsOnly, String era) {
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
        if (era != null && !era.isBlank()) queryString.append("&era=").append(encode(era));
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

    private String catalogJson(List<MediaDto> videos, boolean includeDescription) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MediaDto video : videos) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", video.getId() == null ? 0L : video.getId());
            row.put("youtubeId", video.getYoutubeId());
            row.put("tab", video.getTab());
            row.put("tabLabel", video.getTabLabel());
            row.put("title", video.getTitle() == null ? "" : video.getTitle());
            if (includeDescription) {
                row.put("description", video.getDescription() == null ? "" : video.getDescription());
            }
            row.put("publishedOn", video.getPublishedOn() == null ? "" : video.getPublishedOn());
            row.put("publishedLabel", video.getPublishedLabel());
            row.put("duration", video.getDuration() == null ? "" : video.getDuration());
            row.put("views", video.getViews() == null ? 0L : video.getViews());
            row.put("viewCountFmt", video.getViewCountFmt() == null ? "" : video.getViewCountFmt());
            row.put("artistNames", video.getArtistNames() == null ? "" : video.getArtistNames());
            row.put("memberBadges", video.getMemberBadges() == null ? List.of() : video.getMemberBadges());
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

    private static String mediaPageJump(int go, HttpServletRequest request) {
        int target = Math.max(1, go);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/media");
        request.getParameterMap().forEach((name, values) -> {
            if (name == null || values == null || "go".equals(name) || "page".equals(name)) return;
            for (String value : values) {
                if (value != null && !value.isBlank()) builder.queryParam(name, value);
            }
        });
        if (target > 1) builder.queryParam("page", target);
        return "redirect:" + builder.build().encode().toUriString();
    }

    private static List<Map<String, Object>> signalEraTabs(
            String sort,
            String query,
            List<String> members,
            LocalDate from,
            LocalDate to,
            MediaSignalEra selected
    ) {
        String suffix = mediaQuery("", sort, query, members, from, to, false, null);
        List<Map<String, Object>> tabs = new ArrayList<>();
        tabs.add(signalEraTab("전체", "/media?tab=signal" + suffix, selected == null));
        for (MediaSignalEra era : MediaSignalEra.values()) {
            tabs.add(signalEraTab(era.getLabel(), "/media?tab=signal&era=" + era.getKey() + suffix, selected == era));
        }
        return tabs;
    }

    private static Map<String, Object> signalEraTab(String label, String href, boolean active) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("href", href);
        row.put("active", active);
        return row;
    }

    private String normalizeTab(String tab) {
        return switch (tab.toLowerCase()) {
            case "all", "mv", "signal", "behind", "secret", "shorts", "music", "fancam", "contents", "external" -> tab.toLowerCase();
            default -> "mv";
        };
    }

    private List<Map<String, Object>> safeUnitOptions() {
        try {
            List<Map<String, Object>> options = unitService.listUnitCheckboxOptions();
            return options == null ? List.of() : options;
        } catch (RuntimeException ignored) {
            return List.of();
        }
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

    private static boolean shortForm(MediaDto video) {
        return video != null && MediaTab.SHORTS.getKey().equals(video.getTab());
    }

    private static boolean wantsAjax(HttpServletRequest request) {
        String requested = request.getHeader("X-Requested-With");
        if (requested != null && requested.equalsIgnoreCase("XMLHttpRequest")) return true;
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    private static String firstBindingMessage(BindingResult binding, String fallback) {
        if (binding == null || !binding.hasErrors()) return fallback;
        var error = binding.getFieldError();
        if (error != null && error.getDefaultMessage() != null && !error.getDefaultMessage().isBlank()) {
            return error.getDefaultMessage();
        }
        var global = binding.getGlobalError();
        if (global != null && global.getDefaultMessage() != null && !global.getDefaultMessage().isBlank()) {
            return global.getDefaultMessage();
        }
        return fallback;
    }
}
