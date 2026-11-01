package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArticleServiceImpl;
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
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
@Slf4j
public class MainController {

    private static final int HOME_LIMIT = 5;
    private static final int HOME_ARCHIVE_LIMIT = 4;
    private static final String ANDLESS_ALBUM = "LOVE & POP Pt.2";

    private final ArticleServiceImpl articleService;
    private final ScheduleRepository scheduleRepository;
    private final ArchiveService archiveService;
    private final AlbumRepository albumRepository;
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

        String albumHref = albumRepository.findByAlbumName(ANDLESS_ALBUM)
                .map(Album::getAlbumId)
                .map(id -> "/triples/album/" + id)
                .orElse("/triples/album");

        model.addAttribute("albumHref", albumHref);
        model.addAttribute("noticePosts", articleService.findRecentByCategory("notice", HOME_LIMIT));
        model.addAttribute("recentPhotos", archiveService.pageByType(MediaType.IMAGE, 0, HOME_ARCHIVE_LIMIT, null)
                .getContent().stream()
                .filter(photo -> photo.getFile() != null && photo.getFile().getUrl() != null)
                .toList());
        model.addAttribute("recentVideos", archiveService.pageByType(MediaType.VIDEO, 0, HOME_ARCHIVE_LIMIT, null)
                .getContent());
        model.addAttribute("schedulesJson", schedulesJson);

        return "main";
    }
}
