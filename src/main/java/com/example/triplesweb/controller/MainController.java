package com.example.triplesweb.controller;

import com.example.triplesweb.dto.HomeAlbumHero;
import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AlbumService;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.util.SchedulePresentation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
@Slf4j
public class MainController {

    private static final int HOME_ARCHIVE_LIMIT = 4;
    private static final int HOME_UPCOMING_LIMIT = 6;
    private static final DateTimeFormatter HOME_DAY =
            DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN);
    private static final DateTimeFormatter HOME_RANGE_DAY =
            DateTimeFormatter.ofPattern("M.d", Locale.KOREAN);

    private final ScheduleRepository scheduleRepository;
    private final ArchiveService archiveService;
    private final AlbumService albumService;
    private final ArtistSnapshotService artistSnapshot;

    /** 이전 URL 호환: {@code /main} → {@code /} */
    @GetMapping("/main")
    public String redirectMain() {
        return "redirect:/";
    }

    @GetMapping("/")
    public String mainPage(Model model) {

        List<Schedule> scheduleList = scheduleRepository.findAll();
        var artists = artistSnapshot.findAll();
        List<ScheduleForm> scheduleForms = scheduleList.stream()
                .map(schedule -> SchedulePresentation.form(schedule, artists))
                .collect(Collectors.toList());

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String schedulesJson = "[]";
        try {
            schedulesJson = mapper.writeValueAsString(scheduleForms)
                    .replace("<", "\\u003c")
                    .replace("&", "\\u0026");
        } catch (Exception ignored) {}

        HomeAlbumHero homeAlbumHero = albumService.findLatestHomeHero();
        model.addAttribute("homeAlbumHero", homeAlbumHero);
        model.addAttribute("albumHref", homeAlbumHero.getHref());
        model.addAttribute("recentPhotos", archiveService.pageByType(MediaType.IMAGE, 0, HOME_ARCHIVE_LIMIT, null)
                .getContent().stream()
                .filter(photo -> photo.getFile() != null && photo.getFile().getUrl() != null)
                .toList());
        model.addAttribute("recentVideos", archiveService.pageByType(MediaType.VIDEO, 0, HOME_ARCHIVE_LIMIT, null)
                .getContent());
        model.addAttribute("schedulesJson", schedulesJson);
        List<Map<String, String>> upcoming = upcomingSchedules(scheduleForms);
        model.addAttribute("upcomingSchedules", upcoming);
        model.addAttribute("hasUpcomingSchedules", !upcoming.isEmpty());

        return "main";
    }

    static List<Map<String, String>> upcomingSchedules(List<ScheduleForm> scheduleForms) {
        LocalDate today = LocalDate.now();
        return scheduleForms.stream()
                .filter(Objects::nonNull)
                .filter(form -> form.getStart() != null)
                .filter(form -> {
                    LocalDate last = form.getRawEnd() != null ? form.getRawEnd() : form.getStart();
                    return !last.isBefore(today);
                })
                .sorted(Comparator.comparing(ScheduleForm::getStart)
                        .thenComparing(form -> form.getDisplayTitle() == null ? "" : form.getDisplayTitle()))
                .limit(HOME_UPCOMING_LIMIT)
                .map(MainController::upcomingRow)
                .toList();
    }

    private static Map<String, String> upcomingRow(ScheduleForm form) {
        Map<String, String> row = new LinkedHashMap<>();
        String title = form.getDisplayTitle() != null && !form.getDisplayTitle().isBlank()
                ? form.getDisplayTitle()
                : Objects.toString(form.getTitle(), "");
        row.put("displayTitle", title);
        row.put("dateLabel", dateLabel(form));
        row.put("color", form.getColor() != null ? form.getColor() : "#00f5d4");
        if (form.getCategoryLabel() != null && !form.getCategoryLabel().isBlank()) {
            row.put("categoryLabel", form.getCategoryLabel());
        }
        return row;
    }

    private static String dateLabel(ScheduleForm form) {
        LocalDate start = form.getStart();
        LocalDate rawEnd = form.getRawEnd();
        if (start == null) return "";
        if (rawEnd != null && rawEnd.isAfter(start)) {
            return start.format(HOME_RANGE_DAY) + "–" + rawEnd.format(HOME_RANGE_DAY);
        }
        return start.format(HOME_DAY);
    }
}
