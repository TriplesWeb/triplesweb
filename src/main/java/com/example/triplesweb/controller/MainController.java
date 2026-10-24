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
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
@Slf4j
public class MainController {

    private static final int HOME_LIMIT = 5;

    private final ArticleServiceImpl articleService;
    private final ScheduleRepository scheduleRepository;
    private final ArchiveService archiveService;

    /** 이전 URL 호환: {@code /main} → {@code /} */
    @GetMapping("/main")
    public String redirectMain() {
        return "redirect:/";
    }

    @GetMapping("/")
    public String mainPage(Model model) {

        List<Schedule> scheduleList = scheduleRepository.findAll();

        List<ScheduleForm> scheduleForms = scheduleList.stream()
                .map(s -> new ScheduleForm(String.valueOf(s.getId()),
                        s.getTitle(),
                        s.getStart(),
                        s.getEnd() != null ? s.getEnd().plusDays(1) : null,
                        s.getEnd()))
                .collect(Collectors.toList());

        scheduleForms.forEach(f -> { Schedule source = scheduleList.stream().filter(s -> String.valueOf(s.getId()).equals(f.getId())).findFirst().orElse(null); if (source != null) f.setArtistNames(source.getArtistNames() == null ? "" : source.getArtistNames()); });

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String schedulesJson = "";
        try {
            schedulesJson = mapper.writeValueAsString(scheduleForms);
        } catch (Exception ignored) {}

        model.addAttribute("noticePosts", articleService.findRecentByCategory("notice", HOME_LIMIT));
        model.addAttribute("recentPhotos", archiveService.pageByType(MediaType.IMAGE, 0, HOME_LIMIT, null)
                .getContent().stream()
                .filter(photo -> photo.getFile() != null && photo.getFile().getUrl() != null)
                .toList());
        model.addAttribute("schedulesJson", schedulesJson);

        return "main";
    }
}