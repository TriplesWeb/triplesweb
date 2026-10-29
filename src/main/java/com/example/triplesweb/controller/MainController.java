package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArticleServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Controller
@Slf4j
public class MainController {

    private static final int HOME_LIMIT = 5;
    private static final long HOME_CACHE_TTL_MS = 30_000L;

    private final ArticleServiceImpl articleService;
    private final ScheduleRepository scheduleRepository;
    private final ArchiveService archiveService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private volatile HomeSnapshot homeSnapshot;
    private volatile long homeSnapshotExpiresAt;

    /** 이전 URL 호환: {@code /main} → {@code /} */
    @GetMapping("/main")
    public String redirectMain() {
        return "redirect:/";
    }

    @GetMapping("/")
    public String mainPage(Model model) {
        HomeSnapshot snapshot = homeSnapshot();
        model.addAttribute("noticePosts", snapshot.noticePosts());
        model.addAttribute("recentPhotos", snapshot.recentPhotos());
        model.addAttribute("schedulesJson", snapshot.schedulesJson());
        return "main";
    }

    private HomeSnapshot homeSnapshot() {
        long now = System.currentTimeMillis();
        HomeSnapshot cached = homeSnapshot;
        if (cached != null && now < homeSnapshotExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (homeSnapshot != null && System.currentTimeMillis() < homeSnapshotExpiresAt) {
                return homeSnapshot;
            }
            HomeSnapshot fresh = buildHome();
            homeSnapshot = fresh;
            homeSnapshotExpiresAt = System.currentTimeMillis() + HOME_CACHE_TTL_MS;
            return fresh;
        }
    }

    private HomeSnapshot buildHome() {
        List<Schedule> scheduleList = scheduleRepository.findAll();
        List<ScheduleForm> scheduleForms = new ArrayList<>(scheduleList.size());
        for (Schedule s : scheduleList) {
            ScheduleForm form = new ScheduleForm(
                    String.valueOf(s.getId()),
                    s.getTitle(),
                    s.getStart(),
                    s.getEnd() != null ? s.getEnd().plusDays(1) : null,
                    s.getEnd());
            form.setArtistNames(s.getArtistNames() == null ? "" : s.getArtistNames());
            scheduleForms.add(form);
        }

        String schedulesJson = "[]";
        try {
            schedulesJson = objectMapper.writeValueAsString(scheduleForms);
        } catch (Exception ignored) {}

        List<?> noticePosts = articleService.findRecentByCategory("notice", HOME_LIMIT);
        List<?> recentPhotos = archiveService.pageByType(MediaType.IMAGE, 0, HOME_LIMIT, null)
                .getContent().stream()
                .filter(photo -> photo.getFile() != null && photo.getFile().getUrl() != null)
                .toList();
        return new HomeSnapshot(noticePosts, recentPhotos, schedulesJson);
    }

    private record HomeSnapshot(List<?> noticePosts, List<?> recentPhotos, String schedulesJson) {}
}
