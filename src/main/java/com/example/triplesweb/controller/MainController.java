package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.ArticleServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
@Slf4j
public class MainController {

    private final ArticleServiceImpl articleService;
    private final ScheduleRepository scheduleRepository;

    /** 이전 URL 호환: {@code /main} → {@code /} */
    @GetMapping("/main")
    public String redirectMain() {
        return "redirect:/";
    }

    @GetMapping("/")
    public String mainPage(Model model, @RequestParam(defaultValue = "5") Integer limit) {
        // [수정 완료] 로그인 세션(loginMember) 체크 로직 및 관련 모델 속성(userId, level, points 등) 제거

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

        model.addAttribute("freePosts",   articleService.findRecentByCategory("free",   limit));
        model.addAttribute("noticePosts", articleService.findRecentByCategory("notice", limit));
        model.addAttribute("popularPosts",articleService.findPopularArticles(null, limit));
        model.addAttribute("schedulesJson", schedulesJson);

        return "main";
    }
}