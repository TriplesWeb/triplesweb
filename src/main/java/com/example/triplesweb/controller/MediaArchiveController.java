package com.example.triplesweb.controller;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.YoutubeIdExtractor;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MediaArchiveController {
    private final MediaService media;
    private final ArtistRepository artistRepository;
    private final ScheduleRepository scheduleRepository;
    private final AboutAdminAuth aboutAdminAuth;

    @GetMapping("/media")
    public String mediaPage(@RequestParam(name = "tab", defaultValue = "mv") String tab,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) String artist,
                            @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to,
                            HttpServletRequest request,
                            HttpSession session,
                            Model model) {
        tab = normalizeTab(tab);
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), 100);

        model.addAttribute("isAdmin", aboutAdminAuth.isAdmin(request, session));
        model.addAttribute("artistOptions", artistFilterOptions(artist));
        model.addAttribute("currentTab", tab);
        model.addAttribute("isMV", "mv".equals(tab));
        model.addAttribute("isSIGNAL", "signal".equals(tab));
        model.addAttribute("isBehind", "behind".equals(tab));
        model.addAttribute("isSecret", "secret".equals(tab));
        model.addAttribute("isContents", "contents".equals(tab));

        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        final LocalDate rangeStart = fromDate;
        final LocalDate rangeEnd = toDate;
        boolean artistFiltered = artist != null && !artist.isBlank();
        boolean dateFiltered = rangeStart != null || rangeEnd != null;
        List<MediaDto> videos;
        int total;
        if (artistFiltered || dateFiltered) {
            List<MediaDto> filtered = media.allByTab(tab).stream()
                    .filter(v -> !artistFiltered || splitNames(v.getArtistNames()).contains(artist))
                    .filter(v -> matchesPublished(v.getPublishedOn(), rangeStart, rangeEnd))
                    .toList();
            total = filtered.size();
            int startIndex = Math.min((page - 1) * size, total);
            int endIndex = Math.min(startIndex + size, total);
            videos = filtered.subList(startIndex, endIndex);
        } else {
            videos = media.pageByTab(tab, page, size);
            total = media.totalByTab(tab);
        }

        String fromText = rangeStart == null ? "" : rangeStart.toString();
        String toText = rangeEnd == null ? "" : rangeEnd.toString();
        model.addAttribute("from", fromText);
        model.addAttribute("to", toText);
        model.addAttribute("hasDateFilter", dateFiltered);
        model.addAttribute("dateQuery", dateQuery(fromText, toText));
        model.addAttribute("selectedArtist", artist == null ? "" : artist);
        int totalPages = (int) Math.ceil(total / (double) size);
        model.addAttribute("videos", videos);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", Math.max(totalPages, 1));
        boolean hasPrev = page > 1;
        boolean hasNext = page < totalPages;
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", hasPrev ? page - 1 : 1);
        model.addAttribute("nextPage", hasNext ? page + 1 : totalPages);

        List<Map<String, Object>> pages = new ArrayList<>();
        int start = Math.max(1, page - 3);
        int end = Math.min(Math.max(totalPages, 1), start + 6);
        for (int i = start; i <= end; i++) {
            pages.add(Map.of("num", i, "active", i == page));
        }
        model.addAttribute("pages", pages);
        return "archive/media";
    }

    @GetMapping("/media/{tab}/{youtubeId}")
    public String mediaDetail(@PathVariable String tab,
                              @PathVariable String youtubeId,
                              Model model) throws IOException {
        MediaTab t = MediaTab.from(tab);
        MediaDto video = media.findOne(t, youtubeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없습니다."));
        model.addAttribute("currentTab", t.getKey());
        model.addAttribute("tabNameKo", t.getKor());
        model.addAttribute("video", video);
        model.addAttribute("related", media.related(t, youtubeId, 8));

        List<String> names = splitNames(video.getArtistNames());
        List<Schedule> relatedSchedules = scheduleRepository.findAll().stream()
                .filter(s -> !names.isEmpty()
                        && splitNames(s.getArtistNames()).stream().anyMatch(names::contains))
                .toList();
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
            if (form.getDescription() == null || form.getDescription().isBlank()) {
                Object description = info.get("description");
                if (description != null) form.setDescription(description.toString());
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

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDate.parse(raw.trim()); }
        catch (DateTimeParseException ex) { return null; }
    }

    private static boolean matchesPublished(String publishedOn, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        LocalDate day = parseDate(publishedOn);
        if (day == null) return false;
        if (from != null && day.isBefore(from)) return false;
        return to == null || !day.isAfter(to);
    }

    private static String dateQuery(String from, String to) {
        StringBuilder query = new StringBuilder();
        if (from != null && !from.isBlank()) query.append("&from=").append(java.net.URLEncoder.encode(from, java.nio.charset.StandardCharsets.UTF_8));
        if (to != null && !to.isBlank()) query.append("&to=").append(java.net.URLEncoder.encode(to, java.nio.charset.StandardCharsets.UTF_8));
        return query.toString();
    }

    private List<Map<String, Object>> artistFilterOptions(String selectedArtist) {
        List<Map<String, Object>> options = new ArrayList<>();
        String selected = selectedArtist == null ? "" : selectedArtist.trim();
        for (Artist artist : artistRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", artist.getName());
            row.put("selected", selected.equals(artist.getName()));
            options.add(row);
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

    private List<String> splitNames(String names) {
        if (names == null || names.isBlank()) return List.of();
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeTab(String tab) {
        return switch (tab.toLowerCase()) {
            case "mv", "signal", "behind", "secret", "contents" -> tab.toLowerCase();
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
